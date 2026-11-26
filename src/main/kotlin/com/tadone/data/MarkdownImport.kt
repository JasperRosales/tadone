package com.tadone.data

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.File

/**
 * Turns study files into Markdown entirely on this computer (PDFBox + Apache POI):
 * no API calls, no Gemini tokens. Supported: .pdf .pptx .ppt .docx .md .markdown .txt
 */
object MarkdownImport {
    val extensions = setOf("pdf", "pptx", "ppt", "docx", "md", "markdown", "txt")
    private const val MAX_BYTES = 60L * 1024 * 1024

    data class Result(val title: String, val markdown: String)

    fun convert(f: File): Result {
        require(f.isFile) { "File not found: ${f.name}" }
        require(f.length() <= MAX_BYTES) { "${f.name} is larger than 60 MB." }
        val md = when (f.extension.lowercase()) {
            "pdf" -> pdf(f)
            "pptx" -> pptx(f)
            "ppt" -> ppt(f)
            "docx" -> docx(f)
            "md", "markdown", "txt" -> f.readText()
            else -> throw IllegalArgumentException("Tadone can't read .${f.extension} files yet.")
        }.let(::tidy)
        if (md.isBlank()) throw IllegalStateException("${f.name} has no readable text (it may be scanned images).")
        return Result(f.nameWithoutExtension.replace('_', ' ').trim(), md)
    }

    // ---------- PowerPoint ----------
    private fun pptx(f: File): String = XMLSlideShow(f.inputStream().buffered()).use { show ->
        buildString {
            show.slides.forEachIndexed { i, slide ->
                val title = slide.title?.trim().orEmpty()
                appendLine("## Slide ${i + 1}${if (title.isNotBlank()) ": $title" else ""}").appendLine()
                slide.shapes.forEach { shape(it, title, this) }
                slide.notes?.let { n ->
                    val text = n.shapes.filterIsInstance<XSLFTextShape>()
                        .filter { it.textType?.name != "SLIDE_NUMBER" && it.placeholder?.name != "SLIDE_IMAGE" }
                        .joinToString("\n") { it.text.trim() }.trim()
                    if (text.isNotBlank()) appendLine("> Speaker notes: ${text.replace("\n", " ")}").appendLine()
                }
            }
        }
    }

    private fun shape(s: XSLFShape, title: String, out: StringBuilder) {
        when (s) {
            is XSLFGroupShape -> s.shapes.forEach { shape(it, title, out) }
            is XSLFTable -> {
                val rows = s.rows.map { r -> r.cells.map { it.text.trim().replace("|", "\\|").replace("\n", " ") } }
                if (rows.isNotEmpty()) {
                    out.appendLine("| " + rows[0].joinToString(" | ") + " |")
                    out.appendLine("|" + rows[0].joinToString("|") { " --- " } + "|")
                    rows.drop(1).forEach { out.appendLine("| " + it.joinToString(" | ") + " |") }
                    out.appendLine()
                }
            }
            is XSLFTextShape -> {
                if (s.text.trim() == title) return
                s.textParagraphs.forEach { p ->
                    val t = p.text.trim()
                    if (t.isNotEmpty()) out.appendLine("  ".repeat(p.indentLevel.coerceIn(0, 4)) + "- " + t)
                }
                out.appendLine()
            }
            else -> {}
        }
    }

    private fun ppt(f: File): String = HSLFSlideShow(f.inputStream().buffered()).use { show ->
        buildString {
            show.slides.forEachIndexed { i, slide ->
                val title = slide.title?.trim().orEmpty()
                appendLine("## Slide ${i + 1}${if (title.isNotBlank()) ": $title" else ""}").appendLine()
                slide.shapes.filterIsInstance<HSLFTextShape>().forEach { s ->
                    if (s.text.trim() == title) return@forEach
                    s.textParagraphs.forEach { p ->
                        val t = p.joinToString("") { it.rawText }.trim()
                        if (t.isNotEmpty()) appendLine("  ".repeat(p.indentLevel.coerceIn(0, 4)) + "- " + t)
                    }
                }
                appendLine()
            }
        }
    }

