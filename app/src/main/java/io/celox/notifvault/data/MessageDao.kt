package io.celox.notifvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Just enough of a row to decide whether it is noise (one-time cleanup). The [conversation] title
 * matters as much as the [text]: a missed call stores its wording as the *title* ("Verpasster
 * Sprachanruf") and the contact name as the text.
 */
data class MessageText(val id: String, val text: String, val conversation: String)

/** Lightweight projection for the conversation overview list. */
data class ConversationSummary(
    val conversationKey: String,
    val conversation: String,   // latest title for this chat
    val packageName: String,
    val appLabel: String,
    val isGroup: Boolean,
    val lastText: String,
    val lastTime: Long,
    val messageCount: Int,
    val deletedCount: Int,      // messages in this chat detected as deleted by the sender
    val editedCount: Int        // earlier versions of messages the sender later edited
)

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<CapturedMessage>): List<Long>

    // Group by the stable conversationKey. The bare columns (conversation, appLabel, isGroup,
    // text) resolve to the row holding MAX(messageTime) — SQLite's documented min/max-bare-column
    // behaviour — so the overview shows each chat's latest title and last message.
    @Query(
        """
        SELECT conversationKey, conversation, packageName, appLabel, isGroup,
               text AS lastText, MAX(messageTime) AS lastTime, COUNT(*) AS messageCount,
               SUM(deletionSuspected) AS deletedCount, SUM(editSuperseded) AS editedCount
        FROM messages
        GROUP BY conversationKey, packageName
        ORDER BY lastTime DESC
        """
    )
    fun conversations(): Flow<List<ConversationSummary>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationKey = :conversationKey AND packageName = :pkg
        ORDER BY messageTime ASC
        """
    )
    fun messagesFor(conversationKey: String, pkg: String): Flow<List<CapturedMessage>>

    // ESCAPE '\' so % and _ typed by the user match literally
    // (the ViewModel backslash-escapes them before calling this).
    @Query(
        """
        SELECT * FROM messages
        WHERE text LIKE '%' || :q || '%' ESCAPE '\'
           OR sender LIKE '%' || :q || '%' ESCAPE '\'
           OR conversation LIKE '%' || :q || '%' ESCAPE '\'
        ORDER BY messageTime DESC
        LIMIT 500
        """
    )
    fun search(q: String): Flow<List<CapturedMessage>>

    @Query("SELECT * FROM messages ORDER BY messageTime DESC")
    suspend fun exportAll(): List<CapturedMessage>

    // Streaming export reads the archive in blocks instead of materialising all of it: the old
    // path held the full list, the serialised string, the gzip buffer and the ciphertext at the
    // same time. Ordered by the primary key so paging stays stable while rows keep arriving.
    @Query("SELECT * FROM messages ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun exportChunk(limit: Int, offset: Int): List<CapturedMessage>

    @Query("SELECT COUNT(*) FROM messages")
    fun count(): Flow<Int>

    @Query("DELETE FROM messages")
    suspend fun clear()

    @Query("DELETE FROM messages WHERE conversationKey = :conversationKey AND packageName = :pkg")
    suspend fun deleteConversation(conversationKey: String, pkg: String)

    // Flag the stored original that a deletion placeholder refers to (same chat + sender + time).
    @Query(
        """
        UPDATE messages SET deletionSuspected = 1
        WHERE conversationKey = :conversationKey AND sender = :sender AND messageTime = :messageTime
        """
    )
    suspend fun markDeleted(conversationKey: String, sender: String, messageTime: Long): Int

    // Edit detection: an edited message re-arrives with the same chat + sender + original
    // timestamp but new text (→ new content-hash row). Called after each *actual* insert,
    // this flags any older sibling rows as superseded. For a brand-new message it matches
    // nothing (0 rows) — the composite index keeps that check cheap.
    @Query(
        """
        UPDATE messages SET editSuperseded = 1
        WHERE conversationKey = :conversationKey AND packageName = :pkg
          AND sender = :sender AND messageTime = :messageTime AND id != :newId
        """
    )
    suspend fun markEditSuperseded(
        conversationKey: String, pkg: String, sender: String, messageTime: Long, newId: String
    ): Int

    // Everything the vault has "uncovered": deleted-by-sender originals and earlier
    // versions of edited messages, newest first (global "Aufgedeckt" view).
    @Query(
        """
        SELECT * FROM messages
        WHERE deletionSuspected = 1 OR editSuperseded = 1
        ORDER BY messageTime DESC
        """
    )
    fun flagged(): Flow<List<CapturedMessage>>

    // Retention: drop messages older than the cutoff (only ever called with retention enabled).
    @Query("DELETE FROM messages WHERE messageTime < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long): Int

    // One-time cleanup of service/status notifications captured before the noise filter existed
    // (WhatsApp's "Überprüfe auf neue Nachrichten" & co.). Matching happens in Kotlin —
    // SQLite's LIKE/LOWER are ASCII-only and would trip over the umlauts.
    @Query("SELECT id, text, conversation FROM messages")
    suspend fun idsAndTexts(): List<MessageText>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    // Restore path: re-apply flags from a backup to rows that already existed unflagged
    // (insert IGNORE keeps the existing row, so flags must be merged separately).
    @Query("UPDATE messages SET deletionSuspected = 1 WHERE id IN (:ids)")
    suspend fun applyDeletedFlags(ids: List<String>)

    @Query("UPDATE messages SET editSuperseded = 1 WHERE id IN (:ids)")
    suspend fun applyEditedFlags(ids: List<String>)

    // ---- Attachments (images pulled out of notifications) ----------------------------------
    //
    // Deletions are always paired explicitly with the matching message delete rather than left
    // to the foreign key: ON DELETE CASCADE only fires while SQLite has `PRAGMA foreign_keys`
    // enabled, and orphaned blobs would be invisible — they show up as storage that never
    // shrinks. Attachments go first, messages second.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttachments(items: List<CapturedAttachment>)

    /** Ids only: which bubbles have a picture. Never pulls blobs into a list query. */
    @Query(
        "SELECT messageId FROM attachments WHERE conversationKey = :conversationKey AND packageName = :pkg"
    )
    fun attachmentIdsFor(conversationKey: String, pkg: String): Flow<List<String>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun attachment(messageId: String): CapturedAttachment?

    @Query("SELECT COUNT(*) FROM attachments")
    fun attachmentCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(LENGTH(bytes)), 0) FROM attachments")
    fun attachmentBytes(): Flow<Long>

    @Query("DELETE FROM attachments")
    suspend fun clearAttachments()

    @Query("DELETE FROM attachments WHERE conversationKey = :conversationKey AND packageName = :pkg")
    suspend fun deleteAttachmentsFor(conversationKey: String, pkg: String)

    @Query("DELETE FROM attachments WHERE messageId IN (SELECT id FROM messages WHERE messageTime < :cutoff)")
    suspend fun pruneAttachmentsOlderThan(cutoff: Long)

    @Query("DELETE FROM attachments WHERE messageId IN (:ids)")
    suspend fun deleteAttachmentsByIds(ids: List<String>)
}
