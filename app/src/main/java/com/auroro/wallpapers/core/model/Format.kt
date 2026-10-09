package com.auroro.wallpapers.core.model

import java.util.Locale

object Format {
    fun fileSize(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "—"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun dimensions(width: Int, height: Int): String = if (width > 0 && height > 0) "$width × $height" else "Unknown size"

    fun mimeLabel(mime: String?): String = when (mime?.lowercase(Locale.US)) {
        "image/jpeg", "image/jpg" -> "JPEG"
        "image/png" -> "PNG"
        "image/webp" -> "WebP"
        "image/gif" -> "GIF"
        null -> "—"
        else -> mime.substringAfter('/').uppercase(Locale.US)
    }

    fun extensionForMime(mime: String?): String = when (mime?.lowercase(Locale.US)) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    fun compactCount(n: Int?): String = when {
        n == null -> "—"
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
