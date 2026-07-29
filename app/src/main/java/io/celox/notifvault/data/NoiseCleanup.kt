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
 * same one the capture filter uses, so nothing is deleted here that would be kept on arrival.
 */
object NoiseCleanup {

    /** 1 = service/status texts, 2 = + call notifications (v1.6.3). Bump when markers are added. */
    const val VERSION = 2

    suspend fun runOnce(context: Context) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        if (settings.noiseCleanupVersion.first() >= VERSION) return
        // Claim the slot first: a racing second caller (service + UI share the process) backs off,
        // and a crash mid-delete must not turn this into a full scan on every start.
        settings.setNoiseCleanupVersion(VERSION)

        val dao = DatabaseProvider.get(ctx).messageDao()
        dao.idsAndTexts()
            .filter { isNoiseText(it.text) }
            .map { it.id }
            .chunked(BackupMerge.FLAG_CHUNK)
            .forEach { dao.deleteByIds(it) }
    }
}
