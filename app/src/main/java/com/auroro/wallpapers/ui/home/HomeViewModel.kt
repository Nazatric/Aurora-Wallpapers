package com.auroro.wallpapers.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.R
import com.auroro.wallpapers.app.AppContainer
import com.auroro.wallpapers.core.data.download.ApplyResult
import com.auroro.wallpapers.core.data.download.ApplyTarget
import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.DiscoveryStyle
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
class HomeViewModel(private val app: AppContainer) : ViewModel() {

    data class UiState(
        val items: List<Wallpaper> = emptyList(),
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val endReached: Boolean = false,
        val style: DiscoveryStyle? = null,
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
    private var emptyStreak = 0

    init {
        viewModelScope.launch {
            app.favorites.observeKeys().collect { keys ->
                _state.update { it.copy(favoriteKeys = keys) }
            }
        }
        reload()
    }

    fun reload(style: DiscoveryStyle? = _state.value.style) {
        loadJob?.cancel()
        moreJob?.cancel()
        val gen = generation.incrementAndGet()
        _state.update {
            it.copy(items = emptyList(), loading = true, loadingMore = false, error = null, notice = null, endReached = false, style = style)
        }
        loadJob = viewModelScope.launch {
            val prefs = app.settings.current()
            val now = System.currentTimeMillis()
            val planned = DiscoverySessionPlanner.newRandomSession(
                nowMillis = now,
                rotateHourly = prefs.rotateDiscoveryHourly,
                refreshOrdinal = session?.refreshOrdinal?.plus(1) ?: 0,
                recentRecipeIds = app.settings.recentlyUsedDiscoveryRecipeIds(now, prefs.repeatAfterHours),
                preferredRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(setOfNotNull(style?.id)),
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
                    _state.update { it.copy(items = progress.items, loading = progress.loading) }
                }
            }
            if (gen != generation.get()) return@launch
            emptyStreak = 0
            val items = result.items
            if (items.isNotEmpty()) {
                app.settings.recordDiscoverySeen(items.map { it.key }, System.currentTimeMillis())
            }
            _state.update {
                it.copy(
                    items = items,
                    loading = false,
                    endReached = items.isEmpty(),
                    error = if (items.isEmpty()) result.message() ?: GENERIC_ERROR else null,
                    notice = if (items.isNotEmpty() && result.staleCacheUsed) result.message() else null,
                )
            }
        }
    }

    /** Near-end trigger from the list: fetches the next discovery batch and appends fresh items. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached || current.items.isEmpty()) return
        if (moreJob?.isActive == true) return
        val base = session ?: return
        val gen = generation.get()
        _state.update { it.copy(loadingMore = true, notice = null) }
        moreJob = viewModelScope.launch {
            val prefs = app.settings.current()
            val now = System.currentTimeMillis()
            val excludeKeys = _state.value.items.mapTo(hashSetOf()) { it.key }
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
                preferredRecipeIds = DiscoveryStylePreferenceMapping.recipeIds(setOfNotNull(_state.value.style?.id)),
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
                app.settings.recordDiscoverySeen(fresh.map { it.key }, System.currentTimeMillis())
                _state.update {
                    it.copy(
                        items = it.items + fresh,
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

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { app.favorites.toggle(wallpaper) }
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

    /** Fetches the original and applies it via WallpaperManager; honours both rights gates. */
    fun setWallpaper(wallpaper: Wallpaper, target: ApplyTarget) {
        viewModelScope.launch {
            when {
                !wallpaper.downloadAllowed ->
                    _messages.emit(app.appContext.getString(R.string.download_blocked_rights))

                !wallpaper.setWallpaperAllowed ->
                    _messages.emit(app.appContext.getString(R.string.apply_blocked_licence))

                else -> runCatching {
                    val original = app.wallpaperApplyRepository.prepare(wallpaper)
                    app.wallpaperApplier.apply(original.uri, target)
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
