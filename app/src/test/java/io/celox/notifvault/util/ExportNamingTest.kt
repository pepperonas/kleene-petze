package io.celox.notifvault.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat titles are attacker-controlled in the sense that they come from whoever writes to you —
 * they must never be able to steer a file path.
 */
class ExportNamingTest {

    @Test
    fun `a normal chat title becomes a readable file base`() {
        assertEquals("kleene-petze_Alice", ExportNaming.chatExportBase("Alice"))
        assertEquals("kleene-petze_Alice_Müller", ExportNaming.chatExportBase("Alice Müller"))
    }

    @Test
    fun `german umlauts and eszett survive`() {
        assertEquals("kleene-petze_Grüße_Jörg_Straße", ExportNaming.chatExportBase("Grüße Jörg Straße"))
    }

    @Test
    fun `path separators and traversal are stripped`() {
        for (title in listOf("../../etc/passwd", "/etc/passwd", "..", "../..", "a/b\\c")) {
            val name = ExportNaming.chatExportBase(title)
            assertFalse("'$name' still contains a path separator", name.contains('/') || name.contains('\\'))
            assertFalse("'$name' still contains a traversal", name.contains(".."))
        }
        assertEquals("kleene-petze_etc_passwd", ExportNaming.chatExportBase("../../etc/passwd"))
    }

    @Test
    fun `titles that sanitize to nothing use the fallback`() {
        assertEquals("kleene-petze_chat", ExportNaming.chatExportBase(""))
        assertEquals("kleene-petze_chat", ExportNaming.chatExportBase("   "))
        assertEquals("kleene-petze_chat", ExportNaming.chatExportBase("👻🎉"))
        assertEquals("kleene-petze_chat", ExportNaming.chatExportBase("..."))
    }

    @Test
    fun `long titles are truncated without a trailing separator`() {
        val name = ExportNaming.chatExportBase("A".repeat(200))
        assertEquals("kleene-petze_" + "A".repeat(40), name)
        assertFalse(name.endsWith("_"))
        // A title whose 41st char is the only thing after a space must not leave "_" dangling.
        assertFalse(ExportNaming.chatExportBase("B".repeat(40) + " tail").endsWith("_"))
    }

    @Test
    fun `runs of separators collapse and edges are trimmed`() {
        assertEquals("kleene-petze_Alice_Bob", ExportNaming.chatExportBase("  ***Alice   &&&  Bob!!!  "))
    }

    @Test
    fun `backup file names carry the vault extension`() {
        val name = ExportNaming.backupFileName("2026-07-26")
        assertEquals("kleene-petze-2026-07-26.kpvault", name)
        assertTrue(name.endsWith("." + VaultBackup.FILE_EXTENSION))
    }

    @Test
    fun `a broken date stamp cannot inject a path`() {
        assertEquals("kleene-petze-2026-07-26.kpvault", ExportNaming.backupFileName("/2026/07/26"))
        assertFalse(ExportNaming.backupFileName("../x").contains(".."))
    }
}
