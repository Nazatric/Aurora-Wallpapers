package com.auroro.wallpapers.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

/** A restrained, lightweight glass plate for controls and grouped information. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    elevation: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Aero.colors
    val opacity = ((if (c.isDark) 0.72f else 0.86f) * c.opacityBoost).coerceIn(0.72f, 0.98f)
    Column(
        modifier = modifier
            .shadow(
                elevation,
                shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.23f else 0.08f),
                spotColor = c.accentDeep.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        c.glassTint.copy(alpha = (opacity + 0.08f).coerceAtMost(0.99f)),
                        c.glassTint.copy(alpha = opacity),
                        c.glassTint.copy(alpha = (opacity + 0.025f).coerceAtMost(0.99f)),
                    ),
                ),
            )
            .border(
                BorderStroke(
                    0.8.dp,
                    Brush.verticalGradient(
                        listOf(
                            c.glassRimLight.copy(alpha = 0.43f),
                            c.glassRimDark.copy(alpha = 0.25f),
                            c.glassRimLight.copy(alpha = 0.18f),
                        ),
                    ),
                ),
                shape,
            ),
        content = {
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, c.glassHighlight.copy(alpha = 0.43f), Color.Transparent),
                    ),
                ),
            )
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
    val opacity = ((if (c.isDark) 0.66f else 0.84f) * c.opacityBoost).coerceIn(0.66f, 0.98f)
    val base = modifier
        .shadow(5.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.18f else 0.07f))
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(c.glassTint.copy(alpha = (opacity + 0.09f).coerceAtMost(0.99f)), c.glassTint.copy(alpha = opacity)),
            ),
        )
        .border(
            BorderStroke(0.8.dp, Brush.verticalGradient(listOf(c.glassRimLight.copy(alpha = 0.39f), c.glassRimDark.copy(alpha = 0.22f)))),
            shape,
        )
    val interactive = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
    Box(interactive, content = content)
}

@Composable
fun GlassPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    selectionRole: Role? = Role.RadioButton,
    onClick: () -> Unit,
) {
    val c = Aero.colors
    val active = selected && enabled
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (active) {
                    Brush.verticalGradient(
                        listOf(c.accentDeep.copy(alpha = 0.96f), c.accentDeep),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            c.glassTint.copy(alpha = if (c.isDark) 0.81f else 0.89f),
                            c.glassTint.copy(alpha = if (c.isDark) 0.62f else 0.77f),
                        ),
                    )
                },
            )
            .border(
                0.8.dp,
                if (active) c.glassHighlight.copy(alpha = 0.58f) else c.glassRimLight.copy(alpha = if (c.isDark) 0.24f else 0.66f),
                CircleShape,
            )
            .then(
                if (selectionRole == null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.selectable(selected = selected, enabled = enabled, role = selectionRole, onClick = onClick)
                },
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Color.White else c.textSecondary.copy(alpha = if (enabled) 1f else 0.52f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            Text(title, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
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
    val glowRadius = size.maxDimension * 0.78f
    val skyLight = Brush.radialGradient(
        colors = listOf(c.glowA.copy(alpha = if (c.isDark) 0.19f else 0.22f), Color.Transparent),
        center = Offset(size.width * 0.88f, size.height * 0.12f),
        radius = glowRadius,
    )
    val leafLight = Brush.radialGradient(
        colors = listOf(c.glowB.copy(alpha = if (c.isDark) 0.11f else 0.13f), Color.Transparent),
        center = Offset(size.width * 0.03f, size.height * 0.78f),
        radius = glowRadius * 0.75f,
    )
    onDrawBehind {
        drawRect(skyLight)
        drawRect(leafLight)
    }
}

@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val c = Aero.colors
    val shape = RoundedCornerShape(size * 0.31f)
    Box(
        modifier
            .size(size)
            .shadow(6.dp, shape, clip = false, ambientColor = c.accent.copy(alpha = 0.22f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF9AD9EC), Color(0xFF3D9AB9), Color(0xFF1D5E70))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.76f), Color.White.copy(alpha = 0.1f))), shape),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFFFF5C8).copy(alpha = 0.96f),
                radius = size.minDimension * 0.115f,
                center = Offset(size.width * 0.72f, size.height * 0.28f),
            )
            val farShore = Path().apply {
                moveTo(0f, size.height * 0.70f)
                cubicTo(size.width * 0.27f, size.height * 0.54f, size.width * 0.43f, size.height * 0.77f, size.width * 0.67f, size.height * 0.62f)
                cubicTo(size.width * 0.82f, size.height * 0.53f, size.width * 0.92f, size.height * 0.62f, size.width, size.height * 0.59f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                farShore,
                Brush.verticalGradient(listOf(Color(0xFF92D899), Color(0xFF3D9C70)), startY = size.height * 0.56f, endY = size.height),
            )
            val nearShore = Path().apply {
                moveTo(0f, size.height * 0.83f)
                cubicTo(size.width * 0.25f, size.height * 0.72f, size.width * 0.46f, size.height * 0.91f, size.width * 0.68f, size.height * 0.80f)
                cubicTo(size.width * 0.83f, size.height * 0.73f, size.width * 0.92f, size.height * 0.78f, size.width, size.height * 0.75f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                nearShore,
                Brush.verticalGradient(listOf(Color(0xFF55B782), Color(0xFF1E704F)), startY = size.height * 0.74f, endY = size.height),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.58f),
                start = Offset(size.width * 0.12f, size.height * 0.16f),
                end = Offset(size.width * 0.5f, size.height * 0.16f),
                strokeWidth = size.minDimension * 0.035f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Aero.colors.textPrimary,
    selected: Boolean = false,
) {
    val shape = CircleShape
    val c = Aero.colors
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(if (selected) c.accentDeep.copy(alpha = if (c.isDark) 0.72f else 0.18f) else c.glassTint.copy(alpha = if (c.isDark) 0.76f else 0.82f))
            .border(0.8.dp, c.glassRimLight.copy(alpha = if (c.isDark) 0.34f else 0.68f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}
