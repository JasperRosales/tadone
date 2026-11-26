package com.tadone

import com.tadone.data.Db
import com.tadone.data.Keys
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * System notifications + sounds.
 *
 * Linux: notify-send if installed, otherwise the freedesktop notification service directly over D-Bus
 * (gdbus ships with GLib, so it's on GNOME, KDE, XFCE…). Windows / macOS: the tray icon set up in Main.
 */
object Notifier {
    /** Set by Main when a tray icon exists: (title, body) -> Unit. */
    @Volatile var tray: ((String, String) -> Unit)? = null

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tadone-notify").apply { isDaemon = true } }
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")

    private fun which(cmd: String): String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, cmd) }.firstOrNull { it.canExecute() }?.path

    private val icon: String? by lazy {
        runCatching {
            val f = File(System.getProperty("java.io.tmpdir"), "tadone-icon.png")
            if (!f.exists()) Notifier::class.java.getResourceAsStream("/tadone.png")?.use { i -> f.outputStream().use { i.copyTo(it) } }
            f.takeIf { it.exists() }?.absolutePath
        }.getOrNull()
    }

    /** Fire-and-forget: shows the notification and plays the sound for [kind] off the UI thread. */
    fun send(title: String, body: String, kind: String = "activity") {
        worker.execute { deliver(title, body); Sounds.play(kind) }
    }

    /** Same as [send] but waits and reports what happened (used by the Settings test button). */
    fun test(): String {
        val result = worker.submit<String> { val r = deliver("Tadone", "Notifications are working."); Sounds.play("test"); r }
        return runCatching { result.get(8, TimeUnit.SECONDS) }.getOrElse { "Timed out talking to the notification service." }
    }

    /** Returns a short description of how it was delivered, or why it failed. */
    private fun deliver(title: String, body: String): String {
        if (isLinux) {
            which("notify-send")?.let { ns ->
                val cmd = mutableListOf(ns, "-a", "Tadone")
                icon?.let { cmd += listOf("-i", it) }
                cmd += listOf(title, body)
                val (ok, err) = run(cmd)
                if (ok) return "Sent with notify-send." else if (which("gdbus") == null) return "notify-send failed: $err"
            }
            which("gdbus")?.let { gd ->
                // org.freedesktop.Notifications.Notify(app_name, replaces_id, app_icon, summary, body, actions, hints, expire_timeout)
                val (ok, err) = run(listOf(gd, "call", "--session",
                    "--dest", "org.freedesktop.Notifications", "--object-path", "/org/freedesktop/Notifications",
                    "--method", "org.freedesktop.Notifications.Notify",
                    gv("Tadone"), "uint32 0", gv(icon.orEmpty()), gv(title), gv(body), "@as []",
                    "{'desktop-entry': <'tadone'>, 'urgency': <byte 1>}", "int32 8000"))
                return if (ok) "Sent to the desktop notification service." else "The desktop notification service refused it: $err"
            }
            return "No way to show notifications: install libnotify (notify-send) or GLib (gdbus)."
        }
        val t = tray ?: return "System notifications aren't available on this computer."
        t(title, body)
        return "Sent through the system tray."
    }

    /** Quotes text as a GVariant string literal for gdbus. */
    private fun gv(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

    private fun run(cmd: List<String>): Pair<Boolean, String> = runCatching {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        if (!p.waitFor(4, TimeUnit.SECONDS)) { p.destroy(); return false to "no answer" }
        val out = p.inputStream.bufferedReader().readText().trim()
        (p.exitValue() == 0) to out.take(200)
    }.getOrElse { false to (it.message ?: it.toString()) }
}

/**
 * Notification sounds. Uses the desktop's own sound theme when it can (Linux: pw-play / paplay with the
 * freedesktop theme), otherwise plays a short synthesized chime through Java Sound, which works everywhere.
 */
object Sounds {
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private const val THEME = "/usr/share/sounds/freedesktop/stereo"

    /** Distinct sound per kind so you can tell a deadline from a finished import without looking. */
    private val themeSound = mapOf(
        "deadline" to "message-new-instant", "class" to "bell", "activity" to "complete",
        "error" to "dialog-warning", "test" to "message-new-instant",
    )
    /** Fallback chimes: note frequencies (Hz). */
    private val chime = mapOf(
        "deadline" to listOf(880.0, 1174.7), "class" to listOf(659.3, 880.0, 659.3), "activity" to listOf(784.0, 1046.5),
        "error" to listOf(392.0, 311.1), "test" to listOf(880.0, 1174.7),
    )

    fun enabled() = runCatching { Db.get(Keys.NOTIFY_SOUND, "1") == "1" }.getOrDefault(true)

    /** Plays the sound for [kind] (blocking, call off the UI thread). Returns false if sounds are off or nothing could play. */
    fun play(kind: String, force: Boolean = false): Boolean {
        if (kind == "silent" || (!force && !enabled())) return false
        if (isLinux && playTheme(themeSound[kind] ?: "complete")) return true
        return runCatching { synth(chime[kind] ?: chime.getValue("activity")); true }.getOrDefault(false)
    }

    private fun playTheme(name: String): Boolean {
        val file = File(THEME, "$name.oga").takeIf { it.isFile } ?: return false
        val player = listOf("pw-play", "paplay", "ogg123").firstNotNullOfOrNull { cmd ->
            System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, cmd) }.firstOrNull { it.canExecute() }?.path
        } ?: return false
        return runCatching {
            val p = ProcessBuilder(player, file.path).redirectErrorStream(true).start()
            p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
    }

    /** A soft two- or three-note chime (sine with a quick attack and exponential decay). */
    private fun synth(notes: List<Double>) {
        val rate = 44_100f
        val noteMs = 170
        val samplesPer = (rate * noteMs / 1000).toInt()
        val tail = (rate * 0.25).toInt()
        val total = samplesPer * notes.size + tail
        val buf = ByteArray(total * 2)
        notes.forEachIndexed { n, f ->
            val start = n * samplesPer
            for (i in 0 until samplesPer + tail) {
                val idx = start + i
                if (idx >= total) break
                val t = i / rate
                val env = (if (i < 220) i / 220.0 else 1.0) * exp(-t * 7.0)
                val v = (sin(2 * PI * f * t) * 0.8 + sin(4 * PI * f * t) * 0.2) * env * 0.35
                val prev = (buf[idx * 2].toInt() and 0xFF) or (buf[idx * 2 + 1].toInt() shl 8)
                val mixed = (prev + v * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buf[idx * 2] = (mixed and 0xFF).toByte(); buf[idx * 2 + 1] = (mixed shr 8).toByte()
            }
        }
        val fmt = AudioFormat(rate, 16, 1, true, false)
        AudioSystem.getSourceDataLine(fmt).use { line ->
            line.open(fmt); line.start(); line.write(buf, 0, buf.size); line.drain()
        }
    }
}
