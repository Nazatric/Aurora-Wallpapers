package com.auroro.wallpapers.core.data.download

import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

data class ImageInfo(val mime: String, val width: Int, val height: Int)

/** Confirms a downloaded file really is a decodable image, using only a bounds decode (no pixel allocation). */
object ImageValidator {
    val SUPPORTED_MIME = setOf("image/jpeg", "image/png", "image/webp")

    fun inspect(file: File): ImageInfo? {
        if (!file.isFile || file.length() < 16) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            file.inputStream().use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: IOException) {
            return null
        }
        val mime = opts.outMimeType?.lowercase() ?: return null
        if (mime !in SUPPORTED_MIME || opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return ImageInfo(mime, opts.outWidth, opts.outHeight)
    }
}

object Downscaler {
    /**
     * For "Fit to screen": returns the target size when the long edge exceeds [maxLongEdge], otherwise null
     * (no downscale needed). Aspect ratio is preserved.
     */
    fun plan(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int>? {
        val long = max(width, height)
        if (maxLongEdge <= 0 || long <= maxLongEdge) return null
        val scale = maxLongEdge.toFloat() / long
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }
}
