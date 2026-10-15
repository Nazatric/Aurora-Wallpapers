package com.auroro.wallpapers.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auroro.wallpapers.R
import com.auroro.wallpapers.ui.components.AuroraBackdrop
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.ui.components.BottomPillBar
import com.auroro.wallpapers.ui.components.ShellTab
import com.auroro.wallpapers.ui.coverflow.CoverFlowScreen
import com.auroro.wallpapers.ui.crop.CropScreen
import com.auroro.wallpapers.ui.detail.DetailScreen
import com.auroro.wallpapers.ui.home.HomeScreen
import com.auroro.wallpapers.ui.home.HomeViewModel
import com.auroro.wallpapers.ui.library.LibraryScreen
import com.auroro.wallpapers.ui.library.LibraryViewModel
import com.auroro.wallpapers.ui.settings.SettingsScreen
import com.auroro.wallpapers.ui.settings.SettingsViewModel
import com.auroro.wallpapers.ui.search.SearchScreen
import com.auroro.wallpapers.ui.search.SearchViewModel
import com.auroro.wallpapers.ui.theme.AuroroPal
import com.auroro.wallpapers.ui.theme.AuroroMotion
import com.auroro.wallpapers.ui.theme.auroroHazeSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth

/** One wallpaper action that needs a crop editor pass before applying. */
private data class CropRequest(val wallpaper: Wallpaper)

/**
 * App shell: tab slot switched by the floating pill bar with Material Expressive
 * transitions, plus three overlays — fullscreen detail (slice 2), the cover-flow browser
 * behind the magic button, and the crop editor for Set Wallpaper. Overlay state is
 * process-memory only, so a process-death restore lands back on the feed, never on a
 * half-initialized screen. Deep-link routing stays a later slice.
 */
