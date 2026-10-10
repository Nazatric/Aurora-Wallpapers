package com.auroro.wallpapers.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.core.data.CollectionNameResult
import com.auroro.wallpapers.core.data.FeedPager
import com.auroro.wallpapers.core.data.PageResult
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.data.SourceStatus
import com.auroro.wallpapers.core.data.WallpaperMapper
import com.auroro.wallpapers.core.data.download.ApplyResult
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.DownloadErrorKind
import com.auroro.wallpapers.core.data.download.LocalFiles
import com.auroro.wallpapers.core.database.CollectionSummary
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SearchSourceSelection
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.UrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withTimeout
import java.io.File

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
)

data class DetailUiState(
    val wallpaper: Wallpaper,
    val loading: Boolean = false,
    val related: List<Wallpaper> = emptyList(),
    val error: String? = null,
)

data class OfflineRow(val download: DownloadEntity, val wallpaper: Wallpaper?, val fileExists: Boolean)

private enum class FeedScope { HOME, SEARCH }

internal data class FeedSession(
    val state: FeedUiState,
    val pager: FeedPager?,
    val resumeOnReturn: Boolean,
)

/** Independent in-memory sessions prevent Home and Search navigation from clobbering one another. */
internal class FeedSessionCache {
    private val home = mutableMapOf<HomeTab, FeedSession>()
    private var search: FeedSession? = null

    fun saveHome(tab: HomeTab, session: FeedSession) { home[tab] = session }
    fun takeHome(tab: HomeTab): FeedSession? = home.remove(tab)
    fun clearHome(tab: HomeTab) { home.remove(tab) }

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

    fun openHome(tab: HomeTab = HomeTab.FOR_YOU) {
        if (activeFeedScope == FeedScope.HOME && currentTab == tab && feedInitialized) return
        if (activeFeedScope != FeedScope.HOME || currentTab != tab) saveActiveFeedSession()
        feedPreparationJob?.cancel()
        feedPreparationJob = null
        currentTab = tab
        activeFeedScope = FeedScope.HOME

        val cached = feedSessions.takeHome(tab)
        if (cached != null) {
            restoreFeedSession(FeedScope.HOME, cached)
            return
        }

        feedInitialized = false
        _feed.value = FeedUiState(loading = true)
        feedPreparationJob = viewModelScope.launch {
            val tags = if (tab == HomeTab.FOR_YOU) app.preferenceSignals.topTags() else emptyList()
            val effectiveSort = tab.sort ?: if (tags.isEmpty()) SortOption.NEWEST else SortOption.RELEVANCE
            val query = if (tags.isEmpty()) "" else tags.joinToString(" ")
            val sources = if (tab == HomeTab.FOR_YOU) emptySet() else setOf(WallpaperSource.WALLHAVEN)
            val request = FeedRequest(query = query, filter = WallpaperFilter(sort = effectiveSort, sources = sources))
            val summary = tags.takeIf { tab == HomeTab.FOR_YOU && it.isNotEmpty() }
                ?.joinToString(" · ", prefix = "Based on your saved tags: ")
            if (activeFeedScope == FeedScope.HOME && currentTab == tab) {
                feedPreparationJob = null
                startFeed(request, summary, FeedScope.HOME)
            }
        }
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
            restoreFeedSession(FeedScope.SEARCH, cached)
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
        val effectiveRequest = request.copy(aspectTolerance = settings.value.aspectTolerance)
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

    private suspend fun loadPage() {
        val p = pager ?: return
        _feed.value = _feed.value.copy(loading = true, pageError = null)
        try {
            val result = p.loadNext()
            app.wallpaperStore.remember(result.items)
            val before = _feed.value
            val combined = dedupeByKey(before.items + result.items)
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
                appendCount = before.appendCount + if (result.items.isNotEmpty()) 1 else 0,
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

    /** Prompts to apply using the full local original; downloads it first if no saved file exists. */
    fun applyWallpaper(w: Wallpaper, target: ApplyTarget) {
        viewModelScope.launch {
            if (!w.setWallpaperAllowed) {
                _message.emit("This image's licence doesn't permit setting it as a wallpaper.")
                return@launch
            }
            try {
                val existing = app.database.downloads().get(w.key)
                val local = existing?.takeIf { it.status == DownloadStatus.COMPLETED.name && app.downloads.fileExists(it) }?.localUri
                if (local == null && !w.downloadAllowed) {
                    _message.emit("The original isn't available to download from this retired source. Check Downloads for a saved copy.")
                    return@launch
                }
                val uri = if (local != null) {
                    local
                } else {
                    app.downloads.enqueue(w)
                    _message.emit("Downloading the original before applying it…")
                    val done = withTimeout(APPLY_DOWNLOAD_TIMEOUT_MS) {
                        app.downloads.observe(w.key).first { row ->
                            row?.status in setOf(DownloadStatus.COMPLETED.name, DownloadStatus.FAILED.name, DownloadStatus.CANCELED.name)
                        }
                    }
                    if (done?.status != DownloadStatus.COMPLETED.name || !app.downloads.fileExists(done)) {
                        throw IllegalStateException(done?.errorMessage ?: "The download didn't finish.")
                    }
                    done.localUri ?: throw IllegalStateException("The saved image has no local file URI.")
                }
                when (val result = app.wallpaperApplier.apply(uri, target)) {
                    ApplyResult.Success -> _message.emit("Wallpaper applied to ${target.label.lowercase()}.")
                    is ApplyResult.Failure -> _message.emit(result.message)
                }
            } catch (e: TimeoutCancellationException) {
                _message.emit("The original is still downloading. You can apply it later from Downloads.")
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
