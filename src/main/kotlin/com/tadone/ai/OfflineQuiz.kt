package com.tadone.ai

import kotlin.random.Random

/**
 * "Quick quiz": builds multiple-choice questions straight from a Markdown note on this computer,
 * with zero Gemini calls. It works best on structured notes (headings, bullets, "Term: meaning").
 */
object OfflineQuiz {
    data class Definition(val term: String, val meaning: String, val source: String)
    private data class Section(val heading: String, val points: List<String>)

    private val defLine = Regex("^\\**([A-Za-z][\\w ()/'-]{1,40}?)\\**\\s*(?::|\\s[-\\u2013\\u2014]\\s)\\s*(.{8,220})$")
    private val isAre = Regex("^(?:The |An |A )?([A-Z][\\w -]{1,40}?) (?:is|are|refers to|means) (?:the |a |an )?(.{8,200})$")

    fun generate(markdown: String, count: Int, rnd: Random = Random(System.nanoTime())): List<QuizQuestion> {
        val sections = sections(markdown)
        val defs = definitions(markdown)
        val terms = keyTerms(markdown, defs)
        val out = mutableListOf<QuizQuestion>()
        val usedStems = mutableSetOf<String>()
        fun add(q: QuizQuestion?) { if (q != null && usedStems.add(q.question.lowercase())) out += q }

        // 1) definitions both ways; when a note has few definitions, other bullet points / terms fill the wrong answers
        // bullet points used as wrong answers, with any "Term:" prefix removed so they don't give themselves away
        val allPoints = sections.flatMap { it.points }.map { p -> defLine.find(p)?.groupValues?.get(2)?.trim() ?: p }
        defs.shuffled(rnd).forEachIndexed { i, d ->
            val others = defs.filter { it.term != d.term }
            val meaningPool = others.map { it.meaning } + allPoints.filter { !it.contains(d.term, true) && !it.contains(d.meaning, true) }
            val termPool = others.map { it.term } + terms.filter { !it.equals(d.term, true) }
            if (i % 2 == 0) add(choice("Which best describes \"${d.term}\"?", d.meaning, meaningPool, d.source, rnd))
            else add(choice("\"${d.meaning.trimEnd('.')}\" describes which term?", d.term, termPool, d.source, rnd))
        }
        // 2) which point belongs to which section
        // study-note scaffolding sections make poor "which belongs under" questions
        val withPoints = sections.filter { it.points.isNotEmpty() && it.heading.lowercase() !in setOf("key terms", "overview", "summary", "source") }
        if (withPoints.size >= 2) withPoints.shuffled(rnd).forEach { sec ->
            val right = sec.points.random(rnd)
            val wrong = withPoints.filter { it !== sec }.flatMap { it.points }.filter { it != right }
            add(choice("Which of these belongs under \"${sec.heading}\"?", right, wrong, "From the section \"${sec.heading}\": $right", rnd))
        }
        // 3) fill in the blank on key terms
        if (terms.size >= 4) sentences(markdown).shuffled(rnd).forEach { s ->
            if (defs.any { d -> s.startsWith(d.term) }) return@forEach // already asked as a definition
            val term = terms.firstOrNull { t -> Regex("\\b${Regex.escape(t)}\\b").containsMatchIn(s) } ?: return@forEach
            val stem = s.replaceFirst(Regex("\\b${Regex.escape(term)}\\b"), "_____")
            add(choice("Fill in the blank: $stem", term, terms.filter { it != term }, s, rnd))
        }
        // interleave kinds a little, then cut to size
        return out.shuffled(rnd).take(count)
    }

    private fun choice(question: String, right: String, pool: List<String>, why: String, rnd: Random): QuizQuestion? {
        val wrong = pool.map { it.trim() }.filter { it.isNotBlank() && !it.equals(right, true) }.distinct().shuffled(rnd).take(3)
        if (wrong.size < 3) return null
        val choices = (wrong + right).shuffled(rnd)
        return QuizQuestion(question, choices.map { it.trimEnd('.').take(160) }, choices.indexOf(right), why.take(300))
    }

