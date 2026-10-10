package com.auroro.wallpapers.core.data

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.DiscoveryStyle
import java.security.MessageDigest
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
enum class AppearancePreset(val label: String, val description: String) {
    FRUTIGER_AERO("Frutiger Aero", "Sky, water, fresh greens and translucent surfaces"),
    FRUTIGER_FLOWER("Frutiger Flower", "Petal pink, lilac and soft botanical greens"),
    FRUTIGER_ZEN("Frutiger Zen", "Quiet sea-glass, sage and warm stone"),
    DARK_AERO("Dark Aero", "Deep ocean, luminous aqua and reflective edges"),
    AQUA_DAY("Aqua Day", "Bright sky blue, clean white and crisp contrast"),
    SYSTEM("Follow system", "Use the device's light or dark appearance"),
}
enum class FontChoice(val label: String, val description: String) {
    OPEN_SANS("Open Sans", "Bundled humanist sans · works offline"),
    SYSTEM("System", "Use the Android default typeface"),
}
enum class GlassQuality(val label: String, val description: String) {
    FULL("Full glass", "Haze refraction, highlights and depth"),
    BALANCED("Balanced", "Source-aware glass at Haze's balanced quality"),
    REDUCED("Reduced effects", "Opaque material with no live glass rendering"),
}

data class ManualRefreshResult(val allowed: Boolean, val remainingMillis: Long, val ordinal: Int)

enum class SaveLocation(val label: String, val description: String) {
    GALLERY("Gallery", "Pictures/Auroro Wallpapers, visible in your gallery and kept if you uninstall."),
    APP_STORAGE("App storage only", "Private to Auroro. Not shown in the gallery; removed on uninstall."),
}

