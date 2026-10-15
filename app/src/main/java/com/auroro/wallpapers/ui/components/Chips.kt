package com.auroro.wallpapers.ui.components

import com.auroro.wallpapers.ui.theme.AuroroPal
import com.auroro.wallpapers.ui.theme.auroroGlass
import com.auroro.wallpapers.ui.theme.auroroGloss
import com.auroro.wallpapers.ui.theme.LocalGlassQuality
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.ui.theme.AuroroMotion

/**
 * Shared pill chip with Material Expressive feedback: colour animates between resting and
 * selected states and the whole chip squishes on press with a bouncy spring. Used by the
 * Home style row, the Search source row and the cover-flow actions.
 */
@Composable
fun AuroraChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AuroroMotion.PressFloat,
        label = "chipPress",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            AuroroPal.textSecondary
        },
        animationSpec = AuroroMotion.ColorCalm,
        label = "chipContent",
    )
    // Every chip is real liquid glass over whatever lives behind it; the selected one adds an
    // accent tint to the refraction so state stays unmistakable on every quality tier (the
    // REDUCED tier falls back to the solid accent/surface colours, never to transparency).
    // Glossy aero pills: lit gradient + luminous rim, zero backdrop capture. Chips multiply and
    // scroll, so real blur per chip would re-render every frame; gloss stays free at 120 Hz.
    val glassModifier = Modifier.auroroGloss(
        shape = RoundedCornerShape(50),
        base = if (active) AuroroPal.accent else AuroroPal.surfaceBright,
        rim = if (active) Color.White else AuroroPal.glow,
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = contentColor,
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .then(glassModifier)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
