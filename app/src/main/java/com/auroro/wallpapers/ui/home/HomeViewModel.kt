package com.auroro.wallpapers.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.R
import com.auroro.wallpapers.app.AppContainer
import com.auroro.wallpapers.core.data.FeedPager
import com.auroro.wallpapers.core.data.SourceState
import com.auroro.wallpapers.core.data.SourceStatus
import com.auroro.wallpapers.core.data.download.ApplyResult
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.data.download.NormalizedCrop
import com.auroro.wallpapers.core.data.FeedLayout
import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.LocalFilter
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallpaperFilter

import com.auroro.wallpapers.core.model.DiscoveryStylePreferenceMapping
import com.auroro.wallpapers.core.model.DiscoverySessionPlanner
import com.auroro.wallpapers.core.model.Wallpaper
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Slice-1 home feed state: one discovery session per selection, honest loading/error/end
 * states, and continuation batches when the first pass is consumed. The engine and planners
 * live in the data layer; this ViewModel only sequences them and exposes immutable UI state.
 * Slice 2 adds detail actions (download, set wallpaper) routed through the rights gates.
 */
/** Top-level home feed modes; each maps to a real catalogue sort or the curated engine. */
enum class FeedMode(@param:StringRes val labelRes: Int) {
    FOR_YOU(R.string.chip_for_you),
    TRENDING(R.string.mode_trending),
    NEWEST(R.string.mode_newest),
    RANDOM(R.string.mode_random),
}

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    data class UiState(
        val items: List<Wallpaper> = emptyList(),
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val endReached: Boolean = false,
        val mode: FeedMode = FeedMode.FOR_YOU,
        val orientation: Orientation = Orientation.ANY,
        val layout: FeedLayout = FeedLayout.CARDS,
        val favoriteKeys: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-off UI messages (resource-resolved here, or verbatim data-layer text) for snackbars. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val generation = AtomicLong(0L)
    private var session: DiscoverySession? = null
    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var shuffleJob: Job? = null
    private var sortPager: FeedPager? = null
    private var rawItems: List<Wallpaper> = emptyList()
    private var emptyStreak = 0

    /** Orientation filter is applied locally over the loaded batch (honest, instant). */
    private fun filtered(list: List<Wallpaper>): List<Wallpaper> {
        val orientation = _state.value.orientation
        if (orientation == Orientation.ANY) return list
        val filter = WallpaperFilter(orientation = orientation)
        return list.filter { LocalFilter.matches(it, filter) }
    }

    init {
        viewModelScope.launch {
            app.favorites.observeKeys().collect { keys ->
                _state.update { it.copy(favoriteKeys = keys) }
            }
        }
        viewModelScope.launch {
            app.settings.settings.collect { settings ->
                _state.update { it.copy(layout = settings.feedLayout) }
            }
        }
        reload()
    }

    fun reload() {
        if (_state.value.mode != FeedMode.FOR_YOU) {
            loadSorted(_state.value.mode)
            return
        }
        loadJob?.cancel()
        moreJob?.cancel()
        val gen = generation.incrementAndGet()
        _state.update {
            it.copy(items = emptyList(), loading = true, loadingMore = false, error = null, notice = null, endReached = false)
        }
        loadJob = viewModelScope.launch {
            val prefs = app.settings.current()
            val now = System.currentTimeMillis()
            val planned = DiscoverySessionPlanner.newRandomSession(
                nowMillis = now,
                rotateHourly = prefs.rotateDiscoveryHourly,
                refreshOrdinal = session?.refreshOrdinal?.plus(1) ?: 0,
                recentRecipeIds = app.settings.recentlyUsedDiscoveryRecipeIds(now, prefs.repeatAfterHours),
                excludedRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(prefs.hiddenDiscoveryStyleIds),
            )
            session = planned
            app.discoveryPools.recordSessionRecipeIds(planned.candidateRecipeIds, now)
            val seen = if (prefs.avoidRecentlySeen) {
                app.settings.recentlySeenDiscoveryKeys(now, prefs.repeatAfterHours)
            } else {
                emptySet()
            }
            val result = app.discoveryEngine.discover(
                session = planned,
                generation = gen,
                preferences = prefs,
                recentlySeenKeys = seen,
            ) { progress ->
                if (gen == generation.get()) {
                    rawItems = progress.items
                    _state.update { it.copy(items = filtered(progress.items), loading = progress.loading) }
                }
            }
            if (gen != generation.get()) return@launch
            emptyStreak = 0
            val items = result.items
            rawItems = items
            if (items.isNotEmpty()) {
                app.settings.recordDiscoverySeen(items.map { it.key }, System.currentTimeMillis())
            }
            _state.update {
                it.copy(
                    items = filtered(items),
                    loading = false,
                    endReached = items.isEmpty(),
                    error = if (items.isEmpty()) result.message() ?: GENERIC_ERROR else null,
                    notice = if (items.isNotEmpty() && result.staleCacheUsed) result.message() else null,
                )
            }
        }
    }

    /**
     * Magic-button shuffle: plans a fresh session (next ordinal, current mix excluded) but
     * keeps showing the current items until the new batch actually arrives — one shuffle at
     * a time, so rapid taps can never cancel a batch mid-flight and leave the screen empty.
     */
    fun shuffle() {
        if (_state.value.mode != FeedMode.FOR_YOU) {
            shuffleSorted()
            return
        }
        if (shuffleJob?.isActive == true) return
        loadJob?.cancel()
        moreJob?.cancel()
        val gen = generation.incrementAndGet()
        _state.update { it.copy(loading = true, loadingMore = false, error = null, notice = null) }
        shuffleJob = viewModelScope.launch {
            val prefs = app.settings.current()
            val now = System.currentTimeMillis()
            val currentKeys = _state.value.items.mapTo(hashSetOf()) { it.key }
            val planned = DiscoverySessionPlanner.newRandomSession(
                nowMillis = now,
                rotateHourly = prefs.rotateDiscoveryHourly,
                refreshOrdinal = session?.refreshOrdinal?.plus(1) ?: 0,
                recentRecipeIds = app.settings.recentlyUsedDiscoveryRecipeIds(now, prefs.repeatAfterHours),
                excludedRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(prefs.hiddenDiscoveryStyleIds),
            )
            session = planned
            app.discoveryPools.recordSessionRecipeIds(planned.candidateRecipeIds, now)
            val seen = if (prefs.avoidRecentlySeen) {
                app.settings.recentlySeenDiscoveryKeys(now, prefs.repeatAfterHours)
            } else {
                emptySet()
            }
            val result = app.discoveryEngine.discover(
                session = planned,
                generation = gen,
                preferences = prefs,
                recentlySeenKeys = seen + currentKeys,
            ) { _ -> }
            if (gen != generation.get()) return@launch
            emptyStreak = 0
            val fresh = result.items
            when {
                fresh.isNotEmpty() -> {
                    rawItems = fresh
                    app.settings.recordDiscoverySeen(fresh.map { it.key }, System.currentTimeMillis())
                    _state.update {
                        it.copy(
                            items = filtered(fresh),
                            loading = false,
                            endReached = false,
                            notice = if (result.staleCacheUsed) result.message() else null,
                        )
                    }
                }
                // Nothing new came back: keep the mix the user is looking at and say so.
                else -> _state.update {
                    it.copy(
                        loading = false,
                        notice = result.message() ?: "That shuffle came back empty — keeping your current mix.",
                    )
                }
            }
        }
    }

    /** Near-end trigger from the list: fetches the next discovery batch and appends fresh items. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached || current.items.isEmpty()) return
        if (current.mode != FeedMode.FOR_YOU) {
            loadMoreSorted()
            return
        }
        if (moreJob?.isActive == true) return
        val base = session ?: return
        val gen = generation.get()
        _state.update { it.copy(loadingMore = true, notice = null) }
        moreJob = viewModelScope.launch {
            val prefs = app.settings.current()
            val now = System.currentTimeMillis()
            val excludeKeys = rawItems.mapTo(hashSetOf()) { it.key }
            val seen = if (prefs.avoidRecentlySeen) {
                app.settings.recentlySeenDiscoveryKeys(now, prefs.repeatAfterHours)
            } else {
                emptySet()
            }
            val batch = DiscoverySessionPlanner.newRandomSession(
                nowMillis = now,
                rotateHourly = false,
                refreshOrdinal = base.refreshOrdinal,
                recentRecipeIds = app.settings.recentlyUsedDiscoveryRecipeIds(now, prefs.repeatAfterHours) +
                    base.candidateRecipeIds,
                excludedRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(prefs.hiddenDiscoveryStyleIds),
            )
            app.discoveryPools.recordSessionRecipeIds(batch.candidateRecipeIds, now)
            val result = app.discoveryEngine.discover(
                session = batch,
                generation = gen,
                preferences = prefs,
                recentlySeenKeys = seen + excludeKeys,
            ) { progress ->
                if (gen == generation.get()) {
                    _state.update { it.copy(loadingMore = progress.loading) }
                }
            }
            if (gen != generation.get()) return@launch
            val fresh = result.items.filterNot { it.key in excludeKeys }
            if (fresh.isNotEmpty()) {
                emptyStreak = 0
                rawItems = rawItems + fresh
                app.settings.recordDiscoverySeen(fresh.map { it.key }, System.currentTimeMillis())
                _state.update {
                    it.copy(
                        items = filtered(rawItems),
                        loadingMore = false,
                        notice = null,
                    )
                }
            } else {
                emptyStreak += 1
                val exhausted = emptyStreak >= MAX_EMPTY_CONTINUATIONS
                _state.update {
                    it.copy(
                        loadingMore = false,
                        endReached = exhausted,
                        notice = if (exhausted) {
                            null
                        } else {
                            result.message() ?: "Nothing new in that pass — scroll again for another batch."
                        },
                    )
                }
            }
        }
    }

    /** Switches the top-level feed mode: curated discovery or a real catalogue sort. */
    fun selectMode(mode: FeedMode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        if (mode == FeedMode.FOR_YOU) reload() else loadSorted(mode)
    }

    /** Icon-only orientation filter on the feed header; re-filters the loaded batch instantly. */
    fun setOrientation(orientation: Orientation) {
        if (orientation == _state.value.orientation) return
        // Filter with the NEW orientation explicitly: reading _state.value inside the
        // update lambda would still see the previous value and silently no-op.
        val filter = WallpaperFilter(orientation = orientation)
        val next = rawItems.filter { LocalFilter.matches(it, filter) }
        _state.update { it.copy(orientation = orientation, items = next) }
    }

    private fun buildSortedPager(mode: FeedMode): FeedPager {
        val sort = when (mode) {
            FeedMode.TRENDING -> SortOption.POPULAR
            FeedMode.NEWEST -> SortOption.NEWEST
            FeedMode.RANDOM -> SortOption.RANDOM
            FeedMode.FOR_YOU -> SortOption.SOURCE_DEFAULT
        }
        return FeedPager(
            providers = app.feedProviders,
            request = FeedRequest(filter = WallpaperFilter(sort = sort)),
        )
    }

    private fun loadSorted(mode: FeedMode) {
        loadJob?.cancel()
        moreJob?.cancel()
        val gen = generation.incrementAndGet()
        _state.update {
            it.copy(
                items = emptyList(), loading = true, loadingMore = false,
                error = null, notice = null, endReached = false, mode = mode,
            )
        }
        loadJob = viewModelScope.launch {
            val pager = buildSortedPager(mode)
            sortPager = pager
            val result = pager.loadNext()
            if (gen != generation.get()) return@launch
            val seen = hashSetOf<String>()
            val fresh = result.items.filter { seen.add(it.key) }
            rawItems = fresh
            if (fresh.isNotEmpty()) {
                app.settings.recordDiscoverySeen(fresh.map { it.key }, System.currentTimeMillis())
            }
            _state.update {
                it.copy(
                    items = filtered(fresh),
                    loading = false,
                    endReached = result.endReached,
                    error = if (fresh.isEmpty()) {
                        "The catalogues returned nothing for this mode right now. Try again shortly."
                    } else {
                        null
                    },
                    notice = if (fresh.isNotEmpty()) statusNotice(result.statuses) else null,
                )
            }
        }
    }

    private fun loadMoreSorted() {
        val pager = sortPager ?: return
        if (moreJob?.isActive == true) return
        val gen = generation.get()
        _state.update { it.copy(loadingMore = true, notice = null) }
        moreJob = viewModelScope.launch {
            val result = pager.loadNext()
            if (gen != generation.get()) return@launch
            val existing = rawItems.mapTo(hashSetOf()) { it.key }
            val fresh = result.items.filter { existing.add(it.key) }
            rawItems = rawItems + fresh
            if (fresh.isNotEmpty()) {
                app.settings.recordDiscoverySeen(fresh.map { it.key }, System.currentTimeMillis())
            }
            _state.update {
                it.copy(
                    items = filtered(rawItems),
                    loadingMore = false,
                    endReached = result.endReached,
                    notice = statusNotice(result.statuses),
                )
            }
        }
    }

    /** Magic button in a sorted mode: rebuilds the pager (fresh random seed) and swaps atomically. */
    private fun shuffleSorted() {
        if (shuffleJob?.isActive == true) return
        loadJob?.cancel()
        moreJob?.cancel()
        val mode = _state.value.mode
        val gen = generation.incrementAndGet()
        _state.update { it.copy(loading = true, loadingMore = false, error = null, notice = null) }
        shuffleJob = viewModelScope.launch {
            val pager = buildSortedPager(mode)
            sortPager = pager
            val result = pager.loadNext()
            if (gen != generation.get()) return@launch
            val seen = hashSetOf<String>()
            val fresh = result.items.filter { seen.add(it.key) }
            when {
                fresh.isNotEmpty() -> {
                    rawItems = fresh
                    app.settings.recordDiscoverySeen(fresh.map { it.key }, System.currentTimeMillis())
                    _state.update {
                        it.copy(items = filtered(fresh), loading = false, endReached = result.endReached, notice = null)
                    }
                }
                else -> _state.update {
                    it.copy(
                        loading = false,
                        notice = statusNotice(result.statuses)
                            ?: "That shuffle came back empty — keeping your current mix.",
                    )
                }
            }
        }
    }

    /** Honest, per-source explanation text; null when every source delivered. */
    private fun statusNotice(statuses: List<SourceStatus>): String? {
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

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { app.favorites.toggle(wallpaper) }
    }

    /** Opening a detail screen counts as a deliberate view: it feeds the Library history. */
    fun recordHistory(wallpaper: Wallpaper) {
        viewModelScope.launch { app.history.record(wallpaper) }
    }

    /** Queues the original download through the rights-gated data layer. */
    fun download(wallpaper: Wallpaper) {
        viewModelScope.launch {
            if (!wallpaper.downloadAllowed) {
                _messages.emit(app.appContext.getString(R.string.download_blocked_rights))
                return@launch
            }
            runCatching { app.downloads.enqueue(wallpaper) }
                .onSuccess { _messages.emit(app.appContext.getString(R.string.download_started)) }
                .onFailure {
                    _messages.emit(it.message ?: "The download could not be queued.")
                }
        }
    }

    /**
     * Fetches the original and applies it via WallpaperManager; honours both rights gates.
     * [crop] comes from the crop editor; null lets the applier center-crop to the screen.
     */
    fun setWallpaper(
        wallpaper: Wallpaper,
        target: ApplyTarget,
        crop: NormalizedCrop? = null,
        rotationDegrees: Int = 0,
    ) {
        viewModelScope.launch {
            when {
                !wallpaper.downloadAllowed ->
                    _messages.emit(app.appContext.getString(R.string.download_blocked_rights))

                !wallpaper.setWallpaperAllowed ->
                    _messages.emit(app.appContext.getString(R.string.apply_blocked_licence))

                else -> {
                    val capabilities = app.wallpaperApplier.capabilities()
                    if (!capabilities.usable) {
                        _messages.emit(
                            capabilities.unavailableReason
                                ?: app.appContext.getString(R.string.apply_unavailable),
                        )
                    } else {
                        runCatching {
                            val original = app.wallpaperApplyRepository.prepare(wallpaper)
                            app.wallpaperApplier.apply(original.uri, target, crop, rotationDegrees)
                        }.onSuccess { result ->
                            when (result) {
                                ApplyResult.Success ->
                                    _messages.emit(app.appContext.getString(R.string.apply_success))

                                is ApplyResult.Failure -> _messages.emit(result.message)
                            }
                        }.onFailure {
                            _messages.emit(it.message ?: "The wallpaper could not be set.")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_EMPTY_CONTINUATIONS = 2
        private const val GENERIC_ERROR = "The enabled catalogues returned nothing usable. Try again shortly."

        fun factory(app: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(app) as T
            }
        }
    }
}
