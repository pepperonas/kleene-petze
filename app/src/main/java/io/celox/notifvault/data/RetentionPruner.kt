package io.celox.notifvault.data

import android.content.Context
import kotlinx.coroutines.flow.first

/**
 * Applies the optional retention policy: deletes messages older than the configured number
 * of days. Both entry points (service onCreate and app start) funnel through [pruneIfDue],
 * which no-ops unless retention is enabled AND the last prune is more than a day old — so
 * prunes never run twice in quick succession.
 */
object RetentionPruner {

    suspend fun pruneIfDue(context: Context) {
        val settings = SettingsStore(context.applicationContext)
        val days = settings.retentionDays.first()
        val now = System.currentTimeMillis()
        if (!RetentionPolicy.isDue(days, settings.lastPruneAt.first(), now)) return
        // Claim the slot before deleting so a racing second caller backs off immediately.
        settings.setLastPruneAt(now)
        DatabaseProvider.get(context.applicationContext).messageDao()
            .pruneOlderThan(RetentionPolicy.cutoff(days, now))
    }
}
