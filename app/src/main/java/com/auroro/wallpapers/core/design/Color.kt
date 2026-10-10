package com.auroro.wallpapers.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.AppearancePreset
import com.auroro.wallpapers.core.data.GlassQuality

/** Shared appearance tokens for app chrome; wallpaper artwork itself is never tinted by the theme. */
@Immutable
data class AeroColors(
    val isDark: Boolean,
    val appearance: AppearancePreset,
    val glassQuality: GlassQuality,
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
    /** 1.0 normal; larger values increase fallback panel opacity when transparency is reduced. */
    val opacityBoost: Float,
)

fun aeroColors(
    dark: Boolean,
    accent: AccentTheme,
    amoled: Boolean,
    reduceTransparency: Boolean,
    appearance: AppearancePreset = AppearancePreset.FRUTIGER_AERO,
    glassQuality: GlassQuality = GlassQuality.BALANCED,
): AeroColors {
    val (a, aLight, aDeep, green, greenDeep) = when (appearance) {
        AppearancePreset.FRUTIGER_FLOWER -> listOf(
            Color(0xFFB84F8C), Color(0xFFEAA9CF), Color(0xFF813C68), Color(0xFF4C9A6C), Color(0xFF2E6848),
        )
        AppearancePreset.FRUTIGER_ZEN -> listOf(
            Color(0xFF5E8C7C), Color(0xFFB1D4C3), Color(0xFF365F55), Color(0xFF719B6B), Color(0xFF45694C),
        )
        AppearancePreset.DARK_AERO -> listOf(
            Color(0xFF49C6D9), Color(0xFFB2F1F2), Color(0xFF226B83), Color(0xFF58BE9C), Color(0xFF216D59),
        )
        AppearancePreset.AQUA_DAY -> listOf(
            Color(0xFF438BAA), Color(0xFFB6E5F0), Color(0xFF275E7A), Color(0xFF4D9D77), Color(0xFF2E6B50),
        )
        AppearancePreset.FRUTIGER_AERO, AppearancePreset.SYSTEM -> when (accent) {
            AccentTheme.AQUA -> listOf(Color(0xFF59B9D0), Color(0xFFBCECF3), Color(0xFF246C80), Color(0xFF70C89A), Color(0xFF297956))
            AccentTheme.EMERALD -> listOf(Color(0xFF45AD83), Color(0xFFB6E9D0), Color(0xFF236B4D), Color(0xFF70C89A), Color(0xFF297956))
            AccentTheme.SKY -> listOf(Color(0xFF659DCD), Color(0xFFC1DDF5), Color(0xFF315F93), Color(0xFF70C89A), Color(0xFF297956))
        }
    }
    val colors = if (dark) {
        when (appearance) {
            AppearancePreset.DARK_AERO -> DarkPalette(
                top = if (amoled) Color.Black else Color(0xFF06151E),
                mid = if (amoled) Color(0xFF050C12) else Color(0xFF0B2631),
                bottom = if (amoled) Color(0xFF050C0A) else Color(0xFF10302D),
                glowA = Color(0xFF36C4DC), glowB = Color(0xFF5AC79E),
                glass = if (amoled) Color(0xFF090F13) else Color(0xFF0A202A),
                text = Color(0xFFF0FAFB), secondary = Color(0xFFB8D3D9), tertiary = Color(0xFF90ADB5),
                surface = if (amoled) Color(0xFF080D11) else Color(0xFF122C35),
            )
            else -> DarkPalette(
                top = if (amoled) Color.Black else Color(0xFF081721),
                mid = if (amoled) Color(0xFF050B10) else Color(0xFF10242E),
                bottom = if (amoled) Color(0xFF07110F) else Color(0xFF142A27),
                glowA = a, glowB = green,
                glass = if (amoled) Color(0xFF090F13) else Color(0xFF0D1C25),
                text = Color(0xFFF0F7F7), secondary = Color(0xFFB7CDD2), tertiary = Color(0xFF8FA9B0),
                surface = if (amoled) Color(0xFF080D11) else Color(0xFF142831),
            )
        }
    } else {
        when (appearance) {
            AppearancePreset.FRUTIGER_FLOWER -> DarkPalette(
                top = Color(0xFFFFF2F9), mid = Color(0xFFF9F2FC), bottom = Color(0xFFEEF7F0),
                glowA = Color(0xFFEAA9CF), glowB = Color(0xFF9CCFA8), glass = Color(0xFFFFFAFD),
                text = Color(0xFF352B37), secondary = Color(0xFF625365), tertiary = Color(0xFF7B6A7D),
                surface = Color(0xFFFFFAFD),
            )
            AppearancePreset.FRUTIGER_ZEN -> DarkPalette(
                top = Color(0xFFEAF3ED), mid = Color(0xFFF2F1E8), bottom = Color(0xFFE5EFEB),
                glowA = Color(0xFFA9D3C4), glowB = Color(0xFFB0C68E), glass = Color(0xFFF8FBF6),
                text = Color(0xFF273833), secondary = Color(0xFF4D6259), tertiary = Color(0xFF6E8077),
                surface = Color(0xFFF8FBF6),
            )
            AppearancePreset.AQUA_DAY -> DarkPalette(
                top = Color(0xFFE6F5FA), mid = Color(0xFFF4FAFC), bottom = Color(0xFFE7F5F0),
                glowA = Color(0xFF85CEE0), glowB = Color(0xFF8CC79D), glass = Color(0xFFFBFEFF),
                text = Color(0xFF16323D), secondary = Color(0xFF46616B), tertiary = Color(0xFF647E87),
                surface = Color(0xFFFBFEFF),
            )
            else -> DarkPalette(
                top = Color(0xFFEAF4F8), mid = Color(0xFFF3F7F3), bottom = Color(0xFFE4F0E8),
                glowA = Color(0xFF80CDE0), glowB = Color(0xFF79BC91), glass = Color(0xFFF8FCFC),
                text = Color(0xFF152D36), secondary = Color(0xFF405D66), tertiary = Color(0xFF607981),
                surface = Color(0xFFF8FCFC),
            )
        }
    }
    val boost = if (reduceTransparency) 1.42f else 1f
    return AeroColors(
        isDark = dark,
        appearance = appearance,
        glassQuality = glassQuality,
        backdropTop = colors.top,
        backdropMid = colors.mid,
        backdropBottom = colors.bottom,
        glowA = colors.glowA,
        glowB = colors.glowB,
        glassTint = colors.glass,
        glassHighlight = Color.White,
        glassRimLight = if (dark) Color(0xFFDDF8FA) else Color.White,
        glassRimDark = if (dark) aLight else aDeep,
        textPrimary = colors.text,
        textSecondary = colors.secondary,
        textTertiary = colors.tertiary,
        accent = if (dark) a else aDeep,
        accentLight = aLight,
        accentDeep = aDeep,
        onAccent = if (dark) Color(0xFF051D25) else Color.White,
        emerald = green,
        emeraldDeep = greenDeep,
        error = if (dark) Color(0xFFFF9292) else Color(0xFFB3261E),
        warning = if (dark) Color(0xFFFFCF75) else Color(0xFF7D5600),
        success = if (dark) Color(0xFF82D6A9) else Color(0xFF236D4A),
        surfaceSolid = colors.surface,
        scrim = if (dark) Color(0xC9081218) else Color(0x66061116),
        opacityBoost = boost,
    )
}

private data class DarkPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glowA: Color,
    val glowB: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
)

val LocalAero = staticCompositionLocalOf {
    aeroColors(true, AccentTheme.AQUA, amoled = false, reduceTransparency = false)
}
