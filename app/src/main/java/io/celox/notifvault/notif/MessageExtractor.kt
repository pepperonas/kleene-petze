package io.celox.notifvault.notif

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import io.celox.notifvault.data.CapturedMessage

/** Identifies a previously-stored original message that has since been deleted. */
data class DeletionMark(val conversationKey: String, val sender: String, val messageTime: Long)

/**
 * Stand-in for a picture that arrived without a caption. A message needs *some* text to have an
 * identity — the content hash is built from it — and an empty one would be dropped.
 */
const val IMAGE_PLACEHOLDER = "📷 Bild"

/**
 * An image this notification carries, together with the message it belongs to. Decoding needs a
 * Context, so the extractor only points at the source and the capture service resolves it —
 * immediately, because the Uri grant dies with the notification.
 */
data class PendingImage(val message: CapturedMessage, val source: ImageSource)

/** Normal messages to store + deletions to apply to already-stored originals + images. */
data class ExtractResult(
    val messages: List<CapturedMessage>,
    val deletions: List<DeletionMark>,
    val images: List<PendingImage> = emptyList()
) {
    companion object { val EMPTY = ExtractResult(emptyList(), emptyList(), emptyList()) }
}

/**
 * Converts a posted notification into messages to store and/or deletions to apply.
 *
 * Strategy:
 *  1. Skip the group-summary notification ("5 new messages from 3 chats") to avoid noise.
 *  2. Skip status/service notifications — WhatsApp's permanent "Überprüfe auf neue Nachrichten",
 *     backup progress, calls, media controls (see [isNonMessageNotification]).
 *  3. Prefer MessagingStyle: WhatsApp/Signal/etc. embed each individual message with its
 *     real sender + timestamp. This is far more accurate than reading EXTRA_TITLE/TEXT and
 *     also recovers the short back-history bundled into each notification.
 *  4. Fall back to title/text (and inbox-style text lines) for apps without MessagingStyle.
 *
 * The de-dup key is a content hash, so the same message arriving inside many subsequent
 * notifications collapses to one stored row.
 *
 * **Deletion handling:** when a still-unread message is deleted, WhatsApp re-posts the
 * notification with that message's text replaced by a "deleted" placeholder — but the
 * sender and original timestamp are preserved. We don't store the placeholder; instead we
 * emit a [DeletionMark] so the already-stored original (same key + sender + time) can be
 * flagged. Messages deleted *after* being read produce no notification at all and are
 * therefore undetectable — a hard platform limit.
 */
class MessageExtractor(private val pm: PackageManager) {

