package com.auroro.wallpapers.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.CollectionNameResult
import com.auroro.wallpapers.core.data.DiscoveryGenerationGate
import com.auroro.wallpapers.core.data.FeedPager
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.data.SourceStatus
import com.auroro.wallpapers.core.data.WallpaperMapper
import com.auroro.wallpapers.core.data.download.ApplyResult
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.DiscoveryRotation
import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.DiscoverySessionPlanner
import com.auroro.wallpapers.core.model.DiscoveryStylePreferenceMapping
import com.auroro.wallpapers.core.model.LocalFilter
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SearchSourceSelection
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home feed tabs that providers actually support. */
enum class HomeTab(val label: String, val sort: SortOption?) {
    FOR_YOU("For you", null), POPULAR("Popular", SortOption.POPULAR), LATEST("Latest", SortOption.NEWEST), RANDOM("Random", SortOption.RANDOM),
}

data class FeedUiState(
    val request: FeedRequest = FeedRequest(),
    val items: List<Wallpaper> = emptyList(),
    val statuses: List<SourceStatus> = emptyList(),
    val loading: Boolean = false,
    val initialLoadFinished: Boolean = false,
    val endReached: Boolean = false,
    val pageError: String? = null,
    val appendCount: Int = 0,
    val forYouLabel: String? = null,
    val discoverySession: DiscoverySession? = null,
    val discoveryValidatedRecipeCount: Int = 0,
    val discoveryRotationEnabled: Boolean? = null,
    val discoveryWallhavenEnabled: Boolean? = null,
    val enabledSourcesAtRequest: Set<WallpaperSource>? = null,
    val cacheNotice: String? = null,
)

data class DetailUiState(
    val wallpaper: Wallpaper,
    val loading: Boolean = false,
    val related: List<Wallpaper> = emptyList(),
    val error: String? = null,
)

data class OfflineRow(val download: DownloadEntity, val wallpaper: Wallpaper?, val fileExists: Boolean)

