package com.auroro.wallpapers.feature.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeaderBar("Sources", "Two integrated wallpaper catalogues", onBack = onBack) }
        item { SectionTitle("Wallhaven") }
        item {
            SourceCard(
                source = WallpaperSource.WALLHAVEN,
                enabled = settings.wallhavenEnabled,
                onToggle = { onToggle(WallpaperSource.WALLHAVEN, it) },
                description = "Public image search · SFW results only · no API key required.",
                detail = "Wallhaven supports relevance, newest, popular and random sorting. Ratio and resolution are checked from image dimensions.",
                docsLabel = "Wallhaven API help",
                docsUrl = "https://wallhaven.cc/help/api",
                onOpen = onOpen,
                onSearch = { onSearch(WallpaperSource.WALLHAVEN) },
            )
        }
        item { SectionTitle("Openverse") }
        item {
            SourceCard(
                source = WallpaperSource.OPENVERSE,
                enabled = settings.openverseEnabled,
                onToggle = { onToggle(WallpaperSource.OPENVERSE, it) },
                description = "Openly licensed images · anonymous API access · no credential setup.",
                detail = "Searches are rate-limited locally and respect Openverse's response headers. Results retain the creator, source page, tags and licence. Check the original page before reuse.",
                docsLabel = "Openverse API reference",
                docsUrl = "https://api.openverse.org/v1/",
                onOpen = onOpen,
                onSearch = { onSearch(WallpaperSource.OPENVERSE) },
                note = "Made using Openverse; not endorsed or certified by Openverse.",
            )
        }
        item {
            Text(
                "Openverse indexes third-party works and does not verify every licence claim. Attribution and licence details are shown on each wallpaper's detail page.",
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Aero.colors.textSecondary,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
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
    note: String? = null,
) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 3.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceGlyph(source)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(source.displayName, style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = Aero.colors.accentLight)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpen(docsUrl) }) { Text(docsLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextButton(onClick = onSearch, enabled = enabled) { Text("Search") }
            }
        }
    }
}
