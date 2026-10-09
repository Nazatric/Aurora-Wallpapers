package com.auroro.wallpapers.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.model.WallpaperSource

@Composable
fun HeaderBar(
    title: String,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leading = onBack ?: onMenu
        if (leading != null) {
            GlassIconButton(
                onClick = leading,
                description = if (onBack != null) "Back" else "Open navigation menu",
                icon = if (onBack != null) Icons.Rounded.ArrowBack else Icons.Rounded.Menu,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun SourceGlyph(source: WallpaperSource, modifier: Modifier = Modifier, small: Boolean = false) {
    val (top, bottom, letter) = when (source) {
        WallpaperSource.WALLHAVEN -> Triple(Color(0xFFBFE8ED), Color(0xFF6CAAB4), "W")
        WallpaperSource.OPENVERSE -> Triple(Color(0xFFC7EAD5), Color(0xFF79B99B), "O")
        WallpaperSource.ARCHIVED -> Triple(Color(0xFFD5E0E3), Color(0xFF8CA3A7), "S")
    }
    Box(
        modifier.size(if (small) 22.dp else 30.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(0.8.dp, Color.White.copy(alpha = 0.54f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = Color(0xFF08252D))
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Info,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Aero.colors.accent.copy(alpha = 0.22f), Aero.colors.emerald.copy(alpha = 0.09f))))
                .border(1.dp, Aero.colors.glassRimLight.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Aero.colors.accentLight, modifier = Modifier.size(25.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, textAlign = TextAlign.Center)
        if (action != null) action()
    }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Aero.colors.surfaceSolid.copy(alpha = 0.96f))
            .border(0.7.dp, Aero.colors.glassRimDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(start = 13.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Aero.colors.error, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            androidx.compose.material3.IconButton(onClick = onRetry, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Refresh, "Retry", tint = Aero.colors.accent)
            }
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val c = Aero.colors
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(c.glassTint.copy(alpha = 0.67f), c.glassTint.copy(alpha = 0.4f))))
            .border(0.7.dp, c.glassRimLight.copy(alpha = 0.19f), RoundedCornerShape(16.dp)),
    )
}

@Composable
fun SourceDot(source: WallpaperSource) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.58f)).padding(start = 5.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceGlyph(source, small = true)
        Spacer(Modifier.width(5.dp))
        Text(source.displayName, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}
