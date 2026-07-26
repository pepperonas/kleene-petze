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
