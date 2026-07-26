package io.celox.notifvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retention deletes user data irreversibly, so both halves of the decision matter: it must
 * stay off by default, and it must not run twice in a row.
 */
class RetentionPolicyTest {

    private val day = RetentionPolicy.DAY_MS
    private val now = 1_800_000_000_000L

    @Test
    fun `retention is off unless a positive number of days is configured`() {
        assertFalse(RetentionPolicy.isDue(0, 0, now))
        assertFalse(RetentionPolicy.isDue(-1, 0, now))
    }

    @Test
    fun `a fresh install (never pruned) is due immediately`() {
        assertTrue(RetentionPolicy.isDue(30, 0, now))
    }

    @Test
    fun `a prune within the last day is skipped`() {
        assertFalse(RetentionPolicy.isDue(30, now, now))
        assertFalse(RetentionPolicy.isDue(30, now - day / 2, now))
        assertFalse(RetentionPolicy.isDue(30, now - day + 1, now))
    }

    @Test
    fun `exactly one day later it is due again`() {
        assertTrue(RetentionPolicy.isDue(30, now - day, now))
        assertTrue(RetentionPolicy.isDue(30, now - 5 * day, now))
    }

    @Test
    fun `a last-prune timestamp in the future does not stall pruning`() {
        // Clock moved backwards: without this, pruning would pause until the clock caught up.
        assertTrue(RetentionPolicy.isDue(30, now + 10 * day, now))
    }

    @Test
    fun `cutoff is exactly the configured number of days back`() {
        assertEquals(now - 30 * day, RetentionPolicy.cutoff(30, now))
        assertEquals(now - 365 * day, RetentionPolicy.cutoff(365, now))
    }

    @Test
    fun `cutoff does not overflow for the largest offered option`() {
        val cutoff = RetentionPolicy.cutoff(RetentionPolicy.OPTIONS.max(), now)
        assertTrue("cutoff must stay below now, was $cutoff", cutoff < now)
        assertTrue("cutoff must stay positive, was $cutoff", cutoff > 0)
    }

    @Test
    fun `the offered options start with keep-forever`() {
        assertEquals(0, RetentionPolicy.OPTIONS.first())
        assertEquals(RetentionPolicy.OPTIONS.sorted(), RetentionPolicy.OPTIONS)
    }
}
