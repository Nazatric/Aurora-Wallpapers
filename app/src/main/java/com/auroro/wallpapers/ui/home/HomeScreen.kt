package com.auroro.wallpapers.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.DiscoveryStyle
import com.auroro.wallpapers.ui.components.FeedEmptyState
import com.auroro.wallpapers.ui.components.FeedFooterState
import com.auroro.wallpapers.ui.components.FeedLoadingState
import com.auroro.wallpapers.ui.components.HeroPager
import com.auroro.wallpapers.ui.components.WallpaperCard
import com.auroro.wallpapers.ui.theme.AuroroAccent
import com.auroro.wallpapers.ui.theme.AuroroSurfaceBright
import com.auroro.wallpapers.ui.theme.AuroroTextSecondary
import com.auroro.wallpapers.ui.theme.AuroroTextTertiary

private const val HERO_COUNT = 4
private const val NEAR_END_THRESHOLD = 3

/**
 * Slice-1 Home: brand header, style chips, a hero pager over the first items and a stacked
 * card feed with honest loading / empty / error / end states. Continuation batches are
 * requested when the list is scrolled near its end.
 */
@Composable
fun HomeScreen(
    state: HomeViewModel.UiState,
    onSelectStyle: (DiscoveryStyle?) -> Unit,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (com.auroro.wallpapers.core.model.Wallpaper) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.let { it.index >= info.totalItemsCount - NEAR_END_THRESHOLD } ?: false
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 118.dp),
    ) {
        item(key = "header") {
            HomeHeader(onSearch = onSearch)
        }
        item(key = "chips") {
            StyleChips(selected = state.style, onSelect = onSelectStyle)
        }
        when {
            state.loading && state.items.isEmpty() -> item(key = "loading") { FeedLoadingState() }
            state.items.isEmpty() -> item(key = "empty") {
                FeedEmptyState(error = state.error, onRetry = onReload)
            }
            else -> {
                val hero = state.items.take(HERO_COUNT)
                val rest = state.items.drop(HERO_COUNT)
                item(key = "hero") {
                    HeroPager(
                        items = hero,
                        favoriteKeys = state.favoriteKeys,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                items(rest, key = { it.key }) { wallpaper ->
                    WallpaperCard(
                        wallpaper = wallpaper,
                        favorite = wallpaper.key in state.favoriteKeys,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                item(key = "footer") {
                    FeedFooterState(
                        loadingMore = state.loadingMore,
                        notice = state.notice,
                        endReached = state.endReached,
                        onRetryMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(start = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = AuroroTextSecondary,
            )
        }
        IconButton(onClick = onSearch, modifier = Modifier.padding(end = 8.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_search_outlined),
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun StyleChips(selected: DiscoveryStyle?, onSelect: (DiscoveryStyle?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(16.dp, 1.dp))
        StyleChip(
            label = stringResource(R.string.chip_for_you),
            active = selected == null,
            onClick = { onSelect(null) },
        )
        DiscoveryStyle.entries.forEach { style ->
            StyleChip(
                label = style.label,
                active = selected == style,
                onClick = { onSelect(style) },
            )
        }
        Spacer(Modifier.size(16.dp, 1.dp))
    }
}

@Composable
private fun StyleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) AuroroAccent else AuroroSurfaceBright,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimary else AuroroTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
