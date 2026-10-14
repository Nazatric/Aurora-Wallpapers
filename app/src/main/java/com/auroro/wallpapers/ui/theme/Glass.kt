package com.auroro.wallpapers.ui.theme

import androidx.compose.foundation.background
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
    if (state == null || quality == GlassQuality.REDUCED) {
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
