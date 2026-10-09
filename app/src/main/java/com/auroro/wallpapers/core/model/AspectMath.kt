package com.auroro.wallpapers.core.model

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Aspect-ratio rules.
 *
 * Ratios are always `width / height` of the **actual image dimensions** reported by the provider.
 * An image matches a target when its *relative* difference is within a tolerance:
 *
 *     |ratio - target| / target <= tolerance
 *
 * - The tolerance is configurable in Settings (default [DEFAULT_TOLERANCE] = 3 %).
 * - Orientation is part of the ratio: 9:16 is portrait (0.5625), 16:9 is landscape (1.7778).
 *   A 16:9 filter never matches a 9:16 image, and vice versa.
 * - Custom ratios are taken literally as `width:height` (so "9:20" is a tall portrait ratio and "20:9" a wide one).
 * - Example: 2160 × 3840 → 0.5625 → matches 9:16 exactly.
 */
object AspectMath {
    const val DEFAULT_TOLERANCE = 0.03f
    /** Maximum difference between the edges as a fraction of the longer edge for square classification. */
    const val SQUARE_TOLERANCE = 0.05f
    val TOLERANCE_CHOICES = listOf(0.01f, 0.02f, 0.03f, 0.05f)

    fun ratio(width: Int, height: Int): Float = if (height <= 0 || width <= 0) 0f else width.toFloat() / height

    /** Classifies actual dimensions; square tolerance is symmetric when width and height are swapped. */
    fun classifyOrientation(width: Int, height: Int): Orientation? {
        val value = ratio(width, height)
        if (!value.isFinite() || value <= 0f) return null
        val longerEdge = max(width, height)
        val relativeEdgeDifference = abs(width.toLong() - height.toLong()).toFloat() / longerEdge
        return when {
            relativeEdgeDifference <= SQUARE_TOLERANCE -> Orientation.SQUARE
            width < height -> Orientation.PORTRAIT
            else -> Orientation.LANDSCAPE
        }
    }

    fun matches(width: Int, height: Int, targetRatio: Float, tolerance: Float = DEFAULT_TOLERANCE): Boolean {
        if (targetRatio <= 0f) return false
        val r = ratio(width, height)
        if (r <= 0f) return false
        return abs(r - targetRatio) / targetRatio <= tolerance
    }

    /** Human label such as "16:9" for a known ratio, otherwise a reduced fraction or decimal. */
    fun describe(width: Int, height: Int, tolerance: Float = 0.015f): String {
        if (width <= 0 || height <= 0) return "—"
        val r = ratio(width, height)
        AspectPreset.entries.firstOrNull { matches(width, height, it.ratio, tolerance) }?.let { return it.label }
        listOf("16:10" to 1.6f, "3:2" to 1.5f, "5:4" to 1.25f, "32:9" to 3.5556f, "10:16" to 0.625f, "2:3" to 0.6667f, "3:4" to 0.75f)
            .firstOrNull { matches(width, height, it.second, tolerance) }?.let { return it.first }
        val g = gcd(width, height)
        val rw = width / g
        val rh = height / g
        return if (rw <= 32 && rh <= 32) "$rw:$rh" else String.format(java.util.Locale.US, "%.2f:1", r)
    }

    /** Parses user input such as `3:2`, `19.5:9` or `1.5` into a ratio, or null if invalid. */
    fun parseCustom(input: String): CustomRatio? {
        val text = input.trim().replace('x', ':').replace('X', ':').replace('×', ':').replace('/', ':')
        if (text.isEmpty()) return null
        val parts = text.split(':')
        return when (parts.size) {
            1 -> parts[0].toFloatOrNull()?.takeIf(::valid)?.let { CustomRatio(it, 1f) }
            2 -> {
                val w = parts[0].trim().toFloatOrNull()
                val h = parts[1].trim().toFloatOrNull()
                if (w != null && h != null && valid(w) && valid(h)) CustomRatio(w, h) else null
            }
            else -> null
        }
    }

    private fun valid(v: Float) = v.isFinite() && v > 0f && v <= 100f

    private fun gcd(a: Int, b: Int): Int {
        var x = max(a, b)
        var y = min(a, b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x.coerceAtLeast(1)
    }
}

@Immutable
data class CustomRatio(val w: Float, val h: Float) {
    val ratio: Float get() = w / h
}

enum class AspectPreset(val label: String, val w: Float, val h: Float) {
    R9_16("9:16", 9f, 16f),
    R16_9("16:9", 16f, 9f),
    R4_3("4:3", 4f, 3f),
    R1_1("1:1", 1f, 1f),
    R21_9("21:9", 21f, 9f),
    R9_19_5("9:19.5", 9f, 19.5f);

    val ratio: Float get() = w / h
}

sealed interface AspectFilter {
    data object Any : AspectFilter

    @Immutable
    data class Preset(val preset: AspectPreset) : AspectFilter

    @Immutable
    data class Custom(val ratio: CustomRatio) : AspectFilter

    val targetRatio: Float?
        get() = when (this) {
            Any -> null
            is Preset -> preset.ratio
            is Custom -> ratio.ratio
        }

    val label: String
        get() = when (this) {
            Any -> "Any"
            is Preset -> preset.label
            is Custom -> "${trim(ratio.w)}:${trim(ratio.h)}"
        }

    private fun trim(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else v.toString()
}

/**
 * Resolution rules. Presets compare the **short edge** and **long edge** separately, so a portrait
 * 2160 × 3840 wallpaper counts as 4K exactly like a landscape 3840 × 2160 one.
 */
enum class ResolutionPreset(val label: String, val shortEdge: Int, val longEdge: Int) {
    P720("720p+", 720, 1280),
    P1080("1080p+", 1080, 1920),
    P1440("1440p+", 1440, 2560),
    K4("4K+", 2160, 3840),
    K8("8K+", 4320, 7680),
}

sealed interface ResolutionFilter {
    data object Any : ResolutionFilter

    @Immutable
    data class Preset(val preset: ResolutionPreset) : ResolutionFilter

    /** Minimum short/long edges; portrait and landscape images are treated identically. */
    @Immutable
    data class Custom(val minWidth: Int, val minHeight: Int) : ResolutionFilter {
        val shortEdge: Int get() = min(minWidth, minHeight)
        val longEdge: Int get() = max(minWidth, minHeight)
    }

    val label: String
        get() = when (this) {
            Any -> "Any"
            is Preset -> preset.label
            is Custom -> "≥ ${min(minWidth, minHeight)} × ${max(minWidth, minHeight)} px"
        }

    fun matches(width: Int, height: Int): Boolean = when (this) {
        Any -> true
        is Preset -> width > 0 && height > 0 &&
            min(width, height) >= preset.shortEdge && max(width, height) >= preset.longEdge
        is Custom -> width > 0 && height > 0 &&
            min(width, height) >= shortEdge && max(width, height) >= longEdge
    }
}
