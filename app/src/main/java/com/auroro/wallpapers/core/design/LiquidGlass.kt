package com.auroro.wallpapers.core.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens

/**
 * Liquid-glass glue for Auroro, built on the Apache-2.0 Kyant0 backdrop engine
 * (`io.github.kyant0:backdrop`, https://github.com/Kyant0/AndroidLiquidGlass).
 *
 * The surface architecture follows the pattern BitChord (GPL-3.0) demonstrated on the same
 * engine: one opaque page-level recording, glass chrome mounted as siblings *outside* the
 * recorded subtree (never inside it, which would self-sample), and a restrained
 * saturation → blur → lens effect stack. This file is a clean-room implementation for
 * Auroro — no BitChord code is copied — and it keeps the Frutiger Aero palette: glass is
 * tinted with the theme's aqua [AeroColors.glassTint] instead of a neutral grey, and every
 * surface keeps its specular rim so the identity stays recognisable when the engine falls
 * back on older devices.
 *
 * Engine behaviour (upstream, verified against 2.0.1):
 * - `blur` is a no-op below API 31 and `lens` a no-op below API 33, so the [supportsLiquidGlass]
 *   gate only has to guard the capture cost, not correctness.
 * - `lens` supports [CornerBasedShape] outlines (rounded rectangles, circles); other shapes
 *   would throw, so [Modifier.aeroLiquidGlass] only accepts corner-based shapes.
 * - The capture is full resolution (no downscale knob upstream), so the number of live glass
 *   surfaces is deliberately small: the bottom bar, the drawer sheet, and the floating
 *   controls on the detail/crop screens. Panels inside scrolling content keep the lightweight
 *   Aero treatment to protect scroll performance.
 */

/** True when the platform can blur a captured layer at all (Android 12+). */
val supportsLiquidGlass: Boolean
    get() = Build.VERSION.SDK_INT >= 31

/** Frosted-glass blur radius for floating chrome; measured on emulator, see CHANGELOG 1.3.0. */
private val LiquidBlurRadius: Dp = 12.dp

/** Edge refraction depth. Kept subtler than the engine demos so artwork stays undistorted. */
private val LensRefractionHeight: Dp = 18.dp
private val LensRefractionAmount: Dp = 12.dp

/** Saturation lift applied to sampled content — the "liquid" pop from the reference design. */
private const val LiquidSaturation = 1.5f

/**
 * Creates the app-level recording backdrop, or `null` when live glass is disabled
 * (reduce-transparency, GlassQuality.REDUCED) so no capture work happens at all.
 *
 * [onDrawBase] paints the opaque base of the recording — a recording with transparent areas
 * makes glass sample nothing — before the recorded content is drawn.
 */
@Composable
fun rememberAuroraBackdrop(onDrawBase: DrawScope.() -> Unit): LayerBackdrop? {
    if (!isLiveGlassEnabled()) return null
    val base = rememberUpdatedState(onDrawBase)
    // rememberLayerBackdrop keys its remember() on the onDraw lambda, so the lambda identity
    // must stay stable across recompositions or the backdrop (and every consumer) would churn.
    val onDraw: ContentDrawScope.() -> Unit = remember(base) {
        {
            base.value.invoke(this)
            drawContent()
        }
    }
    return rememberLayerBackdrop(onDraw = onDraw)
}

/** Convenience recording whose base is a single colour (detail/crop heroes sit on black). */
@Composable
fun rememberSolidBackdrop(baseColor: Color): LayerBackdrop? {
    val color = rememberUpdatedState(baseColor)
    return rememberAuroraBackdrop { drawRect(color.value) }
}

/** Ambient-gradient recording base matching [AmbientBackdrop], so glass stays Aero-tinted. */
@Composable
fun rememberAmbientBackdrop(): LayerBackdrop? {
    val colors = rememberUpdatedState(Aero.colors)
    return rememberAuroraBackdrop {
        val c = colors.value
        drawRect(
            Brush.verticalGradient(
                listOf(c.backdropTop, c.backdropMid, c.backdropBottom),
                startY = 0f,
                endY = size.height,
            ),
        )
    }
}

/** Attaches a recording to the subtree that glass surfaces will sample. */
fun Modifier.recordAuroraBackdrop(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * Liquid-glass surface treatment: samples [backdrop], boosts saturation, frosts it, and adds
 * the API-33 lens refraction; falls back to [fallback] (a solid Aero brush) when the backdrop
 * is null or the device cannot blur. The specular rim stays caller-owned so lightweight and
 * live surfaces read as the same material.
 *
 * [lensEnabled] lets GlassQuality.BALANCED skip the runtime-shader pass while keeping blur.
 */
fun Modifier.aeroLiquidGlass(
    backdrop: Backdrop?,
    shape: CornerBasedShape,
    tint: Color,
    tintAlpha: Float,
    fallback: Brush,
    lensEnabled: Boolean,
): Modifier = then(
    if (backdrop != null && supportsLiquidGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(saturation = LiquidSaturation)
                blur(LiquidBlurRadius.toPx())
                if (lensEnabled && Build.VERSION.SDK_INT >= 33) {
                    lens(
                        refractionHeight = LensRefractionHeight.toPx(),
                        refractionAmount = LensRefractionAmount.toPx(),
                        depthEffect = true,
                        chromaticAberration = true,
                    )
                }
            },
            onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha)) },
        )
    } else {
        Modifier.background(fallback)
    },
)
