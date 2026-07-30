package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * In-memory serialization for the *per-chat* export (a single conversation is small enough to
 * build as a string; the full-archive export streams instead — see [VaultTransfer]).
 *
 * Both formats are the same ones the importer reads, so a shared chat export can be pulled back
 * into the vault later. Earlier revisions had their own CSV/JSON shapes here: the CSV dropped
 * `id`/`packageName`/`conversationKey` and replaced newlines inside a message with spaces, which
 * made it a dead end *and* silently rewrote the archived text.
 */
object ExportUtils {

    // SimpleDateFormat is not thread-safe — create one per export run instead of sharing.
    private fun dateFmt() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)

    fun toCsv(messages: List<CapturedMessage>): String {
        val fmt = dateFmt()
        return buildString {
            append(VaultCsv.HEADER)
            for (m in messages) {
                append(VaultCsv.row(m, fmt.format(Date(m.messageTime)), fmt.format(Date(m.capturedAt))))
            }
        }
    }

    fun toJson(messages: List<CapturedMessage>): String = buildString {
        append(VaultJson.header(messages.size, isoNow()))
        messages.forEachIndexed { i, m -> append(VaultJson.record(m, first = i == 0)) }
        append(VaultJson.footer())
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(System.currentTimeMillis()))
}
