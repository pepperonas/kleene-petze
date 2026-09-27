package io.celox.notifvault.update

import io.celox.notifvault.update.UpdatePolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun `nothing happens without the opt-in`() {
        assertEquals(Outcome.DISABLED, UpdatePolicy.decide(false, "v9.9.9", "1.9.1", null))
        assertNull(UpdatePolicy.bannerVersion(false, "v9.9.9", "1.9.1"))
        // The privacy guarantee: no work is enqueued at app start unless switched on.
        assertFalse(UpdatePolicy.scheduleOnStart(false))
    }

    @Test
    fun `a newer release is announced once`() {
        assertEquals(Outcome.NOTIFY, UpdatePolicy.decide(true, "v1.10.0", "1.9.1", null))
        assertEquals(Outcome.NOTIFY, UpdatePolicy.decide(true, "v1.10.0", "1.9.1", "v1.9.5"))
        assertEquals(Outcome.ALREADY_NOTIFIED, UpdatePolicy.decide(true, "v1.10.0", "1.9.1", "v1.10.0"))
    }

    @Test
    fun `the installed or an older version is up to date`() {
        assertEquals(Outcome.UP_TO_DATE, UpdatePolicy.decide(true, "v1.9.1", "1.9.1", null))
        assertEquals(Outcome.UP_TO_DATE, UpdatePolicy.decide(true, "v1.8.0", "1.9.1", null))
    }

    @Test
    fun `no answer or garbage is a failure, not an update`() {
        assertEquals(Outcome.FAILED, UpdatePolicy.decide(true, null, "1.9.1", null))
        assertEquals(Outcome.FAILED, UpdatePolicy.decide(true, "Not Found", "1.9.1", null))
    }

    @Test
    fun `the banner offers only a newer version`() {
        assertEquals("1.10.0", UpdatePolicy.bannerVersion(true, "v1.10.0", "1.9.1"))
        assertNull(UpdatePolicy.bannerVersion(true, "v1.9.1", "1.9.1"))
        assertNull(UpdatePolicy.bannerVersion(true, null, "1.9.1"))
    }
}
