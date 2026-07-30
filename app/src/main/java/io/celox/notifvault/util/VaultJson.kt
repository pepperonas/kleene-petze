package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import java.io.Reader

/**
 * The canonical **plain-text** vault format: JSON, lossless, and re-importable.
 *
 * The envelope is ordinary JSON, but every message sits on its own line:
 *
 * ```
 * {
 * "format": "kleene-petze",
 * "version": 1,
 * "exported": "2026-07-30T03:40:00Z",
 * "count": 2,
 * "messages": [
 * {"id":"a1…","pkg":"com.whatsapp",…}
 * ,{"id":"b2…",…}
 * ]
 * }
 * ```
 *
 * That shape is deliberate: external tools (`jq`, editors) see valid JSON, while the importer
 * can stream it one line at a time and never holds more than a single record in memory.
 *
 * Hand-rolled rather than pulled from a library — the app takes no dependencies it doesn't need,
 * and `android.util.JsonReader` / `org.json` are framework classes that unit tests cannot run.
 * The schema is flat (strings, longs, booleans), so the parser stays small and fully testable.
 */
object VaultJson {

    const val FORMAT = "kleene-petze"
    const val VERSION = 1
    const val FILE_EXTENSION = "json"
    const val MIME = "application/json"

    /** What a file claims about itself, read from the envelope before importing anything. */
    data class Envelope(val version: Int, val exported: String?, val count: Int?)

    // ---- writing ----

    fun header(count: Int, exportedIso: String): String = buildString {
        append("{\n")
        append("\"format\": \"").append(FORMAT).append("\",\n")
        append("\"version\": ").append(VERSION).append(",\n")
        append("\"exported\": \"").append(esc(exportedIso)).append("\",\n")
        append("\"count\": ").append(count).append(",\n")
        append("\"messages\": [\n")
    }

    /**
     * One message, one line. [first] controls the separating comma so records can be written
     * as they stream out of the database without knowing which one is last.
     */
    fun record(m: CapturedMessage, first: Boolean): String = buildString {
        if (!first) append(',')
        append("{\"id\":\"").append(esc(m.id))
            .append("\",\"pkg\":\"").append(esc(m.packageName))
            .append("\",\"app\":\"").append(esc(m.appLabel))
            .append("\",\"chatKey\":\"").append(esc(m.conversationKey))
            .append("\",\"chat\":\"").append(esc(m.conversation))
            .append("\",\"sender\":\"").append(esc(m.sender))
            .append("\",\"group\":").append(m.isGroup)
            .append(",\"text\":\"").append(esc(m.text))
            .append("\",\"time\":").append(m.messageTime)
            .append(",\"captured\":").append(m.capturedAt)
            .append(",\"deleted\":").append(m.deletionSuspected)
            .append(",\"edited\":").append(m.editSuperseded)
            .append("}\n")
    }

    fun footer(): String = "]\n}\n"

    // ---- reading ----

    /**
     * Streams a file record by record, calling [onMessage] for each. Structural lines (envelope,
     * brackets) are skipped, so the reader never has to hold the document.
     *
     * @throws IllegalArgumentException if the file is not a Kleene-Petze JSON export.
     */
    fun parse(reader: Reader, onMessage: (CapturedMessage) -> Unit) {
        var sawFormat = false
        var sawRecord = false
        // Deliberately not forEachLine: that closes the caller's stream, and the caller owns it.
        val buffered = reader.buffered()
        while (true) {
            val line = buffered.readLine() ?: break
            val t = line.trim()
            when {
                t.startsWith("\"format\"") -> {
                    require(t.contains("\"$FORMAT\"")) { "Keine Kleene-Petze-Exportdatei" }
                    sawFormat = true
                }
                isRecord(t) -> { onMessage(parseRecord(t)); sawRecord = true }
            }
        }
        require(sawFormat || sawRecord) { "Keine Kleene-Petze-Exportdatei" }
    }

    /**
     * Reads just the envelope — enough to tell the user what a file holds before importing.
     * Stops at the first record, so this costs a handful of lines, not the whole file.
     */
    fun readEnvelope(reader: Reader): Envelope {
        var version = 0
        var exported: String? = null
        var count: Int? = null
        val buffered = reader.buffered()
        while (true) {
            val line = buffered.readLine() ?: break
            val t = line.trim()
            when {
                t.startsWith("\"version\"") -> version = numberAfterColon(t)?.toInt() ?: 0
                t.startsWith("\"exported\"") -> exported = stringAfterColon(t)
                t.startsWith("\"count\"") -> count = numberAfterColon(t)?.toInt()
                isRecord(t) || t.startsWith("]") -> return Envelope(version, exported, count)
            }
        }
        return Envelope(version, exported, count)
    }

