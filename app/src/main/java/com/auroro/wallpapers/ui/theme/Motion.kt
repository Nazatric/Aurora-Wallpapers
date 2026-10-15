package com.auroro.wallpapers.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Material 3 Expressive motion tokens for Auroro. Springs carry the personality (small
 * elements bounce, larger surfaces settle without overshoot); the emphasized easings follow
 * the M3 motion guidance for enter/exit transitions. Everything here is cheap: no blur,
 * no particles — motion never competes with the wallpapers.
 */
object AuroroMotion {
    /** Bouncy settle for small elements: chips, hearts, pills, pager dots. */
    val BouncyFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Snappy, no overshoot: presses, scale corrections, larger surfaces. */
    val SnappyFloat: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Colour transitions: quick and calm. */
    val ColorCalm: FiniteAnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Size transitions with a little life (pager dots, indicators). */
    val BouncyDp: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** M3 emphasized easing — confident deceleration for entering elements. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 emphasized acceleration — exiting elements leave decisively. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Standard M3 emphasized curve for equal-weight transitions. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * Overshoot scale that replays whenever [trigger] flips to true — used for the favorite
 * heart so saving feels physical. Returns 1f at rest.
 */
@Composable
fun rememberBounceScale(trigger: Boolean): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            scale.snapTo(0.68f)
            scale.animateTo(1f, AuroroMotion.BouncyFloat)
        }
    }
    return scale.value
}

/**
 * Honours the system "remove animations" scales: when Android is told to kill transition/window
 * animation scales, the shell drops slides and long fades instead of animating against the user's
 * accessibility preference.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        val resolver = context.contentResolver
        val transition = android.provider.Settings.Global.getFloat(
            resolver, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1f,
        )
        val window = android.provider.Settings.Global.getFloat(
            resolver, android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, 1f,
        )
        transition == 0f || window == 0f
    }
}
