package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPill
import com.auroro.wallpapers.core.design.HeaderBar
import com.auroro.wallpapers.core.design.SectionTitle
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.feature.home.WallpaperGrid
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    feed: FeedUiState,
    queryText: String,
    enabledSources: Set<WallpaperSource>,
    onQueryText: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onMenu: () -> Unit,
    onSource: (Set<WallpaperSource>) -> Unit,
    onOrientation: (Orientation) -> Unit,
    onOpen: (Wallpaper) -> Unit,
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
    val openverseUsesRelevance = WallpaperSource.OPENVERSE in effectiveSources && (
        filter.sort != com.auroro.wallpapers.core.model.SortOption.RELEVANCE ||
            request.normalizedQuery.isEmpty() && WallpaperSource.WALLHAVEN in effectiveSources
        )
    val sourceNotes = feed.statuses.mapNotNull { status ->
        (status.state as? SourceState.Skipped)?.let { "${status.source.displayName}: ${it.reason}" }
    }.distinct().joinToString(" · ").takeIf(String::isNotBlank)

    // Debounce edits so a settled query refreshes quickly without sending a request for every keypress.
    LaunchedEffect(queryText, request.query, filter) {
        val normalized = queryText.trim().replace(Regex("\\s+"), " ")
        if (normalized != request.normalizedQuery) {
            delay(520)
            onSubmit(queryText)
        }
    }

    WallpaperGrid(
        wallpapers = feed.items,
        loading = feed.loading,
        initialLoadFinished = feed.initialLoadFinished,
        endReached = feed.endReached,
        pageError = feed.pageError,
        onOpen = onOpen,
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
            else -> "Try a broader phrase or adjust the shape and size filters."
        },
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderBar("Search", onMenu = onMenu)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { onQueryText(it.take(200)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(17.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Aero.colors.accent) },
                        trailingIcon = {
                            if (queryText.isNotEmpty()) {
                                IconButton(onClick = { onQueryText(""); focusManager.clearFocus() }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Aero.colors.textSecondary)
                                }
                            }
                        },
                        placeholder = { Text("Try ocean, forest or a place", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            if (queryText.trim().replace(Regex("\\s+"), " ") != request.normalizedQuery) onSubmit(queryText)
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Aero.colors.textPrimary,
                            unfocusedTextColor = Aero.colors.textPrimary,
                            focusedBorderColor = Aero.colors.accent,
                            unfocusedBorderColor = Aero.colors.glassRimDark.copy(alpha = 0.62f),
                            focusedLabelColor = Aero.colors.accent,
                            unfocusedLabelColor = Aero.colors.textTertiary,
                            cursorColor = Aero.colors.accent,
                        ),
                    )
                    GlassIconButton(
                        onClick = onOpenFilters,
                        description = if (filter.activeCount == 0) "Open filters" else "Open filters, ${filter.activeCount} active",
                        icon = Icons.Rounded.FilterList,
                        tint = if (filter.activeCount == 0) Aero.colors.textPrimary else Aero.colors.accent,
                        selected = filter.activeCount > 0,
                    )
                }
                Text("Results update when you pause typing.", style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
                SectionTitle("Sources")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Aero.colors.warning, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (openverseUsesRelevance) {
                    Text(
                        if (request.normalizedQuery.isEmpty() && filter.sort == com.auroro.wallpapers.core.model.SortOption.RELEVANCE && WallpaperSource.WALLHAVEN in effectiveSources) {
                            "Wallhaven uses newest for a blank search; Openverse keeps relevance. Provider order isn't blended."
                        } else {
                            "Openverse keeps its own relevance order; provider rankings aren't blended."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Aero.colors.textTertiary,
                    )
                }
                SectionTitle("Orientation", "Measured from the image's original pixel dimensions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Orientation.entries.forEach { orientation ->
                        GlassPill(
                            text = orientation.label,
                            selected = filter.orientation == orientation,
                            onClick = { onOrientation(orientation) },
                        )
                    }
                }
                SectionTitle("Results", if (feed.items.isEmpty()) null else "${feed.items.size} loaded")
            }
        },
    )
}
