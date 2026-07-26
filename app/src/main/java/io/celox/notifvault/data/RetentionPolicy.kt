package io.celox.notifvault.data

/**
 * The decision half of the retention feature, free of Context/DataStore so it can be
 * unit-tested: *may* we prune right now, and what is the cutoff? [RetentionPruner] does the
 * I/O around it.
 */
object RetentionPolicy {

    const val DAY_MS = 86_400_000L

    /** At most one prune per day — both entry points (service start, app start) share this. */
    const val MIN_INTERVAL_MS = DAY_MS

    /** The values offered in Settings; 0 = keep forever (default for an archive app). */
    val OPTIONS = listOf(0, 30, 90, 180, 365)

    /**
     * True when retention is enabled and the last prune is at least a day ago. A `lastPruneAt`
     * in the future (user moved the clock back) counts as due — otherwise pruning could stall
     * until the clock caught up again.
     */
    fun isDue(retentionDays: Int, lastPruneAt: Long, now: Long): Boolean {
        if (retentionDays <= 0) return false
        val elapsed = now - lastPruneAt
        return elapsed >= MIN_INTERVAL_MS || elapsed < 0
    }

    /** Messages with `messageTime` below this are dropped. */
    fun cutoff(retentionDays: Int, now: Long): Long = now - retentionDays.toLong() * DAY_MS
}
