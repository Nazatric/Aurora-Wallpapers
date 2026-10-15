package com.auroro.wallpapers.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.app.AppContainer
import com.auroro.wallpapers.core.data.FeedPager
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.data.SourceStatus
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SearchSourceSelection
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Slice-3 search: debounced queries paged through [FeedPager], which keeps one cursor per
 * provider and reports per-source statuses. Nothing is fabricated — when a source is
 * skipped or fails, its honest reason is surfaced in [UiState.message].
 */
class SearchViewModel(private val app: AppContainer) : ViewModel() {

    /** Result-grid scroll survives tab switches. */
    var gridScroll: Pair<Int, Int> = 0 to 0

    data class UiState(
        val query: String = "",
        /** Empty set means "all integrated sources". */
        val sources: Set<WallpaperSource> = emptySet(),
        val items: List<Wallpaper> = emptyList(),
        val searching: Boolean = false,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val message: String? = null,
        val hasSearched: Boolean = false,
        val favoriteKeys: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pager: FeedPager? = null
    private var searchJob: Job? = null
    private var moreJob: Job? = null

    init {
        viewModelScope.launch {
            app.favorites.observeKeys().collect { keys ->
                _state.update { it.copy(favoriteKeys = keys) }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        schedule()
    }

    fun setSources(sources: Set<WallpaperSource>) {
        _state.update { it.copy(sources = sources) }
        schedule()
    }

    /** Toggles one source chip; an empty selection means "All sources". */
    fun toggleSource(source: WallpaperSource) {
        val current = _state.value.sources
        setSources(if (source in current) current - source else current + source)
    }

    /** "All sources" chip: clears the selection back to every integrated catalogue. */
    fun clearSources() {
        setSources(emptySet())
    }

    fun retry() {
        pager?.retry()
        schedule(immediate = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.searching || current.loadingMore || current.endReached) return
        val p = pager ?: return
        if (moreJob?.isActive == true) return
        _state.update { it.copy(loadingMore = true) }
        moreJob = viewModelScope.launch {
            val result = p.loadNext()
            val existing = _state.value.items.mapTo(hashSetOf()) { it.key }
            val fresh = result.items.filterNot { it.key in existing }
            _state.update {
                it.copy(
                    items = it.items + fresh,
                    loadingMore = false,
                    endReached = result.endReached,
                    message = statusMessage(result.statuses) ?: it.message,
                )
            }
        }
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { app.favorites.toggle(wallpaper) }
    }

    private fun schedule(immediate: Boolean = false) {
        searchJob?.cancel()
        moreJob?.cancel()
        val query = _state.value.query
        val sources = _state.value.sources
        if (query.isBlank() && sources.isEmpty()) {
            // Nothing to search for yet: return to the idle state instead of fetching a feed.
            pager = null
            _state.update {
                it.copy(items = emptyList(), searching = false, loadingMore = false, endReached = false, message = null, hasSearched = false)
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(QUERY_DEBOUNCE_MS)
            runSearch()
        }
    }

    private suspend fun runSearch() {
        val query = _state.value.query.trim()
        val selection = SearchSourceSelection.select(WallpaperFilter.Default, _state.value.sources, query)
        if (query.isNotBlank()) {
            app.settings.rememberSearchQuery(query)
        }
        val p = FeedPager(
            providers = app.feedProviders,
            request = FeedRequest(query = query, filter = selection),
        )
        pager = p
        _state.update { it.copy(searching = true, message = null, loadingMore = false) }
        val result = p.loadNext()
        val seen = hashSetOf<String>()
        val fresh = result.items.filter { seen.add(it.key) }
        _state.update {
            it.copy(
                items = fresh,
                searching = false,
                hasSearched = true,
                endReached = result.endReached,
                message = statusMessage(result.statuses),
            )
        }
    }

    /** Honest, per-source explanation text; null when every selected source delivered. */
    private fun statusMessage(statuses: List<SourceStatus>): String? {
        val notes = statuses.mapNotNull { status ->
            when (val state = status.state) {
                is SourceState.Skipped -> "${status.source.displayName}: ${state.reason}"
                is SourceState.Failed ->
                    "${status.source.displayName}: ${state.error.message ?: "unavailable right now"}"

                else -> null
            }
        }
        return notes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    companion object {
        private const val QUERY_DEBOUNCE_MS = 350L

        fun factory(app: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(app) as T
            }
        }
    }
}
