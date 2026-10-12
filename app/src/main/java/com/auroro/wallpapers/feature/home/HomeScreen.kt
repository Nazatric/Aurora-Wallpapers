package com.auroro.wallpapers.feature.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.app.HomeTab
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.feature.categories.SEARCH_TOPICS

@Composable
fun HomeScreen(
    feed: FeedUiState,
    selectedTab: HomeTab,
    gridStateHolder: SaveableStateHolder,
    retainedGridKeys: MutableList<String>,
    onTab: (HomeTab) -> Unit,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onCategory: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val gridStateKey = "${selectedTab.name}:${feed.discoverySession?.sessionId.orEmpty()}"
    LaunchedEffect(gridStateKey) {
        retainedGridKeys.remove(gridStateKey)
        retainedGridKeys.add(gridStateKey)
        while (retainedGridKeys.size > 8) gridStateHolder.removeState(retainedGridKeys.removeAt(0))
    }
    val sourceNotes = feed.statuses.mapNotNull { status ->
        (status.state as? SourceState.Skipped)?.let { "${status.source.displayName}: ${it.reason}" }
    }.distinct().joinToString(" · ").takeIf(String::isNotBlank)
    val featured = feed.items.firstOrNull { it.hasKnownDimensions && it.aspectRatio >= 1.2f && it.thumbUrl.isNotBlank() }
    val gridItems = if (selectedTab == HomeTab.FOR_YOU && featured != null) {
        feed.items.filterNot { it.key == featured.key }
    } else feed.items

    gridStateHolder.SaveableStateProvider(gridStateKey) {
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
                feed.statuses.isNotEmpty() && feed.statuses.all { it.state is SourceState.Skipped } -> "Enable a usable catalogue in Settings → Sources."
                else -> "Try another subject, or open Search to change the source and image filters."
            },
            headerContent = {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    HeaderBar(
                        title = "Auroro",
                        subtitle = "Wallpaper discovery",
                        onMenu = onMenu,
                        trailing = {
                            if (selectedTab == HomeTab.FOR_YOU) {
                                GlassIconButton(onClick = onRefresh, description = "Refresh For You", icon = Icons.Rounded.Refresh)
                            }
                            GlassIconButton(onClick = onSearch, description = "Search wallpapers", icon = Icons.Rounded.Search)
                        },
                    )

                    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onSearch) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, null, tint = Aero.colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(11.dp))
                            Text("Search images, artists or places", style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
                            Text("Search", style = MaterialTheme.typography.labelLarge, color = Aero.colors.accent)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeTab.entries.forEach { tab ->
                            HomeTabLabel(tab, selectedTab == tab) { onTab(tab) }
                        }
                    }

                    if (selectedTab == HomeTab.FOR_YOU) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SEARCH_TOPICS.take(5).forEach { topic ->
                                Column(
                                    Modifier.semantics { contentDescription = "Explore ${topic.title}" }
                                        .clickable(role = Role.Button) { onCategory(topic.query) }
                                        .padding(vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(shape = CircleShape, color = topic.accent.copy(alpha = .13f)) {
                                        Icon(topic.icon, null, tint = topic.accent, modifier = Modifier.padding(11.dp).size(21.dp))
                                    }
                                    Text(topic.title, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val contributingCatalogues = feed.items.map { it.source }.distinct().size
                            Text(
                                when {
                                    feed.discoveryValidatedRecipeCount > 0 ->
                                        "$contributingCatalogues catalogues · ${feed.discoveryValidatedRecipeCount} validated source searches"
                                    contributingCatalogues > 0 -> "$contributingCatalogues catalogues contributing to this mix"
                                    else -> "Validated source-backed discovery"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Aero.colors.textSecondary,
                                maxLines = 2,
                            )
                            feed.cacheNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Aero.colors.textTertiary, maxLines = 2) }
                        }
                    }

                    sourceNotes?.let { note ->
                        Text(note, style = MaterialTheme.typography.labelSmall, color = Aero.colors.warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }

                    if (selectedTab == HomeTab.FOR_YOU && featured != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("Featured", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                            WallpaperTile(featured, onClick = { onOpen(featured) })
                        }
                    }

                    SectionTitle(
                        title = when (selectedTab) {
                            HomeTab.FOR_YOU -> "For You"
                            HomeTab.POPULAR -> "Popular"
                            HomeTab.LATEST -> "Latest"
                            HomeTab.RANDOM -> "Random"
                        },
                        subtitle = when (selectedTab) {
                            HomeTab.FOR_YOU -> feed.forYouLabel
                            else -> null
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun HomeTabLabel(tab: HomeTab, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(tab.label, style = MaterialTheme.typography.labelLarge, color = if (selected) Aero.colors.accent else Aero.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Surface(Modifier.size(width = 24.dp, height = 3.dp), shape = RoundedCornerShape(2.dp), color = if (selected) Aero.colors.accent else Aero.colors.glassRimDark.copy(alpha = .18f)) { }
    }
}
