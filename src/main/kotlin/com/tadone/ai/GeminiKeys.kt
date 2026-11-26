package com.tadone.ai

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.util.concurrent.TimeUnit

/**
 * "Get a key from Gemini": Google has no API to mint AI Studio keys, so we open the key page
 * and watch the clipboard for a freshly copied key, then verify it and pick a usable model.
 */
object GeminiKeys {
    const val KEY_PAGE = "https://aistudio.google.com/apikey"
    private val keyPattern = Regex("AIza[0-9A-Za-z_\\-]{35}")
    private val http = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    fun findKey(text: String?): String? = text?.let { keyPattern.find(it)?.value }

    fun clipboardText(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    /** Returns the models this key can call generateContent on; throws if the key is rejected. */
    fun models(key: String): List<String> {
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
            .header("x-goog-api-key", key).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException(
                if (r.code == 400 || r.code == 403) "Google rejected that key — copy it again from AI Studio." else "Couldn't check the key (${r.code})."
            )
            return json.parseToJsonElement(text).jsonObject["models"]?.jsonArray.orEmpty().mapNotNull { m ->
                val o = m.jsonObject
                val methods = o["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                o["name"]?.jsonPrimitive?.content?.removePrefix("models/")?.takeIf { "generateContent" in methods }
            }
        }
    }

    /** Keeps [current] if available, else the newest plain "flash" text model. */
    fun pickModel(available: List<String>, current: String): String {
        if (current in available) return current
        val skip = listOf("lite", "tts", "image", "preview", "exp", "live", "audio", "embedding", "thinking")
        fun version(n: String) = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return available.filter { it.startsWith("gemini-") && "flash" in it && skip.none(it::contains) }
            .maxByOrNull(::version)
            ?: available.firstOrNull { it.startsWith("gemini-") }
            ?: current
    }
}
