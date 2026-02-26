package com.gymlog.app.ui.common

fun parseMMSS(input: String): Int? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":")
    if (parts.size != 2) return null
    val minutes = parts[0].toIntOrNull() ?: return null
    val seconds = parts[1].toIntOrNull() ?: return null
    if (minutes < 0 || seconds < 0 || seconds >= 60) return null
    return minutes * 60 + seconds
}

fun formatMMSS(totalSeconds: Int?): String {
    if (totalSeconds == null) return "-"
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "$min:%02d".format(sec)
}
