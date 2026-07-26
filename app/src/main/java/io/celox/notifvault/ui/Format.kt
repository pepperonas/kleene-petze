package io.celox.notifvault.ui

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clockFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
private val shortWeekdayFmt = SimpleDateFormat("EEE", Locale.GERMANY)   // "Mi."
private val weekdayFmt = SimpleDateFormat("EEEE", Locale.GERMANY)       // "Mittwoch"
private val shortDateFmt = SimpleDateFormat("dd.MM.yy", Locale.GERMANY)
private val fullDateFmt = SimpleDateFormat("d. MMMM yyyy", Locale.GERMANY)
private val fullDateTimeFmt = SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMANY)

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
fun formatClock(millis: Long): String = clockFmt.format(Date(millis))

/** Centered date-separator label between day groups in a conversation. */
fun formatDayHeader(millis: Long): String = when (daysAgo(millis)) {
    0 -> "Heute"
    1 -> "Gestern"
    in 2..6 -> weekdayFmt.format(Date(millis))
    else -> fullDateFmt.format(Date(millis))
}

/** Compact relative time for list rows (conversation overview, search results). */
fun formatListTime(millis: Long): String = when (daysAgo(millis)) {
    0 -> clockFmt.format(Date(millis))
    1 -> "Gestern"
    in 2..6 -> shortWeekdayFmt.format(Date(millis))
    else -> shortDateFmt.format(Date(millis))
}

/** Full, unambiguous date+time (used in the search-result detail line). */
fun formatTimestamp(millis: Long): String = when (daysAgo(millis)) {
    0 -> "Heute ${clockFmt.format(Date(millis))}"
    1 -> "Gestern ${clockFmt.format(Date(millis))}"
    else -> fullDateTimeFmt.format(Date(millis))
}

/**
 * Relative age of an event ("vor 5 min"), used for the capture heartbeat in Settings; falls
 * back to the absolute timestamp once it is more than a day old. [millis] <= 0 means "never".
 * A timestamp in the future (clock change) reads as "gerade eben" rather than a negative age.
 */
fun formatRelativeSince(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return "noch nie"
    val mins = (now - millis) / 60_000
    return when {
        mins < 1 -> "gerade eben"
        mins < 60 -> "vor $mins min"
        mins < 24 * 60 -> "vor ${mins / 60} h"
        else -> formatTimestamp(millis)
    }
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
        parts.size == 1 -> parts[0].take(1).uppercase(Locale.GERMANY)
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase(Locale.GERMANY)
    }
}
