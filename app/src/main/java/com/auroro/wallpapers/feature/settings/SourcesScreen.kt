package com.auroro.wallpapers.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.SourceAvailability
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.ProviderAvailabilityLine
import com.auroro.wallpapers.core.model.WallpaperSource

@Composable
fun SourcesScreen(
    availability: Map<WallpaperSource, SourceAvailability>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onConfigure: () -> Unit,
    onSearch: (WallpaperSource) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 17.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { HeaderBar("Wallpaper sources", "Honest availability · no scraping or demo results", onBack = onBack) }
        item { Text("Auroro uses publisher-supported access only. Wallpaper Abyss requires a user's own paid API key; Unsplash is a browser handoff because its API explicitly excludes wallpaper apps.", Modifier.padding(vertical = 8.dp), color = Aero.colors.textSecondary) }
        items(WallpaperSource.entries.toList(), key = { it.id }) { source ->
            val a = availability[source] ?: SourceAvailability.DisabledByUser("Status not checked yet")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProviderAvailabilityLine(source, a, onOpen)
                when (source) {
                    WallpaperSource.WALLHAVEN -> androidx.compose.material3.TextButton(onClick = { onSearch(source) }) { androidx.compose.material3.Text("Search Wallhaven", color = Aero.colors.accent) }
                    WallpaperSource.ABYSS -> {
                        androidx.compose.material3.TextButton(onClick = onConfigure) { androidx.compose.material3.Text("Configure Wallpaper Abyss API key", color = Aero.colors.accent) }
                        androidx.compose.material3.TextButton(onClick = { onSearch(source) }) { androidx.compose.material3.Text("Search if configured", color = Aero.colors.accent) }
                    }
                    WallpaperSource.UNSPLASH -> androidx.compose.material3.TextButton(onClick = {
                        onOpen(com.auroro.wallpapers.core.data.UnsplashProvider.searchUrl(""))
                    }) { androidx.compose.material3.Text("Open Unsplash in browser", color = Aero.colors.accent) }
                }
            }
        }
    }
}
