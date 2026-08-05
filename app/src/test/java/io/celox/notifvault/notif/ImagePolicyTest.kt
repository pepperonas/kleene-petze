package io.celox.notifvault.notif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePolicyTest {

    // ---- sampleSize ----

    @Test
    fun `no subsampling for images that already fit`() {
        assertEquals(1, ImagePolicy.sampleSize(800, 600))
        assertEquals(1, ImagePolicy.sampleSize(1280, 960))
    }

    @Test
    fun `subsamples large images by powers of two`() {
        // 4000x3000 → /2 = 2000x1500 (still ≥ 1280 on the long edge? 2000/2=1000 < 1280 → stop)
        assertEquals(2, ImagePolicy.sampleSize(4000, 3000))
        assertEquals(4, ImagePolicy.sampleSize(8000, 6000))
    }

    @Test
    fun `subsampling never drops below the target edge`() {
        // The decoded size must stay >= maxEdge so the exact scaling step has pixels to work
        // with — otherwise subsampling alone would silently degrade the image.
        for (w in listOf(1300, 2000, 2561, 5000, 9999)) {
            val sample = ImagePolicy.sampleSize(w, w / 2)
            assertTrue("w=$w sample=$sample", w / sample >= ImagePolicy.MAX_EDGE)
        }
    }

    @Test
    fun `degenerate sizes do not divide by zero`() {
        assertEquals(1, ImagePolicy.sampleSize(0, 0))
        assertEquals(1, ImagePolicy.sampleSize(-5, 100))
        assertEquals(1, ImagePolicy.sampleSize(100, 100, maxEdge = 0))
    }

    // ---- scaledSize ----

    @Test
    fun `small images are left alone`() {
        assertEquals(640 to 480, ImagePolicy.scaledSize(640, 480))
    }

    @Test
    fun `scaling preserves the aspect ratio on the long edge`() {
        assertEquals(1280 to 960, ImagePolicy.scaledSize(4000, 3000))
        // Portrait: the long edge is the height.
        assertEquals(960 to 1280, ImagePolicy.scaledSize(3000, 4000))
    }

    @Test
    fun `an extreme panorama keeps at least one pixel`() {
        val (w, h) = ImagePolicy.scaledSize(10_000, 3)
        assertEquals(ImagePolicy.MAX_EDGE, w)
        assertTrue("height must not collapse to 0, was $h", h >= 1)
    }

    // ---- isWorthStoring / isSupported ----

    @Test
    fun `icons and avatars are not pictures`() {
        assertFalse(ImagePolicy.isWorthStoring(64, 64))
        assertFalse(ImagePolicy.isWorthStoring(512, 48))
        assertTrue(ImagePolicy.isWorthStoring(96, 96))
        assertTrue(ImagePolicy.isWorthStoring(1600, 1200))
    }

    @Test
    fun `mime type gate`() {
        assertTrue(ImagePolicy.isSupported("image/jpeg"))
        assertTrue(ImagePolicy.isSupported("IMAGE/PNG"))
        assertTrue(ImagePolicy.isSupported("image/webp"))
        // WhatsApp does not always declare one — the decoder decides instead of us guessing.
        assertTrue(ImagePolicy.isSupported(null))
        assertTrue(ImagePolicy.isSupported("  "))
        assertFalse(ImagePolicy.isSupported("video/mp4"))
        assertFalse(ImagePolicy.isSupported("audio/ogg"))
        assertFalse(ImagePolicy.isSupported("application/pdf"))
    }
}
