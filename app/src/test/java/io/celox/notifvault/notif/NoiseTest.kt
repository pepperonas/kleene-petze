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
        for (c in listOf("service", "progress", "transport", "navigation", "sys")) {
            assertTrue(c, isNonMessageNotification(false, false, c))
        }
        assertTrue(isNonMessageNotification(false, false, "SERVICE"))
    }

    // WhatsApp keeps voice/video calls in their own notification — and thus in their own vault
    // chat. Incoming/ongoing calls are CATEGORY_CALL, a missed call CATEGORY_MISSED_CALL.
    @Test
    fun `calls are not messages`() {
        assertTrue(isNonMessageNotification(false, false, "call"))
        assertTrue(isNonMessageNotification(false, false, "missed_call"))
    }

    @Test
    fun `a plain message notification passes`() {
        assertFalse(isNonMessageNotification(false, false, "msg"))
        assertFalse(isNonMessageNotification(false, false, null))
        assertFalse(isNonMessageNotification(false, false, ""))
        assertFalse(isNonMessageNotification(false, false, "email"))
    }

    // ---- isNoiseText ----

    @Test
    fun `the WhatsApp service text is recognised regardless of case or decoration`() {
        assertTrue(isNoiseText("Überprüfe auf neue Nachrichten"))
        assertTrue(isNoiseText("ÜBERPRÜFE AUF NEUE NACHRICHTEN"))
        assertTrue(isNoiseText("WhatsApp · Überprüfe auf neue Nachrichten"))
        assertTrue(isNoiseText("Checking for new messages"))
        assertTrue(isNoiseText("Zoeken naar nieuwe berichten"))
    }

    @Test
    fun `backup and restore progress is noise`() {
        assertTrue(isNoiseText("Backup in progress"))
        assertTrue(isNoiseText("Sicherung läuft"))
        assertTrue(isNoiseText("Restoring media"))
    }

    // The missed-call notification's whole content is this text, so the phrase list has to catch
    // it for app versions that don't set CATEGORY_MISSED_CALL.
    @Test
    fun `missed and incoming calls are noise`() {
        assertTrue(isNoiseText("Verpasster Sprachanruf"))
        assertTrue(isNoiseText("Verpasster Videoanruf"))
        assertTrue(isNoiseText("Verpasster Anruf"))
        assertTrue(isNoiseText("2 verpasste Sprachanrufe"))
        assertTrue(isNoiseText("Missed voice call"))
        assertTrue(isNoiseText("Missed group call"))
        assertTrue(isNoiseText("Incoming video call"))
        assertTrue(isNoiseText("Ongoing call"))
        assertTrue(isNoiseText("Llamada de voz perdida"))
        assertTrue(isNoiseText("Appel vocal manqué"))
        assertTrue(isNoiseText("Videochiamata persa"))
        assertTrue(isNoiseText("Videochamada perdida"))
        assertTrue(isNoiseText("Gemiste spraakoproep"))
        assertTrue(isNoiseText("Cevapsız sesli arama"))
        assertTrue(isNoiseText("Nieodebrane połączenie"))
    }

    // A missed call puts its wording in the notification *title* and the contact name in the
    // text, so the same check has to be applied to the title — otherwise the row lands in the
    // vault as a chat named after the call.
    @Test
    fun `call wording is recognised when it arrives as the notification title`() {
        val title = "Verpasster Sprachanruf"
        val text = "Max Mustermann"
        assertTrue(isNoiseText(title))
        assertFalse(isNoiseText(text))
    }

    // A progress bar is never a message. This is the language-independent catch for WhatsApp's
    // media upload, which had filled a junk chat with thousands of "Sending video to …" rows.
    @Test
    fun `a notification with a progress bar is not a message`() {
        assertTrue(isNonMessageNotification(false, false, null, hasProgress = true))
        assertFalse(isNonMessageNotification(false, false, null, hasProgress = false))
    }

    @Test
    fun `media transfer progress is noise`() {
        assertTrue(isNoiseText("Sending video to Michaela Pampel"))
        assertTrue(isNoiseText("Sending photo to Alice"))
        assertTrue(isNoiseText("Sending voice message to Bob"))
        assertTrue(isNoiseText("Video wird gesendet"))
        assertTrue(isNoiseText("Datei wird heruntergeladen"))
        assertTrue(isNoiseText("Downloading media"))
    }

    // "Sending…" on its own is a progress update; as a substring it would match real sentences.
    @Test
    fun `a bare sending placeholder counts only as the whole text`() {
        assertTrue(isNoiseText("Sending..."))
        assertTrue(isNoiseText("Sending…"))
        assertTrue(isNoiseText("  wird gesendet  "))
        assertFalse(isNoiseText("Sending you the link tomorrow"))
        assertFalse(isNoiseText("Das Paket wird gesendet, sobald es da ist"))
    }

    // Found on the device: WhatsApp words an ongoing call this way.
    @Test
    fun `an active call is noise`() {
        assertTrue(isNoiseText("Aktiver Sprachanruf"))
        assertTrue(isNoiseText("Aktiver Videoanruf"))
        assertTrue(isNoiseText("Active call"))
    }

    @Test
    fun `real messages are never dropped`() {
        for (t in listOf(
            "Hast du meine Nachrichten gesehen?",
            "Ich prüfe das morgen",
            "Neue Nachrichten sind angekommen",
            "Check this out",
            "Sicherung der Daten ist wichtig",
            "Ruf mich mal an",
            "Können wir telefonieren?",
            "Der Anruf war nett, danke!",
            "Let's call it a day",
            "🚫"
        )) {
            assertFalse(t, isNoiseText(t))
        }
    }

    @Test
    fun `markers are lowercase so the contains match works`() {
        for (m in SERVICE_NOISE_MARKERS + CALL_NOISE_MARKERS + MEDIA_NOISE_MARKERS) {
            assertTrue(m, m == m.lowercase())
            assertTrue(m, m.isNotBlank())
        }
    }
}
