package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * The old CSV export flattened newlines and dropped three columns, so it could never be read
 * back. These tests pin the two properties that changed: it is lossless, and it round-trips.
 */
class VaultCsvTest {

    private fun msg(
        id: String = "hash-1",
        text: String = "Hallo",
        chat: String = "Familie",
        group: Boolean = true,
        deleted: Boolean = false,
        edited: Boolean = false
    ) = CapturedMessage(
        id = id, packageName = "com.whatsapp", appLabel = "WhatsApp",
        conversationKey = "jid-1@g.us", conversation = chat, sender = "Alice",
        isGroup = group, text = text, messageTime = 1_753_000_000_000,
        capturedAt = 1_753_000_000_005, deletionSuspected = deleted, editSuperseded = edited
    )

    private fun document(messages: List<CapturedMessage>) = buildString {
        append(VaultCsv.HEADER)
        messages.forEach { append(VaultCsv.row(it, "2026-07-20 10:00:00", "2026-07-20 10:00:05")) }
    }

    private fun parse(doc: String): List<CapturedMessage> =
        mutableListOf<CapturedMessage>().also { out ->
            VaultCsv.parse(StringReader(doc)) { out += it }
        }

    @Test
    fun `round trip preserves every field`() {
        val original = msg(deleted = true, edited = true, group = false)
        assertEquals(original, parse(document(listOf(original))).single())
    }

    // The whole reason the parser is a character state machine.
    @Test
    fun `newlines inside a message survive instead of becoming spaces`() {
        val multiline = msg(text = "Zeile 1\nZeile 2\r\nZeile 3")
        assertEquals(multiline, parse(document(listOf(multiline))).single())
    }

    @Test
    fun `separators and quotes inside fields round trip`() {
        val tricky = msg(text = "a;b;c \"zitat\" ;;", chat = "Chat;mit \"Semikolon\"")
        assertEquals(tricky, parse(document(listOf(tricky))).single())
    }

    @Test
    fun `several rows parse independently`() {
        val all = listOf(msg(id = "a"), msg(id = "b", text = "x\ny"), msg(id = "c"))
        assertEquals(all, parse(document(all)))
    }

    @Test
    fun `a trailing newline does not produce a phantom row`() {
        assertEquals(1, parse(document(listOf(msg())) + "\n").size)
    }

    @Test
    fun `a file without the trailing newline still yields its last row`() {
        assertEquals(1, parse(document(listOf(msg())).trimEnd('\n')).size)
    }

    @Test
    fun `the header names every column that is written`() {
        val cols = VaultCsv.HEADER.trimEnd('\n').split(';')
        val fields = VaultCsv.row(msg(), "t", "c").trimEnd('\n').split(';')
        assertEquals(cols.size, fields.size)
        assertTrue(cols.containsAll(listOf("Id", "Paket", "ChatKey", "ZeitMs", "ErfasstMs")))
    }

    @Test
    fun `a foreign csv is rejected rather than imported as garbage`() {
        val e = runCatching { parse("Name;Alter\nAlice;30\n") }.exceptionOrNull()
        assertTrue("expected a rejection, got $e", e is IllegalArgumentException)
    }

    @Test
    fun `an empty file is rejected`() {
        assertTrue(runCatching { parse("") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `a byte order mark in front of the header is tolerated`() {
        assertEquals(1, parse("\uFEFF" + document(listOf(msg()))).size)
    }

    @Test
    fun `a row with a broken timestamp names the row`() {
        val broken = document(listOf(msg())).replace(";1753000000000;", ";keine-zahl;")
        val e = runCatching { parse(broken) }.exceptionOrNull()
        assertTrue("expected a rejection, got $e", e is IllegalArgumentException)
    }
}
