package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/**
 * The per-chat export. It writes the same two formats the importer reads, so the property that
 * matters most is that a chat can be exported and read back unchanged — the previous CSV shape
 * could not, because it flattened newlines and omitted three columns.
 */
class ExportUtilsTest {

    private fun msg(
        text: String,
        isGroup: Boolean = false,
        conversation: String = "Alice",
        sender: String = "Alice",
        appLabel: String = "WhatsApp",
        deleted: Boolean = false,
        edited: Boolean = false
    ) = CapturedMessage(
        id = "id-${text.hashCode()}-$isGroup",
        packageName = "com.whatsapp",
        appLabel = appLabel,
        conversationKey = "key-$conversation",
        conversation = conversation,
        sender = sender,
        isGroup = isGroup,
        text = text,
        messageTime = 1_700_000_000_000,
        capturedAt = 1_700_000_000_000,
        deletionSuspected = deleted,
        editSuperseded = edited
    )

    private fun readCsv(s: String) = mutableListOf<CapturedMessage>()
        .also { out -> VaultCsv.parse(StringReader(s)) { out += it } }

    private fun readJson(s: String) = mutableListOf<CapturedMessage>()
        .also { out -> VaultJson.parse(StringReader(s)) { out += it } }

    // ---- CSV ----

    @Test
    fun `csv starts with the header row`() {
        assertTrue(ExportUtils.toCsv(emptyList()).startsWith(VaultCsv.HEADER))
    }

    @Test
    fun `csv of empty list is header only`() {
        assertEquals(VaultCsv.HEADER, ExportUtils.toCsv(emptyList()))
    }

    @Test
    fun `csv carries the verdict and group columns`() {
        assertTrue(ExportUtils.toCsv(listOf(msg("Hallo", isGroup = false))).contains(";nein;"))
        assertTrue(ExportUtils.toCsv(listOf(msg("Hi", isGroup = true))).contains(";ja;"))
        assertEquals(true, readCsv(ExportUtils.toCsv(listOf(msg("Weg", deleted = true))))
            .single().deletionSuspected)
        assertEquals(true, readCsv(ExportUtils.toCsv(listOf(msg("Alt", edited = true))))
            .single().editSuperseded)
    }

    @Test
    fun `csv quotes separators and doubles quotes`() {
        assertTrue(ExportUtils.toCsv(listOf(msg("preis;menge"))).contains("\"preis;menge\""))
        assertTrue(ExportUtils.toCsv(listOf(msg("sag \"hallo\""))).contains("\"sag \"\"hallo\"\"\""))
    }

    // This is the behaviour that changed: newlines used to be replaced by spaces, which quietly
    // altered the archived message. They are preserved inside the quoted field now.
    @Test
    fun `csv preserves newlines inside a message`() {
        val multiline = msg("zeile1\nzeile2")
        assertEquals(multiline, readCsv(ExportUtils.toCsv(listOf(multiline))).single())
        // Also exact for CRLF: inside a quoted field the \r is content, not a row terminator —
        // only an unquoted \r\n ends a row.
        val crlf = msg("zeile1\r\nzeile2")
        assertEquals(crlf, readCsv(ExportUtils.toCsv(listOf(crlf))).single())
        val bareCr = msg("a\rb")
        assertEquals(bareCr, readCsv(ExportUtils.toCsv(listOf(bareCr))).single())
    }

    @Test
    fun `csv round trips a whole chat`() {
        val chat = listOf(
            msg("Hallo", conversation = "Familie", isGroup = true),
            msg("a;b \"c\"\nd", conversation = "Familie", isGroup = true, deleted = true),
            msg("Ümlaut 😀", conversation = "Familie", isGroup = true, edited = true)
        )
        assertEquals(chat, readCsv(ExportUtils.toCsv(chat)))
    }

    // ---- JSON ----

    @Test
    fun `json round trips a whole chat`() {
        val chat = listOf(
            msg("Hallo"),
            msg("a\"b\\c\td\ne"),
            msg("\u0001 Steuerzeichen", deleted = true, edited = true)
        )
        assertEquals(chat, readJson(ExportUtils.toJson(chat)))
    }

    @Test
    fun `json of empty list is a valid empty document`() {
        val json = ExportUtils.toJson(emptyList())
        assertTrue(json.contains("\"count\": 0"))
        assertEquals(emptyList<CapturedMessage>(), readJson(json))
    }

    @Test
    fun `json encodes booleans as booleans`() {
        assertTrue(ExportUtils.toJson(listOf(msg("x", isGroup = true))).contains("\"group\":true"))
        assertTrue(ExportUtils.toJson(listOf(msg("x", isGroup = false))).contains("\"group\":false"))
        val flagged = ExportUtils.toJson(listOf(msg("x", deleted = true, edited = true)))
        assertTrue(flagged.contains("\"deleted\":true"))
        assertTrue(flagged.contains("\"edited\":true"))
    }

    @Test
    fun `json escapes control characters so the file stays parseable`() {
        assertTrue(ExportUtils.toJson(listOf(msg("a\tb"))).contains("\\t"))
        assertTrue(ExportUtils.toJson(listOf(msg("a\r\nb"))).contains("\\r\\n"))
        assertTrue(ExportUtils.toJson(listOf(msg("a\u0001b"))).contains("\\u0001"))
    }

    @Test
    fun `json keeps one record per line`() {
        val json = ExportUtils.toJson(listOf(msg("one"), msg("two\nlines")))
        assertEquals(2, json.lines().count { VaultJson.isRecord(it.trim()) })
    }
}
