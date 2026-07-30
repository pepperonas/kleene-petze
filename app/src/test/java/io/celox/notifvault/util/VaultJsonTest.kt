package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * JSON is the plain-text format the user gets back out of the app, so a round trip has to be
 * bit-exact — a lossy export of an evidence archive is worse than none.
 */
class VaultJsonTest {

    private fun msg(
        id: String = "hash-1",
        text: String = "Hallo",
        sender: String = "Alice",
        chat: String = "Familie",
        time: Long = 1_753_000_000_000,
        deleted: Boolean = false,
        edited: Boolean = false
    ) = CapturedMessage(
        id = id, packageName = "com.whatsapp", appLabel = "WhatsApp",
        conversationKey = "jid-1@g.us", conversation = chat, sender = sender,
        isGroup = true, text = text, messageTime = time, capturedAt = time + 5,
        deletionSuspected = deleted, editSuperseded = edited
    )

    private fun document(messages: List<CapturedMessage>): String = buildString {
        append(VaultJson.header(messages.size, "2026-07-30T03:40:00Z"))
        messages.forEachIndexed { i, m -> append(VaultJson.record(m, first = i == 0)) }
        append(VaultJson.footer())
    }

    private fun parse(doc: String): List<CapturedMessage> =
        mutableListOf<CapturedMessage>().also { out ->
            VaultJson.parse(StringReader(doc)) { out += it }
        }

    @Test
    fun `round trip preserves every field`() {
        val original = msg(deleted = true, edited = true)
        val back = parse(document(listOf(original)))
        assertEquals(1, back.size)
        assertEquals(original, back[0])
    }

    @Test
    fun `round trip survives quotes, backslashes, newlines, tabs and unicode`() {
        val nasty = msg(
            text = "Er sagte \"hallo\"\nZeile2\tTab\\Ende \u0001 😀 Ümlaut",
            sender = "A\"B\\C",
            chat = "Chat\nmit Umbruch"
        )
        val back = parse(document(listOf(nasty)))
        assertEquals(nasty, back[0])
    }

    @Test
    fun `every message occupies exactly one line so the reader can stream`() {
        val doc = document(listOf(msg(id = "a", text = "eins\nzwei"), msg(id = "b")))
        val recordLines = doc.lines().count { VaultJson.isRecord(it.trim()) }
        assertEquals(2, recordLines)
    }

    @Test
    fun `separating commas make a valid json array`() {
        val doc = document(listOf(msg(id = "a"), msg(id = "b"), msg(id = "c")))
        // First record unprefixed, the following ones comma-prefixed — no trailing comma.
        assertTrue(doc.contains("\n{\"id\":\"a\""))
        assertTrue(doc.contains("\n,{\"id\":\"b\""))
        assertTrue(doc.contains("\n,{\"id\":\"c\""))
        assertTrue(doc.trimEnd().endsWith("]\n}"))
    }

    @Test
    fun `empty archive still yields a readable document`() {
        val doc = document(emptyList())
        assertTrue(doc.contains("\"count\": 0"))
        assertEquals(emptyList<CapturedMessage>(), parse(doc))
    }

    @Test
    fun `envelope is readable without parsing the messages`() {
        val env = VaultJson.readEnvelope(StringReader(document(List(3) { msg(id = "id$it") })))
        assertEquals(VaultJson.VERSION, env.version)
        assertEquals(3, env.count)
        assertEquals("2026-07-30T03:40:00Z", env.exported)
    }

    @Test
    fun `a foreign json file is rejected`() {
        val e = runCatching { parse("""{"foo": 1, "bar": [] }""") }.exceptionOrNull()
        assertTrue("expected a rejection, got $e", e is IllegalArgumentException)
    }

    @Test
    fun `a truncated record is rejected instead of silently dropping fields`() {
        val e = runCatching {
            VaultJson.parseRecord("""{"id":"a","pkg":"com.whatsapp"}""")
        }.exceptionOrNull()
        assertTrue("expected a rejection, got $e", e is IllegalArgumentException)
    }

    @Test
    fun `unicode escapes are decoded`() {
        val line = VaultJson.record(msg(text = "a\u0001b"), first = true)
        assertTrue(line.contains("\\u0001"))
        assertEquals("a\u0001b", VaultJson.parseRecord(line).text)
    }

    @Test
    fun `isRecord ignores structural lines`() {
        assertTrue(VaultJson.isRecord("""{"id":"x"}"""))
        assertTrue(VaultJson.isRecord(""",{"id":"x"}"""))
        assertTrue(!VaultJson.isRecord("{"))
        assertTrue(!VaultJson.isRecord("]"))
        assertTrue(!VaultJson.isRecord("\"count\": 3,"))
    }

    @Test
    fun `envelope of a file without a count reports null rather than guessing`() {
        val env = VaultJson.readEnvelope(StringReader("{\n\"format\": \"kleene-petze\",\n"))
        assertNull(env.count)
    }
}
