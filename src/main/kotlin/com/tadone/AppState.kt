package com.tadone

import androidx.compose.runtime.*
import com.tadone.ai.Assistant
import com.tadone.ai.DEFAULT_MODEL
import com.tadone.ai.GeminiKeys
import com.tadone.data.*
import kotlinx.coroutines.*
import java.io.File

enum class Screen { Tasks, Classes, Schedule, Notes, Apps, Inbox, Settings }
enum class Filter { Open, Today, Classroom, Done, All }

class AppState(private val scope: CoroutineScope) {
    var screen by mutableStateOf(Screen.Tasks)
    var filter by mutableStateOf(Filter.Open)
    var tasks by mutableStateOf(listOf<Task>())
    var selectedId by mutableStateOf<Long?>(null)
    /** Running background jobs (key → label). Different keys run side by side; the same key never runs twice. */
    val jobs = mutableStateMapOf<String, String>()
    fun running(key: String) = key in jobs

    /** Queued messages, shown one after another so parallel jobs don't overwrite each other. */
    val toasts = mutableStateListOf<Pair<Long, String>>()
    private var toastSeq = 0L
    var toast: String?
        get() = toasts.firstOrNull()?.second
        set(v) { if (v == null) toasts.removeFirstOrNull() else if (toasts.none { it.second == v }) toasts.add(++toastSeq to v) }

    /** True while the main window has focus; background results also go to system notifications when it doesn't. */
    var windowFocused by mutableStateOf(true)

    /** Messages per chat: null = the floating assistant, otherwise a task's own chat. Each can be waiting on Gemini at once. */
    var chats by mutableStateOf(mapOf<Long?, List<ChatMsg>>())
    var thinkingIn by mutableStateOf(setOf<Long?>())
    fun chatFor(taskId: Long?) = chats[taskId].orEmpty()
    fun isThinking(taskId: Long?) = taskId in thinkingIn

    /** Floating assistant bubble: open/closed, and whether a reply arrived while it was closed. */
    var assistantOpen by mutableStateOf(false)
    var assistantUnread by mutableStateOf(false)

    /** False until the user signs in (or chooses offline) — shows the sign-in page. */
    var signedIn by mutableStateOf(Db.get(Keys.COMPOSIO_KEY).isNotBlank() || Db.get(Keys.OFFLINE) == "1")
    var account by mutableStateOf(Db.get(Keys.COMPOSIO_ACCOUNT))
    var signingIn by mutableStateOf(false)
    private var signInJob: Job? = null

    /** toolkit slug → connected account id; null until first loaded. */
    var connections by mutableStateOf<Map<String, String>?>(null)
    var connecting by mutableStateOf(setOf<String>())
    private val connectJobs = mutableMapOf<String, Job>()

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

    private fun composio() = Composio(Db.get(Keys.COMPOSIO_KEY), Db.get(Keys.COMPOSIO_USER, "default"), Db.get(Keys.COMPOSIO_PROJECT))
    private fun ai() = Assistant(Db.get(Keys.GEMINI_KEY), Db.get(Keys.MODEL, DEFAULT_MODEL))

    var events by mutableStateOf(listOf<ScheduleEvent>())
    var notes by mutableStateOf(listOf<Note>())
    var selectedNoteId by mutableStateOf<Long?>(null)
    var quizzing by mutableStateOf(setOf<Long>())
    var noteTab by mutableStateOf(0)

    // Classroom browser: everything comes from the SQLite cache; the network is only hit on first open or Refresh.
    var courses by mutableStateOf(listOf<Course>())
    var selectedCourseId by mutableStateOf<String?>(null)
    var courseTopics by mutableStateOf(listOf<Topic>())
    var courseItems by mutableStateOf(listOf<CourseItem>())
    val loadingCourses get() = running("courses")
    val loadingModules get() = jobs.keys.filter { it.startsWith("modules:") }.map { it.removePrefix("modules:") }.toSet()

    /** Bumped when a background job rewrites a note, so an open editor picks up the new text. */
    var noteRevisions by mutableStateOf(mapOf<Long, Int>())
    private fun bumpNote(id: Long) { noteRevisions = noteRevisions + (id to (noteRevisions[id] ?: 0) + 1) }

    var inbox by mutableStateOf(listOf<InboxItem>())
    val unread get() = inbox.count { !it.read }

    fun reload() { tasks = Db.tasks(); events = Db.events(); notes = Db.notes(); courses = Db.courses(); inbox = Db.inbox() }

