package com.tadone

import com.tadone.data.Db

/** Dev check: sends one real notification and plays the theme sound and the synthesized fallback chime. */
fun main() {
    Db.init()
    println("notification: " + Notifier.test())
    println("theme sound (deadline): " + Sounds.play("deadline", force = true))
    val synth = Sounds::class.java.getDeclaredMethod("synth", List::class.java).apply { isAccessible = true }
    println("synth chime: " + runCatching { synth.invoke(Sounds, listOf(784.0, 1046.5)); true }.getOrElse { "failed: ${it.cause ?: it}" })
    System.exit(0)
}
