package com.auroro.wallpapers.core.data.download

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ApplyTarget(val label: String) {
    HOME("Home screen"),
    LOCK("Lock screen"),
    BOTH("Both"),
}

/** Visible part of the source image, as fractions (0..1) of its width/height. */
data class NormalizedCrop(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

object CropMath {
    /**
     * Which part of the image is visible when it's shown "cover"-fitted in a [viewW]×[viewH] frame,
     * then zoomed by [userScale] (>= 1) and panned by ([offsetX], [offsetY]) pixels (translation of the image
     * relative to a centered position, in view pixels).
     */
    fun visibleRect(imageW: Int, imageH: Int, viewW: Float, viewH: Float, userScale: Float, offsetX: Float, offsetY: Float): NormalizedCrop {
        val base = max(viewW / imageW, viewH / imageH)
        val s = base * userScale.coerceAtLeast(1f)
        val (cx, cy) = clampOffset(imageW, imageH, viewW, viewH, s, offsetX, offsetY)
        val centerX = imageW / 2f - cx / s
        val centerY = imageH / 2f - cy / s
        val halfW = viewW / (2f * s)
        val halfH = viewH / (2f * s)
        return NormalizedCrop(
            left = ((centerX - halfW) / imageW).coerceIn(0f, 1f),
            top = ((centerY - halfH) / imageH).coerceIn(0f, 1f),
            right = ((centerX + halfW) / imageW).coerceIn(0f, 1f),
            bottom = ((centerY + halfH) / imageH).coerceIn(0f, 1f),
        )
    }

    /** Keeps the scaled image covering the whole frame. */
    fun clampOffset(imageW: Int, imageH: Int, viewW: Float, viewH: Float, absoluteScale: Float, ox: Float, oy: Float): Pair<Float, Float> {
        val maxX = max(0f, (imageW * absoluteScale - viewW) / 2f)
        val maxY = max(0f, (imageH * absoluteScale - viewH) / 2f)
        return ox.coerceIn(-maxX, maxX) to oy.coerceIn(-maxY, maxY)
    }

    /** Centered crop with the aspect ratio of the screen. */
    fun centerCrop(imageW: Int, imageH: Int, screenW: Int, screenH: Int): NormalizedCrop =
        visibleRect(imageW, imageH, screenW.toFloat(), screenH.toFloat(), 1f, 0f, 0f)
}

sealed interface ApplyResult {
    data object Success : ApplyResult
    data class Failure(val message: String) : ApplyResult
}

data class WallpaperCapabilities(val supported: Boolean, val allowed: Boolean) {
    val usable get() = supported && allowed
    val unavailableReason: String?
        get() = when {
            !supported -> "This device doesn't support changing the wallpaper."
            !allowed -> "Changing the wallpaper is blocked by device policy."
            else -> null
        }
}

/** Applies an already-saved image through Android's [WallpaperManager], honouring the user's crop. */
class WallpaperApplier(private val context: Context) {

    fun capabilities(): WallpaperCapabilities {
        val wm = WallpaperManager.getInstance(context)
        return WallpaperCapabilities(supported = wm.isWallpaperSupported, allowed = wm.isSetWallpaperAllowed)
    }

    @Suppress("DEPRECATION")
    suspend fun apply(uri: String, target: ApplyTarget, crop: NormalizedCrop? = null): ApplyResult = withContext(Dispatchers.IO) {
        val caps = capabilities()
        caps.unavailableReason?.let { return@withContext ApplyResult.Failure(it) }
        var bitmap: Bitmap? = null
        try {
            val (iw, ih) = bounds(uri) ?: return@withContext ApplyResult.Failure("The saved image can't be read. It may have been moved or deleted.")
            val dm = context.resources.displayMetrics
            val screenW = min(dm.widthPixels, dm.heightPixels)
            val screenH = max(dm.widthPixels, dm.heightPixels)
            val c = crop ?: CropMath.centerCrop(iw, ih, screenW, screenH)
            val region = Rect(
                (c.left * iw).roundToInt().coerceIn(0, iw - 1),
                (c.top * ih).roundToInt().coerceIn(0, ih - 1),
                (c.right * iw).roundToInt().coerceIn(1, iw),
                (c.bottom * ih).roundToInt().coerceIn(1, ih),
            )
            if (region.width() < 1 || region.height() < 1) return@withContext ApplyResult.Failure("The selected crop is empty.")
            // Decode only the chosen region, sampled so the result is at most ~2x the screen width.
            val desiredW = min(region.width(), screenW * 2)
            var sample = 1
            while (region.width() / (sample * 2) >= desiredW) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val stream = LocalFiles.open(context, uri) ?: return@withContext ApplyResult.Failure("The saved image can't be opened.")
            bitmap = stream.use { s ->
                val decoder = BitmapRegionDecoder.newInstance(s, false) ?: throw IOException("Unsupported image format")
                try {
                    decoder.decodeRegion(region, opts)
                } finally {
                    decoder.recycle()
                }
            } ?: return@withContext ApplyResult.Failure("The image couldn't be decoded.")

            val wm = WallpaperManager.getInstance(context)
            val flags = when (target) {
                ApplyTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                ApplyTarget.LOCK -> WallpaperManager.FLAG_LOCK
                ApplyTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            val id = wm.setBitmap(bitmap, null, true, flags)
            if (id == 0) {
                ApplyResult.Failure("Android didn't accept the wallpaper for ${target.label.lowercase()}. Your device may not allow this target.")
            } else {
                ApplyResult.Success
            }
        } catch (e: SecurityException) {
            ApplyResult.Failure("Android denied permission to change the wallpaper.")
        } catch (e: OutOfMemoryError) {
            ApplyResult.Failure("Not enough memory to prepare this wallpaper. Try a smaller crop.")
        } catch (e: IOException) {
            ApplyResult.Failure("Couldn't read the image: ${e.message ?: "I/O error"}.")
        } catch (e: RuntimeException) {
            ApplyResult.Failure("Android couldn't apply the wallpaper: ${e.message ?: "unknown error"}.")
        } finally {
            bitmap?.recycle()
        }
    }

    private fun bounds(uri: String): Pair<Int, Int>? {
        val stream = LocalFiles.open(context, uri) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream.use { BitmapFactory.decodeStream(it, null, o) }
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }
}
