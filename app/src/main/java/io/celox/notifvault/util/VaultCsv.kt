package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import java.io.Reader

/**
 * Lossless CSV (RFC 4180, semicolon-separated for German spreadsheet locales) — readable in
 * Excel/LibreOffice *and* re-importable.
 *
 * The older export was neither: it dropped `id`, `packageName` and `conversationKey`, and it
 * replaced newlines inside a message with spaces. For an archive whose whole point is preserving
 * what someone wrote, silently rewriting the text is a defect, so quoted fields now keep their
 * line breaks verbatim — which is precisely why the parser has to be a character state machine
 * rather than a line splitter.
 *
 * Human-readable timestamps stay in the file for the spreadsheet case; the import reads the
 * millisecond columns, which are the authoritative ones.
 */
object VaultCsv {

    const val FILE_EXTENSION = "csv"
    const val MIME = "text/csv"
    private const val SEP = ';'

    private val COLUMNS = listOf(
        "Zeit", "Erfasst", "App", "Chat", "Absender", "Gruppe", "Gelöscht", "Bearbeitet", "Text",
        "ZeitMs", "ErfasstMs", "Paket", "ChatKey", "Id"
    )

    val HEADER: String = COLUMNS.joinToString(SEP.toString()) + "\n"

    /** One message as a CSV row. [time]/[captured] are the pre-formatted human-readable stamps. */
    fun row(m: CapturedMessage, time: String, captured: String): String = buildString {
        append(q(time)).append(SEP)
        append(q(captured)).append(SEP)
        append(q(m.appLabel)).append(SEP)
        append(q(m.conversation)).append(SEP)
        append(q(m.sender)).append(SEP)
        append(yesNo(m.isGroup)).append(SEP)
        append(yesNo(m.deletionSuspected)).append(SEP)
        append(yesNo(m.editSuperseded)).append(SEP)
        append(q(m.text)).append(SEP)
        append(m.messageTime).append(SEP)
        append(m.capturedAt).append(SEP)
        append(q(m.packageName)).append(SEP)
        append(q(m.conversationKey)).append(SEP)
        append(q(m.id)).append('\n')
    }

    /**
     * Streams rows out of [reader], calling [onMessage] for each. The header row is validated so
     * a foreign CSV fails loudly instead of importing garbage.
     *
     * @throws IllegalArgumentException on a foreign or malformed file.
     */
    fun parse(reader: Reader, onMessage: (CapturedMessage) -> Unit) {
        var isHeader = true
        var rows = 0
        forEachRow(reader) { fields ->
            // A blank trailing line is not a record.
            if (fields.all { it.isBlank() }) return@forEachRow
            if (isHeader) {
                // Strip a UTF-8 BOM a spreadsheet may have prepended before comparing.
                val head = fields.toMutableList().also { it[0] = it[0].removePrefix("\uFEFF") }
                require(head.size >= COLUMNS.size && head.take(COLUMNS.size) == COLUMNS) {
                    "Keine Kleene-Petze-CSV-Datei (unerwartete Spalten)"
                }
                isHeader = false
            } else {
                rows++
                onMessage(toMessage(fields, rows))
            }
        }
        require(!isHeader) { "Leere Datei" }
    }

    private fun toMessage(f: List<String>, row: Int): CapturedMessage {
        require(f.size >= COLUMNS.size) { "Zeile $row hat ${f.size} statt ${COLUMNS.size} Spalten" }
        fun long(i: Int) = f[i].trim().toLongOrNull()
            ?: throw IllegalArgumentException("Ungültiger Zeitstempel in Zeile $row")
        return CapturedMessage(
            id = f[13],
            packageName = f[11],
            appLabel = f[2],
            conversationKey = f[12],
            conversation = f[3],
            sender = f[4],
            isGroup = isYes(f[5]),
            text = f[8],
            messageTime = long(9),
            capturedAt = long(10),
            deletionSuspected = isYes(f[6]),
            editSuperseded = isYes(f[7])
        )
    }

    /**
     * RFC-4180 reader: fields may be quoted, quoted fields may contain the separator, `""` for a
     * literal quote, and — the reason this cannot be done line by line — newlines.
     */
    private fun forEachRow(reader: Reader, onRow: (List<String>) -> Unit) {
        val buffered = reader.buffered()
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var any = false

        fun endField() { fields.add(field.toString()); field.setLength(0) }
        fun endRow() { endField(); onRow(fields.toList()); fields.clear(); any = false }

        while (true) {
            val r = buffered.read()
            if (r < 0) break
            val c = r.toChar()
            any = true
            when {
                quoted && c == '"' -> {
                    // Doubled quote inside a quoted field is a literal quote; otherwise it ends it.
                    val next = buffered.peekChar()
                    if (next == '"') { field.append('"'); buffered.read() } else quoted = false
                }
                quoted -> field.append(c)
                c == '"' && field.isEmpty() -> quoted = true
                c == SEP -> endField()
                c == '\r' -> Unit // CRLF: the \n does the work
                c == '\n' -> endRow()
                else -> field.append(c)
            }
        }
        if (any || field.isNotEmpty() || fields.isNotEmpty()) endRow()
    }

    private fun java.io.BufferedReader.peekChar(): Char? {
        mark(1)
        val n = read()
        reset()
        return if (n < 0) null else n.toChar()
    }

    private fun yesNo(b: Boolean) = if (b) "ja" else "nein"
    private fun isYes(s: String) = s.trim().equals("ja", ignoreCase = true) || s.trim() == "1"

    private fun q(s: String): String =
        if (s.any { it == SEP || it == '"' || it == '\n' || it == '\r' })
            "\"" + s.replace("\"", "\"\"") + "\""
        else s
}
