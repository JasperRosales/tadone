package com.tadone

import java.io.File

/**
 * System notifications. Linux uses `notify-send` (works on GNOME, KDE, XFCE without a tray icon);
 * Windows and macOS go through the tray icon set up in Main.
 */
object Notifier {
    /** Set by Main when a tray icon exists: (title, body) -> Unit. */
    @Volatile var tray: ((String, String) -> Unit)? = null

    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private val notifySend: String? by lazy {
        System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "notify-send") }.firstOrNull { it.canExecute() }?.path
    }
    private val icon: String? by lazy {
        runCatching {
            val f = File(System.getProperty("java.io.tmpdir"), "tadone-icon.png")
            if (!f.exists()) Notifier::class.java.getResourceAsStream("/tadone.png")?.use { i -> f.outputStream().use { i.copyTo(it) } }
            f.takeIf { it.exists() }?.absolutePath
        }.getOrNull()
    }

    fun send(title: String, body: String) {
        runCatching {
            val ns = notifySend
            if (isLinux && ns != null) {
                val cmd = mutableListOf(ns, "-a", "Tadone")
                icon?.let { cmd += listOf("-i", it) }
                cmd += listOf(title, body)
                ProcessBuilder(cmd).redirectErrorStream(true).start()
            } else tray?.invoke(title, body)
        }
    }
}
