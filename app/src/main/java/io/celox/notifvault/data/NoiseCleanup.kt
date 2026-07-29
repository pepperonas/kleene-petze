package io.celox.notifvault.data

import android.content.Context
import io.celox.notifvault.notif.isServiceNoiseText
import kotlinx.coroutines.flow.first

/**
 * Removes the service/status notifications that were captured *before* the noise filter existed
 * — WhatsApp's permanent "Überprüfe auf neue Nachrichten" and the backup/restore progress texts,
 * which otherwise sit in the vault forever as a junk chat named after the app.
 *
 * Runs exactly once per install (flag in [SettingsStore]); the phrase list is the same one the
 * capture filter uses, so nothing gets deleted here that would be kept on arrival today.
 */
object NoiseCleanup {

    suspend fun runOnce(context: Context) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        if (settings.noiseCleanupDone.first()) return
        // Claim the slot first: a racing second caller (service + UI share the process) backs off,
        // and a crash mid-delete must not turn this into a full scan on every start.
        settings.setNoiseCleanupDone()

        val dao = DatabaseProvider.get(ctx).messageDao()
        dao.idsAndTexts()
            .filter { isServiceNoiseText(it.text) }
            .map { it.id }
            .chunked(BackupMerge.FLAG_CHUNK)
            .forEach { dao.deleteByIds(it) }
    }
}
