package com.auroro.wallpapers.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.DesignTheme
import com.auroro.wallpapers.core.data.ThemeMode

/**
 * Theme entry point: resolves the palette from real [AppSettings] (dark/light, AMOLED,
 * contrast, accent, design language, font scale) and provides it through
 * [LocalAuroroPalette] so every screen recolours live. The [Surface] wrapper also fixes
 * Compose's default black content colour for text drawn outside cards.
 */
@Composable
fun AuroroTheme(settings: AppSettings? = null, content: @Composable () -> Unit) {
    val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = remember(settings, dark) {
        auroroPalette(
            dark = dark,
            amoled = settings?.amoled == true,
            increaseContrast = settings?.increaseContrast == true,
            accent = settings?.accent ?: com.auroro.wallpapers.core.data.AccentTheme.AQUA,
            design = settings?.designTheme ?: com.auroro.wallpapers.core.data.DesignTheme.LIQUID_GLASS,
        )
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentDim,
            onPrimaryContainer = palette.onAccent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceBright,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
            outlineVariant = palette.outline,
            scrim = palette.scrim,
            error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
            onError = androidx.compose.ui.graphics.Color(0xFF690005),
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentDim,
            onPrimaryContainer = palette.onAccent,
            secondary = palette.accent,
            onSecondary = palette.onAccent,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceBright,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
            outlineVariant = palette.outline,
            scrim = palette.scrim,
            error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
            onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        )
    }
    // Liquid Glass surfaces exist only for the glass theme; every helper no-ops otherwise.
    val glassActive = (settings?.designTheme ?: DesignTheme.LIQUID_GLASS) == DesignTheme.LIQUID_GLASS
    val hazeState = if (glassActive) rememberAuroroHazeState() else null
    val glassQuality = settings?.glassQuality ?: com.auroro.wallpapers.core.data.GlassQuality.BALANCED
    CompositionLocalProvider(
        LocalAuroroPalette provides palette,
        LocalHazeState provides hazeState,
        LocalGlassQuality provides glassQuality,
    ) {
        val baseDensity = LocalDensity.current
        val fontScale = settings?.fontScale ?: 1f
        val density = remember(baseDensity, fontScale) {
            Density(baseDensity.density, baseDensity.fontScale * fontScale)
        }
        CompositionLocalProvider(LocalDensity provides density) {
            MaterialTheme(colorScheme = scheme, typography = AuroroTypography) {
                Surface(
                    color = scheme.background,
                    contentColor = scheme.onBackground,
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}
