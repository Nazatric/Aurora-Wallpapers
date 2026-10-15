package com.auroro.wallpapers.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.ui.theme.AuroroPal

/**
 * Slow-drifting aurora wash behind the shell. Liquid Glass needs living content to refract, and
 * non-image screens (Settings, Search) are otherwise flat colour; two soft radial blobs drift on
 * an 18-second loop so every glass surface has something real to bend. One drawBehind pass — the
 * animation only recomputes two offsets, and the haze source layer above captures it for free.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, active: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "auroraBackdrop")
    // Reduced-motion users get the same depth without the infinite drift.
    val driftState: androidx.compose.runtime.State<Float> = if (active) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "auroraDrift",
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    val drift by driftState
    val accent = AuroroPal.accent
    Box(
        modifier = modifier.fillMaxSize().drawBehind {
            val w = size.width
            val h = size.height
            val c1 = Offset(w * (0.16f + 0.24f * drift), h * (0.10f + 0.12f * drift))
            val c2 = Offset(w * (0.86f - 0.20f * drift), h * (0.48f + 0.28f * (1f - drift)))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    center = c1,
                    radius = w * 0.80f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3B6EF6).copy(alpha = 0.10f), Color.Transparent),
                    center = c2,
                    radius = w * 0.65f,
                ),
            )
            // Frutiger Aero water-light: a low aqua bloom that breathes with the same drift, so
            // glass rims and selected states have a cool counterpoint to the violet wash.
            val c3 = Offset(w * (0.5f + 0.3f * (1f - drift)), h * (0.86f - 0.10f * drift))
            drawRect(
                Brush.radialGradient(
                    colors = listOf(AuroroPal.glow.copy(alpha = 0.08f), Color.Transparent),
                    center = c3,
                    radius = w * 0.70f,
                ),
            )
        },
    )
}
