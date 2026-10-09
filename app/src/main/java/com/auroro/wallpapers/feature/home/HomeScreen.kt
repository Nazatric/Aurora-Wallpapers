package com.auroro.wallpapers.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.HdrStrong
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.app.HomeTab
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.AppLogo
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.design.SourceStatusPanel
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource

private data class SearchShortcut(val title: String, val query: String, val icon: ImageVector, val tint: Color, val subtitle: String)

private val shortcuts = listOf(
    SearchShortcut("Ocean", "ocean water", Icons.Rounded.Water, Color(0xFF59DDF3), "Blue horizons"),
    SearchShortcut("Nature", "nature forest", Icons.Rounded.Forest, Color(0xFF6FDEA9), "A little green"),
    SearchShortcut("Sky", "sky clouds", Icons.Rounded.Cloud, Color(0xFF9CCEFF), "Open skies"),
    SearchShortcut("Mountains", "mountains landscape", Icons.Rounded.Landscape, Color(0xFFFFC979), "Far away"),
    SearchShortcut("Space", "space stars", Icons.Rounded.NightsStay, Color(0xFFC5B2FF), "Out there"),
    SearchShortcut("Abstract", "abstract", Icons.Rounded.HdrStrong, Color(0xFFFF9DBD), "Shapes & light"),
)

@Composable
fun HomeScreen(
    feed: FeedUiState,
    favorites: Set<String>,
    selectedTab: HomeTab,
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
    onOpenExternal: (String) -> Unit,
) {
    WallpaperGrid(
        wallpapers = feed.items,
        favoriteKeys = favorites,
        loading = feed.loading,
        initialLoadFinished = feed.initialLoadFinished,
        endReached = feed.endReached,
        pageError = feed.pageError,
        onOpen = onOpen,
        onFavorite = onFavorite,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        emptyTitle = "No wallpapers yet",
        emptyMessage = "Try another source or a broader filter. Auroro only shows wallpaper records returned by the selected providers.",
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                HeaderBar(
                    title = "Auroro Wallpapers",
                    subtitle = "FIND YOUR LITTLE PIECE OF SKY",
                    onMenu = onMenu,
                    trailing = {
                        GlassIconButton(onClick = onSearch, description = "Search wallpapers", icon = Icons.Rounded.Search)
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(onClick = onSettings, description = "Settings", icon = Icons.Rounded.Settings)
                    },
                )

                GlassPanel(Modifier.fillMaxWidth(), elevation = 12.dp) {
                    Column(Modifier.padding(horizontal = 17.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("A breath of elsewhere", style = MaterialTheme.typography.headlineSmall, color = Aero.colors.textPrimary)
                                Text(
                                    feed.forYouLabel ?: "Fresh wallpapers, gathered from real sources.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Aero.colors.textSecondary,
                                    maxLines = 2,
                                )
                            }
                            AppLogo(Modifier.size(52.dp), 52.dp)
                        }
                        Spacer(Modifier.height(4.dp))
                        SearchPrompt(onClick = onSearch)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionTitle("Discover", "Choose a mood. The sources keep their own ranking.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeTab.entries.forEach { tab ->
                            GlassPill(tab.label, selectedTab == tab, onClick = { onTab(tab) })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionTitle("Sources", "Public SFW search · additional sources are clearly labelled")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill("All", feed.request.filter.sources.isEmpty(), onClick = { onSource(emptySet()) })
                        WallpaperSource.entries.forEach { source ->
                            GlassPill(source.displayName, feed.request.filter.sources == setOf(source), onClick = { onSource(setOf(source)) })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("A little inspiration", "Shortcuts run real searches; no demo wallpaper catalogue.")
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        shortcuts.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                row.forEach { shortcut ->
                                    ShortcutCard(shortcut, Modifier.weight(1f)) { onCategory(shortcut.query) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (feed.statuses.any { it.state !is com.auroro.wallpapers.core.data.SourceState.Ok }) {
                    SourceStatusPanel(feed.statuses, onRetry = onRetry, onOpen = onOpenExternal, compact = true)
                }

                SectionTitle(
                    title = when (selectedTab) {
                        HomeTab.FOR_YOU -> "Picked for you"
                        HomeTab.POPULAR -> "Popular on Wallhaven"
                        HomeTab.LATEST -> "Just added"
                        HomeTab.RANDOM -> "A little surprise"
                    },
                    subtitle = if (feed.items.isEmpty()) null else "${feed.items.size} wallpaper${if (feed.items.size == 1) "" else "s"} · live provider results",
                    trailing = {
                        GlassIconButton(onClick = onSearch, description = "Search this collection", icon = Icons.Rounded.Search)
                    },
                )
            }
        },
    )
}

@Composable
private fun SearchPrompt(onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Aero.colors.accent, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("Search wallpapers, places, colours…", style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Bolt, null, tint = Aero.colors.emerald, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ShortcutCard(item: SearchShortcut, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(37.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Brush.verticalGradient(listOf(item.tint.copy(alpha = .7f), item.tint.copy(alpha = .22f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = Aero.colors.textPrimary)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary, maxLines = 1)
            }
        }
    }
}