@Composable
fun AuroroShell() {
    var tab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    var detail by remember { mutableStateOf<Wallpaper?>(null) }
    var coverFlow by rememberSaveable { mutableStateOf(false) }
    var cropRequest by remember { mutableStateOf<CropRequest?>(null) }
    val container = (LocalContext.current.applicationContext as AuroroApp).container
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val downloadStartedText = stringResource(R.string.download_started)
    val viewLabel = stringResource(R.string.snackbar_view)
    LaunchedEffect(homeViewModel, downloadStartedText) {
        homeViewModel.messages.collect { message ->
            // Snackbar with a real action (reference component): a queued download offers
            // "View" and jumps straight to the Library downloads queue.
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (message == downloadStartedText) viewLabel else null,
            )
            if (result == SnackbarResult.ActionPerformed) tab = ShellTab.LIBRARY
        }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val detailWallpaper = detail
    val crop = cropRequest
    val anyOverlay = detailWallpaper != null || coverFlow || crop != null
    BackHandler(enabled = coverFlow && detailWallpaper == null && crop == null) { coverFlow = false }

    // iOS-style scroll condensing: the pill bar slims to icons while the home feed scrolls down
    // and re-expands with labels on scroll-up or when the feed is back at the top.
    var pillCondensed by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab != ShellTab.HOME) pillCondensed = false
    }
    Box(
        modifier = Modifier.fillMaxSize().background(AuroroPal.background),
    ) {
        AuroraBackdrop()
        AnimatedContent(
            modifier = Modifier.auroroHazeSource(),
            targetState = tab,
            transitionSpec = {
                (
                    // Snappy by design: the incoming screen settles in 220 ms while the outgoing
                    // one is gone in 90 ms, so tab switches read as instant but stay oriented.
                    // Shorter transitions also mean fewer frames of live haze re-capture.
                    // Near-instant: the new page is fully in within 150 ms and the old one is
                    // gone in 60 ms, so switches read as immediate while the small rise keeps
                    // the eye oriented.
                    fadeIn(tween(150, easing = AuroroMotion.EmphasizedDecelerate)) +
                        slideInVertically(tween(150, easing = AuroroMotion.EmphasizedDecelerate)) { it / 32 }
                    ) togetherWith fadeOut(tween(60, easing = AuroroMotion.EmphasizedAccelerate))
            },
            label = "shellTab",
        ) { currentTab ->
            when (currentTab) {
                ShellTab.HOME -> HomeScreen(
                    state = homeState,
                    onSelectMode = homeViewModel::selectMode,
                    onOrientation = homeViewModel::setOrientation,
                    onReload = { homeViewModel.reload() },
                    onLoadMore = homeViewModel::loadMore,
                    onToggleFavorite = homeViewModel::toggleFavorite,
                    onOpen = { wallpaper ->
                        homeViewModel.recordHistory(wallpaper)
                        detail = wallpaper
                    },
                    onSearch = { tab = ShellTab.SEARCH },
                    onCondense = { pillCondensed = it },
                )

                ShellTab.SEARCH -> SearchScreen(
                    state = searchState,
                    onQueryChange = searchViewModel::setQuery,
                    onToggleSource = searchViewModel::toggleSource,
                    onSelectAllSources = searchViewModel::clearSources,
                    onLoadMore = searchViewModel::loadMore,
                    onRetry = searchViewModel::retry,
                    onToggleFavorite = searchViewModel::toggleFavorite,
                    onOpen = { wallpaper ->
                        homeViewModel.recordHistory(wallpaper)
                        detail = wallpaper
                    },
                )

                ShellTab.LIBRARY -> LibraryScreen(
                    state = libraryState,
                    onToggleFavorite = libraryViewModel::toggleFavorite,
                    onOpen = { wallpaper ->
                        homeViewModel.recordHistory(wallpaper)
                        detail = wallpaper
                    },
                    onCancel = libraryViewModel::cancel,
                    onRetry = libraryViewModel::retry,
                    onDelete = libraryViewModel::delete,
                )

                ShellTab.SETTINGS -> SettingsScreen(
                    state = settingsState,
                    onUpdate = settingsViewModel::update,
                    onClearCaches = settingsViewModel::clearCaches,
                )
            }
        }
        if (!anyOverlay) {
            // Seamless bottom: scrolling content fades into the background before it reaches
            // the screen edge, so no card edge ever draws a hard line under the glass bar.
            // A plain Box has no pointer input, so touches pass straight through to the feed.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            BottomPillBar(
                selected = tab,
                condensed = pillCondensed,
                onSelect = { tab = it },
                onTabDoubleTap = { tapped ->
                    if (tapped == ShellTab.HOME) homeViewModel.reload()
                },
                onCenterAction = {
                    coverFlow = true
                    homeViewModel.shuffle()
                },
                centerActionLabel = R.string.action_shuffle,
                centerActionIcon = R.drawable.ic_auto_awesome,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (detailWallpaper != null && crop == null) {
            DetailScreen(
                wallpaper = detailWallpaper,
                favorite = detailWallpaper.key in homeState.favoriteKeys,
                onBack = { detail = null },
                onToggleFavorite = { homeViewModel.toggleFavorite(detailWallpaper) },
                onDownload = { homeViewModel.download(detailWallpaper) },
                onApply = { cropRequest = CropRequest(detailWallpaper) },
            )
        }
        if (coverFlow && detailWallpaper == null && crop == null) {
            CoverFlowScreen(
                items = homeState.items,
                loading = homeState.loading,
                favoriteKeys = homeState.favoriteKeys,
                onToggleFavorite = homeViewModel::toggleFavorite,
                onOpen = { wallpaper ->
                        homeViewModel.recordHistory(wallpaper)
                        detail = wallpaper
                    },
                onShuffle = { homeViewModel.shuffle() },
                onClose = { coverFlow = false },
            )
        }
        crop?.let { request ->
            CropScreen(
                wallpaper = request.wallpaper,
                onBack = { cropRequest = null },
                onConfirm = { normalizedCrop, rotationDegrees, target ->
                    homeViewModel.setWallpaper(request.wallpaper, target, normalizedCrop, rotationDegrees)
                    cropRequest = null
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (anyOverlay) 116.dp else 104.dp),
        )
    }
}
