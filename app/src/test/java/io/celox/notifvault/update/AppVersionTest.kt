package io.celox.notifvault.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parts compare as numbers, not as text`() {
        assertTrue(AppVersion.isNewer("v1.10.0", "1.9.1"))
        assertFalse(AppVersion.isNewer("1.9.1", "1.10.0"))
    }

    @Test
    fun `equal versions are not newer, missing parts count as zero`() {
        assertFalse(AppVersion.isNewer("v1.9.1", "1.9.1"))
        assertFalse(AppVersion.isNewer("1.9", "1.9.0"))
        assertTrue(AppVersion.isNewer("1.9.0.1", "1.9"))
    }

    @Test
    fun `prefix and suffixes are ignored`() {
        assertEquals(listOf(1, 10, 0), AppVersion.parse(" V1.10.0-rc1+42 "))
        assertEquals("1.10.0", AppVersion.display("v1.10.0"))
    }

    @Test
    fun `garbage is never newer`() {
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse(null))
        assertFalse(AppVersion.isNewer("<html>", "1.0.0"))
        assertFalse(AppVersion.isNewer("2.0.0", "unknown"))
    }
}
