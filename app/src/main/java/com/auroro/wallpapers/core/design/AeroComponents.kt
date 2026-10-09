package com.auroro.wallpapers.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.ProviderCapabilities
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.data.SourceStatus
import com.auroro.wallpapers.core.data.SourceAvailability
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
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val leading = onBack ?: onMenu
        if (leading != null) {
            GlassIconButton(
                onClick = leading,
                description = if (onBack != null) "Back" else "Open navigation menu",
                icon = if (onBack != null) Icons.Rounded.ArrowBack else Icons.Rounded.Menu,
            )
            Spacer(Modifier.width(12.dp))
        }
        if (title == "Auroro Wallpapers") {
            AppLogo(Modifier.size(39.dp), 39.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Aero.colors.textPrimary,
    selected: Boolean = false,
) {
    val shape = CircleShape
    val c = Aero.colors
    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.72f) else c.glassTint.copy(alpha = if (c.isDark) 0.46f else 0.66f))
            .border(0.8.dp, c.glassRimLight.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun SourceGlyph(source: WallpaperSource, modifier: Modifier = Modifier, small: Boolean = false) {
    val (top, bottom, letter) = when (source) {
        WallpaperSource.WALLHAVEN -> Triple(Color(0xFF6BE6F5), Color(0xFF197FA0), "W")
        WallpaperSource.ABYSS -> Triple(Color(0xFF9AE9D0), Color(0xFF267F70), "A")
        WallpaperSource.UNSPLASH -> Triple(Color(0xFFDFE7EB), Color(0xFF708691), "U")
    }
    Box(
        modifier.size(if (small) 22.dp else 28.dp).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(0.8.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, color = Color(0xFF062536))
    }
}

@Composable
fun SourceStatusPanel(
    statuses: List<SourceStatus>,
    onRetry: (() -> Unit)? = null,
    onOpen: (String) -> Unit = {},
    compact: Boolean = false,
) {
    val c = Aero.colors
    val informative = statuses.filter { it.state is SourceState.Failed || it.state is SourceState.Skipped || it.state is SourceState.Exhausted }
    if (informative.isEmpty()) return
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(if (compact) 18.dp else 24.dp), elevation = 4.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Source availability", style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
                Spacer(Modifier.weight(1f))
                if (onRetry != null && informative.any { it.state is SourceState.Failed }) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Refresh, "Retry sources", tint = c.accent)
                    }
                }
            }
            informative.forEach { status -> SourceStatusLine(status, onOpen) }
        }
    }
}

@Composable
private fun SourceStatusLine(status: SourceStatus, onOpen: (String) -> Unit) {
    val c = Aero.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourceGlyph(status.source, small = true)
        Column(Modifier.weight(1f)) {
            Text(status.source.displayName, style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            when (val state = status.state) {
                is SourceState.Failed -> Text(state.error.message ?: state.error.kind.title, style = MaterialTheme.typography.bodySmall, color = c.error)
                is SourceState.Skipped -> Text(state.reason, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                SourceState.Exhausted -> Text("No more pages from this source.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                is SourceState.Ok -> Unit
            }
        }
        val link = (status.state as? SourceState.Skipped)?.handoffUrl
        if (link != null) {
            IconButton(onClick = { onOpen(link) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.OpenInNew, "Open ${status.source.displayName}", tint = c.accent)
            }
        }
    }
}

@Composable
fun ProviderAvailabilityLine(source: WallpaperSource, availability: SourceAvailability, onOpen: (String) -> Unit) {
    val c = Aero.colors
    GlassSurface(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceGlyph(source)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(source.displayName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                when (availability) {
                    SourceAvailability.Available -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Available", style = MaterialTheme.typography.bodySmall, color = c.success)
                    }
                    is SourceAvailability.NeedsConfiguration -> Text(availability.reason, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    is SourceAvailability.BlockedByPolicy -> Text(availability.reason, style = MaterialTheme.typography.bodySmall, color = c.warning)
                    is SourceAvailability.DisabledByUser -> Text(availability.reason, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                }
            }
            val url = when (availability) {
                is SourceAvailability.NeedsConfiguration -> availability.handoffUrl
                is SourceAvailability.BlockedByPolicy -> availability.handoffUrl
                else -> null
            }
            if (url != null) IconButton(onClick = { onOpen(url) }) { Icon(Icons.Rounded.OpenInNew, "Open ${source.displayName}", tint = c.accent) }
        }
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
    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Aero.colors.accent.copy(alpha = 0.3f), Aero.colors.emerald.copy(alpha = 0.12f))))
                .border(1.dp, Aero.colors.glassRimLight.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Aero.colors.accentLight, modifier = Modifier.size(31.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Aero.colors.textPrimary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) action()
    }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = Aero.colors
    GlassSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = c.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
            if (onRetry != null) IconButton(onClick = onRetry, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Refresh, "Retry", tint = c.accent) }
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val c = Aero.colors
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(c.glassTint.copy(alpha = 0.46f), c.glassTint.copy(alpha = 0.25f))))
            .border(0.7.dp, c.glassRimLight.copy(alpha = 0.23f), RoundedCornerShape(18.dp)),
    )
}

@Composable
fun SourceDot(source: WallpaperSource) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.54f)).padding(start = 6.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceGlyph(source, small = true)
        Spacer(Modifier.width(5.dp))
        Text(source.displayName, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

@Composable
fun StatusPill(label: String, tone: Color, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(tone.copy(alpha = 0.16f)).border(0.7.dp, tone.copy(alpha = 0.35f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textPrimary)
    }
}