    /** True for a line that carries a message object (with or without its leading comma). */
    fun isRecord(trimmed: String): Boolean =
        trimmed.startsWith("{\"") || trimmed.startsWith(",{\"")

    /** @throws IllegalArgumentException on a malformed record. */
    fun parseRecord(line: String): CapturedMessage {
        val fields = parseObject(line.trim().removePrefix(","))
        fun str(k: String) = fields[k] as? String
            ?: throw IllegalArgumentException("Feld '$k' fehlt oder ist kein Text")
        fun num(k: String) = (fields[k] as? Long)
            ?: throw IllegalArgumentException("Feld '$k' fehlt oder ist keine Zahl")
        fun bool(k: String) = fields[k] as? Boolean ?: false
        return CapturedMessage(
            id = str("id"),
            packageName = str("pkg"),
            appLabel = str("app"),
            conversationKey = str("chatKey"),
            conversation = str("chat"),
            sender = str("sender"),
            isGroup = bool("group"),
            text = str("text"),
            messageTime = num("time"),
            capturedAt = num("captured"),
            deletionSuspected = bool("deleted"),
            editSuperseded = bool("edited")
        )
    }

    // ---- tiny flat-object parser ----

    /** Parses `{"k":"v","n":1,"b":true}` into key → String | Long | Boolean. */
    private fun parseObject(s: String): Map<String, Any> {
        val out = HashMap<String, Any>()
        var i = s.indexOf('{')
        require(i >= 0) { "Kein JSON-Objekt" }
        i++
        while (i < s.length) {
            i = skipWs(s, i)
            if (i < s.length && s[i] == '}') return out
            require(i < s.length && s[i] == '"') { "Schlüssel erwartet an Position $i" }
            val (key, afterKey) = readString(s, i)
            i = skipWs(s, afterKey)
            require(i < s.length && s[i] == ':') { "':' erwartet an Position $i" }
            i = skipWs(s, i + 1)
            val (value, afterValue) = readValue(s, i)
            out[key] = value
            i = skipWs(s, afterValue)
            if (i < s.length && s[i] == ',') i++
        }
        throw IllegalArgumentException("Unvollständiges JSON-Objekt")
    }

    private fun skipWs(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun readValue(s: String, at: Int): Pair<Any, Int> = when {
        at >= s.length -> throw IllegalArgumentException("Wert erwartet")
        s[at] == '"' -> readString(s, at)
        s.startsWith("true", at) -> true to at + 4
        s.startsWith("false", at) -> false to at + 5
        else -> {
            var i = at
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && s[i].isDigit()) i++
            val raw = s.substring(at, i)
            (raw.toLongOrNull() ?: throw IllegalArgumentException("Ungültige Zahl '$raw'")) to i
        }
    }

    private fun readString(s: String, at: Int): Pair<String, Int> {
        require(s[at] == '"') { "Zeichenkette erwartet an Position $at" }
        val sb = StringBuilder()
        var i = at + 1
        while (i < s.length) {
            when (val c = s[i]) {
                '"' -> return sb.toString() to i + 1
                '\\' -> {
                    require(i + 1 < s.length) { "Abgebrochene Escape-Sequenz" }
                    when (val e = s[i + 1]) {
                        '"' -> { sb.append('"'); i += 2 }
                        '\\' -> { sb.append('\\'); i += 2 }
                        '/' -> { sb.append('/'); i += 2 }
                        'b' -> { sb.append('\b'); i += 2 }
                        'f' -> { sb.append('\u000C'); i += 2 }
                        'n' -> { sb.append('\n'); i += 2 }
                        'r' -> { sb.append('\r'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'u' -> {
                            require(i + 5 < s.length) { "Abgebrochene \\u-Sequenz" }
                            val hex = s.substring(i + 2, i + 6)
                            sb.append(
                                (hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Ungültige \\u-Sequenz '$hex'"))
                                    .toChar()
                            )
                            i += 6
                        }
                        else -> throw IllegalArgumentException("Unbekannte Escape-Sequenz '\\$e'")
                    }
                }
                else -> { sb.append(c); i++ }
            }
        }
        throw IllegalArgumentException("Nicht beendete Zeichenkette")
    }

    private fun stringAfterColon(line: String): String? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val q = line.indexOf('"', colon)
        if (q < 0) return null
        return runCatching { readString(line, q).first }.getOrNull()
    }

    private fun numberAfterColon(line: String): Long? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        return line.substring(colon + 1).trim().trimEnd(',').toLongOrNull()
    }

    // Escapes every control character, so a tab or newline inside a message can never break
    // the one-record-per-line layout the streaming reader relies on.
    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}
