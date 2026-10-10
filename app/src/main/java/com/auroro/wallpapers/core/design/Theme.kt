package com.auroro.wallpapers.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.AppearancePreset
import com.auroro.wallpapers.core.data.GlassQuality
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassAccessibilitySettings
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings

private val AeroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@OptIn(ExperimentalHazeApi::class)
@Composable
fun AuroroTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.appearance) {
        AppearancePreset.SYSTEM -> isSystemInDarkTheme()
        AppearancePreset.DARK_AERO -> true
        else -> false
    }
    val aero = remember(dark, settings.appearance, settings.accent, settings.amoled, settings.reduceTransparency, settings.glassQuality) {
        aeroColors(
            dark = dark,
            accent = settings.accent,
            amoled = settings.amoled && dark,
            reduceTransparency = settings.reduceTransparency,
            appearance = settings.appearance,
            glassQuality = settings.glassQuality,
        )
    }
    val context = LocalContext.current
    val reduced = remember { Motion.reducedMotion(context) }
    val hazeState = rememberHazeState()
    val scheme = if (dark) {
        darkColorScheme(
            primary = aero.accent, onPrimary = aero.onAccent, secondary = aero.emerald, onSecondary = aero.onAccent,
            background = aero.backdropTop, onBackground = aero.textPrimary,
            surface = aero.surfaceSolid, onSurface = aero.textPrimary,
            surfaceVariant = aero.surfaceSolid, onSurfaceVariant = aero.textSecondary,
            error = aero.error, outline = aero.glassRimDark,
        )
    } else {
        lightColorScheme(
            primary = aero.accent, onPrimary = aero.onAccent, secondary = aero.emerald, onSecondary = aero.onAccent,
            background = aero.backdropTop, onBackground = aero.textPrimary,
            surface = aero.surfaceSolid, onSurface = aero.textPrimary,
            surfaceVariant = aero.surfaceSolid, onSurfaceVariant = aero.textSecondary,
            error = aero.error, outline = aero.glassRimDark,
        )
    }
    val hazePerformance = when (settings.glassQuality) {
        GlassQuality.FULL -> HazePerformanceMode.Quality
        GlassQuality.BALANCED -> HazePerformanceMode.Balanced
        GlassQuality.REDUCED -> HazePerformanceMode.Performance
    }
    CompositionLocalProvider(
        LocalAero provides aero,
        LocalReducedMotion provides reduced,
        LocalAeroHazeState provides hazeState,
        LocalHazePerformanceMode provides hazePerformance,
        LocalGlassAccessibilitySettings provides GlassAccessibilitySettings(
            reduceTransparency = settings.reduceTransparency,
            increaseContrast = settings.increaseContrast,
            showBorders = settings.increaseContrast || settings.reduceTransparency,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(settings.fontChoice, settings.fontScale),
            shapes = AeroShapes,
            content = content,
        )
    }
}

object Aero {
    val colors: AeroColors @Composable get() = LocalAero.current
}
