package com.auroro.wallpapers.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.app.HomeTab
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.feature.categories.SEARCH_TOPICS

@Composable
fun HomeScreen(
    feed: FeedUiState,
    selectedTab: HomeTab,
    onTab: (HomeTab) -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onCategory: (String) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val sourceNotes = feed.statuses.mapNotNull { status ->
        (status.state as? SourceState.Skipped)?.let { "${status.source.displayName}: ${it.reason}" }
    }.distinct().joinToString(" · ").takeIf(String::isNotBlank)
    val featured = feed.items
        .takeIf { it.size > 1 }
        ?.firstOrNull { it.hasKnownDimensions && it.aspectRatio >= 1.15f && it.thumbUrl.isNotBlank() }
    val gridItems = if (featured == null) feed.items else feed.items.filterNot { it.key == featured.key }

    WallpaperGrid(
        wallpapers = gridItems,
        loading = feed.loading,
        initialLoadFinished = feed.initialLoadFinished,
        endReached = feed.endReached,
        pageError = feed.pageError,
        onOpen = onOpen,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        emptyTitle = when {
            feed.statuses.isNotEmpty() && feed.statuses.all { it.state is SourceState.Skipped } -> "No catalogue available"
            else -> "No wallpapers found"
        },
        emptyMessage = when {
            feed.statuses.isNotEmpty() && feed.statuses.all { it.state is SourceState.Skipped } -> "Enable Wallhaven or Openverse in Sources to browse."
            else -> "Try another topic or open Search to adjust your sources and filters."
        },
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                HeaderBar(
                    title = "Auroro Wallpapers",
                    onMenu = onMenu,
                    trailing = {
                        GlassIconButton(onClick = onSearch, description = "Search wallpapers", icon = Icons.Rounded.Search)
                    },
                )
                SearchPrompt(onClick = onSearch)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeTab.entries.forEach { tab ->
                        GlassPill(tab.label, selectedTab == tab, onClick = { onTab(tab) })
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Explore topics")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SEARCH_TOPICS.forEach { topic ->
                            GlassPill(
                                text = topic.title,
                                selected = false,
                                leading = { Icon(topic.icon, null, tint = topic.accent, modifier = Modifier.size(17.dp)) },
                                selectionRole = null,
                                onClick = { onCategory(topic.query) },
                            )
                        }
                    }
                }
                sourceNotes?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textSecondary, maxLines = 2)
                }
                if (featured != null) {
                    SectionTitle("Featured")
                    WallpaperTile(featured, onClick = { onOpen(featured) })
                }
                SectionTitle(
                    title = when (selectedTab) {
                        HomeTab.FOR_YOU -> "More wallpapers"
                        HomeTab.POPULAR -> "Popular"
                        HomeTab.LATEST -> "Latest"
                        HomeTab.RANDOM -> "Random"
                    },
                    subtitle = feed.forYouLabel?.removePrefix("Based on your saved tags: ")
                        ?.takeIf { selectedTab == HomeTab.FOR_YOU },
                )
            }
        },
    )
}

@Composable
private fun SearchPrompt(onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Aero.colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Search wallpapers",
                style = MaterialTheme.typography.bodyMedium,
                color = Aero.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text("Search", style = MaterialTheme.typography.labelMedium, color = Aero.colors.accent)
        }
    }
}
