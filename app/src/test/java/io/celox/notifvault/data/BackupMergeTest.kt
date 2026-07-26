package io.celox.notifvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restore must merge, never duplicate — and must not lose the deleted/edited verdicts of rows
 * that already exist locally (insert-IGNORE leaves those untouched).
 */
class BackupMergeTest {

    private fun msg(id: String, deleted: Boolean = false, edited: Boolean = false) = CapturedMessage(
        id = id,
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        conversationKey = "key-1",
        conversation = "Alice",
        sender = "Alice",
        isGroup = false,
        text = "Hallo",
        messageTime = 1_700_000_000_000,
        capturedAt = 1_700_000_000_123,
        deletionSuspected = deleted,
        editSuperseded = edited
    )

    @Test
    fun `an empty vault imports everything`() {
        val backup = listOf(msg("a"), msg("b"), msg("c"))
        val plan = BackupMerge.plan(backup, listOf(1L, 2L, 3L))
        assertEquals(3, plan.imported)
        assertEquals(0, plan.alreadyPresent)
        assertTrue(plan.deletedIds.isEmpty())
        assertTrue(plan.editedIds.isEmpty())
    }

    @Test
    fun `restoring the same backup twice imports nothing new (idempotent)`() {
        val backup = listOf(msg("a"), msg("b"))
        val plan = BackupMerge.plan(backup, listOf(-1L, -1L))
        assertEquals(0, plan.imported)
        assertEquals(2, plan.alreadyPresent)
    }

    @Test
    fun `flags of already-present rows are re-applied, new rows carry theirs already`() {
        val backup = listOf(
            msg("present-deleted", deleted = true),
            msg("present-edited", edited = true),
            msg("present-both", deleted = true, edited = true),
            msg("present-plain"),
            msg("new-deleted", deleted = true)
        )
        val plan = BackupMerge.plan(backup, listOf(-1L, -1L, -1L, -1L, 42L))

        assertEquals(1, plan.imported)
        assertEquals(4, plan.alreadyPresent)
        assertEquals(listOf("present-deleted", "present-both"), plan.deletedIds)
        assertEquals(listOf("present-edited", "present-both"), plan.editedIds)
    }

    @Test
    fun `a partially merged backup counts each row by its own row id`() {
        val backup = listOf(msg("a"), msg("b", deleted = true), msg("c"), msg("d", edited = true))
        val plan = BackupMerge.plan(backup, listOf(7L, -1L, 8L, -1L))
        assertEquals(2, plan.imported)
        assertEquals(2, plan.alreadyPresent)
        assertEquals(listOf("b"), plan.deletedIds)
        assertEquals(listOf("d"), plan.editedIds)
    }

    @Test
    fun `an empty backup is a no-op`() {
        val plan = BackupMerge.plan(emptyList(), emptyList())
        assertEquals(0, plan.imported)
        assertEquals(0, plan.alreadyPresent)
    }

    @Test
    fun `a short row-id list is treated as skipped instead of crashing the restore`() {
        val backup = listOf(msg("a"), msg("b", deleted = true))
        val plan = BackupMerge.plan(backup, listOf(1L))
        assertEquals(1, plan.imported)
        assertEquals(1, plan.alreadyPresent)
        assertEquals(listOf("b"), plan.deletedIds)
    }

    @Test
    fun `flag updates are chunked below the SQLite bound-variable limit`() {
        val backup = (1..1200).map { msg("id-$it", deleted = true) }
        val plan = BackupMerge.plan(backup, List(1200) { -1L })
        assertEquals(1200, plan.deletedIds.size)
        val chunks = plan.deletedIds.chunked(BackupMerge.FLAG_CHUNK)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.size <= 999 })
    }
}
