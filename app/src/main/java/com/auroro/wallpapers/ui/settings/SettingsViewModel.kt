package com.auroro.wallpapers.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auroro.wallpapers.app.AppContainer
import com.auroro.wallpapers.core.data.AppSettings
import com.auroro.wallpapers.core.data.download.StorageInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Slice-4 settings: every row maps to a real [AppSettings] field that the data layer already
 * consumes (discovery planner, providers, download executor, notifier, cache sizing). Rows
 * whose rendering lands in later slices (theming, glass) are shown disabled with an honest
 * note instead of pretending to work.
 */
class SettingsViewModel(private val app: AppContainer) : ViewModel() {

    data class UiState(
        val settings: AppSettings? = null,
        val storage: StorageInfo? = null,
        val versionName: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            app.settings.settings.collect { prefs -> _state.update { it.copy(settings = prefs) } }
        }
        viewModelScope.launch { refreshStorage() }
        _state.update {
            it.copy(
                versionName = runCatching {
                    app.appContext.packageManager
                        .getPackageInfo(app.appContext.packageName, 0)
                        .versionName
                }.getOrNull(),
            )
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { app.settings.update(transform) }
    }

    fun refreshStorage() {
        viewModelScope.launch { _state.update { it.copy(storage = app.downloads.storageInfo()) } }
    }

    /** Clears the full-quality apply cache and the discovery snapshot cache. */
    fun clearCaches() {
        viewModelScope.launch {
            app.wallpaperApplyRepository.clearCache()
            app.wallpaperStore.clearDiscoveryCache()
            refreshStorage()
            _messages.emit(app.appContext.getString(com.auroro.wallpapers.R.string.set_cache_cleared))
        }
    }

    companion object {
        fun factory(app: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(app) as T
            }
        }
    }
}
