package com.auroro.wallpapers.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.app.AppContainer
import com.auroro.wallpapers.core.data.download.StorageInfo
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.model.Wallpaper
import kotlinx.coroutines.flow.MutableStateFlow
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
        val storage: StorageInfo? = null,
    )

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
        viewModelScope.launch { refreshStorage() }
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
