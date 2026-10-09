package com.auroro.wallpapers.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsets.Companion.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.auroro.wallpapers.core.data.SourceAvailability
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.LocalFiles
import com.auroro.wallpapers.core.data.download.NormalizedCrop
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.design.Aero
import com.auroro.wallpapers.core.design.AmbientBackdrop
import com.auroro.wallpapers.core.design.AppLogo
import com.auroro.wallpapers.core.design.GlassIconButton
import com.auroro.wallpapers.core.design.GlassPanel
import com.auroro.wallpapers.core.design.LocalReducedMotion
import com.auroro.wallpapers.core.design.Motion
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.feature.categories.CategoriesScreen
import com.auroro.wallpapers.feature.collections.AddToCollectionDialog
import com.auroro.wallpapers.feature.collections.CollectionDetailScreen
import com.auroro.wallpapers.feature.collections.CollectionsScreen
import com.auroro.wallpapers.feature.collections.FavoritesScreen
import com.auroro.wallpapers.feature.home.HomeScreen
import com.auroro.wallpapers.feature.offline.OfflineScreen
import com.auroro.wallpapers.feature.search.FilterScreen
import com.auroro.wallpapers.feature.search.SearchScreen
import com.auroro.wallpapers.feature.settings.SettingsScreen
import com.auroro.wallpapers.feature.settings.SourcesScreen
import com.auroro.wallpapers.feature.wallpaper.ApplyTargetDialog
import com.auroro.wallpapers.feature.wallpaper.CropScreen
import com.auroro.wallpapers.feature.wallpaper.WallpaperDetailScreen
import com.auroro.wallpapers.feature.wallpaper.shareWallpaper
import kotlinx.coroutines.launch

private data class DrawerEntry(val label: String, val route: String, val icon: ImageVector)
private data class TabEntry(val label: String, val route: String, val icon: ImageVector)

private val drawerEntries = listOf(
    DrawerEntry("Home", "home", Icons.Rounded.Home),
    DrawerEntry("Search", "search", Icons.Rounded.Search),
    DrawerEntry("Collections", "collections", Icons.Rounded.CollectionsBookmark),
    DrawerEntry("Offline downloads", "offline", Icons.Rounded.Download),
    DrawerEntry("Favorites", "favorites", Icons.Rounded.Favorite),
    DrawerEntry("Categories", "categories", Icons.Rounded.Category),
    DrawerEntry("Wallpaper sources", "sources", Icons.Rounded.Source),
    DrawerEntry("Settings", "settings", Icons.Rounded.Settings),
)

private val bottomEntries = listOf(
    TabEntry("Home", "home", Icons.Rounded.Home),
    TabEntry("Search", "search", Icons.Rounded.Search),
    TabEntry("Collections", "collections", Icons.Rounded.CollectionsBookmark),
    TabEntry("Offline", "offline", Icons.Rounded.Download),
)

