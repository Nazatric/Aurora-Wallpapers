package com.auroro.wallpapers.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.app.AppContainer
import com.auroro.wallpapers.core.data.download.StorageInfo
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.model.Wallpaper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One saved/active download joined with its catalogue record for titles and thumbnails. */
data class DownloadRow(val entity: DownloadEntity, val wallpaper: Wallpaper?)

/**
 * Slice-4 library: favorites straight from the Room favourites table and downloads from the
 * download queue, each joined to catalogue metadata. No invented entries — empty states say
 * exactly what's missing.
 */
class LibraryViewModel(private val app: AppContainer) : ViewModel() {

    data class UiState(
        val favorites: List<Wallpaper> = emptyList(),
        val favoriteKeys: Set<String> = emptySet(),
        val downloads: List<DownloadRow> = emptyList(),
        val recent: List<Wallpaper> = emptyList(),
        val storage: StorageInfo? = null,
        val collections: List<CollectionCover> = emptyList(),
        val openCollectionId: Long? = null,
        val openCollectionName: String = "",
        val openCollectionItems: List<Wallpaper> = emptyList(),
        /** Wallpaper the add-to-collection sheet is targeting, plus its current memberships. */
        val collectTarget: Wallpaper? = null,
        val collectMemberships: Set<Long> = emptySet(),
    )

    /** A saved collection with its real cover artworks resolved from the store. */
    data class CollectionCover(
        val id: Long,
        val name: String,
        val count: Int,
        val covers: List<Wallpaper>,
    )

    private var collectionJob: kotlinx.coroutines.Job? = null
    private var membershipJob: kotlinx.coroutines.Job? = null
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.favorites.observeFavorites().collect { list ->
                _state.update { it.copy(favorites = list, favoriteKeys = list.mapTo(hashSetOf()) { it.key }) }
            }
        }
        viewModelScope.launch {
            app.downloads.observeAll().collect { entities ->
                val keys = entities.map { it.wallpaperKey }
                val byKey = if (keys.isEmpty()) emptyMap() else app.wallpaperStore.getAll(keys).associateBy { it.key }
                _state.update {
                    it.copy(downloads = entities.map { entity -> DownloadRow(entity, byKey[entity.wallpaperKey]) })
                }
            }
        }
        viewModelScope.launch {
            app.history.observeRecent().collect { list ->
                _state.update { it.copy(recent = list) }
            }
        }
        viewModelScope.launch {
            app.collections.observeSummaries().collect { summaries ->
                val keys = summaries
                    .flatMap { listOfNotNull(it.cover1, it.cover2, it.cover3, it.cover4) }
                    .distinct()
                val walls = if (keys.isEmpty()) {
                    emptyMap()
                } else {
                    app.wallpaperStore.getAll(keys).associateBy { it.key }
                }
                _state.update {
                    it.copy(
                        collections = summaries.map { summary ->
                            CollectionCover(
                                id = summary.id,
                                name = summary.name,
                                count = summary.itemCount,
                                covers = listOfNotNull(summary.cover1, summary.cover2, summary.cover3, summary.cover4)
                                    .mapNotNull { key -> walls[key] },
                            )
                        },
                    )
                }
            }
        }
        viewModelScope.launch { refreshStorage() }
    }


    fun openCollection(id: Long?) {
        collectionJob?.cancel()
        if (id == null) {
            _state.update { it.copy(openCollectionId = null, openCollectionItems = emptyList(), openCollectionName = "") }
            return
        }
        _state.update { it.copy(openCollectionId = id) }
        collectionJob = viewModelScope.launch {
            combine(
                app.collections.observeCollection(id),
                app.collections.observeItems(id),
            ) { entity, items -> Pair(entity?.name.orEmpty(), items) }.collect { (name, items) ->
                _state.update { it.copy(openCollectionName = name, openCollectionItems = items) }
            }
        }
    }

    fun renameOpenCollection(name: String, onResult: (Boolean) -> Unit) {
        val id = _state.value.openCollectionId ?: return
        viewModelScope.launch { onResult(app.collections.rename(id, name).isSuccess) }
    }

    fun deleteOpenCollection(onDone: () -> Unit) {
        val id = _state.value.openCollectionId ?: return
        viewModelScope.launch {
            app.collections.delete(id)
            openCollection(null)
            onDone()
        }
    }

    fun setCollectTarget(wallpaper: Wallpaper?) {
        membershipJob?.cancel()
        _state.update { it.copy(collectTarget = wallpaper, collectMemberships = emptySet()) }
        if (wallpaper == null) return
        membershipJob = viewModelScope.launch {
            app.collections.observeMembership(wallpaper.key).collect { ids ->
                _state.update { it.copy(collectMemberships = ids) }
            }
        }
    }

    fun toggleCollectionMember(collectionId: Long) {
        val target = _state.value.collectTarget ?: return
        viewModelScope.launch {
            if (collectionId in _state.value.collectMemberships) {
                app.collections.remove(collectionId, target.key)
            } else {
                app.collections.add(collectionId, target)
            }
        }
    }

    fun createCollectionForTarget(name: String, onResult: (Boolean) -> Unit) {
        val target = _state.value.collectTarget ?: return
        viewModelScope.launch {
            app.collections.create(name).onSuccess { id ->
                app.collections.add(id, target)
                onResult(true)
            }.onFailure { onResult(false) }
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _state.update { it.copy(storage = app.downloads.storageInfo()) }
        }
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { app.favorites.toggle(wallpaper) }
    }

    fun cancel(key: String) {
        viewModelScope.launch {
            app.downloads.cancel(key)
            refreshStorage()
        }
    }

    fun retry(key: String) {
        viewModelScope.launch { app.downloads.retry(key) }
    }

    fun delete(key: String) {
        viewModelScope.launch {
            app.downloads.delete(key)
            refreshStorage()
        }
    }

    companion object {
        fun factory(app: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LibraryViewModel(app) as T
            }
        }
    }
}
