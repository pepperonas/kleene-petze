package io.celox.notifvault.notif

/**
 * Not everything a messenger posts is a message. WhatsApp permanently shows a background-service
 * notification ("Überprüfe auf neue Nachrichten" / "Checking for new messages"), plus backup and
 * restore progress, calls and media controls. Captured, those pile up as a junk chat named after
 * the app and drown the real ones.
 *
 * Two independent filters, because neither is complete on its own:
 *  - **structural** ([isNonMessageNotification]) — ongoing / foreground-service notifications and
 *    the non-message categories. Language-independent; the platform sets `FLAG_FOREGROUND_SERVICE`
 *    itself for anything a foreground service posts, which is exactly what the WhatsApp service
 *    notification is.
 *  - **textual** ([isServiceNoiseText]) — the concrete service phrases, as a safety net for OEM
 *    builds / app versions that post the same thing without those flags or categories.
 *
 * Both are deliberately conservative: a false positive silently drops a real message.
 */

// Notification.CATEGORY_* values that are never a chat message. Kept as literals so this file
// stays framework-free (and unit-testable); the extractor passes `notification.category` in.
private val NOISE_CATEGORIES = setOf(
    "service",   // CATEGORY_SERVICE — background service ("Checking for new messages")
    "progress",  // CATEGORY_PROGRESS — backup / restore / download progress
    "transport", // CATEGORY_TRANSPORT — media playback controls
    "call",      // CATEGORY_CALL — incoming / ongoing call (missed_call is left alone)
    "navigation",
    "sys"        // CATEGORY_SYSTEM
)

/**
 * True when the whole notification is a status/service notification rather than a message.
 * [ongoing] = `FLAG_ONGOING_EVENT`, [foregroundService] = `FLAG_FOREGROUND_SERVICE`.
 */
fun isNonMessageNotification(ongoing: Boolean, foregroundService: Boolean, category: String?): Boolean =
    ongoing || foregroundService || category?.lowercase() in NOISE_CATEGORIES

/**
 * The service phrases themselves, lowercased. Matched with `contains` so a prefixed/suffixed
 * variant ("WhatsApp · Überprüfe auf neue Nachrichten") is still recognised. Also used by the
 * one-time cleanup of rows captured before this filter existed.
 */
val SERVICE_NOISE_MARKERS = listOf(
    // "checking for new messages" — WhatsApp's permanent background-service notification
    "überprüfe auf neue nachrichten",
    "prüfe auf neue nachrichten",
    "suche nach neuen nachrichten",
    "nach neuen nachrichten suchen",
    "checking for new messages",
    "looking for new messages",
    "comprobando si hay mensajes nuevos",
    "buscando mensajes nuevos",
    "recherche de nouveaux messages",
    "controllo la presenza di nuovi messaggi",
    "verificando se há novas mensagens",
    "zoeken naar nieuwe berichten",
    "yeni mesajlar kontrol ediliyor",
    "sprawdzanie nowych wiadomości",
    // backup / restore progress
    "backup in progress",
    "backing up messages",
    "sicherung läuft",
    "backup läuft",
    "wiederherstellung läuft",
    "restoring messages",
    "restoring media"
)

/** True if [text] is one of the known service/status texts (any known language). */
fun isServiceNoiseText(text: String): Boolean {
    val t = text.lowercase()
    return SERVICE_NOISE_MARKERS.any { t.contains(it) }
}
