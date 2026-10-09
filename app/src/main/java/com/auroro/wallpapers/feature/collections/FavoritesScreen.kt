package com.auroro.wallpapers.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.feature.home.WallpaperGrid

@Composable
fun FavoritesScreen(
    wallpapers: List<Wallpaper>,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (Wallpaper) -> Unit,
) {
    WallpaperGrid(
        wallpapers = wallpapers,
        loading = false,
        initialLoadFinished = true,
        endReached = true,
        pageError = null,
        onOpen = onOpen,
        onLoadMore = {},
        onRetry = {},
        emptyTitle = "No favorites yet",
        emptyMessage = "Open a wallpaper's details and choose Favorite to keep it here. Favorites save details, not the original image file.",
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderBar("Favorites", "Saved locally · not downloaded automatically", onMenu = onMenu, trailing = {
                    GlassIconButton(onClick = onSearch, description = "Search wallpapers", icon = Icons.Rounded.Search)
                })
                SectionTitle("Saved wallpapers", "${wallpapers.size} item${if (wallpapers.size == 1) "" else "s"}")
                if (wallpapers.isNotEmpty()) {
                    Text(
                        "Open a wallpaper to manage its favorite or collection.",
                        Modifier.fillMaxWidth(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Aero.colors.textSecondary,
                    )
                }
            }
        },
    )
}
