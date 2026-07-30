package io.celox.notifvault.data

import android.content.Context
import io.celox.notifvault.notif.isNoiseText
import kotlinx.coroutines.flow.first

/**
 * Removes the notifications that were captured *before* the noise filter recognised them —
 * WhatsApp's permanent "Überprüfe auf neue Nachrichten", the backup/restore progress texts and
 * the separate missed-call chat — which otherwise sit in the vault forever.
 *
 * Runs once per [VERSION] (stored in [SettingsStore]): bumping it re-runs the purge on existing
 * installs, which is how newly added markers reach rows captured earlier. The phrase list is the
 * same one the capture filter uses, so nothing is deleted here that would be kept on arrival —
 * and like the filter it looks at the chat title as well as the message text.
 */
object NoiseCleanup {

    /**
     * 1 = service/status texts, 2 = + call notifications (v1.6.3), 3 = + match the chat title,
     * not just the message text (v1.6.4 — a missed call stores its wording as the title, so v2
     * matched nothing and left the call chats in place). Bump whenever the matching widens.
     */
    const val VERSION = 3

    suspend fun runOnce(context: Context) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        if (settings.noiseCleanupVersion.first() >= VERSION) return

        val dao = DatabaseProvider.get(ctx).messageDao()
        dao.idsAndTexts()
            // Title *or* text: the missed-call row carries the phrase in the title and the
            // contact name in the text, so checking only the text finds nothing.
            .filter { isNoiseText(it.text) || isNoiseText(it.conversation) }
            .map { it.id }
            .chunked(BackupMerge.FLAG_CHUNK)
            .forEach { dao.deleteByIds(it) }

        // Only claim the version once the pass actually completed — the caller wraps this in
        // runCatching, so claiming up front would turn one transient failure into "never again".
        settings.setNoiseCleanupVersion(VERSION)
    }
}
