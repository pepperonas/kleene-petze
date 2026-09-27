package io.celox.notifvault.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `stored names come back as the same mode`() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromName(it.name)) }
    }

    @Test
    fun `nothing stored or an unknown name follows the system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(""))
        // e.g. a value written by a future version, or a renamed entry
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("AMOLED"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("dark")) // names are case-sensitive
    }
}
