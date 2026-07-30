package io.celox.notifvault.util

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
    private class FakeDao(initial: List<CapturedMessage> = emptyList()) : MessageDao {
        val rows = LinkedHashMap<String, CapturedMessage>()
        init { initial.forEach { rows[it.id] = it } }

        override suspend fun insertAll(messages: List<CapturedMessage>): List<Long> =
            messages.map { m ->
                // Mirrors OnConflictStrategy.IGNORE: -1 when the id is already present.
                if (rows.containsKey(m.id)) -1L else { rows[m.id] = m; rows.size.toLong() }
            }

        override suspend fun exportAll(): List<CapturedMessage> = rows.values.sortedBy { it.id }

        override suspend fun exportChunk(limit: Int, offset: Int): List<CapturedMessage> =
            rows.values.sortedBy { it.id }.drop(offset).take(limit)

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
}
