package com.tadone.data

import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ComposioException(msg: String) : Exception(msg)

/**
 * Thin client over the Composio v3 REST API. Google Classroom / Drive auth is
 * fully managed by Composio; we just pass the project API key + user id.
 */
class Composio(private val apiKey: String, private val userId: String, private val projectId: String = "") {
    private val base = "https://backend.composio.dev/api/v3"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = sharedHttp
    private val jsonType = "application/json".toMediaType()

    private fun call(method: String, path: String, body: JsonElement? = null): JsonElement {
        if (apiKey.isBlank()) throw ComposioException("Add your Composio API key in Settings first.")
        val req = Request.Builder().url("$base$path").apply {
            // "Sign in with Composio" gives a user key (uak_…) that is scoped per project via a header;
            // a pasted project key (ak_…) works on its own.
            if (apiKey.startsWith("uak_")) {
                header("x-user-api-key", apiKey)
                if (projectId.isNotBlank()) header("x-project-id", projectId)
            } else header("x-api-key", apiKey)
        }.method(method, body?.toString()?.toRequestBody(jsonType)).build()
        gate.acquire()
        try { http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val el = runCatching { json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
            if (!r.isSuccessful) {
                val msg = (el as? JsonObject)?.get("error")?.let { e -> (e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: e.toString() }
                throw ComposioException("Composio ${r.code}: ${msg ?: text.take(200)}")
            }
            return el
        } } finally { gate.release() }
    }

    companion object {
        /** At most 3 Composio requests in flight across the whole app, however many jobs run at once. */
        private val gate = java.util.concurrent.Semaphore(3)
        private val sharedHttp = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()
    }

    /** Execute a tool and return its `data` payload. */
    fun execute(slug: String, args: JsonObject): JsonElement {
        val res = call("POST", "/tools/execute/$slug", buildJsonObject {
            put("user_id", userId)
            put("arguments", args)
            put("version", "latest")
        }).jsonObject
        val ok = res["successful"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!ok) throw ComposioException(res["error"]?.let { if (it is JsonPrimitive) it.content else it.toString() } ?: "$slug failed")
        return res["data"] ?: JsonObject(emptyMap())
    }

    /** Returns a browser URL that connects the given toolkit for this user. */
    fun connectLink(authConfigId: String): String {
        val res = call("POST", "/connected_accounts/link", buildJsonObject {
            put("auth_config_id", authConfigId); put("user_id", userId)
        }).jsonObject
        return (res["redirect_url"] ?: res["redirectUrl"])?.jsonPrimitive?.content
            ?: throw ComposioException("No redirect URL returned")
    }

    /** Finds an enabled auth config for [toolkit], creating a Composio-managed one if none exists. */
    fun ensureAuthConfig(toolkit: String): String {
        val list = call("GET", "/auth_configs?toolkit_slug=$toolkit").jsonObject["items"]?.jsonArray.orEmpty()
        list.map { it.jsonObject }
            .firstOrNull { it["status"]?.jsonPrimitive?.contentOrNull?.uppercase() != "DISABLED" }
            ?.get("id")?.jsonPrimitive?.contentOrNull?.let { return it }
        val res = call("POST", "/auth_configs", buildJsonObject {
            putJsonObject("toolkit") { put("slug", toolkit) }
            putJsonObject("auth_config") { put("type", "use_composio_managed_auth") }
        }).jsonObject
        return (res["auth_config"] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            ?: res["id"]?.jsonPrimitive?.contentOrNull
            ?: throw ComposioException("Composio did not return an auth config id for $toolkit")
    }

    /** Active connections for this user: toolkit slug → connected account id. */
    fun connections(): Map<String, String> {
        val res = call("GET", "/connected_accounts?user_ids=$userId&statuses=ACTIVE&limit=100").jsonObject
        return res["items"]?.jsonArray?.mapNotNull {
            val o = it.jsonObject
            val slug = o["toolkit"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull
            val id = o["id"]?.jsonPrimitive?.contentOrNull
            if (slug != null && id != null) slug to id else null
        }?.toMap().orEmpty()
    }

    fun connectedToolkits(): Set<String> = connections().keys

    fun disconnect(connectedAccountId: String) { call("DELETE", "/connected_accounts/$connectedAccountId") }

    // ---------------- Google Classroom ----------------

    fun courses(): List<Course> = paged("GOOGLE_CLASSROOM_COURSES_LIST", buildJsonObject {
        put("studentId", "me"); putJsonArray("courseStates") { add("ACTIVE") }; put("pageSize", 50)
    }, "courses").map {
        Course(it.str("id")!!, it.str("name") ?: "Course", it.str("section").orEmpty(), it.str("room").orEmpty(), it.str("alternateLink"))
    }

    /**
     * One course's modules: topics, coursework and materials. Exactly 3 tool calls (more only if a
     * list spans several pages). Called lazily — only when a course is opened or refreshed.
     */
    fun courseModules(courseId: String): Pair<List<Topic>, List<CourseItem>> {
        val topics = paged("GOOGLE_CLASSROOM_COURSES_TOPICS_LIST", buildJsonObject { put("courseId", courseId); put("pageSize", 0) }, "topic|topics")
            .mapNotNull { t -> t.str("topicId")?.let { Topic(courseId, it, t.str("name") ?: "Topic") } }
        val work = paged("GOOGLE_CLASSROOM_COURSE_WORK_LIST", buildJsonObject { put("courseId", courseId); put("pageSize", 100) }, "courseWork")
            .mapNotNull { w -> item(courseId, w, if (w.str("workType") == "ASSIGNMENT" || w.str("workType") == null) "assignment" else "question") }
        val materials = paged("GOOGLE_CLASSROOM_COURSE_WORK_MATERIALS_LIST", buildJsonObject { put("courseId", courseId); put("pageSize", 100) }, "courseWorkMaterial|courseWorkMaterials")
            .mapNotNull { m -> item(courseId, m, "material") }
        return topics to (work + materials)
    }

    private fun item(courseId: String, o: JsonObject, kind: String): CourseItem? {
        val id = o.str("id") ?: return null
        return CourseItem(
            courseId = courseId, id = id, kind = kind, topicId = o.str("topicId"),
            title = o.str("title") ?: "Untitled", description = o.str("description").orEmpty(),
            link = o.str("alternateLink"), dueAt = dueMillis(o),
            materials = attachments(o), createdAt = o.str("creationTime").orEmpty(),
        )
    }

    /** Flattens Classroom "materials" (Drive files, YouTube, links, forms) into title + url pairs. */
    private fun attachments(o: JsonObject): List<Attachment> = (o["materials"] as? JsonArray).orEmpty().mapNotNull { m ->
        val mo = m as? JsonObject ?: return@mapNotNull null
        (mo["driveFile"] as? JsonObject)?.let { d ->
            val f = (d["driveFile"] as? JsonObject) ?: d
            return@mapNotNull Attachment(f.str("title") ?: "Drive file", f.str("alternateLink").orEmpty(), "drive")
        }
        (mo["youtubeVideo"] as? JsonObject)?.let { return@mapNotNull Attachment(it.str("title") ?: "YouTube video", it.str("alternateLink").orEmpty(), "video") }
        (mo["link"] as? JsonObject)?.let { return@mapNotNull Attachment(it.str("title") ?: it.str("url").orEmpty(), it.str("url").orEmpty(), "link") }
        (mo["form"] as? JsonObject)?.let { return@mapNotNull Attachment(it.str("title") ?: "Form", it.str("formUrl").orEmpty(), "form") }
        null
    }

    /**
     * Pulls coursework + my submissions. Uses courseWorkId "-" so each course costs 2 calls
     * (coursework + all my submissions) instead of one submissions call per assignment.
     */
    fun fetchTasks(courses: List<Course>, onProgress: (String) -> Unit = {}): List<Task> {
        val out = mutableListOf<Task>()
        for (c in courses) {
            onProgress("Reading ${c.name}…")
            val works = runCatching {
                paged("GOOGLE_CLASSROOM_COURSE_WORK_LIST", buildJsonObject { put("courseId", c.id); put("pageSize", 100) }, "courseWork")
            }.getOrDefault(emptyList())
            if (works.isEmpty()) continue
            val subs = runCatching {
                paged("GOOGLE_CLASSROOM_COURSE_WORK_STUDENT_SUBMISSIONS_LIST", buildJsonObject {
                    put("courseId", c.id); put("courseWorkId", "-"); put("userId", "me"); put("pageSize", 100)
                }, "studentSubmissions")
            }.getOrDefault(emptyList()).associateBy { it.str("courseWorkId") }
            for (w in works) {
                val workId = w.str("id") ?: continue
                val sub = subs[workId]
                val state = sub?.str("state")
                out += Task(
                    title = w.str("title") ?: "Untitled",
                    notes = w.str("description").orEmpty(),
                    course = c.name,
                    dueAt = dueMillis(w),
                    done = state == "TURNED_IN" || state == "RETURNED",
                    source = "classroom",
                    courseId = c.id, workId = workId,
                    submissionId = sub?.str("id"), submissionState = state,
                    link = w.str("alternateLink"),
                )
            }
        }
        return out
    }

    fun turnIn(t: Task) {
        execute("GOOGLE_CLASSROOM_TURN_IN_STUDENT_SUBMISSION", ids(t))
    }

    fun reclaim(t: Task) {
        execute("GOOGLE_CLASSROOM_COURSE_WORK_STUDENT_SUBMISSIONS_RECLAIM", ids(t))
    }

    fun attachLink(t: Task, url: String) = execute("GOOGLE_CLASSROOM_MODIFY_STUDENT_SUBMISSION_ATTACHMENTS", buildJsonObject {
        ids(t).forEach { (k, v) -> put(k, v) }
        putJsonArray("addAttachments") { addJsonObject { putJsonObject("link") { put("url", url) } } }
    })

    /** Uploads a local file to Google Drive, then attaches it to my submission. */
    fun uploadAndAttach(t: Task, file: File) {
        val driveId = uploadToDrive(file)
        execute("GOOGLE_CLASSROOM_MODIFY_STUDENT_SUBMISSION_ATTACHMENTS", buildJsonObject {
            ids(t).forEach { (k, v) -> put(k, v) }
            putJsonArray("addAttachments") { addJsonObject { putJsonObject("driveFile") { put("id", driveId) } } }
        })
    }

    /** Teachers only: push title/description edits back to Classroom. */
    fun patchCourseWork(t: Task) = execute("GOOGLE_CLASSROOM_COURSES_COURSE_WORK_PATCH", buildJsonObject {
        put("courseId", t.courseId); put("id", t.workId)
        put("title", t.title); put("description", t.notes); put("updateMask", "title,description")
    })

    private fun ids(t: Task): JsonObject {
        if (t.courseId == null || t.workId == null || t.submissionId == null)
            throw ComposioException("This task has no Classroom submission yet — sync first.")
        return buildJsonObject { put("courseId", t.courseId); put("courseWorkId", t.workId); put("id", t.submissionId) }
    }

    private fun uploadToDrive(file: File): String {
        val bytes = file.readBytes()
        if (bytes.size > 5 * 1024 * 1024) throw ComposioException("Files must be 5 MB or smaller.")
        val mime = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
        val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        val req = call("POST", "/files/upload/request", buildJsonObject {
            put("toolkit_slug", "googledrive"); put("tool_slug", "GOOGLEDRIVE_UPLOAD_FILE")
            put("filename", file.name); put("mimetype", mime); put("md5", md5)
        }).jsonObject
        val url = (req["new_presigned_url"] ?: req["newPresignedUrl"] ?: req["presigned_url"])?.jsonPrimitive?.content
        val key = (req["key"] ?: req["s3key"])?.jsonPrimitive?.content
        if (url != null) {
            http.newCall(Request.Builder().url(url).put(bytes.toRequestBody(mime.toMediaType())).build()).execute().use {
                if (!it.isSuccessful) throw ComposioException("Upload failed (${it.code})")
            }
        }
        if (key == null) throw ComposioException("Composio did not return an upload key")
        val data = execute("GOOGLEDRIVE_UPLOAD_FILE", buildJsonObject {
            putJsonObject("file_to_upload") { put("name", file.name); put("mimetype", mime); put("s3key", key) }
        })
        return findString(data, "id") ?: throw ComposioException("Drive did not return a file id")
    }

    // ---------------- helpers ----------------

    private fun paged(slug: String, args: JsonObject, field: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var token: String? = null
        var guard = 0
        do {
            val a = JsonObject(args + (token?.let { mapOf("pageToken" to JsonPrimitive(it)) } ?: emptyMap()))
            val data = execute(slug, a)
            // field may list alternatives ("topic|topics") since tool output wrappers vary
            field.split('|').firstNotNullOfOrNull { findArray(data, it) }?.forEach { (it as? JsonObject)?.let(all::add) }
            token = findString(data, "nextPageToken")?.takeIf { it.isNotBlank() }
        } while (token != null && ++guard < 20)
        return all
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun findArray(e: JsonElement, key: String): JsonArray? = when (e) {
        is JsonObject -> (e[key] as? JsonArray) ?: e.values.firstNotNullOfOrNull { findArray(it, key) }
        else -> null
    }

    private fun findString(e: JsonElement, key: String): String? = when (e) {
        is JsonObject -> (e[key] as? JsonPrimitive)?.contentOrNull ?: e.values.firstNotNullOfOrNull { findString(it, key) }
        else -> null
    }

    private fun dueMillis(w: JsonObject): Long? {
        val d = w["dueDate"] as? JsonObject ?: return null
        val date = runCatching {
            LocalDate.of(d["year"]!!.jsonPrimitive.int, d["month"]!!.jsonPrimitive.int, d["day"]!!.jsonPrimitive.int)
        }.getOrNull() ?: return null
        val t = w["dueTime"] as? JsonObject
        val time = LocalTime.of(
            t?.get("hours")?.jsonPrimitive?.intOrNull ?: 23,
            t?.get("minutes")?.jsonPrimitive?.intOrNull ?: 59,
        )
        // Classroom due dates are UTC
        return date.atTime(time).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
}


/** Result of a successful "Sign in with Composio". */
data class ComposioAccount(val userApiKey: String, val projectId: String, val projectName: String, val email: String)

/**
 * Browser sign-in, the same flow the `composio login` CLI uses:
 * create a session → open dashboard.composio.dev/?cliKey=<id> → poll until linked.
 */
object ComposioLogin {
    private const val api = "https://backend.composio.dev/api/v3.1"
    private const val web = "https://dashboard.composio.dev/"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    private fun req(b: Request.Builder): JsonObject = http.newCall(b.build()).execute().use { r ->
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw ComposioException("Composio sign-in failed (${r.code}): ${text.take(200)}")
        json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull

    data class Session(val id: String, val loginUrl: String, val expiresAt: Long)

    fun start(): Session {
        val s = req(Request.Builder().url("$api/cli/create-session")
            .post("""{"scope":"user"}""".toRequestBody("application/json".toMediaType())))
        val id = s.str("id") ?: throw ComposioException("Composio did not return a session id")
        val exp = s.str("expiresAt")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: (System.currentTimeMillis() + 10 * 60_000)
        return Session(id, "$web?cliKey=$id", exp)
    }

    /** Polls every 2s until the browser login completes or the session expires (cancellable). */
    suspend fun await(s: Session): ComposioAccount {
        while (System.currentTimeMillis() < s.expiresAt) {
            val r = runCatching { req(Request.Builder().url("$api/cli/get-session?id=${s.id}")) }.getOrNull()
            if (r?.str("status") == "linked") {
                val key = r.str("api_key") ?: throw ComposioException("Sign-in finished but no key was returned")
                val email = (r["account"] as? JsonObject)?.str("email").orEmpty()
                return account(key, email)
            }
            kotlinx.coroutines.delay(2000)
        }
        throw ComposioException("Composio sign-in timed out — try again.")
    }

    /** Resolves the default project for a user API key. */
    fun account(userApiKey: String, email: String = ""): ComposioAccount {
        val info = req(Request.Builder().url("$api/auth/session/info").header("x-user-api-key", userApiKey))
        val project = info["project"] as? JsonObject
            ?: throw ComposioException("Your Composio account has no project yet — create one at dashboard.composio.dev")
        val member = info["org_member"] as? JsonObject
        return ComposioAccount(
            userApiKey, project.str("nano_id") ?: project.str("id").orEmpty(),
            project.str("name").orEmpty(), email.ifBlank { member?.str("email").orEmpty() },
        )
    }

    /** Reuses an existing `composio login` from the CLI on this machine, if any. */
    fun fromCli(): ComposioAccount? {
        val f = File(System.getProperty("user.home"), ".composio/user_data.json")
        if (!f.isFile) return null
        val key = runCatching { json.parseToJsonElement(f.readText()).jsonObject.str("api_key") }.getOrNull()
        if (key.isNullOrBlank()) return null
        return runCatching { account(key) }.getOrNull()
    }
}
