package io.celox.notifvault.notif

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The noise filter decides what never enters the vault. Both directions matter: WhatsApp's
 * permanent service notification must be dropped, and a real message must never be.
 */
class NoiseTest {

    // ---- isNonMessageNotification ----

    @Test
    fun `foreground service and ongoing notifications are not messages`() {
        // WhatsApp's "Überprüfe auf neue Nachrichten" — the platform sets FLAG_FOREGROUND_SERVICE.
        assertTrue(isNonMessageNotification(ongoing = false, foregroundService = true, category = null))
        assertTrue(isNonMessageNotification(ongoing = true, foregroundService = false, category = null))
    }

    @Test
    fun `service-ish categories are not messages`() {
        for (c in listOf("service", "progress", "transport", "call", "navigation", "sys")) {
            assertTrue(c, isNonMessageNotification(false, false, c))
        }
        assertTrue(isNonMessageNotification(false, false, "SERVICE"))
    }

    @Test
    fun `a plain message notification passes`() {
        assertTrue(!isNonMessageNotification(false, false, "msg"))
        assertTrue(!isNonMessageNotification(false, false, null))
        assertTrue(!isNonMessageNotification(false, false, ""))
        // A missed call still carries content worth keeping.
        assertTrue(!isNonMessageNotification(false, false, "missed_call"))
    }

    // ---- isServiceNoiseText ----

    @Test
    fun `the WhatsApp service text is recognised regardless of case or decoration`() {
        assertTrue(isServiceNoiseText("Überprüfe auf neue Nachrichten"))
        assertTrue(isServiceNoiseText("ÜBERPRÜFE AUF NEUE NACHRICHTEN"))
        assertTrue(isServiceNoiseText("WhatsApp · Überprüfe auf neue Nachrichten"))
        assertTrue(isServiceNoiseText("Checking for new messages"))
        assertTrue(isServiceNoiseText("Zoeken naar nieuwe berichten"))
    }

    @Test
    fun `backup and restore progress is noise`() {
        assertTrue(isServiceNoiseText("Backup in progress"))
        assertTrue(isServiceNoiseText("Sicherung läuft"))
        assertTrue(isServiceNoiseText("Restoring media"))
    }

    @Test
    fun `real messages are never dropped`() {
        for (t in listOf(
            "Hast du meine Nachrichten gesehen?",
            "Ich prüfe das morgen",
            "Neue Nachrichten sind angekommen",
            "Check this out",
            "Sicherung der Daten ist wichtig",
            "🚫"
        )) {
            assertFalse(t, isServiceNoiseText(t))
        }
    }

    @Test
    fun `markers are lowercase so the contains match works`() {
        for (m in SERVICE_NOISE_MARKERS) {
            assertTrue(m, m == m.lowercase())
            assertTrue(m, m.isNotBlank())
        }
    }
}
