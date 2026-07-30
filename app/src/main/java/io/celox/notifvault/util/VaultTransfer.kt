package io.celox.notifvault.util

import io.celox.notifvault.data.CapturedMessage
import io.celox.notifvault.data.MessageDao
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Export and import of the whole archive, streamed.
 *
 * Everything here works block by block: the exporter pulls the archive out of the database in
 * chunks and writes each record straight to the target stream, and the importer parses one record
 * at a time and flushes it to the database in batches. Nothing ever holds the whole vault — the
 * previous implementation kept the full list, the serialised string, the gzip buffer *and* the
 * ciphertext alive simultaneously, which is a plausible OOM on a large archive.
 *
 * The one exception is the encrypted format on the way *in*: see [VaultBackup.encryptingWriter]
 * for why decryption stays a single `doFinal`.
 */
object VaultTransfer {

    /** Rows per database page and per insert batch. */
    const val CHUNK = 500

    /** What an import file contains, read before anything is written to the vault. */
    data class Preview(
        val format: VaultFormat,
        val count: Int,
        val oldest: Long?,
        val newest: Long?,
        val exported: String?
    )

    /** Outcome of an applied import. */
    data class Result(val imported: Int, val alreadyPresent: Int)

    // ---- export ----

    /**
     * Writes the whole archive to [out] in [format]. [passphrase] is required for
     * [VaultFormat.ENCRYPTED] and ignored otherwise.
     *
     * @return number of exported messages.
     */
    suspend fun export(
        dao: MessageDao,
        out: OutputStream,
        format: VaultFormat,
        passphrase: CharArray? = null,
        total: Int
    ): Int {
        val writer = when (format) {
            VaultFormat.ENCRYPTED -> VaultBackup.encryptingWriter(
                out, requireNotNull(passphrase) { "Passphrase fehlt" }
            )
            else -> out.writer(Charsets.UTF_8)
        }

        var written = 0
        writer.buffered().use { w ->
            val human = humanFmt()
            when (format) {
                VaultFormat.ENCRYPTED -> w.write(VaultCodec.HEADER_LINE)
                VaultFormat.JSON -> w.write(VaultJson.header(total, isoNow()))
                VaultFormat.CSV -> w.write(VaultCsv.HEADER)
            }
            eachChunk(dao) { m ->
                when (format) {
                    VaultFormat.ENCRYPTED -> w.write(VaultCodec.row(m))
                    VaultFormat.JSON -> w.write(VaultJson.record(m, first = written == 0))
                    VaultFormat.CSV -> w.write(
                        VaultCsv.row(m, human.format(Date(m.messageTime)), human.format(Date(m.capturedAt)))
                    )
                }
                written++
            }
            if (format == VaultFormat.JSON) w.write(VaultJson.footer())
        }
        return written
    }

    private suspend fun eachChunk(dao: MessageDao, onMessage: (CapturedMessage) -> Unit) {
        var offset = 0
        while (true) {
            val chunk = dao.exportChunk(CHUNK, offset)
            if (chunk.isEmpty()) return
            chunk.forEach(onMessage)
            if (chunk.size < CHUNK) return
            offset += chunk.size
        }
    }

    // ---- import ----

    /**
     * Reads [open]'s stream once *without* touching the database, to tell the user what the file
     * holds. The stream is opened again for [apply] — two sequential reads of a local file are
     * cheaper than keeping every parsed message around just to show a count.
     *
     * For the encrypted format the decrypted messages are returned in [decrypted] so the
     * passphrase only has to be entered (and the work only done) once.
     */
    fun preview(
        format: VaultFormat,
        open: () -> InputStream,
        passphrase: CharArray?
    ): Pair<Preview, List<CapturedMessage>?> {
        if (format == VaultFormat.ENCRYPTED) {
            val bytes = open().use { it.readBytes() }
            val messages = VaultCodec.decode(
                VaultBackup.decrypt(bytes, requireNotNull(passphrase) { "Passphrase fehlt" })
            )
            return Preview(
                format, messages.size,
                messages.minOfOrNull { it.messageTime },
                messages.maxOfOrNull { it.messageTime },
                null
            ) to messages
        }

        var count = 0
        var oldest: Long? = null
        var newest: Long? = null
        var exported: String? = null
        if (format == VaultFormat.JSON) {
            exported = open().use { VaultJson.readEnvelope(it.reader(Charsets.UTF_8)).exported }
        }
        open().use { stream ->
            val reader = stream.reader(Charsets.UTF_8)
            parseInto(format, reader) { m ->
                count++
                if (oldest == null || m.messageTime < oldest!!) oldest = m.messageTime
                if (newest == null || m.messageTime > newest!!) newest = m.messageTime
            }
        }
        return Preview(format, count, oldest, newest, exported) to null
    }

    /**
     * Applies an import in batches. [decrypted] short-circuits the re-read for the encrypted
     * format (its messages already came out of [preview]).
     *
     * The merge is non-destructive by construction: ids are content hashes and inserts use
     * IGNORE, so re-importing the same file changes nothing.
     */
    suspend fun apply(
        format: VaultFormat,
        open: () -> InputStream,
        decrypted: List<CapturedMessage>?,
        merge: suspend (List<CapturedMessage>) -> Result
    ): Result {
        var imported = 0
        var present = 0
        fun add(r: Result) { imported += r.imported; present += r.alreadyPresent }

        if (decrypted != null) {
            for (batch in decrypted.chunked(CHUNK)) add(merge(batch))
            return Result(imported, present)
        }

        val batch = ArrayList<CapturedMessage>(CHUNK)
        open().use { stream ->
            parseInto(format, stream.reader(Charsets.UTF_8)) { m ->
                batch.add(m)
                if (batch.size >= CHUNK) {
                    // The parser hands records back through a plain callback, so the suspending
                    // write is bridged here. Collecting the batches instead and merging them
                    // afterwards would put the entire file back in memory — the very thing this
                    // class exists to avoid. apply() runs on an IO dispatcher, so blocking this
                    // thread for the insert is exactly the sequential behaviour we want.
                    kotlinx.coroutines.runBlocking { add(merge(batch)) }
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) add(merge(batch))
        return Result(imported, present)
    }

    private fun parseInto(format: VaultFormat, reader: Reader, onMessage: (CapturedMessage) -> Unit) {
        when (format) {
            VaultFormat.JSON -> VaultJson.parse(reader, onMessage)
            VaultFormat.CSV -> VaultCsv.parse(reader, onMessage)
            VaultFormat.ENCRYPTED -> throw IllegalStateException("Verschlüsselt: siehe preview()")
        }
    }

    // ---- helpers ----

    private fun humanFmt() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(System.currentTimeMillis()))
}