@Composable
fun AuroroRoot(vm: MainViewModel, incomingRoute: String? = null) {
    val nav = rememberNavController()
    val drawer = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val darkAppearance = Aero.colors.isDark
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !darkAppearance
                isAppearanceLightNavigationBars = !darkAppearance
            }
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val feed by vm.feed.collectAsState()
    val favorites by vm.favoriteKeys.collectAsState()
    val favoriteWallpapers by vm.app.favorites.observeFavorites().collectAsState(initial = emptyList())
    val collectionSummaries by vm.collections.collectAsState()
    val downloadRows by vm.offlineRows.collectAsState()
    val downloadEntities by vm.downloads.collectAsState()
    val history by vm.history.collectAsState()
    val detail by vm.detail.collectAsState()
    val relatedLoading by vm.relatedLoading.collectAsState()
    val settings by vm.settings.collectAsState()
    val reducedMotion = LocalReducedMotion.current
    val goWallpaper: (Wallpaper) -> Unit = { w -> openWallpaper(nav, w) }
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.FOR_YOU) }
    var providerAvailability by remember { mutableStateOf<Map<WallpaperSource, SourceAvailability>>(emptyMap()) }
    var addToCollection by remember { mutableStateOf<Wallpaper?>(null) }
    var storageInfo by remember { mutableStateOf<com.auroro.wallpapers.core.data.download.StorageInfo?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbar.showSnackbar("Notifications are off. Downloads still work; Android may keep progress in its system task panel.") }
    }

    LaunchedEffect(vm) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(settings.wallhavenEnabled, settings.abyssEnabled, settings.abyssApiKey) {
        providerAvailability = vm.app.aggregator.availability()
    }
    LaunchedEffect(incomingRoute) {
        if (incomingRoute == "offline") {
            nav.navigate("offline") { launchSingleTop = true }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    val isImmersive = route.startsWith("wallpaper/") || route.startsWith("crop/") || route == "filters" || route.startsWith("collection/")
    val currentTab = bottomEntries.firstOrNull { route == it.route }?.route

    fun openExternal(url: String) {
        vm.openSource(url) { safe ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, safe))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar("No browser is available to open this link.") }
            }
        }
    }

    fun navigateTab(target: String) {
        scope.launch { drawer.close() }
        when (target) {
            "home" -> vm.openHome(homeTab)
            "search" -> vm.openSearch()
            else -> Unit
        }
        nav.navigate(target) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun startSearch(query: String = "", filter: WallpaperFilter = WallpaperFilter.Default) {
        scope.launch { drawer.close() }
        vm.openSearch(query, filter)
        nav.navigate("search") {
            launchSingleTop = true
            popUpTo("home") { saveState = true }
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !isImmersive,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(320.dp),
                drawerContainerColor = Aero.colors.surfaceSolid.copy(alpha = if (Aero.colors.isDark) .97f else .95f),
                drawerContentColor = Aero.colors.textPrimary,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            ) {
                DrawerContent(
                    selectedRoute = route,
                    onChoose = { dest ->
                        scope.launch { drawer.close() }
                        when (dest) {
                            "home" -> vm.openHome(homeTab)
                            "search" -> vm.openSearch()
                            else -> Unit
                        }
                        nav.navigate(dest) { launchSingleTop = true; popUpTo("home") { saveState = true }; restoreState = true }
                    },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackdrop(Modifier.fillMaxSize())
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!isImmersive) BottomNavigation(
                        selected = currentTab ?: route,
                        onSelect = ::navigateTab,
                    )
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize().padding(padding),
                    enterTransition = {
                        if (reducedMotion) EnterTransition.None else fadeIn(tween(180, easing = Motion.Easing)) + slideInHorizontally(tween(220, easing = Motion.Easing)) { it / 30 }
                    },
                    exitTransition = {
                        if (reducedMotion) ExitTransition.None else fadeOut(tween(120, easing = Motion.Easing)) + slideOutHorizontally(tween(160, easing = Motion.Easing)) { -it / 32 }
                    },
                ) {
                    composable("home") {
                        HomeScreen(
                            feed = feed,
                            favorites = favorites,
                            selectedTab = homeTab,
                            onTab = { tab -> homeTab = tab; vm.openHome(tab) },
                            onMenu = { scope.launch { drawer.open() } },
                            onSearch = { startSearch() },
                            onSettings = { nav.navigate("settings") },
                            onSource = vm::selectSources,
                            onCategory = { startSearch(it) },
                            onOpen = goWallpaper,
                            onFavorite = vm::toggleFavorite,
                            onLoadMore = vm::loadMore,
                            onRetry = vm::retryFeed,
                            onOpenExternal = ::openExternal,
                        )
                    }

                    composable("search") {
                        SearchScreen(
                            feed = feed,
                            favoriteKeys = favorites,
                            initialQuery = feed.request.query,
                            onMenu = { scope.launch { drawer.open() } },
                            onOpenFilters = { nav.navigate("filters") },
                            onSubmit = { query, filter -> vm.submitSearch(query, filter) },
                            onFiltersChanged = vm::applyFilters,
                            onOpen = goWallpaper,
                            onFavorite = vm::toggleFavorite,
                            onLoadMore = vm::loadMore,
                            onRetry = vm::retryFeed,
                            onOpenExternal = ::openExternal,
                        )
                    }

                    composable("filters") {
                        FilterScreen(
                            initial = feed.request.filter,
                            query = feed.request.query,
                            onBack = { nav.popBackStack() },
                            onApply = { filter -> vm.applyFilters(filter); nav.popBackStack() },
                        )
                    }

                    composable("collections") {
                        CollectionsScreen(
                            collections = collectionSummaries,
                            onMenu = { scope.launch { drawer.open() } },
                            onCreate = { name -> vm.createCollection(name) },
                            onOpen = { nav.navigate("collection/${it.id}") },
                            onRename = vm::renameCollection,
                            onDelete = vm::deleteCollection,
                        )
                    }

                    composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: -1L
                        val items by vm.app.collections.observeItems(id).collectAsState(initial = emptyList())
                        val summary = collectionSummaries.firstOrNull { it.id == id }
                        CollectionDetailScreen(
                            collection = summary,
                            wallpapers = items,
                            favoriteKeys = favorites,
                            onBack = { nav.popBackStack() },
                            onOpen = goWallpaper,
                            onFavorite = vm::toggleFavorite,
                            onRemove = { vm.setCollectionMembership(id, it, true) },
                            onAddMore = { startSearch() },
                        )
                    }

                    composable("favorites") {
                        FavoritesScreen(
                            wallpapers = favoriteWallpapers,
                            favoriteKeys = favorites,
                            onMenu = { scope.launch { drawer.open() } },
                            onSearch = { startSearch() },
                            onOpen = goWallpaper,
                            onRemove = { vm.toggleFavorite(it) },
                        )
                    }

                    composable("offline") {
                        OfflineScreen(
                            rows = downloadRows,
                            history = history,
                            favoriteKeys = favorites,
                            storageInfo = storageInfo,
                            onRequestStorageInfo = { callback -> vm.storageInfo { info -> storageInfo = info; callback(info) } },
                            onMenu = { scope.launch { drawer.open() } },
                            onOpen = goWallpaper,
                            onCancel = vm::cancelDownload,
                            onRetry = vm::retryDownload,
                            onDelete = vm::deleteDownload,
                            onClearHistory = vm::clearHistory,
                            onFavorite = vm::toggleFavorite,
                            onApply = { wallpaper -> nav.navigate("crop/${Uri.encode(wallpaper.key)}/${ApplyTarget.HOME.name}") },
                        )
                    }

                    composable("categories") {
                        CategoriesScreen(onMenu = { scope.launch { drawer.open() } }, onSearch = { query -> startSearch(query) })
                    }

                    composable("sources") {
                        SourcesScreen(
                            availability = providerAvailability,
                            onBack = { nav.popBackStack() },
                            onOpen = ::openExternal,
                            onConfigure = { nav.navigate("settings") },
                            onSearch = { source -> startSearch(filter = WallpaperFilter(sources = setOf(source))) },
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            availability = providerAvailability,
                            onMenu = { scope.launch { drawer.open() } },
                            onSettings = vm::updateSettings,
                            onClearCache = vm::clearImageCache,
                            onSaveAbyssKey = vm::saveAbyssKey,
                            onOpenSource = ::openExternal,
                            onAbout = { scope.launch { snackbar.showSnackbar("Auroro Wallpapers · version 1.0.0 · native Android, free and ad-free.") } },
                            onPrivacy = { openExternal("https://github.com/Nazatric/Aurora-Wallpapers/blob/main/PRIVACY.md") },
                            onLicenses = { openExternal("https://github.com/Nazatric/Aurora-Wallpapers/blob/main/THIRD_PARTY_NOTICES.md") },
                            onSources = { nav.navigate("sources") },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                    }

                    composable("wallpaper/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                        val key = entry.arguments?.getString("key").orEmpty()
                        LaunchedEffect(key) { vm.loadDetail(key) }
                        val wallpaper = detail?.wallpaper?.takeIf { it.key == key }
                        val download = downloadEntities.firstOrNull { it.wallpaperKey == key }
                        val local = downloadRows.firstOrNull { it.download.wallpaperKey == key }?.takeIf { it.fileExists }?.download?.localUri
                        val caps = remember { vm.app.wallpaperApplier.capabilities() }
                        WallpaperDetailScreen(
                            state = detail?.takeIf { it.wallpaper.key == key },
                            favorite = key in favorites,
                            download = download,
                            localPreviewUri = local,
                            relatedLoading = relatedLoading,
                            canSetWallpaper = caps.usable,
                            setUnavailableReason = caps.unavailableReason,
                            onBack = { nav.popBackStack() },
                            onFavorite = { wallpaper?.let(vm::toggleFavorite) },
                            onDownload = vm::download,
                            onApply = { target -> wallpaper?.let { nav.navigate("crop/${Uri.encode(it.key)}/${target.name}") } },
                            onShare = { shareWallpaper(context, it) },
                            onOpenSource = ::openExternal,
                            onOpenRelated = goWallpaper,
                            onAddToCollection = { addToCollection = it },
                            onTag = { startSearch(it) },
                        )
                    }

                    composable(
                        "crop/{key}/{target}",
                        arguments = listOf(navArgument("key") { type = NavType.StringType }, navArgument("target") { type = NavType.StringType }),
                    ) { entry ->
                        val key = entry.arguments?.getString("key").orEmpty()
                        val target = runCatching { ApplyTarget.valueOf(entry.arguments?.getString("target").orEmpty()) }.getOrDefault(ApplyTarget.HOME)
                        LaunchedEffect(key) { vm.loadDetail(key) }
                        val wallpaper = detail?.wallpaper?.takeIf { it.key == key } ?: downloadRows.firstOrNull { it.download.wallpaperKey == key }?.wallpaper
                        val d = downloadEntities.firstOrNull { it.wallpaperKey == key }
                        CropScreen(
                            wallpaper = wallpaper,
                            target = target,
                            download = d,
                            onBack = { nav.popBackStack() },
                            onEnsureDownload = vm::download,
                            onRetry = vm::retryDownload,
                            onApply = { uri, applyTarget, crop -> vm.applySaved(uri, applyTarget, crop); nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    addToCollection?.let { w ->
        val membership by vm.app.collections.observeMembership(w.key).collectAsState(initial = emptySet())
        AddToCollectionDialog(
            collections = collectionSummaries,
            memberships = membership,
            onDismiss = { addToCollection = null },
            onCreate = { name -> vm.createCollection(name) { id -> if (id != null) vm.addToCollection(id, w) } },
            onToggle = { id, isMember -> vm.setCollectionMembership(id, w, isMember) },
        )
    }
}

@Composable
private fun DrawerContent(selectedRoute: String, onChoose: (String) -> Unit) {
    Column(Modifier.fillMaxHeight().fillMaxWidth().padding(horizontal = 13.dp, vertical = 17.dp)) {
        GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(23.dp), elevation = 10.dp) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(Modifier.size(52.dp), 52.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Auroro Wallpapers", style = MaterialTheme.typography.titleMedium, color = Aero.colors.textPrimary)
                    Text("A little more sky.", style = MaterialTheme.typography.bodySmall, color = Aero.colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.padding(top = 15.dp))
        drawerEntries.forEach { entry ->
            val selected = selectedRoute == entry.route
            NavigationDrawerItem(
                label = { Text(entry.label, style = MaterialTheme.typography.labelLarge) },
                selected = selected,
                onClick = { onChoose(entry.route) },
                icon = { Icon(entry.icon, null) },
                modifier = Modifier.padding(vertical = 2.dp),
                colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = Aero.colors.accent.copy(alpha = .18f),
                    selectedIconColor = Aero.colors.accent,
                    selectedTextColor = Aero.colors.textPrimary,
                    unselectedIconColor = Aero.colors.textSecondary,
                    unselectedTextColor = Aero.colors.textSecondary,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("Real sources · no ads · no accounts", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = Aero.colors.textTertiary)
    }
}

@Composable
private fun BottomNavigation(selected: String, onSelect: (String) -> Unit) {
    GlassPanel(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp).navigationBarsPadding(),
        shape = CircleShape,
        elevation = 14.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            bottomEntries.forEach { item ->
                val active = selected == item.route
                Column(
                    Modifier.weight(1f).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(item.route) }
                        .padding(vertical = 6.dp).semantics { contentDescription = item.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(item.icon, null, tint = if (active) Aero.colors.accent else Aero.colors.textSecondary, modifier = Modifier.size(21.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (active) Aero.colors.accent else Aero.colors.textSecondary)
                }
            }
        }
    }
}

private fun openWallpaper(nav: NavHostController, wallpaper: Wallpaper) {
    nav.navigate("wallpaper/${Uri.encode(wallpaper.key)}")
}
