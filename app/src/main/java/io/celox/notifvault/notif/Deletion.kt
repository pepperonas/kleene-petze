package io.celox.notifvault.notif

// The exact placeholder texts a messenger puts into the *updated* notification when a
// still-unread message is deleted. We match on `contains` (lowercased) so an emoji/prefix
// like "🚫 This message was deleted" is still recognised; the phrases are distinctive
// enough not to collide with normal conversation.
private val DELETION_MARKERS = listOf(
    // English
    "this message was deleted",
    "you deleted this message",
    // German
    "diese nachricht wurde gelöscht",
    "du hast diese nachricht gelöscht",
    // Spanish
    "se eliminó este mensaje",
    "eliminaste este mensaje",
    // French
    "ce message a été supprimé",
    "vous avez supprimé ce message",
    // Italian
    "questo messaggio è stato eliminato",
    "hai eliminato questo messaggio",
    // Portuguese (BR + PT)
    "essa mensagem foi apagada",
    "esta mensagem foi eliminada",
    // Dutch
    "dit bericht is verwijderd",
    // Turkish
    "bu mesaj silindi",
    // Polish
    "ta wiadomość została usunięta",
    // Russian
    "данное сообщение удалено"
)

/** True if [text] is a "this message was deleted" placeholder (any known language). */
fun isDeletionPlaceholder(text: String): Boolean {
    val t = text.lowercase()
    return DELETION_MARKERS.any { t.contains(it) }
}