    // ---------- Word ----------
    private fun docx(f: File): String = XWPFDocument(f.inputStream().buffered()).use { doc ->
        buildString {
            doc.bodyElements.forEach { el ->
                when (el) {
                    is XWPFParagraph -> {
                        val t = el.text.trim()
                        if (t.isEmpty()) return@forEach
                        val style = el.style.orEmpty().lowercase()
                        val level = Regex("heading\\s?(\\d)").find(style)?.groupValues?.get(1)?.toIntOrNull()
                        when {
                            style == "title" -> appendLine("# $t").appendLine()
                            level != null -> appendLine("#".repeat((level + 1).coerceAtMost(6)) + " " + t).appendLine()
                            el.numID != null -> appendLine("  ".repeat((el.numIlvl?.toInt() ?: 0).coerceIn(0, 4)) + "- " + t)
                            else -> appendLine(t).appendLine()
                        }
                    }
                    is XWPFTable -> {
                        val rows = el.rows.map { r -> r.tableCells.map { it.text.trim().replace("|", "\\|").replace("\n", " ") } }
                        if (rows.isNotEmpty()) {
                            appendLine("| " + rows[0].joinToString(" | ") + " |")
                            appendLine("|" + rows[0].joinToString("|") { " --- " } + "|")
                            rows.drop(1).forEach { appendLine("| " + it.joinToString(" | ") + " |") }
                            appendLine()
                        }
                    }
                }
            }
        }
    }

    // ---------- PDF ----------
    /** Lines whose font is clearly bigger than the body text become headings; bullets become list items. */
    private fun pdf(f: File): String = Loader.loadPDF(f).use { doc ->
        val lines = mutableListOf<Pair<String, Float>>() // text, font size
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, positions: MutableList<TextPosition>) {
                val size = positions.map { it.fontSizeInPt }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
                lines += text to size
            }
        }
        stripper.sortByPosition = true
        val pages = doc.numberOfPages
        val out = StringBuilder()
        for (p in 1..pages) {
            lines.clear()
            stripper.startPage = p; stripper.endPage = p
            stripper.getText(doc)
            val body = lines.filter { it.first.isNotBlank() }.map { it.second }.sorted().let { if (it.isEmpty()) 0f else it[it.size / 2] }
            if (pages > 1) out.appendLine("<!-- page $p -->")
            var para = StringBuilder()
            var pendingBullet = false // PDFBox often reports the bullet glyph as its own text run
            fun flush() { if (para.isNotBlank()) out.appendLine(para.toString().trim()).appendLine(); para = StringBuilder() }
            for ((raw, size) in lines) {
                val t = raw.trim()
                if (t.isEmpty()) { flush(); continue }
                if (Regex("^\\d{1,3}$").matches(t)) continue // page numbers
                val bullet = Regex("^(?:[\\u2022\\u25AA\\u25CF\\u2023\\u2043\\u25E6\\u00B7\\uF0B7\\uF0A7\\u2013*]|-(?=[\\s\\u00A0]))[\\s\\u00A0]*").find(t)
                val numbered = Regex("^\\d{1,2}[.)]\\s+").find(t)
                if (bullet != null && t.substring(bullet.range.last + 1).isBlank()) { flush(); pendingBullet = true; continue }
                if (pendingBullet) { pendingBullet = false; out.appendLine("- $t"); continue }
                when {
                    body > 0 && size >= body * 1.35f && t.length < 90 -> { flush(); out.appendLine((if (size >= body * 1.8f) "## " else "### ") + t).appendLine() }
                    bullet != null -> { flush(); out.appendLine("- " + t.substring(bullet.range.last + 1).trim()) }
                    numbered != null -> { flush(); out.appendLine(t) }
                    else -> {
                        // join wrapped lines; repair hyphenated line breaks
                        if (para.endsWith("-")) para.setLength(para.length - 1) else if (para.isNotEmpty()) para.append(' ')
                        para.append(t)
                        if (t.endsWith(".") || t.endsWith("?")) flush()
                    }
                }
            }
            flush()
        }
        out.toString()
    }

    private fun tidy(md: String) = md.replace("\r\n", "\n").replace('\u00A0', ' ')
        .replace(Regex("([^\n])\n(#{1,6} )"), "$1\n\n$2").replace('\u000B', '\n').replace(Regex("[ \\t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n").trim()
}
