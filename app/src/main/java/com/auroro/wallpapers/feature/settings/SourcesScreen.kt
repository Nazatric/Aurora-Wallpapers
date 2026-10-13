package com.auroro.wallpapers.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SourceGlyph
import com.auroro.wallpapers.core.model.WallpaperSource

@Composable
fun SourcesScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onToggle: (WallpaperSource, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onSearch: (WallpaperSource) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeaderBar("Sources", "Real catalogues, clear source policies", onBack = onBack) }
        item {
            Text(
                "Search and For You use source-native Wallhaven and Art Institute queries plus the dated APOD archive. Each catalogue keeps its own query rules, dimensions and rights; APOD remains view-only.",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Aero.colors.textSecondary,
            )
        }
        item {
            SourceCard(
                source = WallpaperSource.WALLHAVEN,
                enabled = settings.wallhavenEnabled,
                onToggle = { onToggle(WallpaperSource.WALLHAVEN, it) },
                description = "Public API · official purity=100 query · source dimensions and uploader metadata.",
                detail = "Popularity and date ordering come from Wallhaven. The source page remains the reference for creator rights.",
                docsLabel = "API guide",
                docsUrl = "https://wallhaven.cc/help/api",
                onOpen = onOpen,
                onSearch = { onSearch(WallpaperSource.WALLHAVEN) },
            )
        }
        item {
            SourceCard(
                source = WallpaperSource.NASA_APOD,
                enabled = settings.nasaApodEnabled,
                onToggle = { onToggle(WallpaperSource.NASA_APOD, it) },
                description = "Current APOD Basic endpoint · newest-first · daily entries only.",
                detail = "APOD Basic does not report pixel dimensions. Credit does not grant reuse permission: entries stay view-only until item-specific rights are verified with the image owner, and their size remains unknown.",
                docsLabel = "NASA APOD",
                docsUrl = "https://science.nasa.gov/apod/",
                onOpen = onOpen,
                onSearch = { onSearch(WallpaperSource.NASA_APOD) },
            )
        }
        item {
            StaticSourceCard(
                source = WallpaperSource.ART_INSTITUTE,
                status = "Retired in 1.3.0 · records repeatedly arrived without usable image dimensions",
                detail = "The catalogue kept returning metadata-only results that rendered as 'Preview unavailable' tiles, so it no longer appears in Home, Search or discovery. Artworks saved before retirement keep their CC0 attribution offline.",
                docsLabel = "API and Open Access",
                docsUrl = "https://api.artic.edu/docs/",
                onOpen = onOpen,
            )
        }
        item {
            StaticSourceCard(
                source = WallpaperSource.REDDIT,
                status = "Paused · authorized API access is not configured",
                detail = "r/Amoledbackgrounds and r/wallpaper are paused until an authorized OAuth application is configured. The app will use only Reddit's official API; no HTML scraping, client impersonation or limit bypass is used.",
                docsLabel = "Reddit API policy",
                docsUrl = "https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki",
                onOpen = onOpen,
            )
        }
        item {
            Text(
                "Pixabay is not integrated because permission and current terms for wallpaper-file use have not been verified. Unsplash's API guidelines explicitly exclude wallpaper applications, so no Unsplash feed is shipped either. Random placeholder feeds are not used.",
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Aero.colors.textTertiary,
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SourceCard(
    source: WallpaperSource,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    description: String,
    detail: String,
    docsLabel: String,
    docsUrl: String,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Aero.colors.surfaceSolid.copy(alpha = if (Aero.colors.isDark) .91f else .96f),
        border = BorderStroke(1.dp, Aero.colors.glassRimDark.copy(alpha = .16f)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceGlyph(source, Modifier.padding(end = 4.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(source.displayName, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpen(docsUrl) }) { Text(docsLabel, maxLines = 1) }
                Button(
                    onClick = onSearch,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = Aero.colors.accent, contentColor = Aero.colors.onAccent),
                ) { Text("Browse") }
            }
        }
    }
}

@Composable
private fun StaticSourceCard(
    source: WallpaperSource,
    status: String,
    detail: String,
    docsLabel: String,
    docsUrl: String,
    onOpen: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Aero.colors.surfaceSolid.copy(alpha = if (Aero.colors.isDark) .91f else .96f),
        border = BorderStroke(1.dp, Aero.colors.glassRimDark.copy(alpha = .16f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceGlyph(source, Modifier.padding(end = 4.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Reddit wallpaper feeds", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                    Text(status, style = MaterialTheme.typography.labelMedium, color = Aero.colors.warning)
                }
                androidx.compose.material3.Icon(Icons.Rounded.Lock, "Not configured", tint = Aero.colors.textTertiary)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            TextButton(onClick = { onOpen(docsUrl) }, modifier = Modifier.align(Alignment.End)) { Text(docsLabel) }
        }
    }
}
