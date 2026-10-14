package com.auroro.wallpapers.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.ui.components.AuroraChip
import com.auroro.wallpapers.ui.components.WallpaperArt
import com.auroro.wallpapers.ui.components.artAspect
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurfaceBright
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

private const val NEAR_END_THRESHOLD = 4

/**
 * Slice-3 search (mockup panel 4): search field, source chips and a two-column staggered
 * grid. Cells keep each wallpaper's real aspect ratio, so the grid reads as varied art
 * instead of uniformly cropped tiles.
 */
@Composable
fun SearchScreen(
    state: SearchViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onToggleSource: (WallpaperSource) -> Unit,
    onSelectAllSources: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyStaggeredGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.let { it.index >= info.totalItemsCount - NEAR_END_THRESHOLD } ?: false
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.size(16.dp, 1.dp))
            AuroraChip(
                label = stringResource(R.string.chip_all_sources),
                active = state.sources.isEmpty(),
                onClick = onSelectAllSources,
            )
            WallpaperSource.integrated.forEach { source ->
                AuroraChip(
                    label = source.displayName,
                    active = source in state.sources,
                    onClick = { onToggleSource(source) },
                )
            }
            Spacer(Modifier.size(16.dp, 1.dp))
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = AuroroTextTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        when {
            state.searching && state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = AuroroAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.search_searching),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroroTextSecondary,
                )
            }

            !state.hasSearched && state.items.isEmpty() -> SearchIdleState(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
            )

            state.hasSearched && state.items.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.search_no_results_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.search_no_results_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroroTextSecondary,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
                Spacer(Modifier.size(14.dp))
                AuroraChip(label = stringResource(R.string.action_try_again), active = true, onClick = onRetry)
            }

            else -> LazyVerticalStaggeredGrid(
                state = gridState,
                columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 126.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(state.items, key = { it.key }) { wallpaper ->
                    WallpaperArt(
                        wallpaper = wallpaper,
                        aspect = artAspect(wallpaper),
                        corner = 18.dp,
                        favorite = wallpaper.key in state.favoriteKeys,
                        onToggleFavorite = onToggleFavorite,
                        onClick = { onOpen(wallpaper) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "search-footer") {
                    if (state.loadingMore) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(color = AuroroAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                        }
                    } else if (state.endReached) {
                        Text(
                            text = stringResource(R.string.search_end_reached),
                            style = MaterialTheme.typography.labelSmall,
                            color = AuroroTextTertiary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = AuroroSurfaceBright,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search_outlined),
                contentDescription = null,
                tint = AuroroTextTertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(AuroroAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_field_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AuroroTextTertiary,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                        tint = AuroroTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchIdleState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = AuroroSurfaceBright) {
            Icon(
                painter = painterResource(R.drawable.ic_search_outlined),
                contentDescription = null,
                tint = AuroroAccent,
                modifier = Modifier.padding(20.dp).size(30.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(text = stringResource(R.string.search_idle_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.search_idle_body),
            style = MaterialTheme.typography.bodySmall,
            color = AuroroTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 44.dp),
        )
    }
}
