package com.tadone

import androidx.compose.runtime.*
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
    fun shot(name: String, w: Int, h: Int, ms: Long, content: @Composable () -> Unit) {
        ImageComposeScene(w, h, content = { TadoneTheme { content() } }).use { sc ->
            var t = 0L; while (t <= ms) { sc.render(t * 1_000_000); t += 16 }
            File("/tmp/tadone-shots/$name.png").apply { parentFile.mkdirs() }.writeBytes(sc.render(ms * 1_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
    shot("splash", 440, 460, 2200) { Splash(.7f) }
    shot("tasks", 1240, 800, 1500) { App() }
    shot("detail", 1240, 800, 1500) {
        val s = AppState(rememberCoroutineScope()).apply { reload(); selectedId = tasks.first { it.isClassroom }.id }
        CompositionLocalProvider { com.tadone.ui.TasksScreen(s) }
    }
    System.exit(0)
}
