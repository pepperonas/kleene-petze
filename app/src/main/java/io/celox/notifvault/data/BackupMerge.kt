package io.celox.notifvault.data

/**
 * Turns the result of a bulk `insertAll` (Room returns −1 for every row dropped by
 * `OnConflictStrategy.IGNORE`) into a restore plan: how many rows were genuinely imported and
 * which already-present rows still need their deleted/edited verdict re-applied.
 *
 * Why that last part matters: a backup row that already exists locally is *ignored* on insert,
 * so a flag that only the backup carries would be silently lost — the flags are exactly the
 * evidence this app exists for. Pure logic, unit-tested by `BackupMergeTest`.
 */
object BackupMerge {

    /** SQLite caps bound variables per statement (999 classic limit) — chunk the UPDATEs. */
    const val FLAG_CHUNK = 500

    data class Plan(
        val imported: Int,
        val alreadyPresent: Int,
        val deletedIds: List<String>,
        val editedIds: List<String>
    )

    /**
     * @param messages the decoded backup, in insert order.
     * @param insertedRowIds row ids from `insertAll` (−1 = skipped by IGNORE). A short list is
     *   treated as "skipped" for the missing tail rather than crashing a restore mid-way.
     */
    fun plan(messages: List<CapturedMessage>, insertedRowIds: List<Long>): Plan {
        var imported = 0
        val deletedIds = mutableListOf<String>()
        val editedIds = mutableListOf<String>()
        for ((i, m) in messages.withIndex()) {
            if (insertedRowIds.getOrElse(i) { -1L } != -1L) {
                imported++
            } else {
                if (m.deletionSuspected) deletedIds += m.id
                if (m.editSuperseded) editedIds += m.id
            }
        }
        return Plan(imported, messages.size - imported, deletedIds, editedIds)
    }
}
