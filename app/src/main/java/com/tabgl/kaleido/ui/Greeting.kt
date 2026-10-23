package com.tabgl.kaleido.ui

/** A time-aware greeting, with the name appended when one is set in Settings. */
fun greetingFor(hour: Int, name: String): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
    return if (name.isBlank()) part else "$part, ${name.trim()}"
}
