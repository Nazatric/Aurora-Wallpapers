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
import com.auroro.wallpapers.core.data.DesignTheme
import com.auroro.wallpapers.core.data.ThemeMode

/** Pure resolver for the basic appearance control; SYSTEM always observes Compose's live system value. */
internal fun resolveDarkMode(mode: ThemeMode, systemIsDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun shapesFor(theme: DesignTheme) = when (theme) {
    DesignTheme.MATERIAL_3 -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    DesignTheme.FRUTIGER_AERO -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )
    DesignTheme.FRUTIGER_ZEN -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(26.dp),
    )
}

@Composable
fun AuroroTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    // isSystemInDarkTheme is a Compose state read, so changes while Follow system is selected
    // immediately recompute the palette rather than only taking effect on the next app launch.
    val dark = resolveDarkMode(settings.themeMode, isSystemInDarkTheme())
    val colors = remember(
        dark,
        settings.designTheme,
        settings.accent,
        settings.amoled,
        settings.reduceTransparency,
        settings.glassQuality,
    ) {
        designColors(
            dark = dark,
            accent = settings.accent,
            amoled = settings.amoled && dark,
            reduceTransparency = settings.reduceTransparency,
            designTheme = settings.designTheme,
            glassQuality = settings.glassQuality,
        )
    }
    val context = LocalContext.current
    val reducedMotion = remember { Motion.reducedMotion(context) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.emerald,
            onSecondary = colors.onAccent,
            background = colors.backdropTop,
            onBackground = colors.textPrimary,
            surface = colors.surfaceSolid,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSolid,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
            outline = colors.glassRimDark,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.emerald,
            onSecondary = colors.onAccent,
            background = colors.backdropTop,
            onBackground = colors.textPrimary,
            surface = colors.surfaceSolid,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSolid,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
            outline = colors.glassRimDark,
        )
    }
    CompositionLocalProvider(
        LocalAero provides colors,
        LocalReducedMotion provides reducedMotion,
        LocalGlassAccessibility provides AeroGlassAccessibility(
            reduceTransparency = settings.reduceTransparency,
            increaseContrast = settings.increaseContrast,
            showBorders = settings.increaseContrast || settings.reduceTransparency,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(settings.fontChoice, settings.fontScale),
            shapes = shapesFor(settings.designTheme),
            content = content,
        )
    }
}

object Aero {
    val colors: AeroColors @Composable get() = LocalAero.current
}