    /**
     * Runs [block] on the IO pool as job [key]. Returns false (and does nothing) if that job is already running.
     * [label] is shown in the activity pill; null keeps it silent. [done] becomes a notification when the window is in the background.
     */
    private fun job(key: String, label: String?, done: String? = null, target: String? = null, block: suspend CoroutineScope.() -> Unit): Boolean {
        if (key in jobs) return false
        jobs[key] = label ?: ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                if (done != null) notify("Done", done, kind = "activity", target = target, background = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                notify("Something went wrong", msg, kind = "error", target = target, system = false)
            } finally { jobs.remove(key); reload() }
        }
        return true
    }
    private fun progress(key: String, label: String) { if (key in jobs) jobs[key] = label }

    /**
     * Records a notification in the Notifications tab and shows a toast. A system notification is also sent
     * when [system] is set, except for [background] results while the window is focused (the toast is enough then).
     */
    fun notify(title: String, body: String, kind: String = "activity", target: String? = null, background: Boolean = false, system: Boolean = true) {
        toast = body
        Db.addInbox(InboxItem(kind = kind, title = title, body = body, target = target))
        inbox = Db.inbox()
        val wantSystem = system && (!background || (!windowFocused && Db.get(Keys.NOTIFY_JOBS, "1") == "1"))
        if (wantSystem) Notifier.send(title, body, kind)
    }

    /** Settings test button: sends a real notification + sound and reports how it went. */
    fun testNotification() = scope.launch {
        toast = withContext(Dispatchers.IO) { Notifier.test() }
    }

    fun markRead(i: InboxItem?) { Db.markInboxRead(i?.id); inbox = Db.inbox() }
    fun clearInbox(i: InboxItem? = null) { Db.deleteInbox(i?.id); inbox = Db.inbox() }

    /** Goes wherever a notification points: a task, note, class or screen. */
    fun openTarget(target: String?) {
        val (kind, id) = target?.split(":", limit = 2)?.let { it[0] to it.getOrElse(1) { "" } } ?: return
        when (kind) {
            "task" -> { selectedId = id.toLongOrNull(); filter = Filter.All; screen = Screen.Tasks }
            "note" -> { selectedNoteId = id.toLongOrNull(); noteTab = 0; screen = Screen.Notes }
            "quiz" -> { selectedNoteId = id.toLongOrNull(); noteTab = 2; screen = Screen.Notes }
            "course" -> { screen = Screen.Classes; openCourse(id) }
            "screen" -> runCatching { Screen.valueOf(id) }.getOrNull()?.let { screen = it }
        }
    }

    // ---- tasks ----
    fun save(t: Task) { val id = Db.upsert(t); if (t.id == 0L) selectedId = id; reload() }
    fun toggle(t: Task) {
        if (t.isClassroom && t.submissionId != null) {
            job("submit:${t.id}", if (t.done) "Reclaiming submission…" else "Turning in…") {
                if (t.done) composio().reclaim(t) else composio().turnIn(t)
                Db.upsert(t.copy(done = !t.done, submissionState = if (t.done) "RECLAIMED_BY_STUDENT" else "TURNED_IN"))
                toast = if (t.done) "Unsubmitted" else "Turned in"
            }
        } else save(t.copy(done = !t.done))
    }
    fun delete(t: Task) { Db.delete(t.id); if (selectedId == t.id) selectedId = null; reload() }

    fun quickAdd(text: String) {
        if (text.isBlank()) return
        val hasAi = Db.get(Keys.GEMINI_KEY).isNotBlank() || System.getenv("GEMINI_API_KEY") != null
        job("quickadd:${text.hashCode()}", if (hasAi) "Tadone is shaping your task…" else null) {
            val t = if (hasAi) runCatching { ai().quickAdd(text) }.getOrElse { Task(title = text) } else Task(title = text)
            selectedId = Db.upsert(t)
        }
    }

    // ---- classroom ----
    /** Refreshes coursework + submission state for the task list (2 calls per course + 1 for the course list). */
    fun syncClassroom() {
        var n = 0
        job("sync", "Syncing Google Classroom…", done = "Classroom sync finished", target = "screen:Tasks") {
            val c = composio()
            val list = c.courses().also(Db::saveCourses)
            courses = Db.courses()
            val items = c.fetchTasks(list) { progress("sync", it) }
            items.forEach(Db::mergeClassroom)
            Db.set(Keys.LAST_SYNC, System.currentTimeMillis().toString())
            n = items.size
            toast = "Synced $n classroom tasks"
        }
    }

    /** Course list: cache first; 1 call only if nothing is cached yet or [force] is set. */
    fun loadCourses(force: Boolean = false) {
        if (!force && courses.isNotEmpty()) return
        if (Db.get(Keys.COMPOSIO_KEY).isBlank()) return
        job("courses", "Loading your classes…") { Db.saveCourses(composio().courses()) }
    }

    /** Opens a course: shows cached modules instantly; fetches (3 calls) only the first time or when [force]d. */
    fun openCourse(id: String, force: Boolean = false) {
        selectedCourseId = id
        courseTopics = Db.topics(id); courseItems = Db.courseItems(id)
        val cached = courses.firstOrNull { it.id == id }?.modulesFetchedAt != null
        if (cached && !force) return
        val name = courses.firstOrNull { it.id == id }?.name ?: "class"
        job("modules:$id", null, done = "Modules for $name are ready", target = "course:$id") {
            val (topics, items) = composio().courseModules(id)
            Db.saveModules(id, topics, items)
            withContext(Dispatchers.Main) {
                if (selectedCourseId == id) { courseTopics = Db.topics(id); courseItems = Db.courseItems(id) }
            }
        }
    }

    /** Copies a Classroom item into Notes so it can be studied / quizzed. No network. */
    fun itemToNote(i: CourseItem) {
        val course = courses.firstOrNull { it.id == i.courseId }?.name.orEmpty()
        val body = buildString {
            if (i.description.isNotBlank()) appendLine(i.description)
            if (i.materials.isNotEmpty()) { appendLine(); appendLine("Materials:"); i.materials.forEach { appendLine("- ${it.title}: ${it.url}") } }
        }.trim()
        selectedNoteId = Db.upsertNote(Note(title = i.title, course = course, body = body))
        notes = Db.notes(); noteTab = 0; screen = Screen.Notes
    }

    fun uploadFile(t: Task, f: File) = job("upload:${t.id}:${f.path}", "Uploading ${f.name}…", done = "Attached ${f.name}") {
        composio().uploadAndAttach(t, f); toast = "Attached ${f.name}"
    }

    fun attachLink(t: Task, url: String) = job("link:${t.id}:$url", "Attaching link…") { composio().attachLink(t, url); toast = "Link attached" }

    fun pushEdits(t: Task) = job("patch:${t.id}", "Updating Classroom…") { composio().patchCourseWork(t); toast = "Classroom updated" }

    // ---- account ----
    /** Signs in to Composio in the browser (or reuses the CLI login) and stores the account keys. */
    fun signIn(useCli: Boolean) {
        if (signingIn) return
        signingIn = true
        signInJob = scope.launch {
            try {
                val acct = withContext(Dispatchers.IO) {
                    (if (useCli) ComposioLogin.fromCli() else null) ?: run {
                        val session = ComposioLogin.start()
                        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(session.loginUrl)) }
                            .onFailure { toast = "Open this link to sign in: ${session.loginUrl}" }
                        ComposioLogin.await(session)
                    }
                }
                Db.set(Keys.COMPOSIO_KEY, acct.userApiKey)
                Db.set(Keys.COMPOSIO_PROJECT, acct.projectId)
                if (Db.get(Keys.COMPOSIO_USER).isBlank()) Db.set(Keys.COMPOSIO_USER, "default")
                account = listOf(acct.email, acct.projectName).filter { it.isNotBlank() }.joinToString(" · ")
                Db.set(Keys.COMPOSIO_ACCOUNT, account)
                Db.set(Keys.OFFLINE, "")
                signedIn = true
                toast = "Welcome${acct.email.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}"
                refreshConnections()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast = e.message ?: e.toString()
            } finally { signingIn = false }
        }
    }

    fun cancelSignIn() { signInJob?.cancel(); signingIn = false }

    fun useOffline() { Db.set(Keys.OFFLINE, "1"); signedIn = true }

    fun signOut() {
        listOf(Keys.COMPOSIO_KEY, Keys.COMPOSIO_PROJECT, Keys.COMPOSIO_ACCOUNT, Keys.OFFLINE).forEach { Db.set(it, "") }
        account = ""; connections = null; signedIn = false; screen = Screen.Tasks
    }

    // ---- Gemini key ----
    var gettingGeminiKey by mutableStateOf(false)
    private var geminiJob: Job? = null

    /** Opens AI Studio and waits for a newly copied key; saves it plus a model the key can use. */
    fun getGeminiKey(onSaved: (key: String, model: String) -> Unit) {
        if (gettingGeminiKey) return
        gettingGeminiKey = true
        geminiJob = scope.launch {
            try {
                val (key, model) = withContext(Dispatchers.IO) {
                    val before = GeminiKeys.findKey(GeminiKeys.clipboardText())
                    java.awt.Desktop.getDesktop().browse(java.net.URI(GeminiKeys.KEY_PAGE))
                    val until = System.currentTimeMillis() + 10 * 60_000
                    var found: String? = null
                    while (found == null && System.currentTimeMillis() < until) {
                        delay(1000)
                        found = GeminiKeys.findKey(GeminiKeys.clipboardText())?.takeIf { it != before }
                    }
                    found ?: throw IllegalStateException("No key copied — you can paste it into the field instead.")
                    jobs["gemini"] = "Checking your Gemini key…"
                    val m = GeminiKeys.pickModel(GeminiKeys.models(found), Db.get(Keys.MODEL, DEFAULT_MODEL))
                    found to m
                }
                Db.set(Keys.GEMINI_KEY, key); Db.set(Keys.MODEL, model)
                onSaved(key, model)
                toast = "Gemini key saved (model: $model)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast = e.message ?: e.toString()
            } finally { gettingGeminiKey = false; jobs.remove("gemini") }
        }
    }

    fun cancelGeminiKey() { geminiJob?.cancel(); gettingGeminiKey = false }

    // ---- connected apps ----
    fun refreshConnections() {
        if (Db.get(Keys.COMPOSIO_KEY).isBlank()) { connections = emptyMap(); return }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { composio().connections() } }
                .onSuccess { connections = it }
                .onFailure { toast = it.message ?: it.toString() }
        }
    }

    /** Connects an app: finds/creates its auth config, opens the sign-in page, then waits until it is active. */
    fun connectApp(slug: String) {
        if (slug in connecting) return
        val name = Apps.byId(slug)?.name ?: slug
        connecting = connecting + slug
        connectJobs[slug] = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val c = composio()
                    val ac = c.ensureAuthConfig(slug)
                    if (slug == "google_classroom") Db.set(Keys.CLASSROOM_AUTH_CONFIG, ac)
                    if (slug == "googledrive") Db.set(Keys.DRIVE_AUTH_CONFIG, ac)
                    java.awt.Desktop.getDesktop().browse(java.net.URI(c.connectLink(ac)))
                    val until = System.currentTimeMillis() + 5 * 60_000
                    while (System.currentTimeMillis() < until) {
                        delay(3000)
                        val now = runCatching { c.connections() }.getOrNull() ?: continue
                        if (slug in now) { connections = now; return@withContext }
                    }
                    throw ComposioException("Didn't hear back from $name — try Connect again.")
                }
                notify("Done", "$name connected", target = "screen:Apps", background = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast = e.message ?: e.toString()
            } finally { connecting = connecting - slug; connectJobs.remove(slug) }
        }
    }

    fun cancelConnect(slug: String) { connectJobs.remove(slug)?.cancel(); connecting = connecting - slug }

    fun disconnectApp(slug: String) {
        val id = connections?.get(slug) ?: return
        job("disconnect:$slug", "Disconnecting ${Apps.byId(slug)?.name ?: slug}…") {
            composio().disconnect(id)
            connections = composio().connections()
        }
    }


    // ---- schedule ----
    fun saveEvent(e: ScheduleEvent) { Db.upsertEvent(e); events = Db.events() }
    fun deleteEvent(e: ScheduleEvent) { Db.deleteEvent(e.id); events = Db.events() }

    // ---- notes ----
    fun newNote() { selectedNoteId = Db.upsertNote(Note(title = "Untitled note")); notes = Db.notes() }
    fun saveNote(n: Note) { Db.upsertNote(n); notes = Db.notes() }
    fun deleteNote(n: Note) { Db.deleteNote(n.id); if (selectedNoteId == n.id) selectedNoteId = null; notes = Db.notes() }

    /** "Smart quiz": Gemini writes the questions from the note's Markdown. One request per click. */
    fun generateQuiz(n: Note, count: Int) {
        if (n.id in quizzing) return
        if (n.body.isBlank()) { toast = "Write or import some notes first, then make a quiz."; return }
        quizzing = quizzing + n.id
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ai().quiz(n.title, n.course, n.body, count) } }
                .onSuccess { q -> Db.upsertNote(Db.notes().firstOrNull { it.id == n.id }?.copy(quiz = q) ?: n.copy(quiz = q)); notes = Db.notes()
                    notify("Done", "Practice quiz ready: ${n.title.ifBlank { "note" }}", target = "quiz:${n.id}", background = true) }
                .onFailure { notify("Quiz failed", it.message ?: it.toString(), kind = "error", target = "note:${n.id}", system = false) }
            quizzing = quizzing - n.id
        }
    }

    /** "Quick quiz": built locally from the note's headings, bullets and definitions. No Gemini call. */
    fun quickQuiz(n: Note, count: Int) {
        if (n.body.isBlank()) { toast = "Write or import some notes first, then make a quiz."; return }
        scope.launch {
            val qs = withContext(Dispatchers.Default) { com.tadone.ai.OfflineQuiz.generate(n.body, count) }
            if (qs.size < 3) { toast = "These notes need more structure (headings, bullets, \"Term: meaning\") for a quick quiz. Try Smart quiz."; return@launch }
            Db.upsertNote(n.copy(quiz = com.tadone.ai.Quiz.toJson(qs))); notes = Db.notes()
            toast = if (qs.size < count) "Made ${qs.size} questions from these notes" else "Quick quiz ready"
        }
    }

    /** Imports each file as its own structured study note (text extraction + structuring both run locally). */
    fun importFiles(files: List<File>) {
        val ok = supported(files)
        ok.forEach { f ->
            var noteId = 0L
            job("import:${f.absolutePath}", "Reading ${f.name}…", done = "Notes ready from ${f.name}", target = null) {
                importGate.withPermit {
                    val r = MarkdownImport.convert(f)
                    progress("import:${f.absolutePath}", "Structuring ${f.name}…")
                    val body = StudyNotes.structure(r.markdown, r.title, f.name)
                    noteId = Db.upsertNote(Note(title = r.title, body = body, source = r.markdown, sourceFiles = f.name))
                    withContext(Dispatchers.Main) { selectedNoteId = noteId; noteTab = 0 }
                }
            }
        }
    }

    /**
     * "Add from file" on an existing note: extracts all text from the files, keeps it as the note's source,
     * and rebuilds the note as structured study content (overview, key terms, sections) ready for a quiz.
     */
    fun addFilesToNote(n: Note, files: List<File>) {
        val ok = supported(files)
        if (ok.isEmpty()) return
        val names = ok.joinToString { it.name }
        job("note-files:${n.id}", "Reading $names…", done = "Structured notes ready: ${n.title.ifBlank { names }}", target = "note:${n.id}") {
            val parts = ok.map { f -> importGate.withPermit { f.name to MarkdownImport.convert(f).markdown } }
            progress("note-files:${n.id}", "Structuring notes…")
            val current = Db.notes().firstOrNull { it.id == n.id } ?: n
            val added = parts.joinToString("\n\n") { (name, md) -> "<!-- from $name -->\n$md" }
            val source = listOf(current.source, added).filter { it.isNotBlank() }.joinToString("\n\n")
            val files = (current.sourceFiles.lines() + parts.map { it.first }).filter { it.isNotBlank() }.distinct()
            val title = current.title.takeUnless { it.isBlank() || it == "Untitled note" } ?: ok.first().nameWithoutExtension.replace('_', ' ')
            val structured = StudyNotes.structure(source, title, files.joinToString(", "))
            // keep anything the student typed that didn't come from a file
            val typed = current.body.takeIf { it.isNotBlank() && current.source.isBlank() }
            val body = if (typed != null) "$structured\n## My notes\n\n$typed\n" else structured
            Db.upsertNote(current.copy(title = title, body = body, source = source, sourceFiles = files.joinToString("\n")))
            withContext(Dispatchers.Main) { bumpNote(n.id); noteTab = 0 }
        }
    }

    /** Rebuilds a note's structure from its saved source text (no file picking, no network). */
    fun restructureNote(n: Note) {
        val current = Db.notes().firstOrNull { it.id == n.id } ?: n
        val raw = current.source.ifBlank { current.body }
        if (raw.isBlank()) { toast = "Nothing to structure yet. Add a file or write some notes."; return }
        Db.upsertNote(current.copy(body = StudyNotes.structure(raw, current.title, current.sourceFiles.lines().filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }),
            source = current.source.ifBlank { current.body }))
        notes = Db.notes(); bumpNote(n.id); toast = "Notes restructured"
    }

    /** Optional: asks Gemini (one request) to write the structured note from the source text, in the same layout. */
    fun structureWithAi(n: Note) {
        val current = Db.notes().firstOrNull { it.id == n.id } ?: n
        val raw = current.source.ifBlank { current.body }
        if (raw.isBlank()) { toast = "Nothing to structure yet. Add a file or write some notes."; return }
        job("ai-structure:${n.id}", "Gemini is organising your notes…", done = "Gemini finished organising ${current.title}", target = "note:${n.id}") {
            val md = ai().structureNotes(current.title, raw)
            Db.upsertNote(current.copy(body = md, source = current.source.ifBlank { current.body }))
            withContext(Dispatchers.Main) { bumpNote(n.id) }
        }
    }

    private fun supported(files: List<File>): List<File> {
        val ok = files.filter { it.extension.lowercase() in MarkdownImport.extensions }
        if (ok.size < files.size) toast = "Skipped ${files.size - ok.size} unsupported file(s). Use PDF, PowerPoint, Word, Markdown or text."
        return ok
    }
    private val importGate = kotlinx.coroutines.sync.Semaphore(3)
    private suspend fun <T> kotlinx.coroutines.sync.Semaphore.withPermit(block: suspend () -> T): T { acquire(); try { return block() } finally { release() } }

    // ---- reminders ----
    /** Checks deadlines and classes once a minute (local data only, no network) and sends system notifications. */
    fun startReminders() = scope.launch {
        delay(5_000)
        while (isActive) {
            runCatching { withContext(Dispatchers.IO) { checkReminders() } }
            delay(60_000)
        }
    }

    private fun checkReminders() {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Triple<String, String, String>>() // title, body, kind
        if (Db.get(Keys.NOTIFY_DEADLINES, "1") == "1") Db.tasks().filter { !it.done && it.dueAt != null && it.dueAt > now }.forEach { t ->
            val left = t.dueAt!! - now
            val (key, msg) = when {
                left <= 3_600_000 -> "t:${t.id}:1h:${t.dueAt}" to "Due within the hour: ${t.title}"
                left <= 86_400_000 -> "t:${t.id}:24h:${t.dueAt}" to "Due in ${left / 3_600_000}h: ${t.title}"
                else -> return@forEach
            }
            if (Db.markNotified(key)) { out += Triple("Deadline", msg, "deadline"); Db.addInbox(InboxItem(kind = "deadline", title = "Deadline", body = msg, target = "task:${t.id}")) }
        }
        if (Db.get(Keys.NOTIFY_CLASSES, "1") == "1") {
            val today = java.time.LocalDate.now()
            val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            Db.events().filter { it.occursOn(today) && it.startMin - nowMin in 0..10 }.forEach { e ->
                if (Db.markNotified("e:${e.id}:${today.toEpochDay()}")) {
                    val msg = "${e.title} at ${com.tadone.ui.minutesLabel(e.startMin)}${if (e.location.isNotBlank()) " · ${e.location}" else ""}"
                    out += Triple("Class soon", msg, "class")
                    Db.addInbox(InboxItem(kind = "class", title = "Class soon", body = msg, target = "screen:Schedule"))
                }
            }
        }
        // never flood: at most 3 separate notifications, the rest are summarised
        out.take(3).forEachIndexed { i, (title, body, kind) -> Notifier.send(title, body, if (i == 0) kind else "silent") }
        if (out.size > 3) Notifier.send("Tadone", "${out.size - 3} more reminders, open Tadone to see them", "silent")
        if (out.isNotEmpty()) { toast = out.first().second; inbox = Db.inbox() }
    }

    // ---- AI ----
    fun loadChat(taskId: Long?) { chats = chats + (taskId to Db.chat(taskId)) }

    /** Each chat (global or per task) can be waiting on Gemini at the same time. */
    fun ask(taskId: Long?, text: String) {
        if (text.isBlank() || taskId in thinkingIn) return
        Db.addChat(ChatMsg(taskId = taskId, role = "user", content = text))
        loadChat(taskId)
        thinkingIn = thinkingIn + taskId
        scope.launch {
            val reply = withContext(Dispatchers.IO) {
                runCatching { ai().chat(Db.chat(taskId), tasks, tasks.firstOrNull { it.id == taskId }) }
                    .getOrElse { "Error: ${it.message ?: it}\n\nCheck your Gemini API key in Settings." }
            }
            Db.addChat(ChatMsg(taskId = taskId, role = "assistant", content = reply))
            thinkingIn = thinkingIn - taskId
            loadChat(taskId)
            if (taskId == null && !assistantOpen) assistantUnread = true
        }
    }

    fun clearChat(taskId: Long?) { Db.clearChat(taskId); loadChat(taskId) }
}
