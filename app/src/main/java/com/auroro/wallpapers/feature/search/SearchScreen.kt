package com.auroro.wallpapers.feature.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.app.FeedUiState
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassSurface
import com.auroro.wallpapers.core.design.HeaderBar
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
    recentSearches: List<String>,
    onQueryText: (String) -> Unit,
    onSelectRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
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
    val activeSources = (filter.sources.ifEmpty { enabledSources }).intersect(enabledSources)
    val sourceNotes = feed.statuses.mapNotNull { status ->
        (status.state as? SourceState.Skipped)?.let { "${status.source.displayName}: ${it.reason}" }
    }.distinct().joinToString("\n").takeIf(String::isNotBlank)

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
            enabledSources.isEmpty() -> "No catalogue enabled"
            activeSources.isEmpty() -> "Selected catalogue is off"
            else -> "No matching wallpapers"
        },
        emptyMessage = when {
            enabledSources.isEmpty() -> "Turn on an approved source in Settings → Sources."
            activeSources.isEmpty() -> "Choose an enabled source or switch it back on in Sources."
            else -> "Try a broader subject or adjust the source and dimension filters."
        },
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                HeaderBar("Search", "Search approved catalogues", onMenu = onMenu)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { onQueryText(it.take(200)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Aero.colors.accent) },
                            trailingIcon = {
                                if (queryText.isNotEmpty()) {
                                    IconButton(onClick = { onQueryText(""); focusManager.clearFocus() }) {
                                        Icon(Icons.Rounded.Close, "Clear query", tint = Aero.colors.textSecondary)
                                    }
                                }
                            },
                            placeholder = { Text("Subject, place, artist…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                if (queryText.trim().replace(Regex("\\s+"), " ") != request.normalizedQuery) onSubmit(queryText)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Aero.colors.textPrimary,
                                unfocusedTextColor = Aero.colors.textPrimary,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedLabelColor = Aero.colors.accent,
                                unfocusedLabelColor = Aero.colors.textTertiary,
                                cursorColor = Aero.colors.accent,
                            ),
                        )
                    }
                    GlassIconButton(
                        onClick = onOpenFilters,
                        description = if (filter.activeCount == 0) "Open filters" else "Open filters, ${filter.activeCount} active",
                        icon = Icons.Rounded.FilterList,
                        tint = if (filter.activeCount == 0) Aero.colors.textPrimary else Aero.colors.accent,
                        selected = filter.activeCount > 0,
                    )
                }

                if (queryText.isBlank() && recentSearches.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.labelLarge, color = Aero.colors.textSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearRecentSearches) { Text("Clear history") }
                    }
                    recentSearches.take(5).forEach { query ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                focusManager.clearFocus()
                                onQueryText(query)
                                onSelectRecentSearch(query)
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Aero.colors.textTertiary, modifier = Modifier.size(18.dp))
                            Text(query, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, color = Aero.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceTab("All", filter.sources.isEmpty(), enabledSources.isNotEmpty()) { onSource(emptySet()) }
                    WallpaperSource.integrated.forEach { source ->
                        SourceTab(source.displayName, filter.sources == setOf(source), source in enabledSources) {
                            onSource(setOf(source))
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Orientation.entries.forEach { orientation ->
                        SourceTab(orientation.label, filter.orientation == orientation, true) {
                            onOrientation(orientation)
                        }
                    }
                    TextButton(onClick = onOpenFilters) { Text("More filters") }
                }

                if (sourceNotes != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Aero.colors.warning.copy(alpha = .09f),
                        border = BorderStroke(1.dp, Aero.colors.warning.copy(alpha = .22f)),
                    ) {
                        Text(sourceNotes, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Results", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("${feed.items.size}", style = MaterialTheme.typography.labelMedium, color = Aero.colors.textTertiary)
                }
            }
        },
    )
}

@Composable
private fun SourceTab(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).semantics { androidx.compose.ui.semantics.selected = selected }.padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = when {
            !enabled -> Aero.colors.textTertiary.copy(alpha = .55f)
            selected -> Aero.colors.accent
            else -> Aero.colors.textSecondary
        }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Surface(
            Modifier.fillMaxWidth().size(width = 28.dp, height = 3.dp),
            color = if (selected) Aero.colors.accent else Color.Transparent,
            shape = RoundedCornerShape(2.dp),
        ) { }
    }
}
