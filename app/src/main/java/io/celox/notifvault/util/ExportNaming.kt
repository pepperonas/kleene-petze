package io.celox.notifvault.util

/**
 * File names for exports and backups. Chat titles come straight from a notification, i.e. from
 * a remote party — so everything but letters/digits (German umlauts included) is collapsed to
 * "_" before it ever reaches the filesystem: no path separators, no traversal, no leading dot,
 * bounded length. Framework-free, pinned by `ExportNamingTest`.
 */
object ExportNaming {

    const val FULL_EXPORT_BASE = "kleene-petze_export"
    private const val CHAT_FALLBACK = "kleene-petze_chat"
    private const val MAX_TITLE_CHARS = 40

    private val unsafe = Regex("[^A-Za-z0-9äöüÄÖÜß]+")

    /** Base name (without extension) for a per-chat export. */
    fun chatExportBase(title: String): String {
        val safe = title.replace(unsafe, "_").trim('_').take(MAX_TITLE_CHARS).trim('_')
        return if (safe.isEmpty()) CHAT_FALLBACK else "kleene-petze_$safe"
    }

    /** Suggested SAF file name for an encrypted vault backup, e.g. `kleene-petze-2026-07-26.kpvault`. */
    fun backupFileName(dateStamp: String): String =
        exportFileName(dateStamp, VaultFormat.ENCRYPTED)

    /** Suggested SAF file name for any export format, e.g. `kleene-petze-2026-07-30.json`. */
    fun exportFileName(dateStamp: String, format: VaultFormat): String {
        val safe = dateStamp.replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-')
        return "kleene-petze-$safe.${format.extension}"
    }
}
