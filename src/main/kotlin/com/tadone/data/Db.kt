package com.tadone.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

data class Task(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val course: String = "",
    val dueAt: Long? = null,
    val done: Boolean = false,
    val priority: Int = 1,               // 0 low, 1 normal, 2 high
    val source: String = "local",        // local | classroom
    val courseId: String? = null,
    val workId: String? = null,
    val submissionId: String? = null,
    val submissionState: String? = null,
    val link: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isClassroom get() = source == "classroom"
}

/** A class or event on the Schedule. Weekly when [days] is set (bit 0 = Monday), otherwise a one-off on [date] (epoch day). */
data class ScheduleEvent(
    val id: Long = 0,
    val title: String,
    val location: String = "",
    val days: Int = 0,
    val date: Long? = null,
    val startMin: Int = 9 * 60,
    val endMin: Int = 10 * 60,
    val color: Int = 0,
) {
    fun occursOn(d: java.time.LocalDate): Boolean =
        if (days != 0) days and (1 shl (d.dayOfWeek.value - 1)) != 0 else date == d.toEpochDay()
}

data class Note(
    val id: Long = 0,
    val title: String = "",
    val course: String = "",
    val body: String = "",
    val quiz: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class Course(val id: String, val name: String, val section: String = "", val room: String = "", val link: String? = null, val modulesFetchedAt: Long? = null)
data class Topic(val courseId: String, val id: String, val name: String)
data class Attachment(val title: String, val url: String, val kind: String)
/** A coursework item (assignment / question) or material inside a course, optionally under a topic (module). */
data class CourseItem(
    val courseId: String, val id: String, val kind: String, val topicId: String?, val title: String,
    val description: String = "", val link: String? = null, val dueAt: Long? = null,
    val materials: List<Attachment> = emptyList(), val createdAt: String = "",
)

data class ChatMsg(val id: Long = 0, val taskId: Long?, val role: String, val content: String, val ts: Long = System.currentTimeMillis())

/** Single-file SQLite store under the user's app-data folder. */
object Db {
    private lateinit var conn: Connection

    val dir: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".local/share")
        File(base, "Tadone").apply { mkdirs() }
    }

    @Synchronized
    fun init() {
        if (::conn.isInitialized) return
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${File(dir, "tadone.db").absolutePath}")
        conn.createStatement().use { st ->
            st.executeUpdate("PRAGMA journal_mode=WAL")
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS tasks(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '',
                course TEXT NOT NULL DEFAULT '', due_at INTEGER, done INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 1, source TEXT NOT NULL DEFAULT 'local',
                course_id TEXT, work_id TEXT, submission_id TEXT, submission_state TEXT, link TEXT,
                updated_at INTEGER NOT NULL, UNIQUE(course_id, work_id))"""
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS chat(
                id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER, role TEXT NOT NULL,
                content TEXT NOT NULL, ts INTEGER NOT NULL)"""
            )
            st.executeUpdate("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            st.executeUpdate("CREATE TABLE IF NOT EXISTS notified(key TEXT PRIMARY KEY, at INTEGER NOT NULL)")
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS events(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, location TEXT NOT NULL DEFAULT '',
                days INTEGER NOT NULL DEFAULT 0, date INTEGER, start_min INTEGER NOT NULL, end_min INTEGER NOT NULL,
                color INTEGER NOT NULL DEFAULT 0)"""
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS courses(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, section TEXT NOT NULL DEFAULT '', room TEXT NOT NULL DEFAULT '',
                link TEXT, modules_fetched_at INTEGER)"""
            )
            st.executeUpdate("CREATE TABLE IF NOT EXISTS topics(course_id TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, ord INTEGER NOT NULL, PRIMARY KEY(course_id, id))")
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS course_items(
                course_id TEXT NOT NULL, id TEXT NOT NULL, kind TEXT NOT NULL, topic_id TEXT, title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '', link TEXT, due_at INTEGER, materials TEXT NOT NULL DEFAULT '[]',
                created_at TEXT NOT NULL DEFAULT '', PRIMARY KEY(course_id, id))"""
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS notes(
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', course TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '', quiz TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)"""
            )
        }
    }

    // ---------- tasks ----------
    private fun ResultSet.toTask() = Task(
        id = getLong("id"), title = getString("title"), notes = getString("notes"), course = getString("course"),
        dueAt = getLong("due_at").takeUnless { wasNull() }, done = getInt("done") == 1, priority = getInt("priority"),
        source = getString("source"), courseId = getString("course_id"), workId = getString("work_id"),
        submissionId = getString("submission_id"), submissionState = getString("submission_state"),
        link = getString("link"), updatedAt = getLong("updated_at"),
    )

    @Synchronized
    fun tasks(): List<Task> = conn.prepareStatement(
        "SELECT * FROM tasks ORDER BY done, due_at IS NULL, due_at, priority DESC, id DESC"
    ).use { ps -> ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toTask()) } } }

    @Synchronized
    fun upsert(t: Task): Long {
        val sql = if (t.id == 0L)
            "INSERT INTO tasks(title,notes,course,due_at,done,priority,source,course_id,work_id,submission_id,submission_state,link,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)"
        else
            "UPDATE tasks SET title=?,notes=?,course=?,due_at=?,done=?,priority=?,source=?,course_id=?,work_id=?,submission_id=?,submission_state=?,link=?,updated_at=? WHERE id=?"
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, t.title); ps.setString(2, t.notes); ps.setString(3, t.course)
            if (t.dueAt == null) ps.setNull(4, java.sql.Types.INTEGER) else ps.setLong(4, t.dueAt)
            ps.setInt(5, if (t.done) 1 else 0); ps.setInt(6, t.priority); ps.setString(7, t.source)
            ps.setString(8, t.courseId); ps.setString(9, t.workId); ps.setString(10, t.submissionId)
            ps.setString(11, t.submissionState); ps.setString(12, t.link); ps.setLong(13, System.currentTimeMillis())
            if (t.id != 0L) ps.setLong(14, t.id)
            ps.executeUpdate()
        }
        return if (t.id != 0L) t.id else conn.createStatement().use { it.executeQuery("SELECT last_insert_rowid()").use { r -> r.next(); r.getLong(1) } }
    }

    /** Insert or refresh a Classroom item while keeping local notes/priority. */
    @Synchronized
    fun mergeClassroom(t: Task) {
        val existing = conn.prepareStatement("SELECT * FROM tasks WHERE course_id=? AND work_id=?").use { ps ->
            ps.setString(1, t.courseId); ps.setString(2, t.workId)
            ps.executeQuery().use { if (it.next()) it.toTask() else null }
        }
        upsert(
            if (existing == null) t
            else existing.copy(
                title = t.title, course = t.course, dueAt = t.dueAt, link = t.link,
                submissionId = t.submissionId, submissionState = t.submissionState,
                done = t.done || existing.done,
                notes = existing.notes.ifBlank { t.notes },
            )
        )
    }

    @Synchronized
    fun delete(id: Long) {
        conn.prepareStatement("DELETE FROM tasks WHERE id=?").use { it.setLong(1, id); it.executeUpdate() }
        conn.prepareStatement("DELETE FROM chat WHERE task_id=?").use { it.setLong(1, id); it.executeUpdate() }
    }

    // ---------- schedule ----------
    @Synchronized
    fun events(): List<ScheduleEvent> = conn.prepareStatement("SELECT * FROM events ORDER BY start_min").use { ps ->
        ps.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(ScheduleEvent(
                    rs.getLong("id"), rs.getString("title"), rs.getString("location"), rs.getInt("days"),
                    rs.getLong("date").takeUnless { rs.wasNull() }, rs.getInt("start_min"), rs.getInt("end_min"), rs.getInt("color"),
                ))
            }
        }
    }

    @Synchronized
    fun upsertEvent(e: ScheduleEvent): Long {
        val sql = if (e.id == 0L) "INSERT INTO events(title,location,days,date,start_min,end_min,color) VALUES(?,?,?,?,?,?,?)"
        else "UPDATE events SET title=?,location=?,days=?,date=?,start_min=?,end_min=?,color=? WHERE id=?"
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, e.title); ps.setString(2, e.location); ps.setInt(3, e.days)
            if (e.date == null) ps.setNull(4, java.sql.Types.INTEGER) else ps.setLong(4, e.date)
            ps.setInt(5, e.startMin); ps.setInt(6, e.endMin); ps.setInt(7, e.color)
            if (e.id != 0L) ps.setLong(8, e.id)
            ps.executeUpdate()
        }
        return if (e.id != 0L) e.id else lastId()
    }

    @Synchronized
    fun deleteEvent(id: Long) = conn.prepareStatement("DELETE FROM events WHERE id=?").use { it.setLong(1, id); it.executeUpdate() }

    // ---------- classroom cache ----------
    @Synchronized
    fun courses(): List<Course> = conn.prepareStatement("SELECT * FROM courses ORDER BY name COLLATE NOCASE").use { ps ->
        ps.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(Course(rs.getString("id"), rs.getString("name"), rs.getString("section"), rs.getString("room"),
                    rs.getString("link"), rs.getLong("modules_fetched_at").takeUnless { rs.wasNull() }))
            }
        }
    }

    /** Replaces the course list, keeping each course's cached modules timestamp. */
    @Synchronized
    fun saveCourses(list: List<Course>) {
        conn.autoCommit = false
        try {
            val keep = list.map { it.id }.toSet()
            courses().filter { it.id !in keep }.forEach { c ->
                listOf("DELETE FROM courses WHERE id=?", "DELETE FROM topics WHERE course_id=?", "DELETE FROM course_items WHERE course_id=?")
                    .forEach { sql -> conn.prepareStatement(sql).use { it.setString(1, c.id); it.executeUpdate() } }
            }
            conn.prepareStatement(
                "INSERT INTO courses(id,name,section,room,link) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name, section=excluded.section, room=excluded.room, link=excluded.link"
            ).use { ps ->
                list.forEach { c -> ps.setString(1, c.id); ps.setString(2, c.name); ps.setString(3, c.section); ps.setString(4, c.room); ps.setString(5, c.link); ps.executeUpdate() }
            }
            conn.commit()
        } finally { conn.autoCommit = true }
    }

    @Synchronized
    fun saveModules(courseId: String, topics: List<Topic>, items: List<CourseItem>) {
        conn.autoCommit = false
        try {
            listOf("DELETE FROM topics WHERE course_id=?", "DELETE FROM course_items WHERE course_id=?")
                .forEach { sql -> conn.prepareStatement(sql).use { it.setString(1, courseId); it.executeUpdate() } }
            conn.prepareStatement("INSERT INTO topics(course_id,id,name,ord) VALUES(?,?,?,?)").use { ps ->
                topics.forEachIndexed { i, t -> ps.setString(1, courseId); ps.setString(2, t.id); ps.setString(3, t.name); ps.setInt(4, i); ps.executeUpdate() }
            }
            conn.prepareStatement(
                "INSERT OR REPLACE INTO course_items(course_id,id,kind,topic_id,title,description,link,due_at,materials,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)"
            ).use { ps ->
                items.forEach { i ->
                    ps.setString(1, courseId); ps.setString(2, i.id); ps.setString(3, i.kind); ps.setString(4, i.topicId)
                    ps.setString(5, i.title); ps.setString(6, i.description); ps.setString(7, i.link)
                    if (i.dueAt == null) ps.setNull(8, java.sql.Types.INTEGER) else ps.setLong(8, i.dueAt)
                    ps.setString(9, encodeAttachments(i.materials)); ps.setString(10, i.createdAt)
                    ps.executeUpdate()
                }
            }
            conn.prepareStatement("UPDATE courses SET modules_fetched_at=? WHERE id=?").use {
                it.setLong(1, System.currentTimeMillis()); it.setString(2, courseId); it.executeUpdate()
            }
            conn.commit()
        } finally { conn.autoCommit = true }
    }

    @Synchronized
    fun topics(courseId: String): List<Topic> = conn.prepareStatement("SELECT * FROM topics WHERE course_id=? ORDER BY ord").use { ps ->
        ps.setString(1, courseId)
        ps.executeQuery().use { rs -> buildList { while (rs.next()) add(Topic(courseId, rs.getString("id"), rs.getString("name"))) } }
    }

    @Synchronized
    fun courseItems(courseId: String): List<CourseItem> = conn.prepareStatement(
        "SELECT * FROM course_items WHERE course_id=? ORDER BY created_at DESC"
    ).use { ps ->
        ps.setString(1, courseId)
        ps.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(CourseItem(
                    courseId, rs.getString("id"), rs.getString("kind"), rs.getString("topic_id"), rs.getString("title"),
                    rs.getString("description"), rs.getString("link"), rs.getLong("due_at").takeUnless { rs.wasNull() },
                    decodeAttachments(rs.getString("materials")), rs.getString("created_at"),
                ))
            }
        }
    }

    private fun encodeAttachments(list: List<Attachment>) = kotlinx.serialization.json.buildJsonArray {
        list.forEach { a -> add(kotlinx.serialization.json.buildJsonObject { put("t", kotlinx.serialization.json.JsonPrimitive(a.title)); put("u", kotlinx.serialization.json.JsonPrimitive(a.url)); put("k", kotlinx.serialization.json.JsonPrimitive(a.kind)) }) }
    }.toString()

    private fun decodeAttachments(raw: String): List<Attachment> = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(raw).let { it as kotlinx.serialization.json.JsonArray }.map { e ->
            val o = e as kotlinx.serialization.json.JsonObject
            fun f(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            Attachment(f("t"), f("u"), f("k"))
        }
    }.getOrDefault(emptyList())

    // ---------- notes ----------
    @Synchronized
    fun notes(): List<Note> = conn.prepareStatement("SELECT * FROM notes ORDER BY updated_at DESC").use { ps ->
        ps.executeQuery().use { rs ->
            buildList {
                while (rs.next()) add(Note(rs.getLong("id"), rs.getString("title"), rs.getString("course"),
                    rs.getString("body"), rs.getString("quiz"), rs.getLong("updated_at")))
            }
        }
    }

    @Synchronized
    fun upsertNote(n: Note): Long {
        val sql = if (n.id == 0L) "INSERT INTO notes(title,course,body,quiz,updated_at) VALUES(?,?,?,?,?)"
        else "UPDATE notes SET title=?,course=?,body=?,quiz=?,updated_at=? WHERE id=?"
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, n.title); ps.setString(2, n.course); ps.setString(3, n.body); ps.setString(4, n.quiz)
            ps.setLong(5, System.currentTimeMillis())
            if (n.id != 0L) ps.setLong(6, n.id)
            ps.executeUpdate()
        }
        return if (n.id != 0L) n.id else lastId()
    }

    @Synchronized
    fun deleteNote(id: Long) = conn.prepareStatement("DELETE FROM notes WHERE id=?").use { it.setLong(1, id); it.executeUpdate() }

    private fun lastId() = conn.createStatement().use { it.executeQuery("SELECT last_insert_rowid()").use { r -> r.next(); r.getLong(1) } }

    // ---------- chat ----------
    @Synchronized
    fun chat(taskId: Long?): List<ChatMsg> {
        val sql = if (taskId == null) "SELECT * FROM chat WHERE task_id IS NULL ORDER BY id" else "SELECT * FROM chat WHERE task_id=? ORDER BY id"
        return conn.prepareStatement(sql).use { ps ->
            if (taskId != null) ps.setLong(1, taskId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(ChatMsg(rs.getLong("id"), rs.getLong("task_id").takeUnless { rs.wasNull() }, rs.getString("role"), rs.getString("content"), rs.getLong("ts")))
                }
            }
        }
    }

    @Synchronized
    fun addChat(m: ChatMsg) = conn.prepareStatement("INSERT INTO chat(task_id,role,content,ts) VALUES(?,?,?,?)").use { ps ->
        if (m.taskId == null) ps.setNull(1, java.sql.Types.INTEGER) else ps.setLong(1, m.taskId)
        ps.setString(2, m.role); ps.setString(3, m.content); ps.setLong(4, m.ts); ps.executeUpdate()
    }

    @Synchronized
    fun clearChat(taskId: Long?) {
        val sql = if (taskId == null) "DELETE FROM chat WHERE task_id IS NULL" else "DELETE FROM chat WHERE task_id=?"
        conn.prepareStatement(sql).use { ps -> if (taskId != null) ps.setLong(1, taskId); ps.executeUpdate() }
    }

    /** Records that a reminder was sent; false if it already had been (so each reminder fires once). */
    @Synchronized
    fun markNotified(key: String): Boolean = conn.prepareStatement("INSERT OR IGNORE INTO notified(key,at) VALUES(?,?)").use { ps ->
        ps.setString(1, key); ps.setLong(2, System.currentTimeMillis()); ps.executeUpdate() == 1
    }

    // ---------- settings ----------
    @Synchronized
    fun get(key: String, def: String = ""): String = conn.prepareStatement("SELECT value FROM settings WHERE key=?").use { ps ->
        ps.setString(1, key); ps.executeQuery().use { if (it.next()) it.getString(1) else def }
    }

    @Synchronized
    fun set(key: String, value: String) = conn.prepareStatement(
        "INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value"
    ).use { ps -> ps.setString(1, key); ps.setString(2, value); ps.executeUpdate() }
}

object Keys {
    const val GEMINI_KEY = "gemini_key"
    const val MODEL = "gemini_model"
    const val COMPOSIO_KEY = "composio_key"
    const val COMPOSIO_USER = "composio_user"
    const val COMPOSIO_PROJECT = "composio_project"
    const val COMPOSIO_ACCOUNT = "composio_account"
    const val OFFLINE = "offline_mode"
    const val NOTIFY_DEADLINES = "notify_deadlines"
    const val NOTIFY_CLASSES = "notify_classes"
    const val NOTIFY_JOBS = "notify_jobs"
    const val CLASSROOM_AUTH_CONFIG = "classroom_auth_config"
    const val DRIVE_AUTH_CONFIG = "drive_auth_config"
    const val LAST_SYNC = "last_sync"
}
