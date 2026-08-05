package io.celox.notifvault.notif

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.ByteArrayOutputStream

/** Where an image referenced by a notification lives until it is decoded. */
sealed interface ImageSource {
    /** `MessagingStyle.Message.setData(...)` — the per-message inline image. */
    data class FromUri(val uri: Uri) : ImageSource

    /** `BigPictureStyle` on older builds: the bitmap sits right in the extras. */
    data class FromBitmap(val bitmap: Bitmap) : ImageSource

    /** `BigPictureStyle` since Android 12, where the picture is an `Icon`. */
    data class FromIcon(val icon: Icon) : ImageSource
}

/** A decoded, re-compressed image ready to be stored. */
class EncodedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String
)

/**
 * Reads the image a messenger put into a notification and re-compresses it for storage.
 *
 * Two things make this possible at all:
 *  - a notification listener is granted `FLAG_GRANT_READ_URI_PERMISSION` for **every** Uri on a
 *    notification, so `content://com.whatsapp…` is readable — but **only while the notification is
 *    live**; the grant is revoked when it is cancelled. The capture service therefore decodes on
 *    arrival, straight out of its queue, and never stores a Uri to open later.
 *  - what a notification carries is the *preview* the messenger built for the shade, not the
 *    original file. This is a copy of that preview, and nothing here can change that.
 */
object NotificationImages {

    // Notification.EXTRA_PICTURE_ICON is API 31; the literal avoids an inlined-constant warning
    // while still reading the extra on the devices that set it.
    private const val EXTRA_PICTURE_ICON = "android.pictureIcon"

    /** @return null when the source is unreadable, too small, or not an image at all. */
    fun encode(context: Context, source: ImageSource): EncodedImage? = runCatching {
        val bitmap = when (source) {
            is ImageSource.FromUri -> decodeUri(context, source.uri)
            is ImageSource.FromBitmap -> source.bitmap
            is ImageSource.FromIcon -> toBitmap(source.icon.loadDrawable(context))
        }
        bitmap?.let { compress(it) }
    }.getOrNull()

    /** The picture of a `BigPictureStyle` notification, in either of its two representations. */
    fun bigPicture(extras: Bundle?): ImageSource? {
        val e = extras ?: return null
        parcelable(e, Notification.EXTRA_PICTURE, Bitmap::class.java)
            ?.let { return ImageSource.FromBitmap(it) }
        parcelable(e, EXTRA_PICTURE_ICON, Icon::class.java)
            ?.let { return ImageSource.FromIcon(it) }
        return null
    }

    private fun decodeUri(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        // Bounds first: never inflate a full-size image just to find out it is an avatar.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (!ImagePolicy.isWorthStoring(bounds.outWidth, bounds.outHeight)) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = ImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun compress(source: Bitmap): EncodedImage? {
        // A hardware bitmap has no pixels in our address space: neither scaling nor compressing
        // it works until it is copied back into software memory.
        val safe = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        } ?: return null
        if (!ImagePolicy.isWorthStoring(safe.width, safe.height)) return null

        val (w, h) = ImagePolicy.scaledSize(safe.width, safe.height)
        val scaled = if (w == safe.width && h == safe.height) safe
        else Bitmap.createScaledBitmap(safe, w, h, true)

        for (quality in ImagePolicy.QUALITY_STEPS) {
            val out = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
            if (out.size() <= ImagePolicy.MAX_BYTES) {
                return EncodedImage(out.toByteArray(), scaled.width, scaled.height, ImagePolicy.MIME)
            }
        }
        // Still over the cap at the lowest quality: drop it rather than let one picture bloat
        // the vault. At 1280 px this does not happen with photographic content.
        return null
    }

    private fun toBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun <T : Any> parcelable(extras: Bundle, key: String, type: Class<T>): T? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(key, type)
        } else {
            @Suppress("DEPRECATION")
            val value = extras.getParcelable<Parcelable>(key)
            if (type.isInstance(value)) type.cast(value) else null
        }
    }.getOrNull()
}
