package io.celox.notifvault.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FormatTest {

    private fun at(year: Int, month0: Int, day: Int, hour: Int, min: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month0, day, hour, min, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    // ---- initials ----

    @Test
    fun `single name yields one initial`() {
        assertEquals("A", initials("Alice"))
    }

    @Test
    fun `full name yields first and last initials`() {
        assertEquals("AM", initials("Alice Müller"))
        assertEquals("JD", initials("John Michael Doe")) // first + last, middle ignored
    }

    @Test
    fun `surrounding and inner whitespace is collapsed`() {
        assertEquals("JD", initials("   john    doe  "))
    }

    @Test
    fun `initials are uppercased with German locale`() {
        assertEquals("Ä", initials("ärzte"))
    }

    @Test
    fun `blank or empty falls back to question mark`() {
        assertEquals("?", initials(""))
        assertEquals("?", initials("   "))
    }

    // ---- dayKey ----

    @Test
    fun `same calendar day maps to the same key`() {
        assertEquals(
            dayKey(at(2024, Calendar.JUNE, 15, 9, 0)),
            dayKey(at(2024, Calendar.JUNE, 15, 9, 0))
        )
        assertEquals(
            dayKey(at(2024, Calendar.JUNE, 15, 0, 1)),
            dayKey(at(2024, Calendar.JUNE, 15, 23, 59))
        )
    }

    @Test
    fun `different days map to different keys`() {
        assertNotEquals(
            dayKey(at(2024, Calendar.JUNE, 15, 23, 59)),
            dayKey(at(2024, Calendar.JUNE, 16, 0, 1))
        )
    }

    @Test
    fun `dayKey is the start of that day`() {
        val noon = at(2024, Calendar.JUNE, 15, 12, 0)
        val key = dayKey(noon)
        assertEquals(at(2024, Calendar.JUNE, 15, 0, 0), key)
        assertTrue(key <= noon)
        assertTrue(noon - key < 24L * 60 * 60 * 1000)
    }

    // ---- formatClock ----

    @Test
    fun `clock is formatted as HH colon mm`() {
        assertTrue(formatClock(at(2024, Calendar.JUNE, 15, 9, 5)).matches(Regex("\\d{2}:\\d{2}")))
    }

    // ---- formatRelativeSince (capture heartbeat) ----

    @Test
    fun `a missing heartbeat reads as never`() {
        assertEquals("noch nie", formatRelativeSince(0, now = 1_000_000, w = FormatWords.GERMAN))
        assertEquals("noch nie", formatRelativeSince(-1, now = 1_000_000, w = FormatWords.GERMAN))
    }

    @Test
    fun `relative age steps from minutes to hours`() {
        val now = 1_800_000_000_000L
        assertEquals("gerade eben", formatRelativeSince(now, now, FormatWords.GERMAN))
        assertEquals("gerade eben", formatRelativeSince(now - 59_000, now, FormatWords.GERMAN))
        assertEquals("vor 1 min", formatRelativeSince(now - 60_000, now, FormatWords.GERMAN))
        assertEquals("vor 59 min", formatRelativeSince(now - 59 * 60_000, now, FormatWords.GERMAN))
        assertEquals("vor 1 h", formatRelativeSince(now - 60 * 60_000, now, FormatWords.GERMAN))
        assertEquals("vor 23 h", formatRelativeSince(now - 23 * 60 * 60_000L, now, FormatWords.GERMAN))
    }

    @Test
    fun `beyond a day it falls back to an absolute timestamp`() {
        val now = 1_800_000_000_000L
        val old = now - 3 * 24 * 60 * 60_000L
        assertEquals(formatTimestamp(old, FormatWords.GERMAN), formatRelativeSince(old, now, FormatWords.GERMAN))
    }

    @Test
    fun `a heartbeat in the future does not render a negative age`() {
        val now = 1_800_000_000_000L
        assertEquals("gerade eben", formatRelativeSince(now + 60_000, now, FormatWords.GERMAN))
    }

    // ---- UI language ----

    @Test
    fun `German devices get German words, every other language English`() {
        assertEquals(FormatWords.GERMAN, FormatWords.of(java.util.Locale.GERMANY))
        assertEquals(FormatWords.GERMAN, FormatWords.of(java.util.Locale("de", "AT")))
        assertEquals(FormatWords.ENGLISH, FormatWords.of(java.util.Locale.US))
        // A French phone falls back to the English resources — so must the dates.
        assertEquals(FormatWords.ENGLISH, FormatWords.of(java.util.Locale.FRANCE))
    }

    @Test
    fun `relative age in English`() {
        val now = 1_800_000_000_000L
        val en = FormatWords.ENGLISH
        assertEquals("never", formatRelativeSince(0, now, en))
        assertEquals("just now", formatRelativeSince(now - 59_000, now, en))
        assertEquals("5 min ago", formatRelativeSince(now - 5 * 60_000, now, en))
        assertEquals("2 h ago", formatRelativeSince(now - 2 * 60 * 60_000L, now, en))
    }

    @Test
    fun `day header speaks the chosen language`() {
        val now = System.currentTimeMillis()
        assertEquals("Today", formatDayHeader(now, FormatWords.ENGLISH))
        assertEquals("Heute", formatDayHeader(now, FormatWords.GERMAN))
        assertEquals("Yesterday", formatListTime(now - 24 * 60 * 60_000L, FormatWords.ENGLISH))
    }

    @Test
    fun `weekday and month names follow the language`() {
        val june15 = at(2024, Calendar.JUNE, 15, 9, 5)
        assertEquals("15 June 2024", FormatWords.ENGLISH.format(FormatWords.ENGLISH.fullDate, june15))
        assertEquals("15. Juni 2024", FormatWords.GERMAN.format(FormatWords.GERMAN.fullDate, june15))
    }

    // ---- formatBytes ----

    @Test
    fun `sizes switch units and use the language's decimal separator`() {
        assertEquals("512 B", formatBytes(512, FormatWords.ENGLISH))
        assertEquals("3 KB", formatBytes(3 * 1024 + 900, FormatWords.ENGLISH))
        assertEquals("1.5 MB", formatBytes(1_572_864, FormatWords.ENGLISH))
        assertEquals("1,5 MB", formatBytes(1_572_864, FormatWords.GERMAN))
    }

    // ---- identityColor ----

    @Test
    fun `identity color is deterministic per name`() {
        assertEquals(identityColor("Alice Müller"), identityColor("Alice Müller"))
        assertEquals(identityColor("WhatsApp Gruppe"), identityColor("WhatsApp Gruppe"))
    }

    @Test
    fun `different names spread across more than one palette color`() {
        val names = listOf("Alice", "Bob", "Carol", "Dave", "Erin", "Frank", "Grace", "Heidi")
        val distinct = names.map { identityColor(it) }.toSet()
        assertTrue("expected variety, got $distinct", distinct.size > 1)
    }
}
