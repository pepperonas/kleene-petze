package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedAttachment
import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.ConversationSummary
import io.celox.notifvault.data.MessageDao
import io.celox.notifvault.data.MessageText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * End-to-end round trip through the real streaming engine: database → file → detection →
 * preview → merge, for all three formats. This is the test that actually proves "exportieren und
 * importieren" works; the per-format tests only cover serialisation.
 */
class VaultTransferTest {

    /** Minimal in-memory DAO. Only the export/insert paths are exercised. */
    private open class FakeDao(initial: List<CapturedMessage> = emptyList()) : MessageDao {
        val rows = LinkedHashMap<String, CapturedMessage>()
        init { initial.forEach { rows[it.id] = it } }

        override suspend fun insertAll(messages: List<CapturedMessage>): List<Long> =
            messages.map { m ->
                // Mirrors OnConflictStrategy.IGNORE: -1 when the id is already present.
                if (rows.containsKey(m.id)) -1L else { rows[m.id] = m; rows.size.toLong() }
            }

        override suspend fun exportAll(): List<CapturedMessage> = rows.values.sortedBy { it.id }

        // Same semantics as the SQL: ids strictly after [afterId], ascending, at most [limit].
        override suspend fun exportChunk(limit: Int, afterId: String): List<CapturedMessage> =
            rows.values.filter { it.id > afterId }.sortedBy { it.id }.take(limit)

        override suspend fun countNow(): Int = rows.size
        override suspend fun existingAttachmentIds(ids: List<String>): List<String> = emptyList()

        override fun conversations(): Flow<List<ConversationSummary>> = flowOf(emptyList())
        override fun messagesFor(conversationKey: String, pkg: String): Flow<List<CapturedMessage>> = flowOf(emptyList())
        override fun search(q: String): Flow<List<CapturedMessage>> = flowOf(emptyList())
        override fun count(): Flow<Int> = flowOf(rows.size)
        override suspend fun clear() { rows.clear() }
        override suspend fun deleteConversation(conversationKey: String, pkg: String) {}
        override suspend fun markDeleted(conversationKey: String, sender: String, messageTime: Long) = 0
        override suspend fun markEditSuperseded(
            conversationKey: String, pkg: String, sender: String, messageTime: Long, newId: String
        ) = 0
        override fun flagged(): Flow<List<CapturedMessage>> = flowOf(emptyList())
        override suspend fun pruneOlderThan(cutoff: Long) = 0
        override suspend fun idsAndTexts(): List<MessageText> =
            rows.values.map { MessageText(it.id, it.text, it.conversation) }
        override suspend fun deleteByIds(ids: List<String>) = 0
        override suspend fun applyDeletedFlags(ids: List<String>) {}
        override suspend fun applyEditedFlags(ids: List<String>) {}

        // Attachments play no part in export/import — pictures stay on the device (see
        // VaultTransfer's docs), so these are inert here.
        override suspend fun insertAttachments(items: List<CapturedAttachment>) {}
        override fun attachmentIdsFor(conversationKey: String, pkg: String): Flow<List<String>> =
            flowOf(emptyList())
        override suspend fun attachment(messageId: String): CapturedAttachment? = null
        override fun attachmentCount(): Flow<Int> = flowOf(0)
        override fun attachmentBytes(): Flow<Long> = flowOf(0L)
        override suspend fun clearAttachments() {}
        override suspend fun deleteAttachmentsFor(conversationKey: String, pkg: String) {}
        override suspend fun pruneAttachmentsOlderThan(cutoff: Long) {}
        override suspend fun deleteAttachmentsByIds(ids: List<String>) {}
    }

    private fun msg(i: Int) = CapturedMessage(
        id = "hash-$i",
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        conversationKey = "jid-$i@g.us",
        conversation = if (i % 2 == 0) "Familie" else "Alice;\"quoted\"",
        sender = "Sender $i",
        isGroup = i % 2 == 0,
        text = "Nachricht $i\nmit Umbruch\tund Tab \"Zitat\" 😀",
        messageTime = 1_753_000_000_000 + i * 1000L,
        capturedAt = 1_753_000_000_500 + i * 1000L,
        deletionSuspected = i % 3 == 0,
        editSuperseded = i % 5 == 0
    )

