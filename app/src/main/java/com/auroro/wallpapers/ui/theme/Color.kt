package com.auroro.wallpapers.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.DesignTheme

/** Dark-first palette matching the 2026 rebuild mockups. Light + dynamic color land in a later slice. */
val AuroroBackground = Color(0xFF0A0A0F)
val AuroroSurface = Color(0xFF15151D)
val AuroroSurfaceBright = Color(0xFF1E1E29)
val AuroroOutline = Color(0xFF2A2A38)
val AuroroAccent = Color(0xFF8B7CF6)
val AuroroAccentDim = Color(0xFF6C5CE0)
val AuroroOnAccent = Color(0xFFFFFFFF)
val AuroroTextPrimary = Color(0xFFF2F2F7)
val AuroroTextSecondary = Color(0xFFA2A2B4)
val AuroroTextTertiary = Color(0xFF6E6E80)
val AuroroScrim = Color(0xCC05050A)

/** Full resolved colour set for one configuration of the app. */
data class AuroroPalette(
    val background: Color,
    val surface: Color,
    val surfaceBright: Color,
    val outline: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
)

/**
 * Resolves the palette from real settings: light/dark, AMOLED, contrast boost, accent hue and
 * design language. The default combination (Frutiger Aero + Aurora accent, dark) reproduces
 * the launch look exactly so theming never regresses the reviewed design.
 */
fun auroroPalette(
    dark: Boolean,
    amoled: Boolean,
    increaseContrast: Boolean,
    accent: AccentTheme,
    design: DesignTheme,
): AuroroPalette {
    val accentColor = when (design) {
        DesignTheme.MATERIAL_3 -> Color(0xFF8B7CF6)
        DesignTheme.FRUTIGER_ZEN -> Color(0xFF9CB8A4)
        DesignTheme.FRUTIGER_AERO -> when (accent) {
            AccentTheme.AQUA -> Color(0xFF8B7CF6) // launch "Aurora" hue
            AccentTheme.EMERALD -> Color(0xFF34D399)
            AccentTheme.SKY -> Color(0xFF38BDF8)
        }
    }
    val accentDim = accentDimOf(accentColor, dark)
    return if (dark) {
        val bg = if (amoled) Color.Black else AuroroBackground
        val surface = if (amoled) Color(0xFF0B0B0E) else AuroroSurface
        val surfaceBright = if (amoled) Color(0xFF141417) else AuroroSurfaceBright
        AuroroPalette(
            background = bg,
            surface = surface,
            surfaceBright = surfaceBright,
            outline = if (increaseContrast) Color(0xFF4A4A5E) else AuroroOutline,
            accent = accentColor,
            accentDim = accentDim,
            onAccent = Color.White,
            textPrimary = if (increaseContrast) Color.White else AuroroTextPrimary,
            textSecondary = if (increaseContrast) Color(0xFFD6D6E2) else AuroroTextSecondary,
            textTertiary = if (increaseContrast) AuroroTextSecondary else AuroroTextTertiary,
            scrim = AuroroScrim,
        )
    } else {
        AuroroPalette(
            background = Color(0xFFF4F4F9),
            surface = Color(0xFFFFFFFF),
            surfaceBright = Color(0xFFE9E9F2),
            outline = Color(0xFFC9C9D8),
            accent = accentDim,
            accentDim = accentColor,
            onAccent = Color.White,
            textPrimary = if (increaseContrast) Color.Black else Color(0xFF17171F),
            textSecondary = if (increaseContrast) Color(0xFF2A2A35) else Color(0xFF4A4A58),
            textTertiary = if (increaseContrast) Color(0xFF3A3A46) else Color(0xFF77778A),
            scrim = Color(0x66FFFFFF),
        )
    }
}

private fun accentDimOf(accent: Color, dark: Boolean): Color =
    if (dark) accent.copy(alpha = 0.72f) else accent.copy(alpha = 0.85f)

val LocalAuroroPalette = staticCompositionLocalOf {
    auroroPalette(
        dark = true,
        amoled = false,
        increaseContrast = false,
        accent = AccentTheme.AQUA,
        design = DesignTheme.FRUTIGER_AERO,
    )
}

/**
 * Composition-local colour accessors. Every screen reads these instead of raw vals so a
 * settings change (theme, accent, AMOLED, contrast) re-colours the whole app live.
 */
object AuroroPal {
    val background: Color @Composable get() = LocalAuroroPalette.current.background
    val surface: Color @Composable get() = LocalAuroroPalette.current.surface
    val surfaceBright: Color @Composable get() = LocalAuroroPalette.current.surfaceBright
    val outline: Color @Composable get() = LocalAuroroPalette.current.outline
    val accent: Color @Composable get() = LocalAuroroPalette.current.accent
    val accentDim: Color @Composable get() = LocalAuroroPalette.current.accentDim
    val onAccent: Color @Composable get() = LocalAuroroPalette.current.onAccent
    val textPrimary: Color @Composable get() = LocalAuroroPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalAuroroPalette.current.textSecondary
    val textTertiary: Color @Composable get() = LocalAuroroPalette.current.textTertiary
    val scrim: Color @Composable get() = LocalAuroroPalette.current.scrim
}
