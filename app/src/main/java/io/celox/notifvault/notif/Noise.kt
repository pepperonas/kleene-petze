package io.celox.notifvault.notif

/**
 * Not everything a messenger posts is a message. WhatsApp permanently shows a background-service
 * notification ("Überprüfe auf neue Nachrichten" / "Checking for new messages"), plus backup and
 * restore progress, voice/video calls and media controls. Captured, those pile up as junk chats
 * next to the real ones — the calls even as a separate chat of their own.
 *
 * Two independent filters, because neither is complete on its own:
 *  - **structural** ([isNonMessageNotification]) — ongoing / foreground-service notifications and
 *    the non-message categories. Language-independent; the platform sets `FLAG_FOREGROUND_SERVICE`
 *    itself for anything a foreground service posts, which is exactly what the WhatsApp service
 *    notification is, and calls carry `CATEGORY_CALL` / `CATEGORY_MISSED_CALL`.
 *  - **textual** ([isNoiseText]) — the concrete service and call phrases, as a safety net for OEM
 *    builds / app versions that post the same thing without those flags or categories.
 *
 * Both stay narrow: a false positive silently drops a real message.
 */

// Notification.CATEGORY_* values that are never a chat message. Kept as literals so this file
// stays framework-free (and unit-testable); the extractor passes `notification.category` in.
private val NOISE_CATEGORIES = setOf(
    "service",   // CATEGORY_SERVICE — background service ("Checking for new messages")
    "progress",  // CATEGORY_PROGRESS — backup / restore / download progress
    "transport", // CATEGORY_TRANSPORT — media playback controls
    "call",        // CATEGORY_CALL — incoming / ongoing call
    "missed_call", // CATEGORY_MISSED_CALL — WhatsApp's separate "Verpasster Sprachanruf" entry
    "navigation",
    "sys"        // CATEGORY_SYSTEM
)

/**
 * True when the whole notification is a status/service notification rather than a message.
 * [ongoing] = `FLAG_ONGOING_EVENT`, [foregroundService] = `FLAG_FOREGROUND_SERVICE`,
 * [hasProgress] = the notification carries `EXTRA_PROGRESS*`.
 *
 * The progress check is what catches WhatsApp's media upload ("Sending video to Alice"): a
 * progress bar is never a message, and unlike a phrase list it works in every language.
 */
fun isNonMessageNotification(
    ongoing: Boolean,
    foregroundService: Boolean,
    category: String?,
    hasProgress: Boolean = false
): Boolean =
    ongoing || foregroundService || hasProgress || category?.lowercase() in NOISE_CATEGORIES

/**
 * The service phrases themselves, lowercased. Matched with `contains` so a prefixed/suffixed
 * variant ("WhatsApp · Überprüfe auf neue Nachrichten") is still recognised. Together with
 * [CALL_NOISE_MARKERS] this is also what the one-time cleanup purges.
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

/**
 * Call notifications, lowercased. WhatsApp keeps voice/video calls in their own notification (and
 * therefore in their own vault chat): an incoming or ongoing call is caught structurally
 * (foreground service + `CATEGORY_CALL`), but a **missed** call is a plain notification whose only
 * content is this text — so it needs the phrase list as well as `CATEGORY_MISSED_CALL`.
 *
 * Matched with `contains` (counted plurals like "2 verpasste Sprachanrufe" exist), which means a
 * chat message that literally contains one of these phrases is dropped too. Accepted trade-off:
 * these are notification wordings, and the alternative is a permanently growing call chat.
 */
val CALL_NOISE_MARKERS = listOf(
    // German
    "verpasster sprachanruf", "verpasste sprachanrufe",
    "verpasster videoanruf", "verpasste videoanrufe",
    "verpasster gruppenanruf", "verpasster anruf", "verpasste anrufe",
    "eingehender sprachanruf", "eingehender videoanruf", "eingehender anruf",
    "laufender anruf", "anruf läuft",
    // Ongoing call, as WhatsApp actually words it on the device
    "aktiver sprachanruf", "aktiver videoanruf", "aktiver gruppenanruf", "aktiver anruf",
    // English
    "missed voice call", "missed video call", "missed group call", "missed call",
    "incoming voice call", "incoming video call", "incoming call",
    "ongoing voice call", "ongoing video call", "ongoing call",
    "active voice call", "active video call", "active group call", "active call",
    // Spanish
    "llamada de voz perdida", "videollamada perdida", "llamada perdida", "llamada entrante",
    // French
    "appel vocal manqué", "appel vidéo manqué", "appel manqué", "appel entrant",
    // Italian ("videochiamata persa" contains "chiamata persa")
    "chiamata vocale persa", "chiamata persa", "chiamata in arrivo",
    // Portuguese ("videochamada perdida" contains "chamada perdida")
    "chamada de voz perdida", "chamada perdida", "chamada recebida",
    // Dutch
    "gemiste spraakoproep", "gemiste video-oproep", "gemiste oproep", "inkomende oproep",
    // Turkish
    "cevapsız sesli arama", "cevapsız görüntülü arama", "cevapsız arama", "gelen arama",
    // Polish
    "nieodebrane połączenie", "połączenie przychodzące"
)

/**
 * Media transfer progress, lowercased. WhatsApp posts one notification per upload
 * ("Sending video to Alice") and updates it continuously, which produced thousands of rows in a
 * single junk chat named after the app.
 *
 * Structurally these carry `EXTRA_PROGRESS` and are caught by [isNonMessageNotification]; this
 * list exists for rows captured before that check and for builds that omit the progress extras.
 * Each marker names a medium, so an ordinary sentence cannot match by accident — the bare word
 * "sending" is handled by [WHOLE_TEXT_NOISE] instead.
 */
val MEDIA_NOISE_MARKERS = listOf(
    // English — "Sending <medium> to <contact>"
    "sending video to ", "sending photo to ", "sending image to ", "sending audio to ",
    "sending document to ", "sending file to ", "sending gif to ", "sending sticker to ",
    "sending media to ", "sending voice message to ",
    "downloading video", "downloading photo", "downloading image", "downloading media",
    "downloading file", "downloading document",
    // German
    "video wird gesendet", "foto wird gesendet", "bild wird gesendet", "datei wird gesendet",
    "audio wird gesendet", "sprachnachricht wird gesendet", "dokument wird gesendet",
    "medien werden gesendet",
    "video wird heruntergeladen", "foto wird heruntergeladen", "bild wird heruntergeladen",
    "datei wird heruntergeladen", "medien werden heruntergeladen"
)

/**
 * Texts that are only noise when they are the *entire* message. "Sending…" as a substring would
 * match ordinary sentences, but a message whose whole content is that word is a progress update.
 */
private val WHOLE_TEXT_NOISE = setOf(
    "sending", "sending…", "sending...", "sending file", "sending media",
    "wird gesendet", "wird gesendet…", "wird gesendet...",
    "senden…", "senden...", "wird heruntergeladen", "downloading", "downloading…", "downloading..."
)

/** True if [text] is a known service, call or transfer-progress notification text. */
fun isNoiseText(text: String): Boolean {
    val t = text.lowercase().trim()
    if (t in WHOLE_TEXT_NOISE) return true
    return SERVICE_NOISE_MARKERS.any { t.contains(it) } ||
        CALL_NOISE_MARKERS.any { t.contains(it) } ||
        MEDIA_NOISE_MARKERS.any { t.contains(it) }
}
