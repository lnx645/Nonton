package id.nonton.app.core

import java.util.Locale

private val idLocale = Locale("id", "ID")

fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(idLocale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(idLocale, "%d:%02d", minutes, seconds)
    }
}

fun formatCount(count: Long): String {
    if (count < 0) return ""
    return when {
        count >= 1_000_000_000 -> "${compact(count / 1_000_000_000.0)} M"
        count >= 1_000_000 -> "${compact(count / 1_000_000.0)} jt"
        count >= 1_000 -> "${compact(count / 1_000.0)} rb"
        else -> count.toString()
    }
}

private fun compact(value: Double): String =
    if (value >= 10) {
        String.format(idLocale, "%.0f", value)
    } else {
        String.format(idLocale, "%.1f", value)
    }

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(idLocale, "%.1f %s", value, units[index])
}

fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/dtk"

fun sanitizeFileName(name: String): String {
    val cleaned = name
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return cleaned.take(120).ifBlank { "media" }
}

fun formatViewsAndDate(views: Long, date: String?): String {
    val parts = mutableListOf<String>()
    if (views >= 0) parts += "${formatCount(views)} tayangan"
    if (!date.isNullOrBlank()) parts += date
    return parts.joinToString(" · ")
}

fun formatTimestamp(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return formatDuration(safe)
}
