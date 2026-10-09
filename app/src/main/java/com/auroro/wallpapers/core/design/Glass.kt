package com.auroro.wallpapers.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A layered, lightweight glass surface. No live blur or shader: just transmission tint, specular strip and luminous rim. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    elevation: Dp = 9.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Aero.colors
    val alpha = (if (c.isDark) 0.68f else 0.84f) * c.opacityBoost.coerceAtMost(1.35f)
    Column(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.26f else 0.12f), spotColor = c.accentDeep.copy(alpha = 0.22f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(c.glassTint.copy(alpha = (alpha + 0.13f).coerceAtMost(0.98f)), c.glassTint.copy(alpha = alpha), c.glassTint.copy(alpha = (alpha + 0.06f).coerceAtMost(0.96f))),
                ),
            )
            .border(
                BorderStroke(0.8.dp, Brush.verticalGradient(listOf(c.glassRimLight.copy(alpha = 0.48f), c.glassRimDark.copy(alpha = 0.5f), c.glassRimLight.copy(alpha = 0.22f)))),
                shape,
            ),
        content = {
            // Only the top edge catches a soft highlight; controls stay readable on bright wallpapers.
            Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, c.glassHighlight.copy(alpha = 0.46f), Color.Transparent))))
            content()
        },
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Aero.colors
    val alpha = (if (c.isDark) 0.63f else 0.82f) * c.opacityBoost.coerceAtMost(1.35f)
    val base = modifier
        .shadow(7.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.2f else 0.1f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(c.glassTint.copy(alpha = (alpha + 0.16f).coerceAtMost(0.98f)), c.glassTint.copy(alpha = alpha))))
        .border(BorderStroke(0.8.dp, Brush.verticalGradient(listOf(c.glassRimLight.copy(alpha = 0.5f), c.glassRimDark.copy(alpha = 0.35f)))), shape)
    val clickable = if (onClick != null) base.clickable(role = Role.Button, interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material.ripple.rememberRipple(), onClick = onClick) else base
    Box(clickable, content = content)
}

@Composable
fun GlassPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Aero.colors
    val shape = CircleShape
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(c.accentLight.copy(alpha = 0.95f), c.accent.copy(alpha = 0.94f), c.accentDeep.copy(alpha = 0.88f)))
                else Brush.verticalGradient(listOf(c.glassTint.copy(alpha = if (c.isDark) 0.48f else 0.7f), c.glassTint.copy(alpha = if (c.isDark) 0.32f else 0.58f))),
            )
            .border(0.8.dp, if (selected) c.glassHighlight.copy(alpha = 0.42f) else c.glassRimLight.copy(alpha = 0.32f), shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material.ripple.rememberRipple(), enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onAccent else c.textSecondary.copy(alpha = if (enabled) 1f else 0.5f))
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val c = Aero.colors
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(c.backdropTop, c.backdropMid, c.backdropBottom)))
            .drawAeroGlows(c),
    )
}

private fun Modifier.drawAeroGlows(c: AeroColors) = drawWithCache {
    val glowRadius = size.maxDimension * 0.92f
    val sky = Brush.radialGradient(
        colors = listOf(c.glowA.copy(alpha = 0.46f), c.glowA.copy(alpha = 0.13f), Color.Transparent),
        center = Offset(size.width * 0.82f, size.height * 0.12f),
        radius = glowRadius,
    )
    val water = Brush.radialGradient(
        colors = listOf(c.glowB.copy(alpha = 0.32f), Color.Transparent),
        center = Offset(size.width * 0.05f, size.height * 0.78f),
        radius = glowRadius * 0.82f,
    )
    onDrawBehind {
        drawRect(sky)
        drawRect(water)
    }
}

@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val c = Aero.colors
    Box(
        modifier
            .shadow(7.dp, CircleShape, clip = false, ambientColor = c.accent.copy(alpha = 0.28f))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFD1FAFF), c.accentLight, Color(0xFF1686B3), Color(0xFF07517B))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.06f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.58f)
                .height(size * 0.26f)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.84f), Color.White.copy(alpha = 0.03f)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = size * 0.13f, bottom = size * 0.14f)
                .background(Brush.radialGradient(listOf(Color(0xFFD6FFEA), Color(0xFF36D49A), Color(0xFF068858))), CircleShape)
                .border(0.7.dp, Color.White.copy(alpha = 0.65f), CircleShape)
                .padding(size * 0.085f),
        )
    }
}
