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

    private const val DAY_MS = 86_400_000L

    suspend fun pruneIfDue(context: Context) {
        val settings = SettingsStore(context.applicationContext)
        val days = settings.retentionDays.first()
        if (days <= 0) return
        val now = System.currentTimeMillis()
        if (now - settings.lastPruneAt.first() < DAY_MS) return
        // Claim the slot before deleting so a racing second caller backs off immediately.
        settings.setLastPruneAt(now)
        DatabaseProvider.get(context.applicationContext).messageDao()
            .pruneOlderThan(now - days * DAY_MS)
    }
}
