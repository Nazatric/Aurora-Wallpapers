package com.auroro.wallpapers.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.core.data.AccentTheme

/** Curated Aero tokens: calm ocean depths, fresh leaf light and readable translucent controls. */
@Immutable
data class AeroColors(
    val isDark: Boolean,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val glowA: Color,
    val glowB: Color,
    /** A stable tint beneath glass so controls stay legible over varied artwork. */
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
    /** 1.0 normal; larger values increase panel opacity when transparency is reduced. */
    val opacityBoost: Float,
)

fun aeroColors(dark: Boolean, accent: AccentTheme, amoled: Boolean, reduceTransparency: Boolean): AeroColors {
    val (a, aLight, aDeep) = when (accent) {
        AccentTheme.AQUA -> Triple(Color(0xFF59B9D0), Color(0xFFBCECF3), Color(0xFF246C80))
        AccentTheme.EMERALD -> Triple(Color(0xFF45AD83), Color(0xFFB6E9D0), Color(0xFF236B4D))
        AccentTheme.SKY -> Triple(Color(0xFF659DCD), Color(0xFFC1DDF5), Color(0xFF315F93))
    }
    val boost = if (reduceTransparency) 1.42f else 1f
    return if (dark) {
        AeroColors(
            isDark = true,
            backdropTop = if (amoled) Color.Black else Color(0xFF081721),
            backdropMid = if (amoled) Color(0xFF050B10) else Color(0xFF10242E),
            backdropBottom = if (amoled) Color(0xFF07110F) else Color(0xFF142A27),
            glowA = a,
            glowB = Color(0xFF70C89A),
            glassTint = if (amoled) Color(0xFF090F13) else Color(0xFF0D1C25),
            glassHighlight = Color.White,
            glassRimLight = Color(0xFFDDF8FA),
            glassRimDark = aLight,
            textPrimary = Color(0xFFF0F7F7),
            textSecondary = Color(0xFFB7CDD2),
            textTertiary = Color(0xFF8FA9B0),
            accent = a,
            accentLight = aLight,
            accentDeep = aDeep,
            onAccent = Color(0xFF051D25),
            emerald = Color(0xFF70C89A),
            emeraldDeep = Color(0xFF297956),
            error = Color(0xFFFF9292),
            warning = Color(0xFFFFCF75),
            success = Color(0xFF82D6A9),
            surfaceSolid = if (amoled) Color(0xFF080D11) else Color(0xFF142831),
            scrim = Color(0xC9081218),
            opacityBoost = boost,
        )
    } else {
        AeroColors(
            isDark = false,
            backdropTop = Color(0xFFEAF4F8),
            backdropMid = Color(0xFFF3F7F3),
            backdropBottom = Color(0xFFE4F0E8),
            glowA = Color(0xFF80CDE0),
            glowB = Color(0xFF79BC91),
            glassTint = Color(0xFFF8FCFC),
            glassHighlight = Color.White,
            glassRimLight = Color.White,
            glassRimDark = aDeep,
            textPrimary = Color(0xFF152D36),
            textSecondary = Color(0xFF405D66),
            textTertiary = Color(0xFF607981),
            accent = aDeep,
            accentLight = a,
            accentDeep = aDeep,
            onAccent = Color.White,
            emerald = Color(0xFF348A60),
            emeraldDeep = Color(0xFF246846),
            error = Color(0xFFB3261E),
            warning = Color(0xFF7D5600),
            success = Color(0xFF236D4A),
            surfaceSolid = Color(0xFFF8FCFC),
            scrim = Color(0x66061116),
            opacityBoost = boost,
        )
    }
}

val LocalAero = staticCompositionLocalOf { aeroColors(true, AccentTheme.AQUA, amoled = false, reduceTransparency = false) }
