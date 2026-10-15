package com.auroro.wallpapers.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.FeedLayout
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.AuroraChip
import com.auroro.wallpapers.ui.components.FeedEmptyState
import com.auroro.wallpapers.ui.components.FeedFooterState
import com.auroro.wallpapers.ui.components.FeedLoadingState
import com.auroro.wallpapers.ui.components.HeroPager
import com.auroro.wallpapers.ui.components.WallpaperArt
import com.auroro.wallpapers.ui.components.WallpaperCard
import com.auroro.wallpapers.ui.components.artAspect
import com.auroro.wallpapers.ui.components.wallpaperMeta
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.AuroroPal
import com.auroro.wallpapers.ui.theme.LocalGlassQuality
import com.auroro.wallpapers.ui.theme.auroroGlass
import androidx.compose.runtime.snapshotFlow

private const val HERO_COUNT = 4
private const val NEAR_END_THRESHOLD = 3

/**
 * Slice-6 home: brand header with an icon-only orientation filter, one row of feed-mode
 * chips, and three honest layouts (stacked cards / 2-column grid / compact list) chosen in
 * Settings. Every layout shows whole artwork and pages in more batches near its end.
 */
@Composable
fun HomeScreen(
    state: HomeViewModel.UiState,
    onSelectMode: (FeedMode) -> Unit,
    onOrientation: (Orientation) -> Unit,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    onSearch: () -> Unit,
    onCondense: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Feed-level entrance: whenever mode or orientation changes the whole feed fades and
    // rises in on the M3 emphasized curve — cheap (one graphicsLayer), never competes
    // with the artwork.
    val feedEntry = remember { Animatable(1f) }
    LaunchedEffect(state.mode, state.orientation, state.layout) {
        feedEntry.snapTo(0f)
        feedEntry.animateTo(1f, tween(360, easing = AuroroMotion.EmphasizedDecelerate))
    }
    Column(modifier = modifier.fillMaxSize()) {
        HomeHeader(
            orientation = state.orientation,
            onOrientation = onOrientation,
            onSearch = onSearch,
        )
        ModeChips(selected = state.mode, onSelect = onSelectMode)
        FeedBody(
            state = state,
            onCondense = onCondense,
            onReload = onReload,
            onLoadMore = onLoadMore,
            onToggleFavorite = onToggleFavorite,
            onOpen = onOpen,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    alpha = feedEntry.value
                    translationY = (1f - feedEntry.value) * 24f
                },
        )
    }
}

@Composable
private fun HomeHeader(
    orientation: Orientation,
    onOrientation: (Orientation) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(start = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.brand_name),
                style = MaterialTheme.typography.headlineLarge,
                // Brand word takes the live accent: visible in every theme, recolours with
                // the accent setting instead of inheriting a default (black) content colour.
                color = AuroroPal.accent,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = AuroroPal.textSecondary,
            )
        }
        OrientationSegmented(selected = orientation, onSelect = onOrientation)
        Spacer(Modifier.size(6.dp))
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(40.dp)
                .auroroGlass(
                    shape = RoundedCornerShape(50),
                    quality = LocalGlassQuality.current,
                    fallback = AuroroPal.surfaceBright,
                    tint = AuroroPal.surfaceBright.copy(alpha = 0.42f),
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search_outlined),
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Icon-only Any / Portrait / Landscape filter — same pill language as cover flow. */
@Composable
private fun OrientationSegmented(selected: Orientation, onSelect: (Orientation) -> Unit) {
    Row(
        modifier = Modifier
            .auroroGlass(
                shape = RoundedCornerShape(50),
                quality = LocalGlassQuality.current,
                fallback = AuroroPal.surfaceBright,
                tint = AuroroPal.surface,
            )
            .clip(RoundedCornerShape(50))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrientationButton(Orientation.ANY, R.drawable.ic_aspect_ratio, R.string.coverflow_orient_all, selected, onSelect)
        OrientationButton(Orientation.PORTRAIT, R.drawable.ic_crop_portrait, R.string.coverflow_orient_portrait, selected, onSelect)
        OrientationButton(Orientation.LANDSCAPE, R.drawable.ic_crop_landscape, R.string.coverflow_orient_landscape, selected, onSelect)
    }
}

