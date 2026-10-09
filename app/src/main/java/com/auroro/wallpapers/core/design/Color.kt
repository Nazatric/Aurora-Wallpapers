package com.auroro.wallpapers.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.core.data.AccentTheme

/**
 * Design tokens. The look is "Frutiger Aero": deep ocean blues fading into turquoise, glass panels with a
 * thin luminous rim and a soft specular highlight, aqua/emerald accents. [isDark] false = "Light sky" variant.
 */
@Immutable
data class AeroColors(
    val isDark: Boolean,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val glowA: Color,
    val glowB: Color,
    /** Tint under glass so text stays legible regardless of what's behind. */
    val glassTint: Color,
    val glassHighlight: Color,
    val glassRimLight: Color,
    val glassRimDark: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentLight: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val emerald: Color,
    val emeraldDeep: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val surfaceSolid: Color,
    val scrim: Color,
    /** 1.0 normal; larger = more opaque glass (used by "Reduce transparency"). */
    val opacityBoost: Float,
)

private val DeepNavy = Color(0xFF031225)

fun aeroColors(dark: Boolean, accent: AccentTheme, amoled: Boolean, reduceTransparency: Boolean): AeroColors {
    val (a, aLight, aDeep) = when (accent) {
        AccentTheme.AQUA -> Triple(Color(0xFF35D3F0), Color(0xFF9DEEFF), Color(0xFF0B8FB8))
        AccentTheme.EMERALD -> Triple(Color(0xFF2EDC96), Color(0xFFA6F5D3), Color(0xFF0E9F66))
        AccentTheme.SKY -> Triple(Color(0xFF5BB6FF), Color(0xFFB3DDFF), Color(0xFF2A7DE1))
    }
    val boost = if (reduceTransparency) 1.9f else 1f
    return if (dark) {
        AeroColors(
            isDark = true,
            backdropTop = if (amoled) Color.Black else Color(0xFF04152B),
            backdropMid = if (amoled) Color(0xFF020A12) else Color(0xFF06355E),
            backdropBottom = if (amoled) Color(0xFF031A1E) else Color(0xFF0A6670),
            glowA = a.copy(alpha = if (amoled) 0.10f else 0.22f),
            glowB = Color(0xFF2EDC96).copy(alpha = if (amoled) 0.06f else 0.14f),
            glassTint = if (amoled) Color(0xFF02101A) else DeepNavy,
            glassHighlight = Color.White,
            glassRimLight = Color.White.copy(alpha = 0.55f),
            glassRimDark = aLight.copy(alpha = 0.22f),
            textPrimary = Color(0xFFEAF8FF),
            textSecondary = Color(0xFFA9CFE0),
            textTertiary = Color(0xFF7FA8BC),
            accent = a,
            accentLight = aLight,
            accentDeep = aDeep,
            onAccent = Color(0xFF02222E),
            emerald = Color(0xFF2EDC96),
            emeraldDeep = Color(0xFF0E9F66),
            error = Color(0xFFFF8A8A),
            warning = Color(0xFFFFC857),
            success = Color(0xFF5CE6A8),
            surfaceSolid = if (amoled) Color(0xFF050E14) else Color(0xFF082A4A),
            scrim = Color(0xAA000A14),
            opacityBoost = boost,
        )
    } else {
        AeroColors(
            isDark = false,
            backdropTop = Color(0xFFB9E8FF),
            backdropMid = Color(0xFFE6F7FF),
            backdropBottom = Color(0xFFCDF3E2),
            glowA = Color.White.copy(alpha = 0.7f),
            glowB = Color(0xFF2EDC96).copy(alpha = 0.16f),
            glassTint = Color.White,
            glassHighlight = Color.White,
            glassRimLight = Color.White.copy(alpha = 0.95f),
            glassRimDark = aDeep.copy(alpha = 0.35f),
            textPrimary = Color(0xFF08233B),
            textSecondary = Color(0xFF2F5670),
            textTertiary = Color(0xFF4F7388),
            accent = aDeep,
            accentLight = a,
            accentDeep = Color(0xFF075F80),
            onAccent = Color.White,
            emerald = Color(0xFF0E9F66),
            emeraldDeep = Color(0xFF07754B),
            error = Color(0xFFB3261E),
            warning = Color(0xFF8A5A00),
            success = Color(0xFF0B7A4B),
            surfaceSolid = Color(0xFFF2FBFF),
            scrim = Color(0x66000000),
            opacityBoost = boost,
        )
    }
}

val LocalAero = staticCompositionLocalOf { aeroColors(true, AccentTheme.AQUA, amoled = false, reduceTransparency = false) }
