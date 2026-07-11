package io.celox.notifvault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single captured message. The [id] is a content hash so that WhatsApp re-posting
 * the same message inside many MessagingStyle notifications results in exactly one row
 * (insert uses IGNORE on conflict). That is the whole de-duplication trick.
 *
 * Chats are grouped by [conversationKey] — a *stable* per-chat identifier taken from the
 * notification (shortcut id / tag), NOT by the human-readable [conversation] title. The
 * title is often null for 1:1 WhatsApp chats and occasionally missing for groups, so
 * grouping by the title mixes distinct chats and splits groups by sender. The key stays
 * constant for a chat even when its displayed title changes.
 */
@Entity(
    tableName = "messages",
    // v3: composite index serves the overview grouping (conversationKey, packageName,
    // MAX(messageTime)) and the per-chat query; it also covers plain conversationKey
    // lookups. The old single-column conversationKey and (unused since the key rework)
    // conversation indexes were dropped in MIGRATION_2_3.
    indices = [
        Index("conversationKey", "packageName", "messageTime"),
        Index("packageName"), Index("messageTime")
    ]
)
data class CapturedMessage(
    @PrimaryKey val id: String,
    val packageName: String,
    val appLabel: String,
    @ColumnInfo(defaultValue = "") val conversationKey: String, // stable per-chat id (grouping)
    val conversation: String,   // chat / group title (display only, may change)
    val sender: String,         // who wrote it
    val isGroup: Boolean,
    val text: String,
    val messageTime: Long,      // original timestamp of the message
    val capturedAt: Long,       // when we stored it
    val deletionSuspected: Boolean = false,
    // v4: an edited message re-arrives with the same conversationKey+sender+messageTime but
    // new text (→ new content hash → new row). This flags the *older* version(s) so the UI
    // can show "edited (earlier version)" while the newest row stays the current one.
    @ColumnInfo(defaultValue = "0") val editSuperseded: Boolean = false
)
