package com.auroro.wallpapers.core.data

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.auroro.wallpapers.core.model.AspectMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark ocean"), LIGHT("Light sky") }
enum class AccentTheme(val label: String) { AQUA("Aqua"), EMERALD("Emerald"), SKY("Sky blue") }

enum class SaveLocation(val label: String, val description: String) {
    GALLERY("Gallery", "Pictures/Auroro Wallpapers, visible in your gallery and kept if you uninstall."),
    APP_STORAGE("App storage only", "Private to Auroro. Not shown in the gallery; removed on uninstall."),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentTheme = AccentTheme.AQUA,
    val amoled: Boolean = false,
    val reduceTransparency: Boolean = false,
    val aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE,
    val cacheLimitMb: Int = DEFAULT_CACHE_MB,
    val saveLocation: SaveLocation = SaveLocation.GALLERY,
    val setAfterDownload: Boolean = false,
    val notifyProgress: Boolean = true,
    val notifyResult: Boolean = true,
    val wallhavenEnabled: Boolean = true,
    val openverseEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_CACHE_MB = 250
        val CACHE_CHOICES_MB = listOf(100, 250, 500, 1000)
    }
}

/** Small local preferences only. No provider keys or download-quality transformations are stored. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
    /** Tiny synchronous mirror used only to size the disposable image cache at process start. */
    private val bootPrefs: SharedPreferences? = null,
) {
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        migrationScope.launch {
            store.edit { prefs ->
                prefs.remove(LEGACY_RETIRED_PROVIDER_ENABLED)
                prefs.remove(LEGACY_DOWNLOAD_QUALITY)
            }
        }
    }

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    /** Recent search terms stay on-device, are capped, and are never sent to an Auroro service. */
    val recentSearches: Flow<List<String>> = store.data.map { prefs ->
        prefs[RECENT_SEARCHES].orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(MAX_RECENT_SEARCHES)
            .toList()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun rememberSearchQuery(query: String) {
        val normalized = query.trim().replace(Regex("\\s+"), " ").take(MAX_SEARCH_LENGTH)
        if (normalized.isBlank()) return
        store.edit { prefs ->
            val previous = prefs[RECENT_SEARCHES].orEmpty()
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filterNot { it.equals(normalized, ignoreCase = true) }
            prefs[RECENT_SEARCHES] = (sequenceOf(normalized) + previous).take(MAX_RECENT_SEARCHES).joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() {
        store.edit { it.remove(RECENT_SEARCHES) }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        store.edit { prefs ->
            prefs[THEME] = next.themeMode.name
            prefs[ACCENT] = next.accent.name
            prefs[AMOLED] = next.amoled
            prefs[REDUCE_TRANSPARENCY] = next.reduceTransparency
            prefs[ASPECT_TOL] = next.aspectTolerance.coerceIn(0.005f, 0.10f)
            prefs[CACHE_MB] = next.cacheLimitMb.coerceIn(50, 2_000)
            prefs[LOCATION] = next.saveLocation.name
            prefs[SET_AFTER] = next.setAfterDownload
            prefs[NOTIFY_PROGRESS] = next.notifyProgress
            prefs[NOTIFY_RESULT] = next.notifyResult
            prefs[WALLHAVEN_ON] = next.wallhavenEnabled
            prefs[OPENVERSE_ON] = next.openverseEnabled
            prefs.remove(LEGACY_RETIRED_PROVIDER_ENABLED)
            prefs.remove(LEGACY_DOWNLOAD_QUALITY)
        }
        bootPrefs?.edit()?.putInt(BOOT_CACHE_MB, next.cacheLimitMb.coerceIn(50, 2_000))?.apply()
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[THEME], defaults.themeMode),
            accent = enumOr(this[ACCENT], defaults.accent),
            amoled = this[AMOLED] ?: defaults.amoled,
            reduceTransparency = this[REDUCE_TRANSPARENCY] ?: defaults.reduceTransparency,
            aspectTolerance = (this[ASPECT_TOL] ?: defaults.aspectTolerance).coerceIn(0.005f, 0.10f),
            cacheLimitMb = (this[CACHE_MB] ?: defaults.cacheLimitMb).coerceIn(50, 2_000),
            saveLocation = enumOr(this[LOCATION], defaults.saveLocation),
            setAfterDownload = this[SET_AFTER] ?: defaults.setAfterDownload,
            notifyProgress = this[NOTIFY_PROGRESS] ?: defaults.notifyProgress,
            notifyResult = this[NOTIFY_RESULT] ?: defaults.notifyResult,
            wallhavenEnabled = this[WALLHAVEN_ON] ?: defaults.wallhavenEnabled,
            openverseEnabled = this[OPENVERSE_ON] ?: defaults.openverseEnabled,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        const val BOOT_CACHE_MB = "cache_limit_mb"
        private const val MAX_RECENT_SEARCHES = 8
        private const val MAX_SEARCH_LENGTH = 200
        private val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val THEME = stringPreferencesKey("theme_mode")
        private val ACCENT = stringPreferencesKey("accent")
        private val AMOLED = booleanPreferencesKey("amoled")
        private val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        private val ASPECT_TOL = floatPreferencesKey("aspect_tolerance")
        private val CACHE_MB = intPreferencesKey("cache_limit_mb")
        private val LOCATION = stringPreferencesKey("save_location")
        private val SET_AFTER = booleanPreferencesKey("set_after_download")
        private val NOTIFY_PROGRESS = booleanPreferencesKey("notify_progress")
        private val NOTIFY_RESULT = booleanPreferencesKey("notify_result")
        private val WALLHAVEN_ON = booleanPreferencesKey("wallhaven_enabled")
        private val OPENVERSE_ON = booleanPreferencesKey("openverse_enabled")
        private val LEGACY_RETIRED_PROVIDER_ENABLED = booleanPreferencesKey("abyss_enabled")
        private val LEGACY_DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    }
}
