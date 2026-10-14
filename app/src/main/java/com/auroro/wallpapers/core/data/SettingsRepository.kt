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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }
enum class AccentTheme(val label: String) { AQUA("Aurora violet"), EMERALD("Emerald"), SKY("Sky blue") }
enum class DesignTheme(val label: String, val description: String) {
    LIQUID_GLASS("Liquid Glass", "Frosted depth, blur and light — renderer ships in its own run"),
    MATERIAL_3("Material 3", "Clear hierarchy, expressive colour and familiar surfaces"),
}

/** Home feed arrangement: stacked cards, two-column grid or a compact list. */
enum class FeedLayout(val label: String) {
    CARDS("Cards"),
    GRID("Grid"),
}
enum class FontChoice(val label: String, val description: String) {
    OPEN_SANS("Open Sans", "Bundled humanist sans · works offline"),
    SYSTEM("System", "Use the Android default typeface"),
}
enum class GlassQuality(val label: String, val description: String) {
    FULL("Full glass", "Liquid glass with lens refraction, highlights and depth"),
    BALANCED("Balanced", "Frosted live glass without the refraction shader"),
    REDUCED("Reduced effects", "Opaque material with no live glass rendering"),
}

data class ManualRefreshResult(val allowed: Boolean, val remainingMillis: Long, val ordinal: Int)

/** Bounded source/recipe metadata; image bytes and user saves are stored separately. */
data class DiscoveryAvailabilityRecord(
    val sourceId: String,
    val recipeId: String,
    val lastSuccessfulAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val retryAfterMillis: Long = 0L,
    val consecutiveFailures: Int = 0,
    val lastFailureKind: String? = null,
    val wallpaperKeys: List<String> = emptyList(),
)

enum class SaveLocation(val label: String, val description: String) {
    GALLERY("Gallery", "Pictures/Auroro Wallpapers, visible in your gallery and kept if you uninstall."),
    APP_STORAGE("App storage only", "Private to Auroro. Not shown in the gallery; removed on uninstall."),
}

