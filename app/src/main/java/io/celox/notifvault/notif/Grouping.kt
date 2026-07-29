package io.celox.notifvault.notif

/**
 * Pure grouping/naming rules used by [MessageExtractor], extracted as a framework-free seam so
 * they can be unit-tested without a `StatusBarNotification`. Getting these wrong is what once
 * mixed distinct chats and split groups per sender, so the behavior is pinned by `GroupingTest`.
 */

/**
 * The stable per-chat identifier a messenger attaches to its (re-used) notification:
 * conversation shortcut id first, then the notification tag (WhatsApp: the chat's JID).
 * `null` when neither is usable — callers then fall back to the display title.
 */
fun stableKeyOf(shortcutId: String?, tag: String?): String? =
    shortcutId?.takeIf { it.isNotBlank() } ?: tag?.takeIf { it.isNotBlank() }

/** Sender of a MessagingStyle message: the person, else the notification's own user, else "Unbekannt". */
fun senderNameOf(personName: String?, userName: String?): String =
    (personName ?: userName ?: "Unbekannt").ifBlank { "Unbekannt" }

/**
 * Display title of a chat: the group's conversation title when present, else the fallback
 * (the sender in 1:1 chats), else the app label. Display-only — never used for grouping.
 */
fun displayTitleOf(conversationTitle: String?, fallback: String, appLabel: String): String =
    (conversationTitle?.trim()?.takeIf { it.isNotEmpty() } ?: fallback).ifBlank { appLabel }

/**
 * Grouping key for a chat: the stable id when the messenger provided one, else the display
 * title (legacy behavior), else the app label. Must never be blank — it is part of the
 * de-dup hash and of every DAO query.
 */
fun conversationKeyOf(stableKey: String?, displayTitle: String, appLabel: String): String =
    (stableKey ?: displayTitle).ifBlank { appLabel }

/**
 * The notification "slot" a messenger re-uses for one chat (package + notification id).
 *
 * This is a last-resort chat identity, but a sound one: the whole app rests on messengers
 * *updating* one notification per chat in place (that is how the bundled back-history and the
 * deletion re-post arrive at all), so the slot is exactly as stable as that mechanism.
 */
fun slotKeyOf(packageName: String, notificationId: Int): String = "slot:$packageName:$notificationId"

/** Grouping key + display title of the chat a conversation-style notification belongs to. */
data class ChatIdentity(val key: String, val title: String)

/**
 * Resolves which chat a MessagingStyle notification belongs to.
 *
 * The order matters: every post of a chat must resolve to the *same* key, no matter what the
 * individual message inside it looks like — otherwise a chat silently splits, or worse, merges
 * into another one.
 *
 *  - `shortcutId` / `tag` are the messenger's own per-chat ids and win whenever present.
 *  - For a **group** there is no safe title fallback. Falling back to the sender (what
 *    [displayTitleOf] yields when the group name is missing) splits the group per sender *and*
 *    merges those parts into the 1:1 chats with the same people — so the group never shows up
 *    as its own chat. WhatsApp omits `conversationTitle` on some posts/versions, which is why
 *    only *some* groups were affected. Falling back to the group *name* is no good either: it
 *    is present in one post and missing in the next, so the chat would split. The notification
 *    slot is the one value that is always there and always the same → use it.
 *  - For a 1:1 chat the sender *is* the chat, so the legacy title fallback stays (it also keeps
 *    existing vaults grouped the way they already are).
 *
 * The title is display-only: for a group it is the group name (never a sender), falling back to
 * the notification's own title and finally the app label.
 */
fun chatIdentityOf(
    shortcutId: String?,
    tag: String?,
    slotKey: String,
    isGroup: Boolean,
    conversationTitle: String?,
    notificationTitle: String?,
    sender: String,
    appLabel: String
): ChatIdentity {
    val groupName = conversationTitle?.trim()?.takeIf { it.isNotEmpty() }
        ?: notificationTitle?.trim()?.takeIf { it.isNotEmpty() }
    val title = if (isGroup) displayTitleOf(groupName, appLabel, appLabel)
    else displayTitleOf(conversationTitle, sender, appLabel)
    val stable = stableKeyOf(shortcutId, tag)
        ?: slotKey.takeIf { isGroup && it.isNotBlank() }
    return ChatIdentity(conversationKeyOf(stable, title, appLabel), title)
}
