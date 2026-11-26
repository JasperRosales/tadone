package com.tadone.data

import com.tadone.ai.OfflineQuiz

/**
 * Turns raw extracted text (Markdown from [MarkdownImport]) into a structured study note, on this computer:
 *
 *   # Title
 *   ## Overview      - what the material covers
 *   ## Key terms     - **Term**: meaning   (every definition found)
 *   ## <Section>     - one bullet per key point, per heading / slide
 *
 * This is the shape the Quick quiz reads best, so a structured note can be quizzed with no Gemini calls.
 */
object StudyNotes {
    private data class Section(var title: String, val points: MutableList<String> = mutableListOf(), val tables: MutableList<String> = mutableListOf())

    fun structure(raw: String, title: String, sourceName: String? = null): String {
        val sections = sections(raw)
        val defs = OfflineQuiz.definitions(raw)
        return buildString {
            appendLine("# ${title.ifBlank { "Study notes" }}")
            if (sourceName != null) appendLine().appendLine("> Source: $sourceName")
            appendLine()

            appendLine("## Overview")
            val named = sections.map { it.title }.filter { it.isNotBlank() && !it.equals(title, true) }.distinct()
            if (named.isNotEmpty()) appendLine("- Covers: ${named.take(12).joinToString(", ")}${if (named.size > 12) ", and more" else ""}")
            sections.flatMap { it.points }.map { it.trimStart().removePrefix("- ") }
                .firstOrNull { p -> p.split(" ").size >= 8 && defs.none { d -> p.startsWith(d.term) } }?.let { appendLine("- $it") }
            appendLine("- ${sections.size} section${if (sections.size == 1) "" else "s"}, ${defs.size} key term${if (defs.size == 1) "" else "s"}")
            appendLine()

            if (defs.isNotEmpty()) {
                appendLine("## Key terms")
                defs.forEach { appendLine("- **${it.term}**: ${it.meaning.trimEnd()}") }
                appendLine()
            }

            sections.forEach { sec ->
                // drop points that are just a definition already listed under Key terms
                val points = sec.points.filterNot { p -> val c = p.trimStart().removePrefix("- "); defs.any { d -> c.startsWith(d.term) && c.contains(d.meaning.take(20)) } }
                if (points.isEmpty() && sec.tables.isEmpty()) return@forEach
                appendLine("## ${sec.title.ifBlank { "Notes" }}")
                points.forEach { appendLine(it) }
                sec.tables.forEach { appendLine().appendLine(it) }
                appendLine()
            }
        }.trim() + "\n"
    }

    /** Groups content under headings (slides with the same title are merged); each point is a ready Markdown bullet line. */
    private fun sections(md: String): List<Section> {
        val out = mutableListOf<Section>()
        var cur = Section("")
        var table = StringBuilder()
        fun flushTable() { if (table.isNotBlank()) cur.tables += table.toString().trimEnd(); table = StringBuilder() }
        fun push() { flushTable(); if (cur.points.isNotEmpty() || cur.tables.isNotEmpty()) out += cur }

        md.lines().forEach { raw ->
            val l = raw.trim()
            when {
                l.isEmpty() -> flushTable()
                l.startsWith("<!--") -> {}
                l.startsWith("#") -> {
                    val h = l.trimStart('#').trim().replace(Regex("^Slide \\d+:?\\s*"), "").trim()
                    val existing = out.lastOrNull()?.takeIf { h.isNotBlank() && it.title.equals(h, true) } // "(cont.)" slides
                    push()
                    cur = existing?.also { out.removeAt(out.lastIndex) } ?: Section(h)
                }
                l.startsWith("|") -> table.appendLine(l)
                l.startsWith(">") -> l.removePrefix(">").trim().removePrefix("Speaker notes:").trim()
                    .takeIf { it.isNotBlank() && !it.startsWith("Source:") }?.let { points(it).forEach { p -> cur.points += "- $p" } }
                else -> {
                    val indent = raw.takeWhile { it == ' ' }.length / 2
                    val text = l.replace(Regex("^([-*+\\u2022]|\\d+[.)])\\s+"), "").trim()
                    if (indent > 0 && Regex("^\\s*[-*+]").containsMatchIn(raw)) cur.points += "  ".repeat(indent.coerceAtMost(3)) + "- " + text
                    else points(text).forEach { p -> cur.points += "- $p" }
                }
            }
        }
        push()
        return out
    }

    /** Splits prose into sentence-sized points; keeps short lines (already bullet-like) as they are. */
    private fun points(text: String): List<String> {
        val t = text.replace(Regex("\\s+"), " ").trim()
        if (t.length < 4) return emptyList()
        return t.split(Regex("(?<=[.?!])\\s+(?=[A-Z0-9\"(])")).map { it.trim() }.filter { it.length >= 4 }
    }
}
