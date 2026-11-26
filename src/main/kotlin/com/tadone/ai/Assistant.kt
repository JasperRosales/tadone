package com.tadone.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.MessageCreateParams
import com.tadone.data.ChatMsg
import com.tadone.data.Task
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date

const val DEFAULT_MODEL = "claude-opus-5-5"

/** Tadone's AI brain: a study helper that knows the student's task list. */
class Assistant(apiKey: String, private val model: String = DEFAULT_MODEL) {
    private val client: AnthropicClient =
        if (apiKey.isNotBlank()) AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        else AnthropicOkHttpClient.fromEnv()

    private val fmt = SimpleDateFormat("EEE, MMM d yyyy HH:mm")

    private val persona = """
        You are Tadone, a friendly study helper living inside a student's task app.
        Help the student understand, plan and finish their tasks: explain concepts, break work into steps,
        suggest outlines, review drafts and keep them on schedule. Guide their learning — don't just hand over
        finished graded work; when asked for an answer, explain the reasoning so they can write it themselves.
        Be concise and warm. Use short Markdown lists when helpful.
    """.trimIndent()

    private fun describe(t: Task) = buildString {
        append("- [${if (t.done) "x" else " "}] ${t.title}")
        if (t.course.isNotBlank()) append(" (${t.course})")
        t.dueAt?.let { append(" — due ${fmt.format(Date(it))}") }
        if (t.isClassroom) append(" [Classroom: ${t.submissionState ?: "unknown"}]")
    }

    private fun send(system: String, history: List<ChatMsg>, maxTokens: Long = 16000L): String {
        val b = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .system(system)
            // Server-side fallback if a request is declined by safety classifiers
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        history.forEach { m -> if (m.role == "user") b.addUserMessage(m.content) else b.addAssistantMessage(m.content) }
        val res = client.beta().messages().create(b.build())
        if (res.stopReason().map { it.toString() }.orElse("") == "refusal")
            return "I can't help with that one — try rephrasing what you need."
        return res.content().mapNotNull { blk -> blk.text().map { it.text() }.orElse(null) }.joinToString("").trim()
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
        val raw = send(sys, listOf(ChatMsg(taskId = null, role = "user", content = text)), 1024)
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
}