    /** Exports [source] and reads it straight back into a fresh vault. */
    private fun roundTrip(
        source: List<CapturedMessage>,
        format: VaultFormat,
        passphrase: String? = null
    ): Pair<List<CapturedMessage>, VaultTransfer.Result> = runBlocking {
        val out = ByteArrayOutputStream()
        val exported = VaultTransfer.export(
            FakeDao(source), out, format, passphrase?.toCharArray(), total = source.size
        )
        assertEquals(source.size, exported)

        val bytes = out.toByteArray()
        assertEquals(
            "detection picked the wrong format",
            format,
            VaultFormat.detect(bytes.copyOf(minOf(VaultFormat.SNIFF_BYTES, bytes.size)))
        )

        val target = FakeDao()
        val (preview, decrypted) = VaultTransfer.preview(
            format, { ByteArrayInputStream(bytes) }, passphrase?.toCharArray()
        )
        assertEquals("preview count", source.size, preview.count)

        val result = VaultTransfer.apply(format, { ByteArrayInputStream(bytes) }, decrypted) { batch ->
            val ids = target.insertAll(batch)
            VaultTransfer.Result(
                imported = ids.count { it != -1L },
                alreadyPresent = ids.count { it == -1L }
            )
        }
        target.exportAll() to result
    }

    @Test
    fun `json round trips the whole archive`() {
        val source = List(7) { msg(it) }
        val (restored, result) = roundTrip(source, VaultFormat.JSON)
        assertEquals(source.sortedBy { it.id }, restored)
        assertEquals(source.size, result.imported)
        assertEquals(0, result.alreadyPresent)
    }

    @Test
    fun `csv round trips the whole archive`() {
        val source = List(7) { msg(it) }
        val (restored, result) = roundTrip(source, VaultFormat.CSV)
        assertEquals(source.sortedBy { it.id }, restored)
        assertEquals(source.size, result.imported)
    }

    @Test
    fun `encrypted round trips the whole archive`() {
        val source = List(7) { msg(it) }
        val (restored, result) = roundTrip(source, VaultFormat.ENCRYPTED, "geheim-passwort")
        assertEquals(source.sortedBy { it.id }, restored)
        assertEquals(source.size, result.imported)
    }

    // More rows than one page/batch, so the chunking is actually exercised.
    @Test
    fun `an archive larger than one chunk round trips in every format`() {
        val source = List(VaultTransfer.CHUNK * 2 + 13) { msg(it) }
        for (format in VaultFormat.entries) {
            val pass = if (format.encrypted) "geheim-passwort" else null
            val (restored, result) = roundTrip(source, format, pass)
            assertEquals("$format lost rows", source.size, restored.size)
            assertEquals("$format imported count", source.size, result.imported)
        }
    }

    @Test
    fun `importing into a vault that already has the messages changes nothing`() = runBlocking {
        val source = List(5) { msg(it) }
        val out = ByteArrayOutputStream()
        VaultTransfer.export(FakeDao(source), out, VaultFormat.JSON, null, source.size)
        val bytes = out.toByteArray()

        val target = FakeDao(source) // same content already present
        val result = VaultTransfer.apply(VaultFormat.JSON, { ByteArrayInputStream(bytes) }, null) { batch ->
            val ids = target.insertAll(batch)
            VaultTransfer.Result(ids.count { it != -1L }, ids.count { it == -1L })
        }
        assertEquals(0, result.imported)
        assertEquals(source.size, result.alreadyPresent)
        assertEquals(source.size, target.rows.size)
    }

