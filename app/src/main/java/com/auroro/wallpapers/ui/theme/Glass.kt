package com.auroro.wallpapers.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.GlassQuality
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Liquid Glass abstraction. One backdrop engine (Haze 2, stable) is used app-wide; screens
 * never talk to Haze directly. When the active design theme is not Liquid Glass, or the
 * device cannot render the effect, every helper degrades to the opaque Material surface so
 * the same screen code serves all themes and all devices.
 *
 * Quality tiers map to real rendering costs:
 *  - FULL: refraction-driven glass (lens distortion, rim light, depth blur)
 *  - BALANCED: frosted live blur of the captured backdrop, no refraction shader
 *  - REDUCED: opaque surface, zero GPU effect work
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Active glass rendering tier from Settings. */
val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.BALANCED }

/**
 * True while a tab transition is running: live backdrop capture is swapped for the opaque
 * fallback for those ~200 ms so switching tabs never pays per-frame blur cost (the lag users
 * feel on 120 Hz panels), then glass returns once the new screen is settled.
 */
val LocalGlassSuspend = staticCompositionLocalOf { false }

/** Tint strength per quality tier; pure so it is unit-tested without a device. */
fun glassTintAlpha(quality: GlassQuality): Float = when (quality) {
    GlassQuality.FULL -> 0.12f
    GlassQuality.BALANCED -> 0.18f
    GlassQuality.REDUCED -> 1f
}

@Composable
fun rememberAuroroHazeState(): HazeState = rememberHazeState()

/** Marks drawn content as a backdrop source for any glass surface layered above it. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.auroroHazeSource(): Modifier {
    val state = LocalHazeState.current ?: return this
    return this.hazeSource(state)
}

/**
 * Applies the Liquid Glass treatment to a floating surface. [fallback] is the opaque colour
 * used when glass is off or unsupported, keeping contrast and legibility honest.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.auroroGlass(shape: RoundedCornerShape, quality: GlassQuality, fallback: Color, tint: Color): Modifier {
    val state = LocalHazeState.current
    if (state == null || quality == GlassQuality.REDUCED || LocalGlassSuspend.current) {
        return this.background(fallback, shape)
    }
    val tinted = tint.copy(alpha = glassTintAlpha(quality))
    return when (quality) {
        GlassQuality.FULL -> this.hazeGlass(
            input = HazeInput.Sources(state),
            style = GlassStyle.regular.then {
                tint(tinted)
                shape(shape)
            },
        )

        GlassQuality.BALANCED -> this
            .hazeBlur(
                input = HazeInput.Sources(state),
                style = dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(24.dp) },
            )
            .background(tinted, shape)

        GlassQuality.REDUCED -> this.background(fallback, shape)
    }
}

/**
 * Frutiger Aero gloss WITHOUT backdrop capture: a lit top highlight, a deep base and a 1 dp
 * luminous rim. Used by chips and other surfaces that scroll or multiply — real blur on every
 * one of them would cost a re-render per frame, gloss costs one gradient.
 */
@Composable
fun Modifier.auroroGloss(shape: RoundedCornerShape, base: Color, rim: Color): Modifier = this
    .background(base, shape)
    .background(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            ),
        ),
        shape = shape,
    )
    .border(
        width = 1.dp,
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(rim.copy(alpha = 0.55f), rim.copy(alpha = 0.06f)),
        ),
        shape = shape,
    )
