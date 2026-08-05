package io.celox.notifvault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The image a notification carried, stored next to the message it belongs to.
 *
 * Kept in its own table on purpose: the overview and the chat list read `messages` constantly, and
 * a blob column there would be dragged through every one of those queries. Here the bytes are only
 * ever loaded for a bubble that is actually on screen.
 *
 * The bytes live **inside the SQLCipher database**, not as files in app storage — that is what
 * keeps the "everything is encrypted at rest" promise true for pictures as well.
 *
 * [messageId] is the message's content hash, so a re-delivered notification maps to the same row
 * and insert-IGNORE collapses it, exactly like the message itself.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = CapturedMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationKey", "packageName")]
)
// Room only ever reads and writes this; the array-identity equals a data class generates for
// `bytes` is never relied on.
data class CapturedAttachment(
    @PrimaryKey val messageId: String,
    val packageName: String,
    val conversationKey: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
    val capturedAt: Long
)
