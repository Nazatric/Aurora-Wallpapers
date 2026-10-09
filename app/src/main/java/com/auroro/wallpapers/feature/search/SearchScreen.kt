package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.design.SourceStatusPanel
import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.feature.home.WallpaperGrid

@Composable
fun SearchScreen(
    feed: FeedUiState,
    favoriteKeys: Set<String>,
    initialQuery: String,
    onMenu: () -> Unit,
    onOpenFilters: () -> Unit,
    onSubmit: (String, WallpaperFilter) -> Unit,
    onFiltersChanged: (WallpaperFilter) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onFavorite: (Wallpaper) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    LaunchedEffect(initialQuery) { text = initialQuery }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    WallpaperGrid(
        wallpapers = feed.items,
        favoriteKeys = favoriteKeys,
        loading = feed.loading,
        initialLoadFinished = feed.initialLoadFinished,
        endReached = feed.endReached,
        pageError = feed.pageError,
        onOpen = onOpen,
        onFavorite = onFavorite,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        emptyTitle = if (text.isBlank()) "No wallpapers yet" else "No matches for “${text.trim()}”",
        emptyMessage = "Try another phrase, widen the filters, or choose a source that is available.",
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HeaderBar(
                    title = "Search",
                    subtitle = "Live results from available wallpaper sources",
                    onMenu = onMenu,
                    trailing = {
                        BadgedBox(badge = { if (feed.request.filter.activeCount > 0) Badge { Text(feed.request.filter.activeCount.toString()) } }) {
                            GlassIconButton(onClick = onOpenFilters, description = "Open filters", icon = Icons.Rounded.Tune)
                        }
                    },
                )
                SearchBox(
                    value = text,
                    onValueChange = { text = it },
                    onSubmit = {
                        focus.clearFocus()
                        keyboard?.hide()
                        onSubmit(text, feed.request.filter)
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Search in", "Each source keeps its own page, ranking and limits.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill("All", feed.request.filter.sources.isEmpty(), onClick = { onFiltersChanged(feed.request.filter.copy(sources = emptySet())) })
                        WallpaperSource.entries.forEach { source ->
                            GlassPill(source.displayName, feed.request.filter.sources == setOf(source), onClick = { onFiltersChanged(feed.request.filter.copy(sources = setOf(source))) })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Quick filters", "Geometry is checked against the provider's real pixel dimensions.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ratio = feed.request.filter.aspect
                        GlassPill("Any ratio", ratio == AspectFilter.Any, onClick = { onFiltersChanged(feed.request.filter.copy(aspect = AspectFilter.Any)) })
                        listOf(AspectPreset.R9_16, AspectPreset.R16_9, AspectPreset.R4_3, AspectPreset.R1_1).forEach { preset ->
                            GlassPill(preset.label, ratio == AspectFilter.Preset(preset), onClick = { onFiltersChanged(feed.request.filter.copy(aspect = AspectFilter.Preset(preset))) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val resolution = feed.request.filter.resolution
                        GlassPill("Any size", resolution == ResolutionFilter.Any, onClick = { onFiltersChanged(feed.request.filter.copy(resolution = ResolutionFilter.Any)) })
                        listOf(ResolutionPreset.P720, ResolutionPreset.P1080, ResolutionPreset.P1440, ResolutionPreset.K4).forEach { preset ->
                            GlassPill(preset.label, resolution == ResolutionFilter.Preset(preset), onClick = { onFiltersChanged(feed.request.filter.copy(resolution = ResolutionFilter.Preset(preset))) })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Sort", "Popularity and relevance are source-specific, not globally comparable.")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val availableSorts = if (feed.request.normalizedQuery.isBlank()) SortOption.entries.filter { it != SortOption.RELEVANCE } else SortOption.entries.toList()
                        availableSorts.forEach { sort ->
                            GlassPill(sort.label, feed.request.filter.sort == sort, onClick = { onFiltersChanged(feed.request.filter.copy(sort = sort)) })
                        }
                    }
                }

                if (feed.statuses.isNotEmpty() && feed.statuses.any { it.state !is com.auroro.wallpapers.core.data.SourceState.Ok }) {
                    SourceStatusPanel(feed.statuses, onRetry = onRetry, onOpen = onOpenExternal, compact = true)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FilterList, null, tint = Aero.colors.accent, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        if (feed.request.normalizedQuery.isEmpty()) "Browse results" else "Results for ${feed.request.normalizedQuery}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Aero.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text("${feed.items.size} shown", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
            }
        },
    )
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), elevation = 9.dp) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Aero.colors.accent, modifier = Modifier.padding(end = 10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).semantics { contentDescription = "Search wallpapers" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = Aero.colors.textPrimary)),
                cursorBrush = SolidColor(Aero.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("Try “ocean”, “mountains”, or “minimal”", color = Aero.colors.textTertiary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            GlassIconButton(onClick = onSubmit, description = "Submit search", icon = Icons.Rounded.Search, modifier = Modifier.padding(vertical = 4.dp), tint = Aero.colors.accent)
        }
    }
}
