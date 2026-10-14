package com.auroro.wallpapers.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auroro.wallpapers.R
import com.auroro.wallpapers.ui.PlaceholderScreen
import com.auroro.wallpapers.ui.components.BottomPillBar
import com.auroro.wallpapers.ui.components.ShellTab
import com.auroro.wallpapers.ui.home.HomeScreen
import com.auroro.wallpapers.ui.home.HomeViewModel
import com.auroro.wallpapers.ui.theme.AuroroBackground

/**
 * Slice-1 shell: a single screen slot switched by the floating pill bar. Navigation
 * (deep links, detail routes, back stack) is a later slice; today only Home has content.
 */
@Composable
fun AuroroShell() {
    var tab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    val container = (LocalContext.current.applicationContext as AuroroApp).container
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize().background(AuroroBackground),
    ) {
        when (tab) {
            ShellTab.HOME -> HomeScreen(
                state = homeState,
                onSelectStyle = { style -> homeViewModel.reload(style) },
                onReload = { homeViewModel.reload() },
                onLoadMore = homeViewModel::loadMore,
                onToggleFavorite = homeViewModel::toggleFavorite,
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
}
