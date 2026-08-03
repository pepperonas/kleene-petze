package io.celox.notifvault.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogPolicyTest {

    // ---- shouldRequestRebind ----

    @Test
    fun `rebinds when access is granted but the listener is gone`() {
        assertTrue(WatchdogPolicy.shouldRequestRebind(accessGranted = true, listenerConnected = false))
    }

    @Test
    fun `never rebinds without notification access`() {
        // requestRebind fails for a listener the user has not granted access to.
        assertFalse(WatchdogPolicy.shouldRequestRebind(accessGranted = false, listenerConnected = false))
        assertFalse(WatchdogPolicy.shouldRequestRebind(accessGranted = false, listenerConnected = true))
    }

    @Test
    fun `leaves a working capture alone`() {
        assertFalse(WatchdogPolicy.shouldRequestRebind(accessGranted = true, listenerConnected = true))
    }

    // ---- shouldSchedule ----

    @Test
    fun `schedules only with autostart and access`() {
        assertTrue(WatchdogPolicy.shouldSchedule(autoStart = true, accessGranted = true))
        assertFalse(WatchdogPolicy.shouldSchedule(autoStart = false, accessGranted = true))
        assertFalse(WatchdogPolicy.shouldSchedule(autoStart = true, accessGranted = false))
        assertFalse(WatchdogPolicy.shouldSchedule(autoStart = false, accessGranted = false))
    }

    // ---- isWatchdogOverdue ----

    private val interval = WatchdogPolicy.INTERVAL_MS

    @Test
    fun `not overdue before three missed runs`() {
        val now = 10L * interval
        assertFalse(WatchdogPolicy.isWatchdogOverdue(now - interval, now))
        assertFalse(WatchdogPolicy.isWatchdogOverdue(now - 2 * interval, now))
        // Exactly at the boundary is still fine — only beyond it counts.
        assertFalse(WatchdogPolicy.isWatchdogOverdue(now - 3 * interval, now))
    }

    @Test
    fun `overdue once three intervals have passed`() {
        val now = 1000L * interval
        assertTrue(WatchdogPolicy.isWatchdogOverdue(now - 3 * interval - 1, now))
        assertTrue(WatchdogPolicy.isWatchdogOverdue(now - 100 * interval, now))
    }

    @Test
    fun `never run is not yet a symptom`() {
        assertFalse(WatchdogPolicy.isWatchdogOverdue(0L, 10L * interval))
    }

    @Test
    fun `a clock moved backwards does not read as overdue`() {
        // now < lastRunAt: the age is negative, so this reports "just ran" instead of stalling
        // the warning forever (mirrors RetentionPolicy's handling of a backwards clock).
        assertFalse(WatchdogPolicy.isWatchdogOverdue(lastRunAt = 100L * interval, now = interval))
    }

    @Test
    fun `interval is JobScheduler's minimum period`() {
        // Pinned: asking for a shorter period silently gets clamped, which would make the
        // overdue threshold above mean something different than it reads.
        org.junit.Assert.assertEquals(15 * 60 * 1000L, WatchdogPolicy.INTERVAL_MS)
    }
}