    fun extract(sbn: StatusBarNotification): ExtractResult {
        val n = sbn.notification ?: return ExtractResult.EMPTY
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return ExtractResult.EMPTY
        // Status/service notification, not a message (WhatsApp's "Überprüfe auf neue
        // Nachrichten" runs as a foreground service, so the platform flags it for us).
        if (isNonMessageNotification(
                ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
                foregroundService = n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
                category = runCatching { n.category }.getOrNull(),
                // A progress bar means a transfer, not a message — this is what WhatsApp's
                // "Sending video to …" upload notification carries.
                hasProgress = n.extras?.let {
                    it.containsKey(Notification.EXTRA_PROGRESS_MAX) ||
                        it.containsKey(Notification.EXTRA_PROGRESS) ||
                        it.containsKey(Notification.EXTRA_PROGRESS_INDETERMINATE)
                } == true
            )
        ) return ExtractResult.EMPTY

        // A call or status notification usually carries its wording in the *title*
        // ("Verpasster Sprachanruf") and the contact in the text — checking only the message
        // text let those through, and because the title becomes the chat name they showed up
        // as a chat of their own, named after the call.
        val notifTitle = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (notifTitle != null && isNoiseText(notifTitle)) return ExtractResult.EMPTY

        val pkg = sbn.packageName
        val appLabel = labelFor(pkg)
        val now = System.currentTimeMillis()
        // Per-chat identity, independent of the (often-missing) display title. WhatsApp & co.
        // post one re-used notification per chat: its conversation shortcut id — or, failing
        // that, its tag (the chat's JID) — is constant for that chat; the notification slot is
        // the fallback for groups (see chatIdentityOf).
        val shortcutId = runCatching { n.shortcutId }.getOrNull()
        val slotKey = slotKeyOf(pkg, sbn.id)
        val stableKey = stableKeyOf(shortcutId, sbn.tag)

        val messages = mutableListOf<CapturedMessage>()
        val deletions = mutableListOf<DeletionMark>()
        val images = mutableListOf<PendingImage>()

        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()

        if (style != null && style.messages.isNotEmpty()) {
            val convTitle = style.conversationTitle?.toString()
            val isGroup = style.isGroupConversation
            for (m in style.messages) {
                val rawText = m.text?.toString()?.trim().orEmpty()
                // A picture message: WhatsApp attaches the shade preview to the message itself.
                val imageUri = runCatching { m.dataUri }.getOrNull()
                    ?.takeIf { ImagePolicy.isSupported(runCatching { m.dataMimeType }.getOrNull()) }
                // An image with a caption carries the caption as its text — that is the part
                // that must survive, and it is stored like any other message. Without a caption
                // the text is empty, and dropping it here (as before) would throw the picture
                // away with it, so it gets a placeholder to hang on.
                val text = if (rawText.isEmpty() && imageUri != null) IMAGE_PLACEHOLDER else rawText
                if (text.isEmpty() || isNoiseText(text)) continue
                val sender = senderNameOf(m.person?.name?.toString(), style.user.name?.toString())
                // Which chat is this? Groups must never be keyed/titled by the sender — that
                // split them per sender and merged them into the 1:1 chats with those people.
                val chat = chatIdentityOf(
                    shortcutId = shortcutId,
                    tag = sbn.tag,
                    slotKey = slotKey,
                    isGroup = isGroup,
                    conversationTitle = convTitle,
                    notificationTitle = notifTitle,
                    sender = sender,
                    appLabel = appLabel
                )
                val time = if (m.timestamp > 0) m.timestamp else sbn.postTime

                if (isDeletionPlaceholder(text)) {
                    deletions += DeletionMark(chat.key, sender, time)
                } else {
                    val stored = message(
                        pkg, appLabel, chat.key, chat.title, sender, isGroup, text, time, now
                    )
                    messages += stored
                    if (imageUri != null) images += PendingImage(stored, ImageSource.FromUri(imageUri))
                }
            }
        } else {
            val extras = n.extras
            val titleRaw = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            val title = displayTitleOf(titleRaw, appLabel, appLabel)
            val key = conversationKeyOf(stableKey, title, appLabel)

            val candidates = when {
                !lines.isNullOrEmpty() -> lines.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                !text.isNullOrEmpty() -> listOf(text)
                else -> emptyList()
            }
            for (t in candidates) {
                // Deletion detection is effectively MessagingStyle-only: there the placeholder
                // keeps the original message's timestamp, so the stored row can be matched.
                // Here we only have the *new* post's time, which never matches a stored
                // original — so just skip storing the placeholder text.
                if (isDeletionPlaceholder(t) || isNoiseText(t)) continue
                messages += message(pkg, appLabel, key, title, title, false, t, sbn.postTime, now)
            }
        }

        // BigPictureStyle keeps its image on the notification rather than on a message, so it can
        // only belong to the newest one. Used as a fallback: when the MessagingStyle path already
        // found per-message images, those are the more precise answer.
        if (images.isEmpty() && messages.isNotEmpty()) {
            NotificationImages.bigPicture(n.extras)?.let { images += PendingImage(messages.last(), it) }
        }

        return ExtractResult(messages, deletions, images)
    }

    private fun message(
        pkg: String, appLabel: String, conversationKey: String, conversation: String, sender: String,
        isGroup: Boolean, text: String, time: Long, capturedAt: Long
    ): CapturedMessage = CapturedMessage(
        // De-dup on the stable key (+ sender/text/time) so re-deliveries collapse even when the
        // notification's displayed title differs between posts.
        id = messageContentId(pkg, conversationKey, sender, text, time),
        packageName = pkg,
        appLabel = appLabel,
        conversationKey = conversationKey,
        conversation = conversation,
        sender = sender,
        isGroup = isGroup,
        text = text,
        messageTime = time,
        capturedAt = capturedAt,
        deletionSuspected = false // set later via DAO.markDeleted when a placeholder arrives
    )

    // One PackageManager round-trip per package, not per notification. The extractor is only
    // used from the service's single-consumer queue, so a plain map is safe.
    private val labelCache = HashMap<String, String>()

    private fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}
