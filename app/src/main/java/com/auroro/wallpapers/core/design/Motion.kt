package com.auroro.wallpapers.core.design

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** True when the user turned system animations off (Developer options / accessibility "Remove animations"). */
val LocalReducedMotion = staticCompositionLocalOf { false }

object Motion {
    const val FAST = 120
    const val BASE = 220
    const val SLOW = 360

    /** One easing curve for everything so the app feels consistent. */
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun reducedMotion(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }

    @Composable
    fun <T> spec(durationMs: Int = BASE): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else tween(durationMs, easing = Easing)
}
