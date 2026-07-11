package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private val header = "Zeit;Erfasst;App;Chat;Absender;Gruppe;Gelöscht;Bearbeitet;Text\n"

    // ---- CSV ----

    @Test
    fun `csv starts with the header row`() {
        assertTrue(ExportUtils.toCsv(emptyList()).startsWith(header))
    }

    @Test
    fun `csv of empty list is header only`() {
        assertEquals(header, ExportUtils.toCsv(emptyList()))
    }

    @Test
    fun `csv leaves plain fields unquoted and maps the flags`() {
        val csv = ExportUtils.toCsv(listOf(msg("Hallo", isGroup = false)))
        assertTrue(csv.contains(";WhatsApp;Alice;Alice;nein;nein;nein;Hallo"))
        val group = ExportUtils.toCsv(listOf(msg("Hi", isGroup = true)))
        assertTrue(group.contains(";ja;nein;nein;Hi"))
        val deleted = ExportUtils.toCsv(listOf(msg("Weg", deleted = true)))
        assertTrue(deleted.contains(";nein;ja;nein;Weg"))
        val edited = ExportUtils.toCsv(listOf(msg("Alt", edited = true)))
        assertTrue(edited.contains(";nein;nein;ja;Alt"))
    }

    @Test
    fun `csv quotes fields containing the separator`() {
        val csv = ExportUtils.toCsv(listOf(msg("preis;menge")))
        assertTrue(csv.contains("\"preis;menge\""))
    }

    @Test
    fun `csv doubles embedded quotes`() {
        val csv = ExportUtils.toCsv(listOf(msg("sag \"hallo\"")))
        assertTrue(csv.contains("\"sag \"\"hallo\"\"\""))
    }

    @Test
    fun `csv flattens newlines to spaces so rows stay intact`() {
        val csv = ExportUtils.toCsv(listOf(msg("zeile1\nzeile2")))
        assertTrue(csv.contains("\"zeile1 zeile2\""))
        // exactly one data row → header newline + one row newline = 2 line breaks total
        assertEquals(2, csv.count { it == '\n' })
    }

    @Test
    fun `csv flattens carriage returns too`() {
        val crlf = ExportUtils.toCsv(listOf(msg("zeile1\r\nzeile2")))
        assertTrue(crlf.contains("\"zeile1 zeile2\""))
        assertFalse(crlf.contains("\r"))
        val bareCr = ExportUtils.toCsv(listOf(msg("a\rb")))
        assertTrue(bareCr.contains("\"a b\""))
    }

    // ---- JSON ----

    @Test
    fun `json is a bracketed array`() {
        val json = ExportUtils.toJson(listOf(msg("x"))).trim()
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
    }

    @Test
    fun `json of empty list is an empty array`() {
        assertEquals("[\n]\n", ExportUtils.toJson(emptyList()))
    }

    @Test
    fun `json renders the group flag as a boolean literal`() {
        assertTrue(ExportUtils.toJson(listOf(msg("x", isGroup = true))).contains("\"group\":true"))
        assertTrue(ExportUtils.toJson(listOf(msg("x", isGroup = false))).contains("\"group\":false"))
    }

    @Test
    fun `json is lossless - epoch times, package, key and verdict flags`() {
        val json = ExportUtils.toJson(listOf(msg("x", deleted = true, edited = true)))
        assertTrue(json.contains("\"timeMs\":1700000000000"))
        assertTrue(json.contains("\"capturedAtMs\":1700000000000"))
        assertTrue(json.contains("\"packageName\":\"com.whatsapp\""))
        assertTrue(json.contains("\"conversationKey\":\"key-Alice\""))
        assertTrue(json.contains("\"deleted\":true"))
        assertTrue(json.contains("\"edited\":true"))
        val plain = ExportUtils.toJson(listOf(msg("x")))
        assertTrue(plain.contains("\"deleted\":false"))
        assertTrue(plain.contains("\"edited\":false"))
    }

    @Test
    fun `json escapes quotes and backslashes`() {
        assertTrue(ExportUtils.toJson(listOf(msg("a\"b"))).contains("\"text\":\"a\\\"b\""))
        assertTrue(ExportUtils.toJson(listOf(msg("a\\b"))).contains("\"text\":\"a\\\\b\""))
    }

    @Test
    fun `json escapes newlines and carriage returns`() {
        val json = ExportUtils.toJson(listOf(msg("a\r\nb")))
        assertTrue(json.contains("a\\r\\nb"))
        assertFalse(json.contains("\r"))
    }

    @Test
    fun `json escapes tabs and other control characters`() {
        assertTrue(ExportUtils.toJson(listOf(msg("a\tb"))).contains("\"text\":\"a\\tb\""))
        // U+0001 must not appear raw — strict parsers reject unescaped control chars.
        val json = ExportUtils.toJson(listOf(msg("a\u0001b")))
        assertTrue(json.contains("\"text\":\"a\\u0001b\""))
        assertFalse(json.contains('\u0001'))
    }

    @Test
    fun `json separates multiple objects with commas`() {
        val json = ExportUtils.toJson(listOf(msg("one"), msg("two")))
        assertEquals(1, json.split("},").size - 1)
    }
}