data class AppSettings(
    /** Legacy theme fields remain readable so existing DataStore preferences are migrated safely. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentTheme = AccentTheme.AQUA,
    val appearance: AppearancePreset = AppearancePreset.FRUTIGER_AERO,
    val fontChoice: FontChoice = FontChoice.OPEN_SANS,
    val fontScale: Float = 1f,
    val glassQuality: GlassQuality = GlassQuality.BALANCED,
    val increaseContrast: Boolean = false,
    val amoled: Boolean = false,
    val reduceTransparency: Boolean = false,
    val reducePotentiallyExplicitContent: Boolean = true,
    val rotateDiscoveryHourly: Boolean = true,
    val avoidRecentlySeen: Boolean = true,
    val repeatAfterHours: Int = 24,
    val preferredDiscoveryStyleIds: Set<String> = emptySet(),
    val hiddenDiscoveryStyleIds: Set<String> = emptySet(),
    val pinnedDiscoveryStyleId: String? = null,
    val discoveryRefreshOrdinal: Int = 0,
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

    /** Locally cached identifiers that appeared in discovery, bounded by the user's repeat window. */
    suspend fun recentlySeenDiscoveryKeys(now: Long, repeatAfterHours: Int): Set<String> {
        val cutoff = now - repeatAfterHours.coerceIn(1, 720) * 60L * 60L * 1000L
        return store.data.first()[RECENT_DISCOVERY_KEYS].orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.lastIndexOf('|')
                if (separator <= 0) return@mapNotNull null
                val key = line.substring(0, separator)
                val at = line.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                key.takeIf { at >= cutoff && at <= now }
            }
            .toSet()
    }

    suspend fun recordDiscoverySeen(keys: List<String>, now: Long) {
        if (keys.isEmpty()) return
        store.edit { prefs ->
            val entries = linkedMapOf<String, Long>()
            prefs[RECENT_DISCOVERY_KEYS].orEmpty().lineSequence().forEach { line ->
                val split = line.lastIndexOf('|')
                if (split > 0) line.substring(split + 1).toLongOrNull()?.let { entries[line.substring(0, split)] = it }
            }
            keys.forEach { key -> entries[key] = now }
            prefs[RECENT_DISCOVERY_KEYS] = entries.entries
                .sortedByDescending { it.value }
                .take(MAX_RECENT_DISCOVERY_KEYS)
                .joinToString("\n") { (key, at) -> "$key|$at" }
        }
    }

    /** Small identifier-only snapshots support fast return and offline recovery; image bytes stay in Coil's cache. */
    suspend fun saveDiscoverySnapshot(cacheKey: String, wallpaperKeys: List<String>) {
        if (wallpaperKeys.isEmpty()) return
        store.edit { prefs ->
            val prior = prefs[DISCOVERY_SNAPSHOT_INDEX].orEmpty().lineSequence().filter(String::isNotBlank).toList()
            val next = (listOf(cacheKey) + prior.filterNot { it == cacheKey }).take(MAX_DISCOVERY_SNAPSHOTS)
            (prior - next.toSet()).forEach { old -> prefs.remove(snapshotKey(old)) }
            prefs[snapshotKey(cacheKey)] = wallpaperKeys.distinct().take(MAX_DISCOVERY_SNAPSHOT_ITEMS).joinToString("\n")
            prefs[DISCOVERY_SNAPSHOT_INDEX] = next.joinToString("\n")
        }
    }

    suspend fun discoverySnapshot(cacheKey: String): List<String> =
        store.data.first()[snapshotKey(cacheKey)].orEmpty().lineSequence().filter(String::isNotBlank).toList()

    suspend fun latestDiscoverySnapshot(): Pair<String, List<String>>? {
        val prefs = store.data.first()
        val key = prefs[DISCOVERY_SNAPSHOT_INDEX].orEmpty().lineSequence().firstOrNull(String::isNotBlank) ?: return null
        val values = prefs[snapshotKey(key)].orEmpty().lineSequence().filter(String::isNotBlank).toList()
        return (key to values).takeIf { values.isNotEmpty() }
    }

    suspend fun claimManualDiscoveryRefresh(now: Long, cooldownMillis: Long = MANUAL_REFRESH_COOLDOWN_MILLIS): ManualRefreshResult {
        var result = ManualRefreshResult(false, cooldownMillis, 0)
        store.edit { prefs ->
            val last = prefs[LAST_MANUAL_DISCOVERY_REFRESH] ?: Long.MIN_VALUE
            val elapsed = if (last == Long.MIN_VALUE) Long.MAX_VALUE else (now - last).coerceAtLeast(0)
            val ordinal = prefs[DISCOVERY_REFRESH_ORDINAL] ?: 0
            val remaining = (cooldownMillis - elapsed).coerceAtLeast(0)
            if (remaining == 0L) {
                result = ManualRefreshResult(true, 0, (ordinal + 1).coerceAtMost(Int.MAX_VALUE - 1))
                prefs[LAST_MANUAL_DISCOVERY_REFRESH] = now
                prefs[DISCOVERY_REFRESH_ORDINAL] = result.ordinal
            } else {
                result = ManualRefreshResult(false, remaining, ordinal)
            }
        }
        return result
    }

    suspend fun resetDiscovery() {
        store.edit { prefs ->
            prefs.remove(RECENT_DISCOVERY_KEYS)
            prefs.remove(DISCOVERY_SNAPSHOT_INDEX)
            prefs.remove(LAST_MANUAL_DISCOVERY_REFRESH)
            prefs.remove(DISCOVERY_REFRESH_ORDINAL)
            prefs.asMap().keys.filter { it.name.startsWith(DISCOVERY_SNAPSHOT_PREFIX) }.forEach { key -> prefs.remove(key) }
        }
        update { it.copy(
            rotateDiscoveryHourly = true,
            avoidRecentlySeen = true,
            repeatAfterHours = 24,
            preferredDiscoveryStyleIds = emptySet(),
            hiddenDiscoveryStyleIds = emptySet(),
            pinnedDiscoveryStyleId = null,
            discoveryRefreshOrdinal = 0,
        ) }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        var committed = AppSettings()
        store.edit { prefs ->
            val current = prefs.toSettings()
            val transformed = transform(current)
            val next = if (transformed.appearance == current.appearance && transformed.themeMode != current.themeMode) {
                transformed.copy(
                    appearance = when (transformed.themeMode) {
                        ThemeMode.SYSTEM -> AppearancePreset.SYSTEM
                        ThemeMode.DARK -> AppearancePreset.DARK_AERO
                        ThemeMode.LIGHT -> AppearancePreset.AQUA_DAY
                    },
                )
            } else transformed
            committed = next
            prefs[THEME] = next.themeMode.name
            prefs[ACCENT] = next.accent.name
            prefs[APPEARANCE] = next.appearance.name
            prefs[FONT_CHOICE] = next.fontChoice.name
            prefs[FONT_SCALE] = next.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
            prefs[GLASS_QUALITY] = next.glassQuality.name
            prefs[INCREASE_CONTRAST] = next.increaseContrast
            prefs[AMOLED] = next.amoled
            prefs[REDUCE_TRANSPARENCY] = next.reduceTransparency
            prefs[REDUCE_EXPLICIT] = next.reducePotentiallyExplicitContent
            prefs[ROTATE_DISCOVERY_HOURLY] = next.rotateDiscoveryHourly
            prefs[AVOID_RECENT_DISCOVERY] = next.avoidRecentlySeen
            prefs[DISCOVERY_REPEAT_HOURS] = next.repeatAfterHours.coerceIn(1, 720)
            prefs[PREFERRED_DISCOVERY_STYLES] = next.preferredDiscoveryStyleIds.intersect(DiscoveryStyle.allIds)
            prefs[HIDDEN_DISCOVERY_STYLES] = next.hiddenDiscoveryStyleIds.intersect(DiscoveryStyle.allIds)
            next.pinnedDiscoveryStyleId?.takeIf { it in DiscoveryStyle.allIds }?.let { prefs[PINNED_DISCOVERY_STYLE] = it }
                ?: prefs.remove(PINNED_DISCOVERY_STYLE)
            prefs[DISCOVERY_REFRESH_ORDINAL] = next.discoveryRefreshOrdinal.coerceAtLeast(0)
            prefs[ASPECT_TOL] = next.aspectTolerance.coerceIn(0.005f, 0.10f)
            prefs[CACHE_MB] = next.cacheLimitMb.coerceIn(50, 2_000)
            prefs[LOCATION] = next.saveLocation.name
            // Setting a wallpaper is deliberately no longer coupled to the persistent Download action.
            prefs[SET_AFTER] = false
            prefs[NOTIFY_PROGRESS] = next.notifyProgress
            prefs[NOTIFY_RESULT] = next.notifyResult
            prefs[WALLHAVEN_ON] = next.wallhavenEnabled
            prefs[OPENVERSE_ON] = next.openverseEnabled
            prefs.remove(LEGACY_RETIRED_PROVIDER_ENABLED)
            prefs.remove(LEGACY_DOWNLOAD_QUALITY)
        }
        bootPrefs?.edit()?.putInt(BOOT_CACHE_MB, committed.cacheLimitMb.coerceIn(50, 2_000))?.apply()
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val legacyTheme = enumOr(this[THEME], defaults.themeMode)
        val appearance = this[APPEARANCE]?.let { enumOr(it, defaults.appearance) } ?: when (legacyTheme) {
            ThemeMode.DARK -> AppearancePreset.DARK_AERO
            ThemeMode.LIGHT -> AppearancePreset.AQUA_DAY
            ThemeMode.SYSTEM -> AppearancePreset.FRUTIGER_AERO
        }
        return AppSettings(
            themeMode = legacyTheme,
            accent = enumOr(this[ACCENT], defaults.accent),
            appearance = appearance,
            fontChoice = enumOr(this[FONT_CHOICE], defaults.fontChoice),
            fontScale = (this[FONT_SCALE] ?: defaults.fontScale).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
            glassQuality = enumOr(this[GLASS_QUALITY], defaults.glassQuality),
            increaseContrast = this[INCREASE_CONTRAST] ?: defaults.increaseContrast,
            amoled = this[AMOLED] ?: defaults.amoled,
            reduceTransparency = this[REDUCE_TRANSPARENCY] ?: defaults.reduceTransparency,
            reducePotentiallyExplicitContent = this[REDUCE_EXPLICIT] ?: defaults.reducePotentiallyExplicitContent,
            rotateDiscoveryHourly = this[ROTATE_DISCOVERY_HOURLY] ?: defaults.rotateDiscoveryHourly,
            avoidRecentlySeen = this[AVOID_RECENT_DISCOVERY] ?: defaults.avoidRecentlySeen,
            repeatAfterHours = (this[DISCOVERY_REPEAT_HOURS] ?: defaults.repeatAfterHours).coerceIn(1, 720),
            preferredDiscoveryStyleIds = this[PREFERRED_DISCOVERY_STYLES].orEmpty().intersect(DiscoveryStyle.allIds),
            hiddenDiscoveryStyleIds = this[HIDDEN_DISCOVERY_STYLES].orEmpty().intersect(DiscoveryStyle.allIds),
            pinnedDiscoveryStyleId = this[PINNED_DISCOVERY_STYLE]?.takeIf { it in DiscoveryStyle.allIds },
            discoveryRefreshOrdinal = (this[DISCOVERY_REFRESH_ORDINAL] ?: 0).coerceAtLeast(0),
            aspectTolerance = (this[ASPECT_TOL] ?: defaults.aspectTolerance).coerceIn(0.005f, 0.10f),
            cacheLimitMb = (this[CACHE_MB] ?: defaults.cacheLimitMb).coerceIn(50, 2_000),
            saveLocation = enumOr(this[LOCATION], defaults.saveLocation),
            // Kept as a false legacy value: wallpaper setting now has its own temporary-cache path.
            setAfterDownload = false,
            notifyProgress = this[NOTIFY_PROGRESS] ?: defaults.notifyProgress,
            notifyResult = this[NOTIFY_RESULT] ?: defaults.notifyResult,
            wallhavenEnabled = this[WALLHAVEN_ON] ?: defaults.wallhavenEnabled,
            openverseEnabled = this[OPENVERSE_ON] ?: defaults.openverseEnabled,
        )
    }

    private fun snapshotKey(cacheKey: String) = stringPreferencesKey(DISCOVERY_SNAPSHOT_PREFIX + sha256(cacheKey))

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        const val BOOT_CACHE_MB = "cache_limit_mb"
        const val MANUAL_REFRESH_COOLDOWN_MILLIS = 20L * 60L * 1000L
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.30f
        private const val MAX_RECENT_SEARCHES = 8
        private const val MAX_SEARCH_LENGTH = 200
        private const val MAX_RECENT_DISCOVERY_KEYS = 600
        private const val MAX_DISCOVERY_SNAPSHOTS = 8
        private const val MAX_DISCOVERY_SNAPSHOT_ITEMS = 60
        private const val DISCOVERY_SNAPSHOT_PREFIX = "discovery_snapshot_"
        private val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val RECENT_DISCOVERY_KEYS = stringPreferencesKey("recent_discovery_keys")
        private val DISCOVERY_SNAPSHOT_INDEX = stringPreferencesKey("discovery_snapshot_index")
        private val LAST_MANUAL_DISCOVERY_REFRESH = longPreferencesKey("last_manual_discovery_refresh")
        private val THEME = stringPreferencesKey("theme_mode")
        private val ACCENT = stringPreferencesKey("accent")
        private val APPEARANCE = stringPreferencesKey("appearance_preset")
        private val FONT_CHOICE = stringPreferencesKey("font_choice")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val GLASS_QUALITY = stringPreferencesKey("glass_quality")
        private val INCREASE_CONTRAST = booleanPreferencesKey("increase_contrast")
        private val REDUCE_EXPLICIT = booleanPreferencesKey("reduce_explicit_content")
        private val ROTATE_DISCOVERY_HOURLY = booleanPreferencesKey("rotate_discovery_hourly")
        private val AVOID_RECENT_DISCOVERY = booleanPreferencesKey("avoid_recent_discovery")
        private val DISCOVERY_REPEAT_HOURS = intPreferencesKey("discovery_repeat_hours")
        private val PREFERRED_DISCOVERY_STYLES = stringSetPreferencesKey("preferred_discovery_styles")
        private val HIDDEN_DISCOVERY_STYLES = stringSetPreferencesKey("hidden_discovery_styles")
        private val PINNED_DISCOVERY_STYLE = stringPreferencesKey("pinned_discovery_style")
        private val DISCOVERY_REFRESH_ORDINAL = intPreferencesKey("discovery_refresh_ordinal")
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
