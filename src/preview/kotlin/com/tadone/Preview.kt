package com.tadone

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.tadone.data.Db
import com.tadone.data.Task
import com.tadone.ui.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

fun main() {
    Db.init()
    if (Db.tasks().isEmpty()) {
        val d = 86_400_000L; val now = System.currentTimeMillis()
        Db.upsert(Task(title = "Physics lab report", course = "Physics 2", dueAt = now + d / 3, priority = 2))
        Db.upsert(Task(title = "Read chapter 5: Cell respiration", course = "Biology", dueAt = now + 3 * d))
        Db.upsert(Task(title = "Essay: The role of memory", course = "English 101", dueAt = now - d, priority = 2, source = "classroom", courseId = "1", workId = "2", submissionId = "3", submissionState = "CREATED"))
        Db.upsert(Task(title = "Group project slides", course = "IT Elective", dueAt = now + 9 * d, priority = 0))
        Db.upsert(Task(title = "Math worksheet 4", course = "Calculus", done = true))
    }
    if (Db.events().isEmpty()) {
        Db.upsertEvent(com.tadone.data.ScheduleEvent(title = "Biology lecture", location = "Room 204", days = 0b0010101, startMin = 9 * 60, endMin = 10 * 60 + 30, color = 1))
        Db.upsertEvent(com.tadone.data.ScheduleEvent(title = "Calculus", location = "Hall B", days = 0b0001010, startMin = 13 * 60, endMin = 14 * 60 + 30, color = 2))
        Db.upsertEvent(com.tadone.data.ScheduleEvent(title = "English 101", days = 0b0010101, startMin = 15 * 60, endMin = 16 * 60, color = 0))
        Db.upsertEvent(com.tadone.data.ScheduleEvent(title = "Study group", date = java.time.LocalDate.now().toEpochDay(), startMin = 18 * 60, endMin = 19 * 60 + 30, color = 4))
    }
    if (Db.notes().isEmpty()) {
        val quiz = com.tadone.ai.Quiz.toJson(listOf(
            com.tadone.ai.QuizQuestion("Where does glycolysis take place?", listOf("Mitochondrial matrix", "Cytoplasm", "Inner mitochondrial membrane", "Nucleus"), 1, "Glycolysis happens in the cytoplasm and does not need oxygen."),
            com.tadone.ai.QuizQuestion("Which stage produces the most ATP?", listOf("Glycolysis", "Krebs cycle", "Electron transport chain", "Fermentation"), 2, "Oxidative phosphorylation in the ETC yields about 34 ATP."),
        ))
        Db.upsertNote(com.tadone.data.Note(title = "Cell respiration", course = "Biology", quiz = quiz,
            body = "Cellular respiration turns glucose into ATP.\n\n1. Glycolysis (cytoplasm): glucose -> 2 pyruvate, net 2 ATP\n2. Krebs cycle (matrix): makes NADH, FADH2\n3. Electron transport chain: ~34 ATP, oxygen is the final electron acceptor"))
        Db.upsertNote(com.tadone.data.Note(title = "Limits and continuity", course = "Calculus", body = "A function is continuous at a if the limit equals f(a)."))
    }
    if (Db.courses().isEmpty()) {
        Db.saveCourses(listOf(com.tadone.data.Course("c1", "Biology 2", "Section B", "Room 204", "https://classroom.google.com"),
            com.tadone.data.Course("c2", "Calculus", "BSIT 2-1"), com.tadone.data.Course("c3", "English 101")))
        Db.saveModules("c1", listOf(com.tadone.data.Topic("c1", "t1", "Module 1: Cells"), com.tadone.data.Topic("c1", "t2", "Module 2: Energy")), listOf(
            com.tadone.data.CourseItem("c1", "i1", "material", "t1", "Lecture slides: Cell structure", "Read before Monday.",
                "https://classroom.google.com", materials = listOf(com.tadone.data.Attachment("Cell structure.pdf", "https://drive.google.com", "drive"), com.tadone.data.Attachment("Cell tour", "https://youtube.com", "video")), createdAt = "2026-11-02"),
            com.tadone.data.CourseItem("c1", "i2", "assignment", "t1", "Lab 1: Microscope drawing", "Draw and label an onion cell.", "https://classroom.google.com", System.currentTimeMillis() + 2 * 86_400_000L, createdAt = "2026-11-03"),
            com.tadone.data.CourseItem("c1", "i3", "question", "t2", "Where does glycolysis happen?", createdAt = "2026-11-10"),
            com.tadone.data.CourseItem("c1", "i4", "material", "t2", "Reading: Cellular respiration", materials = listOf(com.tadone.data.Attachment("Chapter 5", "https://drive.google.com", "drive")), createdAt = "2026-11-11"),
            com.tadone.data.CourseItem("c1", "i5", "material", null, "Course syllabus", createdAt = "2026-10-01"),
        ))
    }
    if (Db.inbox().isEmpty()) {
        Db.addInbox(com.tadone.data.InboxItem(kind = "activity", title = "Done", body = "Classroom sync finished", createdAt = System.currentTimeMillis() - 3 * 86_400_000L))
        Db.markInboxRead(null)
        Db.addInbox(com.tadone.data.InboxItem(kind = "error", title = "Something went wrong", body = "scan.pdf has no readable text (it may be scanned images).", createdAt = System.currentTimeMillis() - 86_400_000L))
        Db.addInbox(com.tadone.data.InboxItem(kind = "class", title = "Class soon", body = "Biology lecture at 9:00 AM · Room 204", target = "screen:Schedule", createdAt = System.currentTimeMillis() - 3_600_000L))
        Db.addInbox(com.tadone.data.InboxItem(kind = "activity", title = "Done", body = "Structured notes ready: Cellular respiration", target = "note:1", createdAt = System.currentTimeMillis() - 1_200_000L))
        Db.addInbox(com.tadone.data.InboxItem(kind = "deadline", title = "Deadline", body = "Due in 7h: Physics lab report", target = "task:2"))
    }
    fun shot(name: String, w: Int, h: Int, ms: Long, content: @Composable () -> Unit) {
        ImageComposeScene(w, h, content = { TadoneTheme { content() } }).use { sc ->
            var t = 0L; while (t <= ms) { sc.render(t * 1_000_000); t += 16 }
            File("/tmp/tadone-shots/$name.png").apply { parentFile.mkdirs() }.writeBytes(sc.render(ms * 1_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
    shot("splash", 440, 460, 2200) { Splash(.7f) }
    shot("signin", 1240, 800, 1500) { App() }
    Db.set(com.tadone.data.Keys.OFFLINE, "1")
    shot("tasks", 1240, 800, 1500) { App() }
    Db.set(com.tadone.data.Keys.COMPOSIO_KEY, "preview")
    shot("apps", 1240, 800, 1500) {
        val s = AppState(rememberCoroutineScope()).apply { connections = mapOf("google_classroom" to "x", "googledocs" to "y"); connecting = setOf("googlesheets") }
        com.tadone.ui.AppsScreen(s)
    }
    shot("schedule", 1240, 800, 1500) { com.tadone.ui.ScheduleScreen(AppState(rememberCoroutineScope()).apply { reload() }) }
    shot("notes", 1240, 800, 1500) { com.tadone.ui.NotesScreen(AppState(rememberCoroutineScope()).apply { reload(); selectedNoteId = notes.last().id }) }
    shot("quiz", 1240, 800, 1500) { com.tadone.ui.NotesScreen(AppState(rememberCoroutineScope()).apply { reload(); selectedNoteId = notes.first { it.quiz.isNotBlank() }.id; noteTab = 2 }) }
    Db.set(com.tadone.data.Keys.COMPOSIO_KEY, "preview")
    shot("classes", 1240, 800, 1500) { com.tadone.ui.ClassesScreen(AppState(rememberCoroutineScope()).apply { reload(); openCourse("c1") }) }
    Db.set(com.tadone.data.Keys.COMPOSIO_KEY, "")
    shot("tasks-short", 1240, 640, 1500) { App() }
    shot("assistant", 1240, 800, 1500) {
        val s = AppState(rememberCoroutineScope()).apply {
            reload(); signedIn = true; assistantOpen = true
            if (Db.chat(null).isEmpty()) {
                Db.addChat(com.tadone.data.ChatMsg(taskId = null, role = "user", content = "What should I do first?"))
                Db.addChat(com.tadone.data.ChatMsg(taskId = null, role = "assistant", content = "Start with the Physics lab report: it's due in about 8 hours.\n\n1. Finish the data table\n2. Write the conclusion\n3. Turn it in on Classroom\n\nThen read chapter 5 for Biology before Sunday."))
            }
        }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            com.tadone.ui.TasksScreen(s)
            com.tadone.ui.AssistantBubble(s, androidx.compose.ui.Modifier.padding(end = 24.dp, bottom = 24.dp))
        }
    }
    shot("assistant-closed", 1240, 800, 1500) {
        val s = AppState(rememberCoroutineScope()).apply { reload(); assistantUnread = true }
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            com.tadone.ui.TasksScreen(s)
            com.tadone.ui.AssistantBubble(s, androidx.compose.ui.Modifier.padding(end = 24.dp, bottom = 24.dp))
        }
    }
    shot("inbox", 1240, 800, 1500) { com.tadone.ui.InboxScreen(AppState(rememberCoroutineScope()).apply { reload() }) }
    shot("note-tools", 1240, 800, 1500) {
        val n = Db.notes().first { it.title == "Cell respiration" }
        val raw = "## Cellular Respiration\n\nCellular respiration is the process cells use to turn glucose into ATP. It happens in three stages.\n\n### Glycolysis\n\n- Location: cytoplasm\n- Glucose is split into two pyruvate molecules.\n\n### Electron Transport Chain\n\nMitochondria: the organelle where most ATP is made. Oxygen: the final electron acceptor."
        Db.upsertNote(n.copy(source = raw, sourceFiles = "lecture-5.pdf", body = com.tadone.data.StudyNotes.structure(raw, "Cell respiration", "lecture-5.pdf")))
        com.tadone.ui.NotesScreen(AppState(rememberCoroutineScope()).apply { reload(); selectedNoteId = n.id; noteTab = 0 })
    }
    shot("settings", 1240, 800, 1500) { com.tadone.ui.SettingsScreen(AppState(rememberCoroutineScope()).apply { account = "student@example.com · my_project"; gettingGeminiKey = true }) }
    Db.set(com.tadone.data.Keys.COMPOSIO_KEY, "")
    shot("detail", 1240, 800, 1500) {
        val s = AppState(rememberCoroutineScope()).apply { reload(); selectedId = tasks.first { it.isClassroom }.id }
        CompositionLocalProvider { com.tadone.ui.TasksScreen(s) }
    }
    System.exit(0)
}
