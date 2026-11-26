package com.tadone

import com.tadone.data.MarkdownImport
import org.apache.poi.xslf.usermodel.SlideLayout
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.File

/** Dev check: converts every file passed on the command line (and a generated .pptx) to Markdown. */
fun main(args: Array<String>) {
    val dir = File(args.first())
    XMLSlideShow().use { show ->
        val master = show.slideMasters[0]
        fun slide(title: String, vararg bullets: String) {
            val s = show.createSlide(master.getLayout(SlideLayout.TITLE_AND_CONTENT))
            s.placeholders[0].text = title
            val body = s.placeholders[1]; body.clearText()
            bullets.forEach { b -> body.addNewTextParagraph().apply { if (b.startsWith("  ")) indentLevel = 1 }.addNewTextRun().setText(b.trim()) }
        }
        slide("Photosynthesis", "Happens in chloroplasts", "Light reactions", "  Produce ATP and NADPH", "Calvin cycle: fixes carbon dioxide into sugar")
        slide("Key terms", "Chlorophyll: the pigment that absorbs light", "Stomata: pores that let gases in and out")
        File(dir, "sample.pptx").outputStream().use(show::write)
    }
    dir.listFiles()!!.filter { it.extension in MarkdownImport.extensions && it.extension != "md" }.sortedBy { it.name }.forEach { f ->
        val t0 = System.currentTimeMillis()
        val r = MarkdownImport.convert(f)
        println("===== ${f.name} -> '${r.title}' (${System.currentTimeMillis() - t0} ms)")
        val t1 = System.currentTimeMillis()
        val structured = com.tadone.data.StudyNotes.structure(r.markdown, r.title, f.name)
        println("--- structured note:\n$structured")
        val qs = com.tadone.ai.OfflineQuiz.generate(structured, 6, kotlin.random.Random(7))
        println("quick quiz: ${qs.size} questions in ${System.currentTimeMillis() - t1} ms")
        qs.forEach { q -> println("  Q: ${q.question}"); q.choices.forEachIndexed { i, c -> println("     ${if (i == q.answer) "*" else " "} $c") } }
    }
}
