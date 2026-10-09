package com.auroro.wallpapers.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.app.HomeTab
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.design.SourceDot
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource

private val shortcuts = com.auroro.wallpapers.feature.categories.SEARCH_TOPICS

@Composable
fun HomeScreen(
    feed: FeedUiState,
    favorites: Set<String>,
    selectedTab: HomeTab,
    enabledSources: Set<WallpaperSource>,
    onTab: (HomeTab) -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSource: (Set<WallpaperSource>) -> Unit,
    onCategory: (String) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onFavorite: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val featured = feed.items.firstOrNull()
    val galleryItems = if (featured == null) feed.items else feed.items.drop(1)
    val requestedSources = feed.request.filter.sources.ifEmpty { enabledSources }
    val selectedSourcesDisabled = requestedSources.none { it in enabledSources }
    val openverseSelected = WallpaperSource.OPENVERSE in requestedSources && WallpaperSource.OPENVERSE in enabledSources
    val sourceNotes = feed.statuses.mapNotNull { status ->
        (status.state as? SourceState.Skipped)?.let { "${status.source.displayName}: ${it.reason}" }
    }.distinct().joinToString(" · ").takeIf(String::isNotBlank)
    WallpaperGrid(
        wallpapers = galleryItems,
        favoriteKeys = favorites,
        loading = feed.loading,
        initialLoadFinished = feed.initialLoadFinished,
        endReached = feed.endReached,
        pageError = feed.pageError,
        onOpen = onOpen,
        onFavorite = onFavorite,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        emptyTitle = when {
            enabledSources.isEmpty() -> "No wallpaper source enabled"
            selectedSourcesDisabled -> "Selected source is turned off"
            else -> "No matching wallpapers"
        },
        emptyMessage = when {
            enabledSources.isEmpty() -> "Turn on Wallhaven or Openverse in Sources before browsing."
            selectedSourcesDisabled -> "Enable the selected source in Sources, or choose another enabled source."
            else -> "Adjust the source or filters, then search the live catalogues again."
        },
        hasLeadingResult = featured != null,
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                HeaderBar(
                    title = "Auroro Wallpapers",
                    onMenu = onMenu,
                    trailing = {
                        GlassIconButton(onClick = onSearch, description = "Search wallpapers", icon = Icons.Rounded.Search)
                        Spacer(Modifier.width(7.dp))
                        GlassIconButton(onClick = onSettings, description = "Settings", icon = Icons.Rounded.Settings)
                    },
                )
                SearchPrompt(onClick = onSearch)
                if (featured != null) FeaturedWallpaper(featured, favorite = featured.key in favorites, onOpen = onOpen, onFavorite = onFavorite)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SectionTitle("Sources")
                    SourceChoices(feed.request.filter.sources, enabledSources, onSource)
                    sourceNotes?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (openverseSelected) {
                        Text(
                            "Made using Openverse; not endorsed or certified by Openverse.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Aero.colors.textTertiary,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SectionTitle("Discover")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        HomeTab.entries.forEach { tab ->
                            GlassPill(tab.label, selectedTab == tab, onClick = { onTab(tab) })
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SectionTitle("Explore by topic")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        shortcuts.forEach { shortcut ->
                            GlassPill(
                                shortcut.title,
                                false,
                                leading = { Icon(shortcut.icon, null, tint = Aero.colors.accent, modifier = Modifier.size(16.dp)) },
                                onClick = { onCategory(shortcut.query) },
                            )
                        }
                    }
                }
                SectionTitle(
                    title = when (selectedTab) {
                        HomeTab.FOR_YOU -> "Wallpapers"
                        HomeTab.POPULAR -> "Popular on Wallhaven"
                        HomeTab.LATEST -> "Latest from Wallhaven"
                        HomeTab.RANDOM -> "Random from Wallhaven"
                    },
                    subtitle = feed.forYouLabel.takeIf { selectedTab == HomeTab.FOR_YOU },
                    trailing = { GlassIconButton(onClick = onSearch, description = "Search wallpapers", icon = Icons.Rounded.Search) },
                )
            }
        },
    )
}

@Composable
private fun SearchPrompt(onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Aero.colors.accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("Search wallpapers and places", style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SourceChoices(
    selected: Set<WallpaperSource>,
    enabledSources: Set<WallpaperSource>,
    onSource: (Set<WallpaperSource>) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        GlassPill("All", selected.isEmpty(), enabled = enabledSources.isNotEmpty(), onClick = { onSource(emptySet()) })
        WallpaperSource.integrated.forEach { source ->
            GlassPill(
                source.displayName,
                selected == setOf(source),
                enabled = source in enabledSources,
                onClick = { onSource(setOf(source)) },
            )
        }
    }
}

@Composable
private fun FeaturedWallpaper(
    wallpaper: Wallpaper,
    favorite: Boolean,
    onOpen: (Wallpaper) -> Unit,
    onFavorite: (Wallpaper) -> Unit,
) {
    val context = LocalContext.current
    var imageFailed by remember(wallpaper.previewUrl) { mutableStateOf(false) }
    val metadataRatio = if (wallpaper.hasKnownDimensions && wallpaper.aspectRatio.isFinite() && wallpaper.aspectRatio > 0f) wallpaper.aspectRatio else null
    var ratio by remember(wallpaper.key, wallpaper.width, wallpaper.height) { mutableFloatStateOf(metadataRatio ?: 1f) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.heightIn(max = 340.dp)
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(20.dp))
                .background(Aero.colors.surfaceSolid)
                .clickable(role = Role.Button) { onOpen(wallpaper) },
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(wallpaper.previewUrl)
                    .memoryCacheKey(wallpaper.previewUrl)
                    .diskCacheKey(wallpaper.previewUrl)
                    .crossfade(160)
                    .build(),
                contentDescription = wallpaper.title ?: "${wallpaper.width} by ${wallpaper.height} wallpaper",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    if (metadataRatio == null) {
                        val image = state.result.image
                        if (image.width > 0 && image.height > 0) ratio = image.width.toFloat() / image.height
                    }
                },
                onError = { imageFailed = true },
            )
            if (imageFailed) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = .72f), modifier = Modifier.size(22.dp))
                    Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .84f))
                }
            }
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { SourceDot(wallpaper.source) }
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                GlassIconButton(
                    onClick = { onFavorite(wallpaper) },
                    description = if (favorite) "Remove from favorites" else "Add to favorites",
                    icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    selected = favorite,
                    tint = if (favorite) Aero.colors.accentLight else Aero.colors.textPrimary,
                )
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(horizontal = 11.dp, vertical = 10.dp),
            ) {
                val label = wallpaper.title ?: wallpaper.category ?: wallpaper.tags.firstOrNull()?.name
                if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val contributor = when (wallpaper.source) {
                    WallpaperSource.OPENVERSE -> listOfNotNull(
                        wallpaper.creatorName?.let { "Creator: $it" },
                        wallpaper.licenseCode?.uppercase(java.util.Locale.US),
                    ).joinToString(" · ").takeIf(String::isNotBlank)
                    WallpaperSource.WALLHAVEN -> wallpaper.creatorName?.let { "Uploader: $it" }
                    WallpaperSource.ARCHIVED -> null
                }
                contributor?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .84f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    "${wallpaper.width} × ${wallpaper.height} · ${wallpaper.source.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.84f),
                    maxLines = 1,
                )
            }
        }
    }
}