data class AppSettings(
    /** Basic colour mode; system changes remain live while SYSTEM is selected. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Independent design language; it never overrides the basic colour mode. */
    val designTheme: DesignTheme = DesignTheme.LIQUID_GLASS,
    val accent: AccentTheme = AccentTheme.AQUA,
    val feedLayout: FeedLayout = FeedLayout.CARDS,
    val fontChoice: FontChoice = FontChoice.OPEN_SANS,
    val fontScale: Float = 1f,
    val glassQuality: GlassQuality = GlassQuality.BALANCED,
    val increaseContrast: Boolean = false,
    val amoled: Boolean = false,
    val reduceTransparency: Boolean = false,
    val rotateDiscoveryHourly: Boolean = true,
    val avoidRecentlySeen: Boolean = true,
    val repeatAfterHours: Int = 24,
    /** User-selected topic preferences bias For You's curated query mix; they do not filter provider responses globally. */
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
    val nasaApodEnabled: Boolean = true,
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

    private val preferenceMigration = migrationScope.async {
        store.edit { prefs ->
            prefs.remove(LEGACY_RETIRED_PROVIDER_ENABLED)
            prefs.remove(LEGACY_DOWNLOAD_QUALITY)
            prefs.remove(LEGACY_REDUCE_EXPLICIT)
            prefs.remove(LEGACY_OPENVERSE_ENABLED)

            // Split the old combined appearance preset into independent mode and design theme.
            val migratedSettings = prefs.toSettings()
            if (!prefs.contains(THEME)) prefs[THEME] = migratedSettings.themeMode.name
            if (!prefs.contains(DESIGN_THEME)) prefs[DESIGN_THEME] = migratedSettings.designTheme.name
            prefs.remove(LEGACY_APPEARANCE)

            // Discovery topic IDs are normalized when settings are read or next written,
            // leaving the existing preference values intact until that ordinary update.
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

    suspend fun current(): AppSettings {
        preferenceMigration.await()
        return settings.first()
    }

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

    suspend fun recentlyUsedDiscoveryRecipeIds(now: Long, repeatAfterHours: Int): Set<String> {
        val cutoff = now - repeatAfterHours.coerceIn(1, 720) * 60L * 60L * 1000L
        return store.data.first()[RECENT_DISCOVERY_RECIPES].orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.lastIndexOf('|')
                if (separator <= 0) return@mapNotNull null
                val recipeId = line.substring(0, separator)
                val at = line.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                recipeId.takeIf { at >= cutoff && at <= now }
            }
            .toSet()
    }

    suspend fun recordDiscoveryRecipeIds(recipeIds: List<String>, now: Long) {
        if (recipeIds.isEmpty()) return
        store.edit { prefs ->
            val entries = linkedMapOf<String, Long>()
            prefs[RECENT_DISCOVERY_RECIPES].orEmpty().lineSequence().forEach { line ->
                val separator = line.lastIndexOf('|')
                if (separator > 0) line.substring(separator + 1).toLongOrNull()?.let {
                    entries[line.substring(0, separator)] = it
                }
            }
            recipeIds.forEach { id -> if (id.matches(RECIPE_ID_PATTERN)) entries[id] = now }
            prefs[RECENT_DISCOVERY_RECIPES] = entries.entries
                .sortedByDescending { it.value }
                .take(MAX_RECENT_DISCOVERY_RECIPES)
                .joinToString("\n") { (id, at) -> "$id|$at" }
        }
    }

    suspend fun discoveryAvailability(sourceId: String, recipeId: String): DiscoveryAvailabilityRecord? {
        val raw = store.data.first()[availabilityKey(sourceId, recipeId)] ?: return null
        val lines = raw.lineSequence().toList()
        val header = lines.firstOrNull()?.split('|').orEmpty()
        if (header.size < 5) return null
        return DiscoveryAvailabilityRecord(
            sourceId = sourceId,
            recipeId = recipeId,
            lastSuccessfulAtMillis = header[0].toLongOrNull() ?: 0L,
            updatedAtMillis = header[1].toLongOrNull() ?: 0L,
            retryAfterMillis = header[2].toLongOrNull() ?: 0L,
            consecutiveFailures = header[3].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            lastFailureKind = header[4].takeIf { it.isNotBlank() && it != "-" },
            wallpaperKeys = lines.drop(1).filter(String::isNotBlank).distinct().take(MAX_DISCOVERY_POOL_ITEMS),
        )
    }

    suspend fun saveDiscoveryAvailability(record: DiscoveryAvailabilityRecord) {
        require(record.sourceId.matches(RECIPE_ID_PATTERN) && record.recipeId.matches(RECIPE_ID_PATTERN))
        val safe = record.copy(
            consecutiveFailures = record.consecutiveFailures.coerceAtLeast(0),
            lastFailureKind = record.lastFailureKind.orEmpty().replace('|', ' ').replace('\r', ' ').replace('\n', ' ').take(80),
            wallpaperKeys = record.wallpaperKeys.distinct().take(MAX_DISCOVERY_POOL_ITEMS)
                .filterNot { it.contains('\n') || it.contains('|') },
        )
        store.edit { prefs ->
            val identity = "${safe.sourceId}:${safe.recipeId}"
            val prior = prefs[DISCOVERY_AVAILABILITY_INDEX].orEmpty().lineSequence().filter(String::isNotBlank).toList()
            val next = (listOf(identity) + prior.filterNot { it == identity }).take(MAX_DISCOVERY_POOLS)
            (prior - next.toSet()).forEach { old -> prefs.remove(availabilityKeyForIdentity(old)) }
            val header = listOf(
                safe.lastSuccessfulAtMillis,
                safe.updatedAtMillis,
                safe.retryAfterMillis,
                safe.consecutiveFailures,
                safe.lastFailureKind?.takeIf(String::isNotBlank) ?: "-",
            ).joinToString("|")
            prefs[availabilityKey(safe.sourceId, safe.recipeId)] =
                if (safe.wallpaperKeys.isEmpty()) header else "$header\n${safe.wallpaperKeys.joinToString("\n")}"
            prefs[DISCOVERY_AVAILABILITY_INDEX] = next.joinToString("\n")
        }
    }

    /** Small identifier-only snapshots support fast return and offline recovery; image bytes are never stored here. */
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
        preferenceMigration.await()
        store.edit { prefs ->
            prefs.remove(RECENT_DISCOVERY_KEYS)
            prefs.remove(RECENT_DISCOVERY_RECIPES)
            prefs.remove(DISCOVERY_SNAPSHOT_INDEX)
            prefs.remove(DISCOVERY_AVAILABILITY_INDEX)
            prefs.remove(LAST_MANUAL_DISCOVERY_REFRESH)
            prefs.remove(DISCOVERY_REFRESH_ORDINAL)
            prefs.asMap().keys.filter {
                it.name.startsWith(DISCOVERY_SNAPSHOT_PREFIX) || it.name.startsWith(DISCOVERY_AVAILABILITY_PREFIX)
            }.forEach { key -> prefs.remove(key) }
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
        preferenceMigration.await()
        var committed = AppSettings()
        store.edit { prefs ->
            val current = prefs.toSettings()
            val next = transform(current)
            committed = next
            prefs[THEME] = next.themeMode.name
            prefs[DESIGN_THEME] = next.designTheme.name
            prefs[FEED_LAYOUT] = next.feedLayout.name
            prefs.remove(LEGACY_APPEARANCE)
            prefs[ACCENT] = next.accent.name
            prefs[FONT_CHOICE] = next.fontChoice.name
            prefs[FONT_SCALE] = next.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
            prefs[GLASS_QUALITY] = next.glassQuality.name
            prefs[INCREASE_CONTRAST] = next.increaseContrast
            prefs[AMOLED] = next.amoled
            prefs[REDUCE_TRANSPARENCY] = next.reduceTransparency
            prefs[ROTATE_DISCOVERY_HOURLY] = next.rotateDiscoveryHourly
            prefs[AVOID_RECENT_DISCOVERY] = next.avoidRecentlySeen
            prefs[DISCOVERY_REPEAT_HOURS] = next.repeatAfterHours.coerceIn(1, 720)
            prefs[PREFERRED_DISCOVERY_STYLES] = next.preferredDiscoveryStyleIds
                .mapTo(linkedSetOf(), ::migrateLegacyDiscoveryStyleId).intersect(DiscoveryStyle.allIds)
            prefs[HIDDEN_DISCOVERY_STYLES] = next.hiddenDiscoveryStyleIds
                .mapTo(linkedSetOf(), ::migrateLegacyDiscoveryStyleId).intersect(DiscoveryStyle.allIds)
            next.pinnedDiscoveryStyleId?.let(::migrateLegacyDiscoveryStyleId)
                ?.takeIf { it in DiscoveryStyle.allIds }
                ?.let { prefs[PINNED_DISCOVERY_STYLE] = it }
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
            prefs[NASA_APOD_ON] = next.nasaApodEnabled
            // Retired with the Art Institute source (1.3.0); drop the stale toggle.
            prefs.remove(ART_INSTITUTE_ON)
            prefs.remove(LEGACY_RETIRED_PROVIDER_ENABLED)
            prefs.remove(LEGACY_DOWNLOAD_QUALITY)
        }
        bootPrefs?.edit()?.putInt(BOOT_CACHE_MB, committed.cacheLimitMb.coerceIn(50, 2_000))?.apply()
    }

    private fun migrateLegacyDiscoveryStyleId(id: String): String =
        if (id == LEGACY_BOTANICAL_STYLE_ID) DiscoveryStyle.BOTANICAL_ART.id else id

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val legacyAppearance = this[LEGACY_APPEARANCE]
        val themeMode = if (this[DESIGN_THEME] == null && legacyAppearance != null) {
            // Old appearance presets controlled mode and design together. Preserve the visible
            // light/dark choice while mapping the removed presets to one of the supported themes.
            when (legacyAppearance) {
                "DARK_AERO" -> ThemeMode.DARK
                "AQUA_DAY", "FRUTIGER_AERO", "FRUTIGER_FLOWER", "FRUTIGER_ZEN" -> ThemeMode.LIGHT
                "SYSTEM" -> ThemeMode.SYSTEM
                else -> enumOr(this[THEME], defaults.themeMode)
            }
        } else enumOr(this[THEME], defaults.themeMode)
        val designTheme = this[DESIGN_THEME]?.let { raw ->
            // Legacy names migrate forward: aero becomes Liquid Glass, zen folds into M3.
            when (raw) {
                "FRUTIGER_AERO", "DARK_AERO", "AQUA_DAY" -> DesignTheme.LIQUID_GLASS
                "FRUTIGER_FLOWER", "FRUTIGER_ZEN" -> DesignTheme.MATERIAL_3
                else -> enumOr(raw, defaults.designTheme)
            }
        } ?: when (legacyAppearance) {
            "FRUTIGER_AERO", "DARK_AERO", "AQUA_DAY", "SYSTEM" -> DesignTheme.LIQUID_GLASS
            "FRUTIGER_FLOWER", "FRUTIGER_ZEN" -> DesignTheme.MATERIAL_3
            else -> defaults.designTheme
        }
        return AppSettings(
            themeMode = themeMode,
            designTheme = designTheme,
            feedLayout = enumOr(this[FEED_LAYOUT], defaults.feedLayout),
            accent = enumOr(this[ACCENT], defaults.accent),
            fontChoice = enumOr(this[FONT_CHOICE], defaults.fontChoice),
            fontScale = (this[FONT_SCALE] ?: defaults.fontScale).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
            glassQuality = enumOr(this[GLASS_QUALITY], defaults.glassQuality),
            increaseContrast = this[INCREASE_CONTRAST] ?: defaults.increaseContrast,
            amoled = this[AMOLED] ?: defaults.amoled,
            reduceTransparency = this[REDUCE_TRANSPARENCY] ?: defaults.reduceTransparency,
            rotateDiscoveryHourly = this[ROTATE_DISCOVERY_HOURLY] ?: defaults.rotateDiscoveryHourly,
            avoidRecentlySeen = this[AVOID_RECENT_DISCOVERY] ?: defaults.avoidRecentlySeen,
            repeatAfterHours = (this[DISCOVERY_REPEAT_HOURS] ?: defaults.repeatAfterHours).coerceIn(1, 720),
            preferredDiscoveryStyleIds = this[PREFERRED_DISCOVERY_STYLES].orEmpty()
                .mapTo(linkedSetOf(), ::migrateLegacyDiscoveryStyleId).intersect(DiscoveryStyle.allIds),
            hiddenDiscoveryStyleIds = this[HIDDEN_DISCOVERY_STYLES].orEmpty()
                .mapTo(linkedSetOf(), ::migrateLegacyDiscoveryStyleId).intersect(DiscoveryStyle.allIds),
            pinnedDiscoveryStyleId = this[PINNED_DISCOVERY_STYLE]
                ?.let(::migrateLegacyDiscoveryStyleId)
                ?.takeIf { it in DiscoveryStyle.allIds },
            discoveryRefreshOrdinal = (this[DISCOVERY_REFRESH_ORDINAL] ?: 0).coerceAtLeast(0),
            aspectTolerance = (this[ASPECT_TOL] ?: defaults.aspectTolerance).coerceIn(0.005f, 0.10f),
            cacheLimitMb = (this[CACHE_MB] ?: defaults.cacheLimitMb).coerceIn(50, 2_000),
            saveLocation = enumOr(this[LOCATION], defaults.saveLocation),
            // Kept as a false legacy value: wallpaper setting now has its own temporary-cache path.
            setAfterDownload = false,
            notifyProgress = this[NOTIFY_PROGRESS] ?: defaults.notifyProgress,
            notifyResult = this[NOTIFY_RESULT] ?: defaults.notifyResult,
            wallhavenEnabled = this[WALLHAVEN_ON] ?: defaults.wallhavenEnabled,
            nasaApodEnabled = this[NASA_APOD_ON] ?: defaults.nasaApodEnabled,
        )
    }

    private fun snapshotKey(cacheKey: String) = stringPreferencesKey(DISCOVERY_SNAPSHOT_PREFIX + sha256(cacheKey))

    private fun availabilityKey(sourceId: String, recipeId: String) =
        availabilityKeyForIdentity("$sourceId:$recipeId")

    private fun availabilityKeyForIdentity(identity: String) =
        stringPreferencesKey(DISCOVERY_AVAILABILITY_PREFIX + sha256(identity))

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
        private const val MAX_RECENT_DISCOVERY_RECIPES = 128
        private const val MAX_DISCOVERY_SNAPSHOTS = 8
        private const val MAX_DISCOVERY_SNAPSHOT_ITEMS = 60
        private const val MAX_DISCOVERY_POOLS = 96
        private const val MAX_DISCOVERY_POOL_ITEMS = 24
        private const val DISCOVERY_SNAPSHOT_PREFIX = "discovery_snapshot_"
        private const val DISCOVERY_AVAILABILITY_PREFIX = "discovery_availability_"
        private val RECIPE_ID_PATTERN = Regex("[a-z0-9][a-z0-9-]{0,79}")
        private val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        private val RECENT_DISCOVERY_KEYS = stringPreferencesKey("recent_discovery_keys")
        private val RECENT_DISCOVERY_RECIPES = stringPreferencesKey("recent_discovery_recipes")
        private val DISCOVERY_SNAPSHOT_INDEX = stringPreferencesKey("discovery_snapshot_index")
        private val DISCOVERY_AVAILABILITY_INDEX = stringPreferencesKey("discovery_availability_index")
        private val LAST_MANUAL_DISCOVERY_REFRESH = longPreferencesKey("last_manual_discovery_refresh")
        private val THEME = stringPreferencesKey("theme_mode")
        private val DESIGN_THEME = stringPreferencesKey("design_theme")
        private val FEED_LAYOUT = stringPreferencesKey("feed_layout")
        private val LEGACY_APPEARANCE = stringPreferencesKey("appearance_preset")
        private val ACCENT = stringPreferencesKey("accent")
        private val FONT_CHOICE = stringPreferencesKey("font_choice")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
        private val GLASS_QUALITY = stringPreferencesKey("glass_quality")
        private val INCREASE_CONTRAST = booleanPreferencesKey("increase_contrast")
        private val LEGACY_REDUCE_EXPLICIT = booleanPreferencesKey("reduce_explicit_content")
        private val LEGACY_OPENVERSE_ENABLED = booleanPreferencesKey("openverse_enabled")
        private val ROTATE_DISCOVERY_HOURLY = booleanPreferencesKey("rotate_discovery_hourly")
        private val AVOID_RECENT_DISCOVERY = booleanPreferencesKey("avoid_recent_discovery")
        private val DISCOVERY_REPEAT_HOURS = intPreferencesKey("discovery_repeat_hours")
        private const val LEGACY_BOTANICAL_STYLE_ID = "frutiger-flower"
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
        private val NASA_APOD_ON = booleanPreferencesKey("nasa_apod_enabled")
        private val ART_INSTITUTE_ON = booleanPreferencesKey("art_institute_enabled")
        private val LEGACY_RETIRED_PROVIDER_ENABLED = booleanPreferencesKey("abyss_enabled")
        private val LEGACY_DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
    }
}
