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
    val localized = translateDateText(date)
    if (localized.isNotBlank()) parts += localized
    return parts.joinToString(" · ")
}

fun formatTimestamp(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return formatDuration(safe)
}

/** Waktu relatif Bahasa Indonesia, mis. "3 jam lalu". Null bila tanggal di masa depan. */
fun relativeTimeId(instant: java.time.Instant?): String? {
    if (instant == null) return null
    val seconds = java.time.Duration.between(instant, java.time.Instant.now()).seconds
    if (seconds < 0) return null
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "baru saja"
        minutes < 60 -> "$minutes menit lalu"
        hours < 24 -> "$hours jam lalu"
        days < 7 -> (if (days <= 1) "kemarin" else "$days hari lalu")
        days < 30 -> "${days / 7} minggu lalu"
        days < 365 -> "${days / 30} bulan lalu"
        else -> "${days / 365} tahun lalu"
    }
}

private val relativeEnRegex =
    Regex("(\\d+)\\s*(second|minute|hour|day|week|month|year)s?\\s*ago", RegexOption.IGNORE_CASE)

private val relativeUnitId = mapOf(
    "second" to "detik",
    "minute" to "menit",
    "hour" to "jam",
    "day" to "hari",
    "week" to "minggu",
    "month" to "bulan",
    "year" to "tahun",
)

private val monthEnToId = mapOf(
    "january" to "Januari", "february" to "Februari", "march" to "Maret",
    "april" to "April", "may" to "Mei", "june" to "Juni",
    "july" to "Juli", "august" to "Agustus", "september" to "September",
    "october" to "Oktober", "november" to "November", "december" to "Desember",
    "jan" to "Jan", "feb" to "Feb", "mar" to "Mar", "apr" to "Apr",
    "jun" to "Jun", "jul" to "Jul", "aug" to "Agu", "sep" to "Sep",
    "sept" to "Sep", "oct" to "Okt", "nov" to "Nov", "dec" to "Des",
)

/** Tanggal terbaik untuk ditampilkan: relatif dari Instant bila ada, else terjemahan teks. */
fun bestDateText(textual: String?, instant: java.time.Instant?): String =
    relativeTimeId(instant) ?: translateDateText(textual)

/** Terjemahkan tanggal relatif Inggris ("3 days ago", "Aug 12, 2026") ke Bahasa Indonesia. */
fun translateDateText(text: String?): String {
    if (text.isNullOrBlank()) return ""
    var s = text.trim()
    if (s.equals("just now", ignoreCase = true)) return "baru saja"
    if (s.equals("yesterday", ignoreCase = true)) return "kemarin"
    if (s.equals("today", ignoreCase = true)) return "hari ini"
    s = relativeEnRegex.replace(s) { m ->
        val amount = m.groupValues[1]
        val unit = relativeUnitId[m.groupValues[2].lowercase()] ?: m.groupValues[2]
        "$amount $unit lalu"
    }
    s = s.replace(Regex("(?i)\\bstreamed live on\\b"), "Live pada")
        .replace(Regex("(?i)\\bpremiered\\b"), "Tayang perdana")
    for ((en, id) in monthEnToId) {
        s = s.replace(Regex("\\b$en\\b", RegexOption.IGNORE_CASE), id)
    }
    return s
}