@Composable
private fun OrientationButton(
    value: Orientation,
    icon: Int,
    labelRes: Int,
    selected: Orientation,
    onSelect: (Orientation) -> Unit,
) {
    val active = value == selected
    val label = stringResource(labelRes)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) AuroroPal.accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = label) { onSelect(value) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (active) AuroroPal.accent else AuroroPal.textTertiary,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ModeChips(selected: FeedMode, onSelect: (FeedMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(16.dp, 1.dp))
        FeedMode.entries.forEach { mode ->
            AuroraChip(
                label = stringResource(mode.labelRes),
                active = selected == mode,
                onClick = { onSelect(mode) },
            )
        }
        Spacer(Modifier.size(16.dp, 1.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedBody(
    state: HomeViewModel.UiState,
    onCondense: (Boolean) -> Unit,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading && state.items.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FeedLoadingState()
        }

        state.items.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FeedEmptyState(error = state.error, onRetry = onReload)
        }

        else -> PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onReload,
            modifier = modifier.fillMaxSize(),
        ) {
            when (state.layout) {
                FeedLayout.CARDS -> CardsFeed(state, onCondense, onLoadMore, onToggleFavorite, onOpen, Modifier)
                FeedLayout.GRID -> GridFeed(state, onCondense, onLoadMore, onToggleFavorite, onOpen, Modifier)
            }
        }
    }
}

@Composable
private fun CardsFeed(
    state: HomeViewModel.UiState,
    onCondense: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    NearEndEffect(listState) { onLoadMore() }
    CondenseEffect(
        position = { listState.firstVisibleItemIndex * 100_000L + listState.firstVisibleItemScrollOffset },
        atTop = { !listState.canScrollBackward },
        scrolling = { listState.isScrollInProgress },
        onCondense = onCondense,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 118.dp),
    ) {
        val hero = state.items.take(HERO_COUNT)
        val rest = state.items.drop(HERO_COUNT)
        item(key = "hero") {
            HeroPager(
                items = hero,
                favoriteKeys = state.favoriteKeys,
                onToggleFavorite = onToggleFavorite,
                onOpen = onOpen,
            )
        }
        items(rest, key = { it.key }) { wallpaper ->
            WallpaperCard(
                wallpaper = wallpaper,
                favorite = wallpaper.key in state.favoriteKeys,
                onToggleFavorite = onToggleFavorite,
                onClick = onOpen,
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "footer") { FeedFooter(state, onLoadMore) }
    }
}

@Composable
private fun GridFeed(
    state: HomeViewModel.UiState,
    onCondense: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: (Wallpaper) -> Unit,
    onOpen: (Wallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyStaggeredGridState()
    CondenseEffect(
        position = { gridState.firstVisibleItemIndex * 100_000L + gridState.firstVisibleItemScrollOffset },
        atTop = { !gridState.canScrollBackward },
        scrolling = { gridState.isScrollInProgress },
        onCondense = onCondense,
    )
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            info.visibleItemsInfo.lastOrNull()
                ?.let { it.index >= info.totalItemsCount - NEAR_END_THRESHOLD } ?: false
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 118.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
    ) {
        staggeredItems(state.items, key = { it.key }) { wallpaper ->
            WallpaperArt(
                wallpaper = wallpaper,
                aspect = artAspect(wallpaper),
                corner = 20.dp,
                favorite = wallpaper.key in state.favoriteKeys,
                onToggleFavorite = onToggleFavorite,
                onClick = { onOpen(wallpaper) },
                modifier = Modifier.fillMaxWidth().animateItem(),
            )
        }
        item(key = "footer", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
            FeedFooter(state, onLoadMore)
        }
    }
}

@Composable
private fun FeedFooter(state: HomeViewModel.UiState, onRetryMore: () -> Unit) {
    FeedFooterState(
        loadingMore = state.loadingMore,
        notice = state.notice,
        endReached = state.endReached,
        onRetryMore = onRetryMore,
    )
}

/** Fires [onNearEnd] once the list is scrolled within [NEAR_END_THRESHOLD] items of its end. */
/**
 * Reports scroll direction so the shell can condense/expand the glass pill bar: scrolling down
 * past a small threshold condenses, scrolling up or resting at the top expands again.
 */
@Composable
private fun CondenseEffect(
    position: () -> Long,
    atTop: () -> Boolean,
    scrolling: () -> Boolean,
    onCondense: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        var last = position()
        snapshotFlow { position() }.collect { pos ->
            val delta = pos - last
            last = pos
            if (delta > CONDENSE_THRESHOLD) onCondense(true)
            else if (delta < -CONDENSE_THRESHOLD) onCondense(false)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { atTop() && !scrolling() }.collect { resting ->
            if (resting) onCondense(false)
        }
    }
}

private const val CONDENSE_THRESHOLD = 24L

@Composable
private fun NearEndEffect(listState: androidx.compose.foundation.lazy.LazyListState, onNearEnd: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()
                ?.let { it.index >= info.totalItemsCount - NEAR_END_THRESHOLD } ?: false
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onNearEnd()
    }
}
