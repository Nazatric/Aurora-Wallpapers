package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.feature.home.WallpaperGrid

@Composable
fun SearchScreen(
    feed: FeedUiState,
    favorites: Set<String>,
    queryText: String,
    enabledSources: Set<WallpaperSource>,
    onQueryText: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMenu: () -> Unit,
    onSource: (Set<WallpaperSource>) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onFavorite: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val request = feed.request
    val filter = request.filter
    val requestedSources = filter.sources.ifEmpty { enabledSources }
    val effectiveSources = requestedSources.intersect(enabledSources)
    val selectedSourcesDisabled = effectiveSources.isEmpty()
    val openverseUsesRelevance = WallpaperSource.OPENVERSE in effectiveSources && filter.sort != SortOption.RELEVANCE
    val sourceNotes = feed.statuses.mapNotNull { status ->
        (status.state as? SourceState.Skipped)?.let { "${status.source.displayName}: ${it.reason}" }
    }.distinct().joinToString(" · ").takeIf(String::isNotBlank)

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
        emptyTitle = when {
            enabledSources.isEmpty() -> "No wallpaper source enabled"
            selectedSourcesDisabled -> "Selected source is turned off"
            else -> "No wallpapers matched"
        },
        emptyMessage = when {
            enabledSources.isEmpty() -> "Turn on Wallhaven or Openverse in Sources before searching."
            selectedSourcesDisabled -> "Enable the selected source in Sources, or choose another enabled source."
            else -> "Try a broader search or adjust the filters."
        },
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                HeaderBar("Search", onMenu = onMenu)
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { onQueryText(it.take(200)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Aero.colors.accent) },
                    trailingIcon = {
                        TextButton(onClick = {
                            focusManager.clearFocus()
                            onSubmit(queryText)
                        }) { Text("Search") }
                    },
                    placeholder = { Text("Search wallpapers", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        onSubmit(queryText)
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Aero.colors.textPrimary,
                        unfocusedTextColor = Aero.colors.textPrimary,
                        focusedBorderColor = Aero.colors.accent,
                        unfocusedBorderColor = Aero.colors.glassRimDark.copy(alpha = .7f),
                        focusedLabelColor = Aero.colors.accent,
                        unfocusedLabelColor = Aero.colors.textTertiary,
                        cursorColor = Aero.colors.accent,
                    ),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Sources", modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenFilters) {
                        Icon(Icons.Rounded.FilterList, contentDescription = null)
                        Text(if (filter.activeCount == 0) "Filters" else "Filters · ${filter.activeCount}")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    GlassPill("All", filter.sources.isEmpty(), enabled = enabledSources.isNotEmpty(), onClick = { onSource(emptySet()) })
                    WallpaperSource.integrated.forEach { source ->
                        GlassPill(
                            source.displayName,
                            filter.sources == setOf(source),
                            enabled = source in enabledSources,
                            onClick = { onSource(setOf(source)) },
                        )
                    }
                }
                sourceNotes?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Aero.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (openverseUsesRelevance) {
                    Text(
                        "Openverse keeps relevance order; other selected sources use their own ranking.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Aero.colors.textTertiary,
                    )
                }
                if (WallpaperSource.OPENVERSE in effectiveSources) {
                    Text(
                        "Made using Openverse; not endorsed or certified by Openverse.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Aero.colors.textTertiary,
                    )
                }
                SectionTitle("Results", if (feed.items.isEmpty()) null else "${feed.items.size} loaded")
            }
        },
    )
}
