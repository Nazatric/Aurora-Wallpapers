package com.auroro.wallpapers.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.darkColorScheme

private val AuroroColors = darkColorScheme(
    primary = AuroroAccent,
    onPrimary = AuroroOnAccent,
    primaryContainer = AuroroAccentDim,
    onPrimaryContainer = AuroroOnAccent,
    secondary = AuroroAccent,
    onSecondary = AuroroOnAccent,
    background = AuroroBackground,
    onBackground = AuroroTextPrimary,
    surface = AuroroSurface,
    onSurface = AuroroTextPrimary,
    surfaceVariant = AuroroSurfaceBright,
    onSurfaceVariant = AuroroTextSecondary,
    outline = AuroroOutline,
    outlineVariant = AuroroOutline,
    scrim = AuroroScrim,
)

/**
 * Slice-1 theme: dark scheme only, system typography, no bundled fonts.
 * Light mode, per-system follow and dynamic color are deliberate follow-up slices.
 */
@Composable
fun AuroroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroroColors,
        typography = AuroroTypography,
        content = content,
    )
}
