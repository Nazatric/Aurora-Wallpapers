package com.auroro.wallpapers.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.core.data.CollectionNameResult
import com.auroro.wallpapers.core.data.FeedPager
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.data.SourceStatus
import com.auroro.wallpapers.core.data.WallpaperMapper
import com.auroro.wallpapers.core.data.download.ApplyResult
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.DiscoveryQuality
import com.auroro.wallpapers.core.model.DiscoveryRotation
import com.auroro.wallpapers.core.model.DiscoverySelection
import com.auroro.wallpapers.core.model.DiscoveryStyle
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
    val discoverySelection: DiscoverySelection? = null,
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
        home[homeKey(tab, session.state.discoverySelection?.cacheKey)] = session
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
    private var lastDiscoverySelection: DiscoverySelection? = null
    private var discoveryFallbackCache = false
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
        viewModelScope.launch {
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

    fun openHome(
        tab: HomeTab = HomeTab.FOR_YOU,
        pinnedStyleId: String? = settings.value.pinnedDiscoveryStyleId,
        forceRefresh: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val currentSelection = _feed.value.discoverySelection ?: lastDiscoverySelection
        val visibleSettings = settings.value
        val desiredBucket = when {
            !visibleSettings.rotateDiscoveryHourly -> currentSelection?.bucket ?: 0L
            else -> DiscoveryRotation.bucketAt(now)
        }
        val validPin = DiscoveryStyle.fromId(pinnedStyleId)?.id
        val selectionIsCurrent = tab != HomeTab.FOR_YOU || currentSelection?.let { selection ->
            selection.bucket == desiredBucket &&
                (validPin == null && !selection.pinned || validPin == selection.style.id && selection.pinned)
        } == true
        if (!forceRefresh && activeFeedScope == FeedScope.HOME && currentTab == tab && feedInitialized && selectionIsCurrent) return

        if (activeFeedScope != FeedScope.HOME || currentTab != tab || forceRefresh || !selectionIsCurrent) saveActiveFeedSession()
        feedPreparationJob?.cancel()
        feedPreparationJob = null
        currentTab = tab
        activeFeedScope = FeedScope.HOME
        feedInitialized = false
        _feed.value = FeedUiState(loading = true)
        feedPreparationJob = viewModelScope.launch {
            val prefs = app.settings.current()
            val pin = DiscoveryStyle.fromId(pinnedStyleId ?: prefs.pinnedDiscoveryStyleId)?.id
            val previous = _feed.value.discoverySelection ?: lastDiscoverySelection
            val selection = if (tab == HomeTab.FOR_YOU) {
                val samePinnedStyle = previous != null &&
                    (pin == null && !previous.pinned || pin == previous.style.id && previous.pinned)
                when {
                    !forceRefresh && !prefs.rotateDiscoveryHourly && samePinnedStyle -> previous
                    !forceRefresh && previous?.bucket == DiscoveryRotation.bucketAt(now) && samePinnedStyle -> previous
                    else -> DiscoveryRotation.select(
                        epochMillis = if (prefs.rotateDiscoveryHourly) now else 0L,
                        preferredStyleIds = prefs.preferredDiscoveryStyleIds,
                        hiddenStyleIds = prefs.hiddenDiscoveryStyleIds,
                        pinnedStyleId = pin,
                        refreshOrdinal = prefs.discoveryRefreshOrdinal,
                    )
                }
            } else null

            if (activeFeedScope != FeedScope.HOME || currentTab != tab) return@launch
            if (selection != null) {
                lastDiscoverySelection = selection
                if (!forceRefresh) {
                    val cachedSession = feedSessions.takeHome(tab, selection.cacheKey)
                    if (cachedSession != null) {
                        feedPreparationJob = null
                        restoreFeedSession(FeedScope.HOME, cachedSession)
                        return@launch
                    }
                }
                feedSessions.clearHome(tab)
                val exactSnapshot = app.discoveryCache.restore(selection.cacheKey)
                var initialItems = exactSnapshot
                var usedFallback = false
                var cacheNotice: String? = if (exactSnapshot.isNotEmpty()) "Refreshing this saved hourly collection…" else null
                if (initialItems.isEmpty()) {
                    val offlineFallback = app.discoveryCache.restoreLatest()
                    if (offlineFallback != null) {
                        usedFallback = true
                        initialItems = offlineFallback.second
                        val cachedStyle = offlineFallback.first.split(':').getOrNull(1)
                            ?.let(DiscoveryStyle::fromId)?.label
                        cacheNotice = if (cachedStyle == null) {
                            "Showing a recent on-device collection while this mix loads."
                        } else {
                            "Showing cached $cachedStyle wallpapers while this mix loads."
                        }
                    }
                }
                val note = if (prefs.rotateDiscoveryHourly) "Changes each hour · ${selection.style.description}" else selection.style.description
                val recentlySeen = if (prefs.avoidRecentlySeen) {
                    app.discoveryCache.recentlySeen(now, prefs.repeatAfterHours)
                } else emptySet()
                feedPreparationJob = null
                startDiscoveryFeed(selection, prefs, initialItems, cacheNotice, note, usedFallback, recentlySeen)
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
                        reducePotentiallyExplicitContent = prefs.reducePotentiallyExplicitContent,
                    ),
                )
                feedPreparationJob = null
                startFeed(request, null, FeedScope.HOME)
            }
        }
    }

    private fun startDiscoveryFeed(
        selection: DiscoverySelection,
        prefs: com.auroro.wallpapers.core.data.AppSettings,
        cachedItems: List<Wallpaper>,
        cacheNotice: String?,
        description: String,
        usedFallback: Boolean,
        recentlySeenKeys: Set<String>,
    ) {
        feedJob?.cancel()
        feedJob = null
        activeFeedScope = FeedScope.HOME
        currentTab = HomeTab.FOR_YOU
        feedInitialized = true
        lastDiscoverySelection = selection
        discoveryFallbackCache = usedFallback
        val request = FeedRequest(
            query = selection.query,
            filter = WallpaperFilter(
                sources = setOf(WallpaperSource.WALLHAVEN),
                orientation = Orientation.PORTRAIT,
                resolution = ResolutionFilter.Preset(ResolutionPreset.P1080),
                sort = SortOption.RELEVANCE,
                wallhavenCategories = setOf(WallhavenCategory.GENERAL, WallhavenCategory.PEOPLE),
                reducePotentiallyExplicitContent = prefs.reducePotentiallyExplicitContent,
            ),
            aspectTolerance = prefs.aspectTolerance,
        )
        pager = app.aggregator.newPager(request)
        val rankedCached = DiscoveryQuality.rank(cachedItems, selection, recentlySeenKeys)
        _feed.value = FeedUiState(
            request = request,
            items = rankedCached,
            loading = true,
            initialLoadFinished = rankedCached.isNotEmpty(),
            forYouLabel = description,
            discoverySelection = selection,
            cacheNotice = cacheNotice,
        )
        feedJob = viewModelScope.launch { loadPage() }
    }

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
        if (useSavedSearch && activeFeedScope == FeedScope.SEARCH && feedInitialized) return
        if (activeFeedScope != FeedScope.SEARCH) saveActiveFeedSession()
        feedPreparationJob?.cancel()
        feedPreparationJob = null

        val cached = if (useSavedSearch) feedSessions.takeSearch() else null
        if (cached != null) {
            if (cached.state.request.filter.reducePotentiallyExplicitContent == settings.value.reducePotentiallyExplicitContent) {
                restoreFeedSession(FeedScope.SEARCH, cached)
            } else {
                startFeed(cached.state.request, null, FeedScope.SEARCH)
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
        activeFeedScope = scope
        feedInitialized = true
        pager = session.pager
        session.state.discoverySelection?.let { lastDiscoverySelection = it }
        discoveryFallbackCache = false
        _feed.value = session.state.copy(loading = false)
        if (session.resumeOnReturn && session.pager != null && !session.state.endReached) {
            feedJob = viewModelScope.launch { loadPage() }
        }
    }

    private fun startFeed(request: FeedRequest, forYouLabel: String?, scope: FeedScope = activeFeedScope) {
        feedJob?.cancel()
        feedJob = null
        activeFeedScope = scope
        feedInitialized = true
        if (scope == FeedScope.HOME) feedSessions.clearHome(currentTab) else feedSessions.clearSearch()
        discoveryFallbackCache = false
        val effectiveRequest = request.copy(
            filter = request.filter.copy(reducePotentiallyExplicitContent = settings.value.reducePotentiallyExplicitContent),
            aspectTolerance = settings.value.aspectTolerance,
        )
        pager = app.aggregator.newPager(effectiveRequest)
        _feed.value = FeedUiState(request = effectiveRequest, loading = true, forYouLabel = forYouLabel)
        feedJob = viewModelScope.launch {
            loadPage()
        }
    }

    fun loadMore() {
        if (_feed.value.loading || _feed.value.endReached) return
        if (pager == null) {
            startFeed(_feed.value.request, _feed.value.forYouLabel, activeFeedScope)
            return
        }
        feedJob = viewModelScope.launch { loadPage() }
    }

    fun retryFeed() {
        pager?.retry()
        val current = _feed.value
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
            openHome(HomeTab.FOR_YOU, settings.value.pinnedDiscoveryStyleId, forceRefresh = true)
        }
    }

    fun refreshHourlyHomeOnResume() {
        val prefs = settings.value
        if (!prefs.rotateDiscoveryHourly) return
        val current = _feed.value.discoverySelection ?: lastDiscoverySelection ?: return
        if (current.bucket != DiscoveryRotation.bucketAt(System.currentTimeMillis())) {
            openHome(HomeTab.FOR_YOU, prefs.pinnedDiscoveryStyleId)
        }
    }

    fun resetDiscoveryPreferences() {
        viewModelScope.launch {
            app.discoveryCache.clear()
            feedSessions.clearHome(HomeTab.FOR_YOU)
            lastDiscoverySelection = null
                _message.emit("Discovery preferences, repeat history, cached IDs and disposable feed metadata reset. Saved wallpapers are unchanged.")
            if (activeFeedScope == FeedScope.HOME && currentTab == HomeTab.FOR_YOU) {
                openHome(HomeTab.FOR_YOU, null, forceRefresh = true)
            }
        }
    }

    private suspend fun loadPage() {
        val p = pager ?: return
        _feed.value = _feed.value.copy(loading = true, pageError = null)
        try {
            val result = p.loadNext()
            val before = _feed.value
            val selection = before.discoverySelection
            val prefs = if (selection != null) app.settings.current() else null
            val recentlySeen = if (selection != null && prefs?.avoidRecentlySeen == true) {
                app.discoveryCache.recentlySeen(System.currentTimeMillis(), prefs.repeatAfterHours)
            } else emptySet()
            val freshItems = if (selection != null) {
                DiscoveryQuality.rank(result.items, selection, recentlySeen)
            } else result.items
            val canTryAlternateQuery = selection != null && before.items.isEmpty() && freshItems.isEmpty() &&
                !result.hadFailure && result.statuses.none { it.state is SourceState.Failed || it.state is SourceState.Skipped }
            if (canTryAlternateQuery) {
                val alternate = selection?.fallbackQuery()
                if (alternate != null) {
                    val currentSettings = prefs ?: app.settings.current()
                    val description = if (currentSettings.rotateDiscoveryHourly) {
                        "Changes each hour · ${alternate.style.description}"
                    } else alternate.style.description
                    startDiscoveryFeed(alternate, currentSettings, emptyList(), null, description, false, recentlySeen)
                    return
                }
            }
            val replaceFallback = selection != null && discoveryFallbackCache && freshItems.isNotEmpty()
            val combined = if (replaceFallback) freshItems else dedupeByKey(before.items + freshItems)
            if (selection != null && freshItems.isNotEmpty()) {
                app.discoveryCache.save(selection, combined, System.currentTimeMillis())
                discoveryFallbackCache = false
            } else {
                app.wallpaperStore.remember(result.items)
            }
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
                cacheNotice = if (replaceFallback || freshItems.isNotEmpty()) null else before.cacheNotice,
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