data class ApplyPreparationUiState(
    val wallpaperKey: String? = null,
    val uri: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

private enum class FeedScope { HOME, SEARCH }

internal data class FeedSession(
    val state: FeedUiState,
    val pager: FeedPager?,
    val resumeOnReturn: Boolean,
)

/** Independent in-memory sessions prevent Home and Search navigation from clobbering one another. */
internal class FeedSessionCache {
    private val home = mutableMapOf<String, FeedSession>()
    private var search: FeedSession? = null

    fun saveHome(tab: HomeTab, session: FeedSession) {
        home[homeKey(tab, session.state.discoverySession?.sessionId)] = session
    }

    fun takeHome(tab: HomeTab, discoveryKey: String? = null): FeedSession? = home.remove(homeKey(tab, discoveryKey))

    fun clearHome(tab: HomeTab) {
        home.keys.removeAll { it.startsWith("${tab.name}:") }
    }

    private fun homeKey(tab: HomeTab, discoveryKey: String?) = "${tab.name}:${discoveryKey.orEmpty()}"

    fun saveSearch(session: FeedSession) { search = session }
    fun takeSearch(): FeedSession? = search.also { search = null }
    fun clearSearch() { search = null }
}

/** One state owner for navigation-independent app data and provider-backed feed/search requests. */
class MainViewModel(val app: AppContainer) : ViewModel() {
    private val _feed = MutableStateFlow(FeedUiState())
    val feed: StateFlow<FeedUiState> = _feed.asStateFlow()
    private var pager: FeedPager? = null
    private var feedJob: Job? = null
    private var feedPreparationJob: Job? = null
    private var activeFeedScope = FeedScope.HOME
    private var feedInitialized = false
    private val feedSessions = FeedSessionCache()
    private var currentTab = HomeTab.FOR_YOU
    private var lastDiscoverySession: DiscoverySession? = null
    private val discoveryGeneration = DiscoveryGenerationGate()
    private val _applyPreparation = MutableStateFlow(ApplyPreparationUiState())
    val applyPreparation: StateFlow<ApplyPreparationUiState> = _applyPreparation.asStateFlow()
    private var applyPreparationJob: Job? = null

    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.auroro.wallpapers.core.data.AppSettings())
    val favoriteKeys = app.favorites.observeKeys().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val collections = app.collections.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloads = app.downloads.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = app.history.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentSearches = app.settings.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _offlineRows = MutableStateFlow<List<OfflineRow>>(emptyList())
    val offlineRows: StateFlow<List<OfflineRow>> = _offlineRows.asStateFlow()

    private val _detail = MutableStateFlow<DetailUiState?>(null)
    val detail: StateFlow<DetailUiState?> = _detail.asStateFlow()
    private var detailJob: Job? = null

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val message = _message.asSharedFlow()

    private val _relatedLoading = MutableStateFlow(false)
    val relatedLoading: StateFlow<Boolean> = _relatedLoading.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            app.downloads.reconcile()
        }
        viewModelScope.launch {
            app.downloads.observeAll().collect { rows ->
                val entities = app.database.wallpapers().getAll(rows.map { it.wallpaperKey }.distinct())
                val metadata = entities.mapNotNull { e -> WallpaperMapper.toModel(e)?.let { it.key to it } }.toMap()
                val local = withContext(Dispatchers.IO) {
                    rows.map { row -> OfflineRow(row, metadata[row.wallpaperKey] ?: app.wallpaperStore.get(row.wallpaperKey), app.downloads.fileExists(row)) }
                }
                _offlineRows.value = local
            }
        }
    }

    private fun AppSettings.enabledSources(): Set<WallpaperSource> = buildSet {
        if (wallhavenEnabled) add(WallpaperSource.WALLHAVEN)
        if (nasaApodEnabled) add(WallpaperSource.NASA_APOD)
        if (artInstituteEnabled) add(WallpaperSource.ART_INSTITUTE)
    }

    fun openHome(
        tab: HomeTab = HomeTab.FOR_YOU,
        forceRefresh: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val visibleSettings = settings.value
        val previousDiscoverySession = _feed.value.discoverySession ?: lastDiscoverySession
        val currentFeedIsDiscovery = _feed.value.discoverySession?.sessionId != null &&
            _feed.value.discoverySession?.sessionId == previousDiscoverySession?.sessionId
        val settingsStillMatch = !currentFeedIsDiscovery || (
            _feed.value.discoveryRotationEnabled == visibleSettings.rotateDiscoveryHourly &&
                _feed.value.discoveryWallhavenEnabled == visibleSettings.wallhavenEnabled
            )
        val selectionIsCurrent = tab != HomeTab.FOR_YOU || previousDiscoverySession?.let { session ->
            !visibleSettings.rotateDiscoveryHourly ||
                session.hourBucket == DiscoveryRotation.bucketAt(now) || session.hourBucket == 0L
        } == true
        if (!forceRefresh && activeFeedScope == FeedScope.HOME && currentTab == tab &&
            feedInitialized && selectionIsCurrent && settingsStillMatch
        ) return

        if (activeFeedScope != FeedScope.HOME || currentTab != tab || forceRefresh || !selectionIsCurrent || !settingsStillMatch) {
            saveActiveFeedSession()
        }
        feedPreparationJob?.cancel()
        feedPreparationJob = null
        currentTab = tab
        activeFeedScope = FeedScope.HOME
        feedInitialized = false
        _feed.value = FeedUiState(loading = true)
        feedPreparationJob = viewModelScope.launch {
            val prefs = app.settings.current()
            if (activeFeedScope != FeedScope.HOME || currentTab != tab) return@launch
            if (tab == HomeTab.FOR_YOU) {
                val currentTime = System.currentTimeMillis()
                var session = when {
                    forceRefresh -> null
                    previousDiscoverySession == null -> null
                    !prefs.rotateDiscoveryHourly -> previousDiscoverySession
                    previousDiscoverySession.hourBucket == DiscoveryRotation.bucketAt(currentTime) -> previousDiscoverySession
                    previousDiscoverySession.hourBucket == 0L -> previousDiscoverySession.copy(
                        hourBucket = DiscoveryRotation.bucketAt(currentTime),
                    )
                    else -> null
                }
                if (session == null) {
                    val recentRecipes = if (prefs.avoidRecentlySeen) {
                        app.discoveryPools.recentlyUsedRecipeIds(currentTime, prefs.repeatAfterHours)
                    } else emptySet()
                    session = DiscoverySessionPlanner.newRandomSession(
                        nowMillis = currentTime,
                        rotateHourly = prefs.rotateDiscoveryHourly,
                        refreshOrdinal = prefs.discoveryRefreshOrdinal,
                        recentRecipeIds = recentRecipes,
                        preferredRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(prefs.preferredDiscoveryStyleIds),
                        excludedRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(prefs.hiddenDiscoveryStyleIds),
                    )
                    app.discoveryPools.recordSessionRecipeIds(session.candidateRecipeIds, currentTime)
                }
                lastDiscoverySession = session
                val cachedSession = if (!forceRefresh) feedSessions.takeHome(tab, session.sessionId) else null
                if (cachedSession != null) {
                    feedPreparationJob = null
                    restoreFeedSession(FeedScope.HOME, cachedSession)
                    return@launch
                }
                feedSessions.clearHome(tab)
                val recentlySeen = if (prefs.avoidRecentlySeen) {
                    app.discoveryCache.recentlySeen(currentTime, prefs.repeatAfterHours)
                } else emptySet()
                feedPreparationJob = null
                startDiscoveryFeed(session, prefs, emptyList(), recentlySeen)
            } else {
                val cachedSession = feedSessions.takeHome(tab)
                if (cachedSession != null) {
                    feedPreparationJob = null
                    restoreFeedSession(FeedScope.HOME, cachedSession)
                    return@launch
                }
                val request = FeedRequest(
                    filter = WallpaperFilter(
                        sort = tab.sort ?: SortOption.NEWEST,
                        sources = setOf(WallpaperSource.WALLHAVEN),
                        wallhavenCategories = setOf(WallhavenCategory.GENERAL, WallhavenCategory.PEOPLE),
                            ),
                )
                feedPreparationJob = null
                startFeed(request, null, FeedScope.HOME)
            }
        }
    }

    private fun startDiscoveryFeed(
        session: DiscoverySession,
        prefs: com.auroro.wallpapers.core.data.AppSettings,
        initialItems: List<Wallpaper>,
        recentlySeenKeys: Set<String>,
    ) {
        feedJob?.cancel()
        pager = null
        activeFeedScope = FeedScope.HOME
        currentTab = HomeTab.FOR_YOU
        feedInitialized = true
        lastDiscoverySession = session
        val generation = discoveryGeneration.begin()
        val request = FeedRequest(
            filter = WallpaperFilter(
                sources = setOf(WallpaperSource.WALLHAVEN),
                orientation = Orientation.PORTRAIT,
                resolution = ResolutionFilter.Preset(ResolutionPreset.P1080),
                sort = SortOption.RANDOM,
                wallhavenCategories = setOf(WallhavenCategory.GENERAL, WallhavenCategory.PEOPLE),
            ),
            aspectTolerance = prefs.aspectTolerance,
        )
        val safeInitial = LocalFilter.apply(initialItems, request.filter, request.aspectTolerance)
        _feed.value = FeedUiState(
            request = request,
            items = safeInitial,
            loading = true,
            initialLoadFinished = safeInitial.isNotEmpty(),
            endReached = true,
            forYouLabel = "Source-matched discovery",
            discoverySession = session,
            discoveryRotationEnabled = prefs.rotateDiscoveryHourly,
            discoveryWallhavenEnabled = prefs.wallhavenEnabled,
            cacheNotice = safeInitial.takeIf { it.isNotEmpty() }?.let { "Refreshing this saved mix…" },
        )
        feedJob = viewModelScope.launch {
            try {
                val result = app.discoveryEngine.discover(
                    session = session,
                    generation = generation,
                    preferences = prefs,
                    recentlySeenKeys = recentlySeenKeys,
                ) { progress ->
                    if (isCurrentDiscovery(session.sessionId, generation)) {
                        val current = _feed.value
                        val visibleItems = progress.items.ifEmpty { current.items }
                        _feed.value = current.copy(
                            items = visibleItems,
                            loading = progress.loading,
                            initialLoadFinished = visibleItems.isNotEmpty() || current.initialLoadFinished,
                            endReached = true,
                            pageError = null,
                            discoveryValidatedRecipeCount = progress.sections.size,
                            cacheNotice = if (progress.sections.any { it.fromCache }) "Restoring validated on-device results…" else null,
                        )
                    }
                }
                if (!isCurrentDiscovery(session.sessionId, generation)) return@launch
                val finalItems = when {
                    result.items.isNotEmpty() -> result.items
                    result.disabledReason != null -> emptyList()
                    else -> safeInitial
                }
                if (result.items.isNotEmpty()) {
                    app.settings.recordDiscoverySeen(result.items.map { it.key }, System.currentTimeMillis())
                }
                val message = result.message()
                val hasError = result.errors.isNotEmpty()
                val cacheNotice = when {
                    result.disabledReason != null -> result.disabledReason
                    result.items.isEmpty() && safeInitial.isNotEmpty() -> "Showing the last validated mix; this refresh did not return a replacement."
                    finalItems.isNotEmpty() && result.errors.isNotEmpty() && result.staleCacheUsed -> "Using validated on-device results while Wallhaven refreshes."
                    finalItems.isNotEmpty() && result.requestBudgetReached -> message
                    finalItems.isNotEmpty() && result.errors.isEmpty() && result.allFromCache -> message
                    finalItems.isEmpty() && result.errors.isEmpty() -> message
                    else -> null
                }
                _feed.value = _feed.value.copy(
                    items = finalItems,
                    statuses = listOf(result.sourceStatus),
                    loading = false,
                    initialLoadFinished = true,
                    endReached = true,
                    pageError = message.takeIf { hasError },
                    appendCount = if (finalItems.isNotEmpty()) 1 else 0,
                    forYouLabel = "Source-matched discovery",
                    discoverySession = session,
                    discoveryValidatedRecipeCount = result.sections.size,
                    cacheNotice = cacheNotice,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (isCurrentDiscovery(session.sessionId, generation)) {
                    _feed.value = _feed.value.copy(
                        loading = false,
                        initialLoadFinished = true,
                        endReached = true,
                        pageError = failure.message ?: "Discovery could not be completed.",
                    )
                }
            }
        }
    }

    private fun isCurrentDiscovery(sessionId: String, generation: Long): Boolean =
        discoveryGeneration.isCurrent(generation, sessionId, _feed.value.discoverySession?.sessionId) &&
            activeFeedScope == FeedScope.HOME && currentTab == HomeTab.FOR_YOU

    private fun rememberSearchQuery(query: String) {
        if (query.isNotBlank()) viewModelScope.launch { app.settings.rememberSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { app.settings.clearSearchHistory() }
    }

    /** Re-entering Search restores the last query, provider cursors and loaded results. */
    fun openSearch(query: String? = null, filter: WallpaperFilter? = null) {
        query?.let(::rememberSearchQuery)
        val useSavedSearch = query == null && filter == null
        val currentSettings = settings.value
        val currentEnabledSources = currentSettings.enabledSources()
        if (useSavedSearch && activeFeedScope == FeedScope.SEARCH && feedInitialized) {
            val current = _feed.value
            val settingsMatch = current.enabledSourcesAtRequest == currentEnabledSources
            if (settingsMatch) return
            startFeed(current.request, current.forYouLabel, FeedScope.SEARCH)
            return
        }
        if (activeFeedScope != FeedScope.SEARCH) saveActiveFeedSession()
        feedPreparationJob?.cancel()
        feedPreparationJob = null

        val cached = if (useSavedSearch) feedSessions.takeSearch() else null
        if (cached != null) {
            val saved = cached.state
            val settingsMatch = saved.enabledSourcesAtRequest == currentEnabledSources
            if (settingsMatch) {
                restoreFeedSession(FeedScope.SEARCH, cached)
            } else {
                startFeed(saved.request, saved.forYouLabel, FeedScope.SEARCH)
            }
            return
        }

        feedSessions.clearSearch()
        activeFeedScope = FeedScope.SEARCH
        val request = FeedRequest(
            query = query.orEmpty(),
            filter = filter ?: WallpaperFilter.Default,
        )
        startFeed(request, null, FeedScope.SEARCH)
    }

    fun submitSearch(query: String, filter: WallpaperFilter = _feed.value.request.filter) {
        rememberSearchQuery(query)
        startFeed(FeedRequest(query = query, filter = filter), null, FeedScope.SEARCH)
    }

    fun applyFilters(filter: WallpaperFilter) {
        val old = _feed.value.request
        startFeed(old.copy(filter = filter), null, FeedScope.SEARCH)
    }

    fun selectSources(sources: Set<WallpaperSource>, query: String = _feed.value.request.query) {
        val current = _feed.value.request.filter
        val selected = SearchSourceSelection.select(current, sources, query)
        startFeed(_feed.value.request.copy(query = query, filter = selected), null, FeedScope.SEARCH)
    }

    private fun saveActiveFeedSession() {
        feedPreparationJob?.cancel()
        feedPreparationJob = null
        discoveryGeneration.begin() // invalidate any result that races cancellation/navigation
        if (feedInitialized) {
            val current = _feed.value
            val session = FeedSession(
                state = current.copy(loading = false),
                pager = pager,
                resumeOnReturn = current.loading,
            )
            if (activeFeedScope == FeedScope.HOME) feedSessions.saveHome(currentTab, session) else feedSessions.saveSearch(session)
        }
        feedJob?.cancel()
        feedJob = null
    }

    private fun restoreFeedSession(scope: FeedScope, session: FeedSession) {
        discoveryGeneration.begin()
        activeFeedScope = scope
        feedInitialized = true
        pager = session.pager
        val state = session.state
        if (scope == FeedScope.HOME && state.discoverySession != null) {
            val discoverySession = state.discoverySession
            lastDiscoverySession = discoverySession
            pager = null
            val preferences = settings.value
            val needsSettingsSync = state.discoveryRotationEnabled != preferences.rotateDiscoveryHourly ||
                state.discoveryWallhavenEnabled != preferences.wallhavenEnabled
            val request = state.request
            val safeItems = if (preferences.wallhavenEnabled) {
                LocalFilter.apply(state.items, request.filter, request.aspectTolerance)
            } else emptyList()
            val restoredState = state.copy(
                request = request,
                items = safeItems,
                loading = false,
                discoveryWallhavenEnabled = preferences.wallhavenEnabled,
            )
            _feed.value = restoredState
            if (session.resumeOnReturn || needsSettingsSync) {
                viewModelScope.launch {
                    val currentPreferences = app.settings.current()
                    val now = System.currentTimeMillis()
                    val seen = if (currentPreferences.avoidRecentlySeen) {
                        app.discoveryCache.recentlySeen(now, currentPreferences.repeatAfterHours)
                    } else emptySet()
                    val stillActive = activeFeedScope == FeedScope.HOME && currentTab == HomeTab.FOR_YOU &&
                        lastDiscoverySession?.sessionId == discoverySession.sessionId &&
                        _feed.value.discoverySession?.sessionId == discoverySession.sessionId
                    if (stillActive) startDiscoveryFeed(discoverySession, currentPreferences, restoredState.items, seen)
                }
            }
            return
        }

        _feed.value = state.copy(loading = false)
        if (session.resumeOnReturn && session.pager != null && !state.endReached) {
            feedJob = viewModelScope.launch { loadPage() }
        }
    }

    private fun startFeed(request: FeedRequest, forYouLabel: String?, scope: FeedScope = activeFeedScope) {
        feedPreparationJob?.cancel()
        feedPreparationJob = null
        feedJob?.cancel()
        feedJob = null
        discoveryGeneration.begin()
        activeFeedScope = scope
        feedInitialized = true
        if (scope == FeedScope.HOME) feedSessions.clearHome(currentTab) else feedSessions.clearSearch()
        val currentSettings = settings.value
        val effectiveRequest = request.copy(aspectTolerance = currentSettings.aspectTolerance)
        pager = app.aggregator.newPager(effectiveRequest)
        _feed.value = FeedUiState(
            request = effectiveRequest,
            loading = true,
            forYouLabel = forYouLabel,
            enabledSourcesAtRequest = currentSettings.enabledSources(),
        )
        feedJob = viewModelScope.launch {
            loadPage()
        }
    }

    fun loadMore() {
        if (_feed.value.loading || _feed.value.endReached || _feed.value.discoverySession != null) return
        if (pager == null) {
            startFeed(_feed.value.request, _feed.value.forYouLabel, activeFeedScope)
            return
        }
        feedJob = viewModelScope.launch { loadPage() }
    }

    fun retryFeed() {
        val current = _feed.value
        val discoverySession = current.discoverySession
        if (discoverySession != null) {
            val expectedGeneration = discoveryGeneration.current()
            viewModelScope.launch {
                val preferences = app.settings.current()
                val now = System.currentTimeMillis()
                val seen = if (preferences.avoidRecentlySeen) {
                    app.discoveryCache.recentlySeen(now, preferences.repeatAfterHours)
                } else emptySet()
                if (discoveryGeneration.isCurrent(
                        expectedGeneration,
                        discoverySession.sessionId,
                        _feed.value.discoverySession?.sessionId,
                    ) && activeFeedScope == FeedScope.HOME && currentTab == HomeTab.FOR_YOU
                ) startDiscoveryFeed(discoverySession, preferences, current.items, seen)
            }
            return
        }
        pager?.retry()
        _feed.value = current.copy(pageError = null, endReached = false)
        loadMore()
    }

    fun manualRefreshHome() {
        viewModelScope.launch {
            val result = app.settings.claimManualDiscoveryRefresh(System.currentTimeMillis())
            if (!result.allowed) {
                val minutes = ((result.remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1)
                _message.emit("Manual refresh is cooling down. Try again in about $minutes min.")
                return@launch
            }
            feedSessions.clearHome(HomeTab.FOR_YOU)
            openHome(HomeTab.FOR_YOU, forceRefresh = true)
        }
    }

    fun refreshHourlyHomeOnResume() {
        val prefs = settings.value
        if (!prefs.rotateDiscoveryHourly) return
        val current = _feed.value.discoverySession ?: lastDiscoverySession ?: return
        if (current.hourBucket != DiscoveryRotation.bucketAt(System.currentTimeMillis())) {
            openHome(HomeTab.FOR_YOU)
        }
    }

    fun resetDiscoveryPreferences() {
        viewModelScope.launch {
            app.discoveryCache.clear()
            feedSessions.clearHome(HomeTab.FOR_YOU)
            lastDiscoverySession = null
            _message.emit("Discovery preferences, repeat history, cached IDs and disposable feed metadata reset. Saved wallpapers are unchanged.")
            if (activeFeedScope == FeedScope.HOME && currentTab == HomeTab.FOR_YOU) {
                openHome(HomeTab.FOR_YOU, forceRefresh = true)
            }
        }
    }

    private suspend fun loadPage() {
        val p = pager ?: return
        _feed.value = _feed.value.copy(loading = true, pageError = null)
        try {
            val result = p.loadNext()
            val before = _feed.value
            val freshItems = result.items
            val combined = dedupeByKey(before.items + freshItems)
            app.wallpaperStore.remember(freshItems)
            val sourceErrors = result.statuses.mapNotNull { status ->
                val error = (status.state as? SourceState.Failed)?.error ?: return@mapNotNull null
                "${status.source.displayName}: ${error.message ?: error.kind.title}"
            }
            _feed.value = before.copy(
                items = combined,
                statuses = result.statuses,
                loading = false,
                initialLoadFinished = true,
                endReached = result.endReached,
                pageError = sourceErrors.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                appendCount = before.appendCount + if (freshItems.isNotEmpty()) 1 else 0,
                cacheNotice = if (freshItems.isNotEmpty()) null else before.cacheNotice,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _feed.value = _feed.value.copy(
                loading = false,
                initialLoadFinished = true,
                pageError = t.message ?: "Couldn't load wallpapers.",
            )
        }
    }

    private fun dedupeByKey(items: List<Wallpaper>) = items.distinctBy { it.key }

    fun toggleFavorite(w: Wallpaper) {
        viewModelScope.launch {
            runCatching { app.favorites.toggle(w) }
                .onFailure { _message.emit("Couldn't update favorite: ${it.message ?: "database error"}") }
        }
    }

    fun openWallpaper(w: Wallpaper) {
        detailJob?.cancel()
        _relatedLoading.value = false
        _detail.value = DetailUiState(w, loading = true)
        detailJob = viewModelScope.launch {
            try {
                runCatching { app.history.record(w) }
                val full = app.aggregator.detail(w.key)
                if (full != null && _detail.value?.wallpaper?.key == w.key) {
                    app.wallpaperStore.remember(listOf(full))
                    _detail.value = _detail.value?.copy(wallpaper = full)
                }
                val provider = app.aggregator.provider(w.source)
                if (provider?.capabilities?.supportsRelated == true) {
                    _relatedLoading.value = true
                    val related = app.aggregator.related(full ?: w)
                    if (_detail.value?.wallpaper?.key == w.key) _detail.value = _detail.value?.copy(related = related, loading = false)
                    _relatedLoading.value = false
                } else {
                    if (_detail.value?.wallpaper?.key == w.key) _detail.value = _detail.value?.copy(loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (_detail.value?.wallpaper?.key == w.key) _detail.value = _detail.value?.copy(loading = false)
                _relatedLoading.value = false
            }
        }
    }

    fun openWallpaper(key: String, fallback: Wallpaper? = null) {
        viewModelScope.launch {
            val w = app.wallpaperStore.get(key) ?: fallback
            if (w == null) {
                _message.emit("Wallpaper details are no longer available. Search again to refresh.")
            } else {
                openWallpaper(w)
            }
        }
    }

    fun loadDetail(key: String) {
        val existing = _detail.value
        if (existing?.wallpaper?.key == key) return
        openWallpaper(key)
    }

    fun download(w: Wallpaper) {
        viewModelScope.launch {
            try {
                app.downloads.enqueue(w)
                _message.emit("Original download queued. Progress is in Downloads → Queue.")
            } catch (t: Throwable) {
                _message.emit("Couldn't queue download: ${t.message ?: "unknown error"}")
            }
        }
    }

    fun cancelDownload(key: String) {
        viewModelScope.launch {
            app.downloads.cancel(key)
            _message.emit("Download canceled.")
        }
    }

    fun retryDownload(key: String) {
        viewModelScope.launch {
            runCatching { app.downloads.retry(key) }
                .onSuccess { _message.emit("Retry queued.") }
                .onFailure { _message.emit("Couldn't retry: ${it.message}") }
        }
    }

    fun deleteDownload(row: OfflineRow) {
        viewModelScope.launch {
            val ok = app.downloads.delete(row.download.wallpaperKey)
            _message.emit(if (ok) "Saved wallpaper deleted." else "Android couldn't remove the file; its record and attribution were kept.")
        }
    }

    /** Prepare a set-only original using an existing saved file or a disposable app-cache file. */
    fun prepareWallpaperForApply(w: Wallpaper) {
        if (!w.setWallpaperAllowed) {
            _applyPreparation.value = ApplyPreparationUiState(wallpaperKey = w.key, error = "This image's licence doesn't permit setting it as a wallpaper.")
            return
        }
        val current = _applyPreparation.value
        if (current.wallpaperKey == w.key && (current.loading || current.uri != null)) return
        applyPreparationJob?.cancel()
        _applyPreparation.value = ApplyPreparationUiState(wallpaperKey = w.key, loading = true)
        applyPreparationJob = viewModelScope.launch {
            try {
                val saved = app.database.downloads().get(w.key)?.takeIf {
                    it.status == DownloadStatus.COMPLETED.name && app.downloads.fileExists(it)
                }
                if (saved?.localUri != null) {
                    _applyPreparation.value = ApplyPreparationUiState(
                        wallpaperKey = w.key,
                        uri = saved.localUri,
                        width = saved.savedWidth,
                        height = saved.savedHeight,
                    )
                } else {
                    val original = app.wallpaperApplyRepository.prepare(w)
                    _applyPreparation.value = ApplyPreparationUiState(
                        wallpaperKey = w.key,
                        uri = original.uri,
                        width = original.width,
                        height = original.height,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _applyPreparation.value = ApplyPreparationUiState(
                    wallpaperKey = w.key,
                    error = t.message ?: "Couldn't prepare the original image.",
                )
            }
        }
    }

    fun cancelWallpaperPreparation(key: String) {
        if (_applyPreparation.value.wallpaperKey != key) return
        applyPreparationJob?.cancel()
        applyPreparationJob = null
        if (_applyPreparation.value.loading) _applyPreparation.value = ApplyPreparationUiState()
    }

    /** Direct apply also uses temporary cache; it never enqueues a persistent download. */
    fun applyWallpaper(w: Wallpaper, target: ApplyTarget) {
        viewModelScope.launch {
            if (!w.setWallpaperAllowed) {
                _message.emit("This image's licence doesn't permit setting it as a wallpaper.")
                return@launch
            }
            try {
                val saved = app.database.downloads().get(w.key)?.takeIf {
                    it.status == DownloadStatus.COMPLETED.name && app.downloads.fileExists(it)
                }
                val uri = saved?.localUri ?: app.wallpaperApplyRepository.prepare(w).uri
                when (val result = app.wallpaperApplier.apply(uri, target)) {
                    ApplyResult.Success -> _message.emit("Wallpaper applied to ${target.label.lowercase()}.")
                    is ApplyResult.Failure -> _message.emit(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _message.emit("Couldn't apply wallpaper: ${t.message ?: "unknown error"}")
            }
        }
    }

    /** Apply a cropped preview without another download. */
    fun applySaved(uri: String, target: ApplyTarget, crop: com.auroro.wallpapers.core.data.download.NormalizedCrop) {
        viewModelScope.launch {
            when (val result = app.wallpaperApplier.apply(uri, target, crop)) {
                ApplyResult.Success -> _message.emit("Wallpaper applied to ${target.label.lowercase()}.")
                is ApplyResult.Failure -> _message.emit(result.message)
            }
        }
    }

    fun setCollectionMembership(collectionId: Long, w: Wallpaper, contains: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (contains) app.collections.remove(collectionId, w.key) else app.collections.add(collectionId, w)
            }.onFailure { _message.emit("Couldn't update collection: ${it.message}") }
        }
    }

    fun createCollection(raw: String, done: (Long?) -> Unit = {}) {
        viewModelScope.launch {
            when (val valid = app.collections.validateName(raw)) {
                is CollectionNameResult.Error -> {
                    _message.emit(valid.message)
                    done(null)
                }
                is CollectionNameResult.Ok -> {
                    app.collections.create(valid.name).onSuccess {
                        _message.emit("Collection created.")
                        done(it)
                    }.onFailure {
                        _message.emit(it.message ?: "Couldn't create collection.")
                        done(null)
                    }
                }
            }
        }
    }

    fun renameCollection(id: Long, raw: String, done: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            app.collections.rename(id, raw).onSuccess {
                _message.emit("Collection renamed.")
                done(true)
            }.onFailure {
                _message.emit(it.message ?: "Couldn't rename collection.")
                done(false)
            }
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            runCatching { app.collections.delete(id) }
                .onSuccess { _message.emit("Collection deleted.") }
                .onFailure { _message.emit("Couldn't delete collection: ${it.message}") }
        }
    }

    fun addToCollection(id: Long, w: Wallpaper) {
        viewModelScope.launch {
            runCatching { app.collections.add(id, w) }
                .onSuccess { _message.emit("Added to collection.") }
                .onFailure { _message.emit("Couldn't add to collection: ${it.message}") }
        }
    }

    fun openSource(url: String, action: (Uri) -> Unit) {
        if (UrlPolicy.isAllowedForBrowsing(url)) action(Uri.parse(url))
        else _message.tryEmit("This link isn't from a supported wallpaper source.")
    }

    fun updateSettings(transform: (com.auroro.wallpapers.core.data.AppSettings) -> com.auroro.wallpapers.core.data.AppSettings) {
        viewModelScope.launch {
            runCatching { app.settings.update(transform) }
                .onFailure { _message.emit("Couldn't save settings: ${it.message}") }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            applyPreparationJob?.cancel()
            applyPreparationJob = null
            _applyPreparation.value = ApplyPreparationUiState()
            runCatching { app.clearImageCache() }
                .onSuccess { _message.emit("Image cache cleared. Saved originals are unchanged.") }
                .onFailure { _message.emit("Couldn't clear cache: ${it.message}") }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            app.history.clear()
            _message.emit("History cleared.")
        }
    }

    fun storageInfo(done: (com.auroro.wallpapers.core.data.download.StorageInfo) -> Unit) {
        viewModelScope.launch { runCatching { app.downloads.storageInfo() }.onSuccess(done) }
    }

    companion object {
        const val APPLY_DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

class MainViewModelFactory(private val app: AppContainer) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) return MainViewModel(app) as T
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
