package io.celox.notifvault.util

/**
 * The three shapes an exported vault can have on disk. Encryption is a property of the format,
 * not a separate setting: [ENCRYPTED] is the passphrase-protected container, the other two are
 * plain text — that is what makes encryption optional without a second code path.
 */
enum class VaultFormat(val extension: String, val mime: String, val encrypted: Boolean) {
    ENCRYPTED(VaultBackup.FILE_EXTENSION, "application/octet-stream", true),
    JSON(VaultJson.FILE_EXTENSION, VaultJson.MIME, false),
    CSV(VaultCsv.FILE_EXTENSION, VaultCsv.MIME, false);

    companion object {

        /** How many leading bytes [detect] needs. */
        const val SNIFF_BYTES = 64

        /**
         * Identifies a file from its first bytes so the user only ever needs one "restore"
         * button — picking the wrong format by hand is a pointless way to fail an import.
         *
         * The encrypted container starts with its magic; JSON with `{`; anything else is treated
         * as CSV and validated against the expected header by [VaultCsv.parse], which is where a
         * genuinely foreign file gets rejected with a readable message.
         */
        fun detect(prefix: ByteArray): VaultFormat {
            if (prefix.size >= 4 &&
                prefix[0] == 'K'.code.toByte() && prefix[1] == 'P'.code.toByte() &&
                prefix[2] == 'V'.code.toByte() && prefix[3] == '1'.code.toByte()
            ) return ENCRYPTED

            // Skip a UTF-8 BOM and leading whitespace before looking at the first real character.
            var i = 0
            if (prefix.size >= 3 && prefix[0] == 0xEF.toByte() &&
                prefix[1] == 0xBB.toByte() && prefix[2] == 0xBF.toByte()
            ) i = 3
            while (i < prefix.size && prefix[i].toInt().toChar().isWhitespace()) i++
            return if (i < prefix.size && prefix[i].toInt().toChar() == '{') JSON else CSV
        }
    }
}
