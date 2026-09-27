package io.celox.notifvault.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMotionTest {

    @Test
    fun `a child screen rises a tenth of the height`() {
        assertEquals(200, ScreenMotion.riseOffset(2000))
        assertEquals(0, ScreenMotion.riseOffset(0))
    }

    @Test
    fun `the stagger grows with the index but is capped`() {
        assertEquals(0L, ScreenMotion.staggerDelay(0))
        assertEquals(ScreenMotion.STAGGER_MS, ScreenMotion.staggerDelay(1))
        assertEquals(ScreenMotion.staggerDelay(ScreenMotion.STAGGER_CAP), ScreenMotion.staggerDelay(500))
        assertEquals(0L, ScreenMotion.staggerDelay(-3))
    }

    @Test
    fun `scales stay subtle`() {
        assertTrue(ScreenMotion.PARENT_SCALE in 0.95f..1f)
        assertTrue(ScreenMotion.ENTER_SCALE in 0.9f..1f)
    }
}
