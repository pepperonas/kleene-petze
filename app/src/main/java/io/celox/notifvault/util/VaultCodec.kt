package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage

/**
 * Serializes the vault for encrypted backups: a versioned header line followed by one
 * escaped-TSV row per message (all columns, lossless). Deliberately hand-rolled and
 * framework-free — no JSON parser dependency, trivially unit-testable on the JVM, and the
 * escape set (tab/newline/CR/backslash) is exactly what TSV needs.
 *
 * Restore relies on the content-hash primary key + insert-IGNORE: importing a backup into a
 * non-empty vault merges instead of duplicating.
 */
object VaultCodec {

    private const val HEADER = "KPVAULT\t1"
    private const val COLUMNS = 12

    fun encode(messages: List<CapturedMessage>): String = buildString {
        append(HEADER).append('\n')
        for (m in messages) {
            append(esc(m.id)).append('\t')
            append(esc(m.packageName)).append('\t')
            append(esc(m.appLabel)).append('\t')
            append(esc(m.conversationKey)).append('\t')
            append(esc(m.conversation)).append('\t')
            append(esc(m.sender)).append('\t')
            append(if (m.isGroup) '1' else '0').append('\t')
            append(esc(m.text)).append('\t')
            append(m.messageTime).append('\t')
            append(m.capturedAt).append('\t')
            append(if (m.deletionSuspected) '1' else '0').append('\t')
            append(if (m.editSuperseded) '1' else '0').append('\n')
        }
    }

    /** @throws IllegalArgumentException on a wrong header or malformed row. */
    fun decode(payload: String): List<CapturedMessage> {
        val lines = payload.split('\n')
        require(lines.isNotEmpty() && lines[0] == HEADER) { "Kein Kleene-Petze-Backup (Header fehlt)" }
        val out = ArrayList<CapturedMessage>(lines.size - 1)
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) continue // trailing newline
            val f = line.split('\t')
            require(f.size == COLUMNS) { "Ungültige Backup-Zeile ${i + 1} (${f.size} Spalten)" }
            out += CapturedMessage(
                id = unesc(f[0]),
                packageName = unesc(f[1]),
                appLabel = unesc(f[2]),
                conversationKey = unesc(f[3]),
                conversation = unesc(f[4]),
                sender = unesc(f[5]),
                isGroup = f[6] == "1",
                text = unesc(f[7]),
                messageTime = f[8].toLongOrNull()
                    ?: throw IllegalArgumentException("Ungültige Zeit in Zeile ${i + 1}"),
                capturedAt = f[9].toLongOrNull()
                    ?: throw IllegalArgumentException("Ungültige Zeit in Zeile ${i + 1}"),
                deletionSuspected = f[10] == "1",
                editSuperseded = f[11] == "1"
            )
        }
        return out
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }

    private fun unesc(s: String): String {
        if ('\\' !in s) return s
        return buildString(s.length) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        '\\' -> append('\\')
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(s[i + 1]) // unknown escape: keep the char
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
