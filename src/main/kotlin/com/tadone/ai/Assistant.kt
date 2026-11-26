package com.tadone.ai

import com.tadone.data.ChatMsg
import com.tadone.data.Task
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date

const val DEFAULT_MODEL = "gemini-2.5-flash"

/** Tadone's AI brain: a study helper that knows the student's task list. */
class Assistant(apiKey: String, private val model: String = DEFAULT_MODEL) {
    private val key = apiKey.ifBlank { System.getenv("GEMINI_API_KEY").orEmpty() }
    private val http = OkHttpClient.Builder()
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()

    private val fmt = SimpleDateFormat("EEE, MMM d yyyy HH:mm")

    private val persona = """
        You are Tadone, a friendly study helper living inside a student's task app.
        Help the student understand, plan and finish their tasks: explain concepts, break work into steps,
        suggest outlines, review drafts and keep them on schedule. Guide their learning — don't just hand over
        finished graded work; when asked for an answer, explain the reasoning so they can write it themselves.
        Be concise and warm. Use short Markdown lists when helpful. Never use emojis.
    """.trimIndent()

    private fun describe(t: Task) = buildString {
        append("- [${if (t.done) "x" else " "}] ${t.title}")
        if (t.course.isNotBlank()) append(" (${t.course})")
        t.dueAt?.let { append(" — due ${fmt.format(Date(it))}") }
        if (t.isClassroom) append(" [Classroom: ${t.submissionState ?: "unknown"}]")
    }

    private fun send(system: String, history: List<ChatMsg>, maxTokens: Int? = null, json: Boolean = false): String {
        if (key.isBlank()) error("Add your Gemini API key in Settings first.")
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                history.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == "user") "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", m.content) } }
                    }
                }
            }
            if (maxTokens != null || json) putJsonObject("generationConfig") {
                if (maxTokens != null) put("maxOutputTokens", maxTokens)
                if (json) put("responseMimeType", "application/json")
            }
        }
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val text = http.newCall(req).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching {
                    Json.parseToJsonElement(raw).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
                }.getOrDefault(raw.take(300))
                error("Gemini error ${r.code}: $msg")
            }
            raw
        }
        val res = Json.parseToJsonElement(text).jsonObject
        if (res["promptFeedback"]?.jsonObject?.get("blockReason") != null)
            return "I can't help with that one — try rephrasing what you need."
        val cand = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return "I can't help with that one — try rephrasing what you need."
        val parts = cand["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val out = parts.mapNotNull { p ->
            val o = p.jsonObject
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null
            else o["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("").trim()
        if (out.isEmpty() && cand["finishReason"]?.jsonPrimitive?.contentOrNull == "SAFETY")
            return "I can't help with that one — try rephrasing what you need."
        return out
    }

    /** Chat about everything (taskId = null) or one specific task. */
    fun chat(history: List<ChatMsg>, allTasks: List<Task>, focus: Task?): String {
        val now = fmt.format(Date())
        val sys = buildString {
            appendLine(persona)
            appendLine("\nCurrent time: $now")
            appendLine("\nThe student's tasks:")
            allTasks.take(60).forEach { appendLine(describe(it)) }
            if (focus != null) {
                appendLine("\nThe student is working on this task right now:")
                appendLine(describe(focus))
                if (focus.notes.isNotBlank()) appendLine("Details:\n${focus.notes.take(6000)}")
            }
        }
        return send(sys, history)
    }

    /** Turns free text like "math worksheet due friday" into a task. */
    fun quickAdd(text: String): Task {
        val sys = """
            Convert the student's note into a task. Current time: ${fmt.format(Date())}.
            Reply with ONLY a JSON object: {"title": string, "course": string, "notes": string,
            "due": "YYYY-MM-DDTHH:MM" or null, "priority": 0|1|2}. No prose.
        """.trimIndent()
        val raw = send(sys, listOf(ChatMsg(taskId = null, role = "user", content = text)))
        val obj = runCatching {
            Json.parseToJsonElement(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)).jsonObject
        }.getOrNull() ?: return Task(title = text)
        fun s(k: String) = (obj[k] as? JsonPrimitive)?.contentOrNull
        val due = s("due")?.let { runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(it).time }.getOrNull() }
        return Task(
            title = s("title") ?: text, course = s("course").orEmpty(), notes = s("notes").orEmpty(),
            dueAt = due, priority = s("priority")?.toIntOrNull()?.coerceIn(0, 2) ?: 1,
        )
    }

    /** Rewrites raw extracted text as a structured study note (same layout as the local structurer). */
    fun structureNotes(title: String, raw: String): String {
        val sys = """
            Turn the student's raw course material into clear study notes in Markdown, using exactly this layout:
            # <title>
            ## Overview
            - 2 to 4 bullets on what the material covers
            ## Key terms
            - **Term**: short definition   (every important term, one per line)
            ## <one heading per topic, in the material's order>
            - one fact or idea per bullet, short and precise; nest sub-points with two spaces
            Keep every fact from the material; do not invent anything. No emojis, no extra commentary.
        """.trimIndent()
        val out = send(sys, listOf(ChatMsg(taskId = null, role = "user", content = "Title: $title\n\n${raw.take(60_000)}")))
        return out.removePrefix("```markdown").removePrefix("```md").removePrefix("```").removeSuffix("```").trim() + "\n"
    }

    /** Builds a multiple-choice practice quiz from a note. Returns the quiz as JSON text (see [Quiz.parse]). */
    fun quiz(title: String, course: String, body: String, count: Int): String {
        val sys = """
            You write practice quizzes for a student from their own study notes.
            Write exactly $count multiple-choice questions that test understanding of the notes, not trivia about wording.
            Mix recall, application and "which is NOT" style questions. Each question has exactly 4 choices and one correct answer.
            Base every question only on the notes. Never use emojis.
            Reply with ONLY JSON: {"questions":[{"question":string,"choices":[string,string,string,string],"answer":0-3,"explanation":string}]}
        """.trimIndent()
        val prompt = buildString {
            if (title.isNotBlank()) appendLine("Title: $title")
            if (course.isNotBlank()) appendLine("Subject: $course")
            appendLine("Notes:"); append(body.take(30_000))
        }
        val raw = send(sys, listOf(ChatMsg(taskId = null, role = "user", content = prompt)), json = true)
        val quiz = Quiz.parse(raw)
        if (quiz.isEmpty()) error("Gemini didn't return a usable quiz. Try again.")
        return Quiz.toJson(quiz)
    }
}

data class QuizQuestion(val question: String, val choices: List<String>, val answer: Int, val explanation: String)

object Quiz {
    fun parse(raw: String): List<QuizQuestion> = runCatching {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        Json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject["questions"]!!.jsonArray.mapNotNull { q ->
            val o = q.jsonObject
            val choices = o["choices"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val answer = o["answer"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val text = o["question"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (choices.size < 2 || answer !in choices.indices) null
            else QuizQuestion(text, choices, answer, o["explanation"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }
    }.getOrDefault(emptyList())

    fun toJson(qs: List<QuizQuestion>): String = buildJsonObject {
        putJsonArray("questions") {
            qs.forEach { q ->
                addJsonObject {
                    put("question", q.question)
                    putJsonArray("choices") { q.choices.forEach { add(it) } }
                    put("answer", q.answer); put("explanation", q.explanation)
                }
            }
        }
    }.toString()
}