    private fun clean(line: String) = line.trim().removePrefix(">").trim()
        .replace(Regex("^[-*+]\\s+|^\\d+[.)]\\s+"), "")
        .replace(Regex("[*_`]"), "").trim()

    private fun sections(md: String): List<Section> {
        val out = mutableListOf<Section>()
        var heading: String? = null
        var points = mutableListOf<String>()
        fun push() { heading?.let { h -> out += Section(h, points.toList()) }; points = mutableListOf() }
        md.lines().forEach { raw ->
            val l = raw.trim()
            when {
                l.startsWith("#") -> { push(); heading = l.trimStart('#').trim().replace(Regex("^Slide \\d+:?\\s*"), "").ifBlank { null } }
                Regex("^\\s*([-*+]|\\d+[.)])\\s+").containsMatchIn(raw) -> clean(l).takeIf { it.length in 4..160 }?.let(points::add)
            }
        }
        push()
        return out.filter { it.heading.isNotBlank() }
    }

    fun definitions(md: String): List<Definition> {
        val out = linkedMapOf<String, Definition>()
        md.lines().map(::clean).filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("|") && !it.startsWith("<!--") }.forEach { line ->
            // a line may hold several "Term: meaning." sentences
            line.split(Regex("(?<=[.?!])\\s+")).forEach { sentence ->
                val m = defLine.find(sentence) ?: isAre.find(sentence)
                if (m != null) {
                    val term = m.groupValues[1].trim(); val meaning = m.groupValues[2].trim()
                    val firstWord = meaning.substringBefore(' ').lowercase()
                    val ok = term.split(" ").size <= 5 && term.lowercase() !in labels && !term.startsWith("Speaker notes", true) &&
                        !(m.groups.size > 0 && isAre.matches(sentence) && (firstWord.endsWith("ed") || firstWord in participles))
                    if (ok && term.lowercase() !in out) out[term.lowercase()] = Definition(term, meaning, sentence.trim())
                }
            }
        }
        return out.values.toList()
    }

    /** Definition terms, plus acronyms / capitalised terms that repeat (e.g. ATP, Krebs Cycle). */
    private fun keyTerms(md: String, defs: List<Definition>): List<String> {
        val text = md.lines().filterNot { it.trim().startsWith("<!--") }.joinToString(" ")
        val counts = Regex("\\b([A-Z]{2,6}\\d?|[A-Z][a-z]{3,}(?: [A-Z][a-z]{3,})?)\\b").findAll(text).map { it.value }
            .filter { it.lowercase() !in stop && it.lowercase() !in labels }.groupingBy { it }.eachCount()
        return (defs.map { it.term } + counts.filter { it.value >= 2 }.keys).distinctBy { it.lowercase() }
    }

    private fun sentences(md: String): List<String> = md.lines()
        .filter { val t = it.trim(); t.isNotBlank() && !t.startsWith("#") && !t.startsWith("|") && !t.startsWith("<!--") }
        .map(::clean).flatMap { it.split(Regex("(?<=[.?!])\\s+")) }
        .map { it.trim() }.filter { it.split(" ").size in 6..40 }

    /** Generic field labels that look like "Term: value" but aren't concepts worth quizzing. */
    private val labels = setOf("location", "net gain", "example", "examples", "e.g", "note", "notes", "date", "time", "type", "result", "results",
        "answer", "step", "steps", "purpose", "input", "output", "inputs", "outputs", "summary", "source", "sources", "reference", "references",
        "author", "page", "due", "name", "total", "remember", "tip", "important", "objective", "objectives", "covers", "source")
    private val participles = setOf("split", "made", "used", "found", "known", "called", "done", "given", "taken", "seen", "built", "held", "kept", "broken")

    private val stop = setOf("slide", "this", "that", "these", "there", "they", "their", "when", "what", "which", "with", "from",
        "into", "page", "speaker", "notes", "note", "also", "then", "each", "some", "most", "many", "after", "before", "during")
}
