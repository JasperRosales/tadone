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
    const val ANTHROPIC_KEY = "anthropic_key"
    const val MODEL = "model"
    const val COMPOSIO_KEY = "composio_key"
    const val COMPOSIO_USER = "composio_user"
    const val CLASSROOM_AUTH_CONFIG = "classroom_auth_config"
    const val DRIVE_AUTH_CONFIG = "drive_auth_config"
    const val LAST_SYNC = "last_sync"
}
