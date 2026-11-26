package com.tadone

import androidx.compose.runtime.*
import com.tadone.ai.Assistant
import com.tadone.ai.DEFAULT_MODEL
import com.tadone.data.*
import kotlinx.coroutines.*
import java.io.File

enum class Screen { Tasks, Assistant, Settings }
enum class Filter { Open, Today, Classroom, Done, All }

class AppState(private val scope: CoroutineScope) {
    var screen by mutableStateOf(Screen.Tasks)
    var filter by mutableStateOf(Filter.Open)
    var tasks by mutableStateOf(listOf<Task>())
    var selectedId by mutableStateOf<Long?>(null)
    var busy by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    var thinking by mutableStateOf(false)
    var chat by mutableStateOf(listOf<ChatMsg>())

    val selected get() = tasks.firstOrNull { it.id == selectedId }

    val visible: List<Task>
        get() {
            val endOfDay = java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            return when (filter) {
                Filter.Open -> tasks.filter { !it.done }
                Filter.Today -> tasks.filter { !it.done && it.dueAt != null && it.dueAt < endOfDay }
                Filter.Classroom -> tasks.filter { it.isClassroom }
                Filter.Done -> tasks.filter { it.done }
                Filter.All -> tasks
            }
        }

    private fun composio() = Composio(Db.get(Keys.COMPOSIO_KEY), Db.get(Keys.COMPOSIO_USER, "default"))
    private fun ai() = Assistant(Db.get(Keys.ANTHROPIC_KEY), Db.get(Keys.MODEL, DEFAULT_MODEL))

    fun reload() { tasks = Db.tasks() }

    private fun io(label: String?, block: suspend () -> Unit) = scope.launch {
        busy = label
        try { withContext(Dispatchers.IO) { block() } }
        catch (e: Exception) { toast = e.message ?: e.toString() }
        finally { busy = null; reload() }
    }

    // ---- tasks ----
    fun save(t: Task) = io(null) { val id = Db.upsert(t); if (t.id == 0L) selectedId = id }
    fun toggle(t: Task) {
        if (t.isClassroom && t.submissionId != null) {
            io(if (t.done) "Reclaiming submission…" else "Turning in…") {
                if (t.done) composio().reclaim(t) else composio().turnIn(t)
                Db.upsert(t.copy(done = !t.done, submissionState = if (t.done) "RECLAIMED_BY_STUDENT" else "TURNED_IN"))
                toast = if (t.done) "Unsubmitted" else "Turned in ✨"
            }
        } else save(t.copy(done = !t.done))
    }
    fun delete(t: Task) = io(null) { Db.delete(t.id); if (selectedId == t.id) selectedId = null }

    fun quickAdd(text: String) {
        if (text.isBlank()) return
        val hasAi = Db.get(Keys.ANTHROPIC_KEY).isNotBlank() || System.getenv("ANTHROPIC_API_KEY") != null
        io(if (hasAi) "Tadone is shaping your task…" else null) {
            val t = if (hasAi) runCatching { ai().quickAdd(text) }.getOrElse { Task(title = text) } else Task(title = text)
            selectedId = Db.upsert(t)
        }
    }

    // ---- classroom ----
    fun syncClassroom() = io("Syncing Google Classroom…") {
        val items = composio().fetchTasks { busy = it }
        items.forEach(Db::mergeClassroom)
        Db.set(Keys.LAST_SYNC, System.currentTimeMillis().toString())
        toast = "Synced ${items.size} classroom tasks"
    }

    fun uploadFile(t: Task, f: File) = io("Uploading ${f.name}…") {
        composio().uploadAndAttach(t, f); toast = "Attached ${f.name}"
    }

    fun attachLink(t: Task, url: String) = io("Attaching link…") { composio().attachLink(t, url); toast = "Link attached" }

    fun pushEdits(t: Task) = io("Updating Classroom…") { composio().patchCourseWork(t); toast = "Classroom updated" }

    fun connect(authConfig: String) = io("Opening Composio…") {
        val url = composio().connectLink(authConfig)
        java.awt.Desktop.getDesktop().browse(java.net.URI(url))
    }

    fun checkConnections(onResult: (Set<String>) -> Unit) = io("Checking connections…") {
        val s = composio().connectedToolkits(); withContext(Dispatchers.Main) { onResult(s) }
    }

    // ---- AI ----
    fun loadChat(taskId: Long?) { chat = Db.chat(taskId) }

    fun ask(taskId: Long?, text: String) {
        if (text.isBlank() || thinking) return
        Db.addChat(ChatMsg(taskId = taskId, role = "user", content = text))
        loadChat(taskId)
        thinking = true
        scope.launch {
            val reply = withContext(Dispatchers.IO) {
                runCatching { ai().chat(Db.chat(taskId), tasks, tasks.firstOrNull { it.id == taskId }) }
                    .getOrElse { "⚠️ ${it.message ?: it}\n\nCheck your Anthropic API key in Settings." }
            }
            Db.addChat(ChatMsg(taskId = taskId, role = "assistant", content = reply))
            thinking = false
            loadChat(taskId)
        }
    }

    fun clearChat(taskId: Long?) { Db.clearChat(taskId); loadChat(taskId) }
}
