package io.celox.notifvault.notif

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What of an image found in a notification is worth keeping, and at what size — the decisions
 * on their own, free of framework classes, so they can be unit-tested.
 *
 * Important to know what this is *not*: a notification never carries the original photo, only
 * the preview the messenger built for the shade. Storing it is therefore a copy of that preview,
 * not of the file in WhatsApp's media folder (which Scoped Storage keeps out of reach).
 */
object ImagePolicy {

    /** Longest edge of the stored copy. Notification previews are rarely larger than this. */
    const val MAX_EDGE = 1280

    /**
     * Below this, it is an icon or a contact avatar rather than a picture someone sent. Only a
     * guard: the sources we read are the message's own image, never the notification's icons.
     */
    const val MIN_EDGE = 96

    /** Hard cap per image — the vault is a text archive that keeps pictures, not a gallery. */
    const val MAX_BYTES = 512 * 1024

    /** Re-compression quality, stepped down while an image is still over [MAX_BYTES]. */
    val QUALITY_STEPS = listOf(85, 70, 55)

    const val MIME = "image/jpeg"

    /**
     * `BitmapFactory.inSampleSize` (a power of two) so a large image is never fully decoded
     * into memory first. Errs towards the larger result: the exact size follows in [scaledSize].
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Target size with the aspect ratio preserved; unchanged when it already fits. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val longest = max(width, height)
        if (longest <= maxEdge) return width to height
        val factor = maxEdge.toDouble() / longest
        return max(1, (width * factor).roundToInt()) to max(1, (height * factor).roundToInt())
    }

    /** A picture, not an icon. */
    fun isWorthStoring(width: Int, height: Int): Boolean =
        width >= MIN_EDGE && height >= MIN_EDGE

    /**
     * Unknown ([mimeType] null) counts as supported: WhatsApp does not always declare one, and
     * the decoder is the real arbiter — it returns nothing for something that is not an image.
     */
    fun isSupported(mimeType: String?): Boolean {
        val m = mimeType?.trim()?.lowercase() ?: return true
        return m.isEmpty() || m.startsWith("image/")
    }
}
