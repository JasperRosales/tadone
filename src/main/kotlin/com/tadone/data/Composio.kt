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
class Composio(private val apiKey: String, private val userId: String) {
    private val base = "https://backend.composio.dev/api/v3"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    private fun call(method: String, path: String, body: JsonElement? = null): JsonElement {
        if (apiKey.isBlank()) throw ComposioException("Add your Composio API key in Settings first.")
        val req = Request.Builder().url("$base$path").header("x-api-key", apiKey)
            .method(method, body?.toString()?.toRequestBody(jsonType)).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val el = runCatching { json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
            if (!r.isSuccessful) {
                val msg = (el as? JsonObject)?.get("error")?.let { e -> (e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: e.toString() }
                throw ComposioException("Composio ${r.code}: ${msg ?: text.take(200)}")
            }
            return el
        }
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

    fun connectedToolkits(): Set<String> {
        val res = call("GET", "/connected_accounts?user_ids=$userId&statuses=ACTIVE").jsonObject
        return res["items"]?.jsonArray?.mapNotNull {
            it.jsonObject["toolkit"]?.jsonObject?.get("slug")?.jsonPrimitive?.content
        }?.toSet().orEmpty()
    }

    // ---------------- Google Classroom ----------------

    data class Course(val id: String, val name: String)

    fun courses(): List<Course> = paged("GOOGLE_CLASSROOM_COURSES_LIST", buildJsonObject {
        put("studentId", "me"); putJsonArray("courseStates") { add("ACTIVE") }; put("pageSize", 50)
    }, "courses").map { Course(it.str("id")!!, it.str("name") ?: "Course") }

    /** Pulls every coursework item + my submission for each active course. */
    fun fetchTasks(onProgress: (String) -> Unit = {}): List<Task> {
        val out = mutableListOf<Task>()
        for (c in courses()) {
            onProgress("Reading ${c.name}…")
            val works = runCatching {
                paged("GOOGLE_CLASSROOM_COURSE_WORK_LIST", buildJsonObject { put("courseId", c.id); put("pageSize", 50) }, "courseWork")
            }.getOrDefault(emptyList())
            for (w in works) {
                val workId = w.str("id") ?: continue
                val sub = runCatching {
                    paged("GOOGLE_CLASSROOM_COURSE_WORK_STUDENT_SUBMISSIONS_LIST", buildJsonObject {
                        put("courseId", c.id); put("courseWorkId", workId); put("userId", "me")
                    }, "studentSubmissions").firstOrNull()
                }.getOrNull()
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
            findArray(data, field)?.forEach { (it as? JsonObject)?.let(all::add) }
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
