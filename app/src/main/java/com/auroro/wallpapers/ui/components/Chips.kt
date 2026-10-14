package com.auroro.wallpapers.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.AuroroSurfaceBright
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary

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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AuroroMotion.BouncyFloat,
        label = "chipPress",
    )
    val background by animateColorAsState(
        targetValue = if (active) AuroroAccent else AuroroSurfaceBright,
        animationSpec = AuroroMotion.ColorCalm,
        label = "chipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            AuroroTextSecondary
        },
        animationSpec = AuroroMotion.ColorCalm,
        label = "chipContent",
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = contentColor,
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
