package io.celox.notifvault.ui

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The words and date patterns of the relative-time helpers, per UI language. German where the
 * device speaks German, English everywhere else — the same rule Android applies to the string
 * resources (values-de/ vs. values/), so the dates never speak a different language than the
 * rest of the screen. Plain Kotlin, so the tests can pin both languages.
 */
data class FormatWords(
    val locale: Locale,
    val today: String,
    val yesterday: String,
    val never: String,
    val justNow: String,
    val minutesAgo: (Long) -> String,
    val hoursAgo: (Long) -> String,
    val clock: String,
    val shortWeekday: String,
    val weekday: String,
    val shortDate: String,
    val fullDate: String,
    val fullDateTime: String,
) {
    companion object {
        val GERMAN = FormatWords(
            locale = Locale.GERMANY,
            today = "Heute", yesterday = "Gestern", never = "noch nie", justNow = "gerade eben",
            minutesAgo = { "vor $it min" }, hoursAgo = { "vor $it h" },
            clock = "HH:mm", shortWeekday = "EEE", weekday = "EEEE",
            shortDate = "dd.MM.yy", fullDate = "d. MMMM yyyy", fullDateTime = "dd.MM.yy HH:mm",
        )
        val ENGLISH = FormatWords(
            locale = Locale.UK,
            today = "Today", yesterday = "Yesterday", never = "never", justNow = "just now",
            minutesAgo = { "$it min ago" }, hoursAgo = { "$it h ago" },
            clock = "HH:mm", shortWeekday = "EEE", weekday = "EEEE",
            shortDate = "dd/MM/yy", fullDate = "d MMMM yyyy", fullDateTime = "dd/MM/yy HH:mm",
        )

        fun of(locale: Locale): FormatWords = if (locale.language == "de") GERMAN else ENGLISH
    }

    fun format(pattern: String, millis: Long): String = SimpleDateFormat(pattern, locale).format(Date(millis))
}

/** The words for the current UI language (read on every call — the language can change at runtime). */
private fun words(): FormatWords = FormatWords.of(Locale.getDefault())

private const val DAY_MS = 86_400_000L

private fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Stable per-day key used to group messages under date separators. */
fun dayKey(millis: Long): Long = startOfDay(millis)

private fun daysAgo(millis: Long, now: Long = System.currentTimeMillis()): Int =
    ((startOfDay(now) - startOfDay(millis)) / DAY_MS).toInt()

/** Just the clock, for inside a chat bubble. */
fun formatClock(millis: Long, w: FormatWords = words()): String = w.format(w.clock, millis)

/** Centered date-separator label between day groups in a conversation. */
fun formatDayHeader(millis: Long, w: FormatWords = words()): String = when (daysAgo(millis)) {
    0 -> w.today
    1 -> w.yesterday
    in 2..6 -> w.format(w.weekday, millis)
    else -> w.format(w.fullDate, millis)
}

/** Compact relative time for list rows (conversation overview, search results). */
fun formatListTime(millis: Long, w: FormatWords = words()): String = when (daysAgo(millis)) {
    0 -> w.format(w.clock, millis)
    1 -> w.yesterday
    in 2..6 -> w.format(w.shortWeekday, millis)
    else -> w.format(w.shortDate, millis)
}

/** Full, unambiguous date+time (used in the search-result detail line). */
fun formatTimestamp(millis: Long, w: FormatWords = words()): String = when (daysAgo(millis)) {
    0 -> "${w.today} ${w.format(w.clock, millis)}"
    1 -> "${w.yesterday} ${w.format(w.clock, millis)}"
    else -> w.format(w.fullDateTime, millis)
}

/**
 * Relative age of an event ("vor 5 min" / "5 min ago"), used for the capture heartbeat in
 * Settings; falls back to the absolute timestamp once it is more than a day old. [millis] <= 0
 * means "never". A timestamp in the future (clock change) reads as "just now" rather than a
 * negative age.
 */
fun formatRelativeSince(
    millis: Long,
    now: Long = System.currentTimeMillis(),
    w: FormatWords = words()
): String {
    if (millis <= 0) return w.never
    val mins = (now - millis) / 60_000
    return when {
        mins < 1 -> w.justNow
        mins < 60 -> w.minutesAgo(mins)
        mins < 24 * 60 -> w.hoursAgo(mins / 60)
        else -> formatTimestamp(millis, w)
    }
}

/**
 * Storage sizes in the units people read them in — MB once it is worth mentioning, with the
 * decimal separator of the UI language ("1,5 MB" in German, "1.5 MB" in English).
 */
fun formatBytes(bytes: Long, w: FormatWords = words()): String = when {
    bytes >= 1024L * 1024L -> String.format(w.locale, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

// ---- Identity colors / initials -------------------------------------------

// A small, deterministic palette that reads well on both light and dark surfaces.
private val identityColors = listOf(
    Color(0xFF4FC3F7), Color(0xFF66BB6A), Color(0xFFFFA726), Color(0xFFEF5350),
    Color(0xFFAB47BC), Color(0xFF26A69A), Color(0xFFEC407A), Color(0xFF7E57C2),
    Color(0xFF8D6E63), Color(0xFF5C6BC0), Color(0xFF29B6F6), Color(0xFFD4A017)
)

/** Stable color for a person/chat, derived from its name. */
fun identityColor(name: String): Color =
    identityColors[(name.hashCode() and 0x7fffffff) % identityColors.size]

/** 1–2 letter initials for an avatar; falls back to "?" for unnamed senders. */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase(Locale.ROOT)
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase(Locale.ROOT)
    }
}
