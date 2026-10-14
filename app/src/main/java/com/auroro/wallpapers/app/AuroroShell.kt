package com.auroro.wallpapers.app

import androidx.activity.compose.BackHandler
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
import com.auroro.wallpapers.ui.PlaceholderScreen
import com.auroro.wallpapers.ui.components.BottomPillBar
import com.auroro.wallpapers.ui.components.ShellTab
import com.auroro.wallpapers.ui.detail.DetailScreen
import com.auroro.wallpapers.ui.home.HomeScreen
import com.auroro.wallpapers.ui.home.HomeViewModel
import com.auroro.wallpapers.ui.theme.AuroroBackground

/**
 * App shell: a single screen slot switched by the floating pill bar, plus the slice-2
 * fullscreen detail overlay. Deep-link/navigation-graph routing stays a later slice; detail
 * is process-memory state, so a process-death restore lands back on the feed, never on a
 * half-initialized screen.
 */
@Composable
fun AuroroShell() {
    var tab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    var detail by remember { mutableStateOf<Wallpaper?>(null) }
    val container = (LocalContext.current.applicationContext as AuroroApp).container
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(homeViewModel) {
        homeViewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val detailWallpaper = detail
    BackHandler(enabled = detailWallpaper != null) { detail = null }

    Box(
        modifier = Modifier.fillMaxSize().background(AuroroBackground),
    ) {
        if (detailWallpaper != null) {
            DetailScreen(
                wallpaper = detailWallpaper,
                favorite = detailWallpaper.key in homeState.favoriteKeys,
                onBack = { detail = null },
                onToggleFavorite = { homeViewModel.toggleFavorite(detailWallpaper) },
                onDownload = { homeViewModel.download(detailWallpaper) },
                onApply = { target: ApplyTarget -> homeViewModel.setWallpaper(detailWallpaper, target) },
            )
        } else {
            when (tab) {
                ShellTab.HOME -> HomeScreen(
                    state = homeState,
                    onSelectStyle = { style -> homeViewModel.reload(style) },
                    onReload = { homeViewModel.reload() },
                    onLoadMore = homeViewModel::loadMore,
                    onToggleFavorite = homeViewModel::toggleFavorite,
                    onOpen = { wallpaper -> detail = wallpaper },
                    onSearch = { tab = ShellTab.SEARCH },
                )

                else -> PlaceholderScreen(tab)
            }
            BottomPillBar(
                selected = tab,
                onSelect = { tab = it },
                onCenterAction = { homeViewModel.reload() },
                centerActionLabel = R.string.action_shuffle,
                centerActionIcon = R.drawable.ic_auto_awesome,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (detailWallpaper != null) 116.dp else 104.dp),
        )
    }
}
