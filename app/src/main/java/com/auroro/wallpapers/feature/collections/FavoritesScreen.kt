package com.auroro.wallpapers.feature.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.EmptyState
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.feature.home.WallpaperGrid

@Composable
fun FavoritesScreen(
    wallpapers: List<Wallpaper>,
    favoriteKeys: Set<String>,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onRemove: (Wallpaper) -> Unit,
) {
    WallpaperGrid(
        wallpapers = wallpapers,
        favoriteKeys = favoriteKeys,
        loading = false,
        initialLoadFinished = true,
        endReached = true,
        pageError = null,
        onOpen = onOpen,
        onFavorite = onRemove,
        onLoadMore = {},
        onRetry = {},
        emptyTitle = "No favorites yet",
        emptyMessage = "Tap the heart on a wallpaper to keep it here. Favoriting saves its details, not the original image.",
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderBar("Favorites", "Saved locally · original images aren't downloaded automatically", onMenu = onMenu, trailing = {
                    GlassIconButton(onClick = onSearch, description = "Search wallpaper", icon = Icons.Rounded.Search)
                })
                SectionTitle("Your favorites", "${wallpapers.size} saved wallpaper${if (wallpapers.size == 1) "" else "s"}")
            }
        },
    )
}
