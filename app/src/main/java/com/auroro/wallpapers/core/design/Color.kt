package com.auroro.wallpapers.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.DesignTheme
import com.auroro.wallpapers.core.data.GlassQuality

/** Shared app-chrome tokens; wallpaper artwork itself is never tinted by the theme. */
@Immutable
data class AeroColors(
    val isDark: Boolean,
    val designTheme: DesignTheme,
    val glassQuality: GlassQuality,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val glowA: Color,
    val glowB: Color,
    /** Stable under-glass tint, tuned separately from wallpaper artwork for legibility. */
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
    /** 1.0 normal; larger values make non-blurred fallback panels more opaque. */
    val opacityBoost: Float,
)

fun designColors(
    dark: Boolean,
    accent: AccentTheme,
    amoled: Boolean,
    reduceTransparency: Boolean,
    designTheme: DesignTheme = DesignTheme.MATERIAL_3,
    glassQuality: GlassQuality = GlassQuality.BALANCED,
): AeroColors {
    val (a, aLight, aDeep, green, greenDeep) = when (designTheme) {
        DesignTheme.MATERIAL_3 -> when (accent) {
            AccentTheme.AQUA -> listOf(Color(0xFF4F668B), Color(0xFFC2D4F5), Color(0xFF405C85), Color(0xFFB5CFBD), Color(0xFF3D5947))
            AccentTheme.EMERALD -> listOf(Color(0xFF496B59), Color(0xFFC0DCC8), Color(0xFF3D604B), Color(0xFFBBD3B5), Color(0xFF4A6347))
            AccentTheme.SKY -> listOf(Color(0xFF4A6B91), Color(0xFFC7DBF5), Color(0xFF3D5F87), Color(0xFFB5CFBD), Color(0xFF455F4F))
        }
        DesignTheme.FRUTIGER_AERO -> when (accent) {
            AccentTheme.AQUA -> listOf(Color(0xFF59B9D0), Color(0xFFBCECF3), Color(0xFF246C80), Color(0xFF70C89A), Color(0xFF297956))
            AccentTheme.EMERALD -> listOf(Color(0xFF45AD83), Color(0xFFB6E9D0), Color(0xFF236B4D), Color(0xFF70C89A), Color(0xFF297956))
            AccentTheme.SKY -> listOf(Color(0xFF659DCD), Color(0xFFC1DDF5), Color(0xFF315F93), Color(0xFF70C89A), Color(0xFF297956))
        }
        DesignTheme.FRUTIGER_ZEN -> when (accent) {
            AccentTheme.AQUA -> listOf(Color(0xFF5E8C7C), Color(0xFFB1D4C3), Color(0xFF365F55), Color(0xFF719B6B), Color(0xFF45694C))
            AccentTheme.EMERALD -> listOf(Color(0xFF63866A), Color(0xFFC0D9B9), Color(0xFF416349), Color(0xFF79966B), Color(0xFF4C6548))
            AccentTheme.SKY -> listOf(Color(0xFF64888E), Color(0xFFBFD9DA), Color(0xFF3F626A), Color(0xFF7B9A78), Color(0xFF4F6A50))
        }
    }

    val light = when (designTheme) {
        DesignTheme.MATERIAL_3 -> Palette(
            top = Color(0xFFF7F7FB), mid = Color(0xFFF0F1F7), bottom = Color(0xFFE8EDF6),
            glowA = Color(0xFFC8D8F2), glowB = Color(0xFFD5E2D7), glass = Color(0xFFFEFBFF),
            text = Color(0xFF1A1B20), secondary = Color(0xFF454750), tertiary = Color(0xFF626570),
            surface = Color(0xFFFEFBFF),
        )
        DesignTheme.FRUTIGER_AERO -> Palette(
            top = Color(0xFFEAF4F8), mid = Color(0xFFF3F7F3), bottom = Color(0xFFE4F0E8),
            glowA = Color(0xFF80CDE0), glowB = Color(0xFF79BC91), glass = Color(0xFFF8FCFC),
            text = Color(0xFF152D36), secondary = Color(0xFF405D66), tertiary = Color(0xFF607981),
            surface = Color(0xFFF8FCFC),
        )
        DesignTheme.FRUTIGER_ZEN -> Palette(
            top = Color(0xFFEAF3ED), mid = Color(0xFFF2F1E8), bottom = Color(0xFFE5EFEB),
            glowA = Color(0xFFA9D3C4), glowB = Color(0xFFB0C68E), glass = Color(0xFFF8FBF6),
            text = Color(0xFF273833), secondary = Color(0xFF4D6259), tertiary = Color(0xFF6E8077),
            surface = Color(0xFFF8FBF6),
        )
    }

    val darkPalette = when (designTheme) {
        DesignTheme.MATERIAL_3 -> Palette(
            top = Color(0xFF111318), mid = Color(0xFF181A20), bottom = Color(0xFF1B2027),
            glowA = Color(0xFF354563), glowB = Color(0xFF35473B), glass = Color(0xFF20232A),
            text = Color(0xFFE3E2E9), secondary = Color(0xFFC5C6D0), tertiary = Color(0xFF90919B),
            surface = Color(0xFF1C1D23),
        )
        DesignTheme.FRUTIGER_AERO -> Palette(
            top = Color(0xFF081721), mid = Color(0xFF10242E), bottom = Color(0xFF142A27),
            glowA = a, glowB = green, glass = Color(0xFF0D1C25),
            text = Color(0xFFF0F7F7), secondary = Color(0xFFB7CDD2), tertiary = Color(0xFF8FA9B0),
            surface = Color(0xFF142831),
        )
        DesignTheme.FRUTIGER_ZEN -> Palette(
            top = Color(0xFF101A18), mid = Color(0xFF1B2722), bottom = Color(0xFF222B23),
            glowA = Color(0xFF477466), glowB = Color(0xFF61784C), glass = Color(0xFF1D2924),
            text = Color(0xFFEDF4EF), secondary = Color(0xFFC1D1C7), tertiary = Color(0xFF91A49A),
            surface = Color(0xFF202D27),
        )
    }

    val base = if (dark) darkPalette else light
    val colors = if (dark && amoled) {
        base.copy(
            top = Color.Black,
            mid = Color(0xFF05070A),
            bottom = Color(0xFF070C0A),
            glass = Color(0xFF090D11),
            surface = Color(0xFF080C10),
        )
    } else base
    val boost = if (reduceTransparency) 1.42f else 1f

    return AeroColors(
        isDark = dark,
        designTheme = designTheme,
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
        accent = if (dark) aLight else aDeep,
        accentLight = aLight,
        accentDeep = aDeep,
        onAccent = if (dark) Color(0xFF061D23) else Color.White,
        emerald = if (dark) green else greenDeep,
        emeraldDeep = greenDeep,
        error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
        warning = if (dark) Color(0xFFFFCF75) else Color(0xFF7D5600),
        success = if (dark) Color(0xFF82D6A9) else Color(0xFF236D4A),
        surfaceSolid = colors.surface,
        scrim = if (dark) Color(0xC9081218) else Color(0x66061116),
        opacityBoost = boost,
    )
}

private data class Palette(
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
    designColors(
        dark = false,
        accent = AccentTheme.AQUA,
        amoled = false,
        reduceTransparency = false,
        designTheme = DesignTheme.MATERIAL_3,
    )
}
