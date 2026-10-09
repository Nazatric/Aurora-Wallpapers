package com.auroro.wallpapers.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.ThemeMode

@Composable
fun AuroroTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val aero = remember(dark, settings.accent, settings.amoled, settings.reduceTransparency) {
        aeroColors(dark, settings.accent, settings.amoled && dark, settings.reduceTransparency)
    }
    val context = LocalContext.current
    val reduced = remember { Motion.reducedMotion(context) }
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
    CompositionLocalProvider(LocalAero provides aero, LocalReducedMotion provides reduced) {
        MaterialTheme(colorScheme = scheme, typography = AeroTypography, content = content)
    }
}

object Aero {
    val colors: AeroColors @Composable get() = LocalAero.current
}
