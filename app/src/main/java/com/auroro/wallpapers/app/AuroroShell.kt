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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.data.download.ApplyTarget
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
import com.auroro.wallpapers.ui.theme.AuroroBackground
import com.auroro.wallpapers.ui.theme.AuroroMotion

/** One wallpaper action that needs a crop editor pass before applying. */
private data class CropRequest(val wallpaper: Wallpaper, val target: ApplyTarget)

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
    LaunchedEffect(homeViewModel) {
        homeViewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val detailWallpaper = detail
    val crop = cropRequest
    val anyOverlay = detailWallpaper != null || coverFlow || crop != null
    BackHandler(enabled = coverFlow && detailWallpaper == null && crop == null) { coverFlow = false }

    Box(
        modifier = Modifier.fillMaxSize().background(AuroroBackground),
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (
                    fadeIn(tween(400, easing = AuroroMotion.EmphasizedDecelerate)) +
                        slideInVertically(tween(400, easing = AuroroMotion.EmphasizedDecelerate)) { it / 24 }
                    ) togetherWith fadeOut(tween(200, easing = AuroroMotion.EmphasizedAccelerate))
            },
            label = "shellTab",
        ) { currentTab ->
            when (currentTab) {
                ShellTab.HOME -> HomeScreen(
                    state = homeState,
                    onSelectStyle = { style -> homeViewModel.reload(style) },
                    onReload = { homeViewModel.reload() },
                    onLoadMore = homeViewModel::loadMore,
                    onToggleFavorite = homeViewModel::toggleFavorite,
                    onOpen = { wallpaper -> detail = wallpaper },
                    onSearch = { tab = ShellTab.SEARCH },
                )

                ShellTab.SEARCH -> SearchScreen(
                    state = searchState,
                    onQueryChange = searchViewModel::setQuery,
                    onToggleSource = searchViewModel::toggleSource,
                    onSelectAllSources = searchViewModel::clearSources,
                    onLoadMore = searchViewModel::loadMore,
                    onRetry = searchViewModel::retry,
                    onToggleFavorite = searchViewModel::toggleFavorite,
                    onOpen = { wallpaper -> detail = wallpaper },
                )

                ShellTab.LIBRARY -> LibraryScreen(
                    state = libraryState,
                    onToggleFavorite = libraryViewModel::toggleFavorite,
                    onOpen = { wallpaper -> detail = wallpaper },
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
            BottomPillBar(
                selected = tab,
                onSelect = { tab = it },
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
                onApply = { target -> cropRequest = CropRequest(detailWallpaper, target) },
            )
        }
        if (coverFlow && detailWallpaper == null && crop == null) {
            CoverFlowScreen(
                items = homeState.items,
                loading = homeState.loading,
                favoriteKeys = homeState.favoriteKeys,
                onToggleFavorite = homeViewModel::toggleFavorite,
                onOpen = { wallpaper -> detail = wallpaper },
                onShuffle = { homeViewModel.shuffle() },
                onClose = { coverFlow = false },
            )
        }
        crop?.let { request ->
            CropScreen(
                wallpaper = request.wallpaper,
                target = request.target,
                onBack = { cropRequest = null },
                onConfirm = { normalizedCrop ->
                    homeViewModel.setWallpaper(request.wallpaper, request.target, normalizedCrop)
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