    @Test
    fun `an empty archive exports and imports without error`() {
        val (restored, result) = roundTrip(emptyList(), VaultFormat.JSON)
        assertTrue(restored.isEmpty())
        assertEquals(0, result.imported)
    }

    @Test
    fun `the wrong passphrase is reported instead of yielding an empty import`() = runBlocking {
        val out = ByteArrayOutputStream()
        VaultTransfer.export(
            FakeDao(List(3) { msg(it) }), out, VaultFormat.ENCRYPTED, "richtig-lang".toCharArray(), 3
        )
        val bytes = out.toByteArray()
        val e = runCatching {
            VaultTransfer.preview(
                VaultFormat.ENCRYPTED, { ByteArrayInputStream(bytes) }, "falsch-genug".toCharArray()
            )
        }.exceptionOrNull()
        assertTrue("expected an AEAD failure, got $e", e is javax.crypto.AEADBadTagException)
    }

    @Test
    fun `preview reports the time range of the file`() {
        val source = List(4) { msg(it) }
        val bytes = ByteArrayOutputStream().also { out ->
            runBlocking { VaultTransfer.export(FakeDao(source), out, VaultFormat.JSON, null, source.size) }
        }.toByteArray()
        val (preview, _) = VaultTransfer.preview(VaultFormat.JSON, { ByteArrayInputStream(bytes) }, null)
        assertEquals(source.minOf { it.messageTime }, preview.oldest)
        assertEquals(source.maxOf { it.messageTime }, preview.newest)
    }

    // Ids are content hashes, i.e. random. A message captured while the export runs can land in
    // front of the page already written; with LIMIT/OFFSET that shifted every later page and one
    // row was exported twice. Keyset paging must neither duplicate nor skip.
    @Test
    fun `rows arriving mid-export neither duplicate nor drop existing rows`() = runBlocking {
        val source = List(VaultTransfer.CHUNK * 2 + 7) { msg(it) }
        var pages = 0
        val dao = object : FakeDao(source) {
            override suspend fun exportChunk(limit: Int, afterId: String): List<CapturedMessage> {
                val page = super.exportChunk(limit, afterId)
                if (++pages == 1) {
                    // Sorts before everything already written, then something is deleted too.
                    rows["aaa-new"] = msg(9999).copy(id = "aaa-new")
                    rows.remove(page.first().id)
                }
                return page
            }
        }
        val out = ByteArrayOutputStream()
        VaultTransfer.export(dao, out, VaultFormat.JSON, null, total = source.size)

        val ids = mutableListOf<String>()
        VaultJson.parse(ByteArrayInputStream(out.toByteArray()).reader(Charsets.UTF_8)) { ids += it.id }
        assertEquals("a row was exported twice", ids.size, ids.toSet().size)
        assertEquals("existing rows went missing", source.map { it.id }.toSet(), ids.toSet())
    }

    // A failed export must not end in a well-formed file: closing the writer would write the
    // GZIP trailer and the GCM tag and turn a partial archive into a valid, authenticated one.
    @Test
    fun `an export that fails halfway leaves no valid encrypted archive`() {
        val source = List(VaultTransfer.CHUNK * 2) { msg(it) }
        var pages = 0
        val dao = object : FakeDao(source) {
            override suspend fun exportChunk(limit: Int, afterId: String): List<CapturedMessage> {
                if (++pages == 2) throw java.io.IOException("Speicher voll")
                return super.exportChunk(limit, afterId)
            }
        }
        val out = ByteArrayOutputStream()
        val failed = runCatching {
            runBlocking {
                VaultTransfer.export(dao, out, VaultFormat.ENCRYPTED, "geheim-passwort".toCharArray(), source.size)
            }
        }
        assertTrue(failed.isFailure)
        val decoded = runCatching {
            VaultTransfer.preview(
                VaultFormat.ENCRYPTED, { ByteArrayInputStream(out.toByteArray()) },
                "geheim-passwort".toCharArray()
            )
        }
        assertTrue("a partial export decrypted as a valid archive", decoded.isFailure)
    }
}
