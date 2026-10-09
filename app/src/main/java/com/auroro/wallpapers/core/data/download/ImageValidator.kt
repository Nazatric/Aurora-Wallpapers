package com.auroro.wallpapers.core.data.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import kotlin.math.max

data class ImageInfo(val mime: String, val width: Int, val height: Int)

/**
 * Confirms a saved payload is a decodable JPEG, PNG or WebP. It first reads bounds, then performs a
 * sampled decode so truncated/corrupt payloads are rejected without allocating a full-size bitmap.
 * The original file is never rewritten or downscaled.
 */
object ImageValidator {
    val SUPPORTED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    private const val MAX_DIMENSION = 32_000
    private const val VALIDATION_LONG_EDGE = 256

    fun inspect(file: File): ImageInfo? {
        if (!file.isFile || file.length() < 16) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (_: IOException) {
            return null
        }
        val mime = bounds.outMimeType?.lowercase() ?: return null
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (mime !in SUPPORTED_MIME || width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) return null

        var sample = 1
        while (max(width, height) / (sample * 2) >= VALIDATION_LONG_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = try {
            file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null

        val decodes = decoded.width > 0 && decoded.height > 0
        decoded.recycle()
        if (!decodes) return null
        return ImageInfo(mime, width, height)
    }
}
