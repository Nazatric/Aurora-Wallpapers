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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark ocean"), LIGHT("Light sky") }

enum class AccentTheme(val label: String) { AQUA("Aqua"), EMERALD("Emerald"), SKY("Sky blue") }

enum class DownloadQuality(val label: String, val description: String) {
    ORIGINAL("Original", "Saves the exact file published by the source."),
    SCREEN_FIT("Fit to screen", "Downscales files larger than twice your screen to save space."),
}

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
    val downloadQuality: DownloadQuality = DownloadQuality.ORIGINAL,
    val saveLocation: SaveLocation = SaveLocation.GALLERY,
    val setAfterDownload: Boolean = false,
    val notifyProgress: Boolean = true,
    val notifyResult: Boolean = true,
    val wallhavenEnabled: Boolean = true,
    val abyssEnabled: Boolean = true,
    val abyssApiKey: String = "",
) {
    companion object {
        const val DEFAULT_CACHE_MB = 250
        val CACHE_CHOICES_MB = listOf(100, 250, 500, 1000)
    }
}

/** Persists lightweight preferences in DataStore. The Abyss key stays in app-private storage. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
    /** Tiny synchronous mirror used only so the image cache can be sized at process start without blocking on DataStore. */
    private val bootPrefs: SharedPreferences? = null,
    /** AES-GCM encrypted AndroidKeyStore-backed storage; never persisted as plaintext in DataStore. */
    private val secrets: SecureSecretStore? = null,
) {
    private val secretRevision = MutableStateFlow(0)
    val settings: Flow<AppSettings> = combine(store.data, secretRevision) { preferences, _ ->
        preferences.toSettings().copy(abyssApiKey = secrets?.get(SecureSecretStore.ABYSS_KEY).orEmpty())
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        withContext(Dispatchers.IO) { secrets?.put(SecureSecretStore.ABYSS_KEY, next.abyssApiKey) }
        store.edit { p ->
            p[THEME] = next.themeMode.name
            p[ACCENT] = next.accent.name
            p[AMOLED] = next.amoled
            p[REDUCE_TRANSPARENCY] = next.reduceTransparency
            p[ASPECT_TOL] = next.aspectTolerance
            p[CACHE_MB] = next.cacheLimitMb
            p[QUALITY] = next.downloadQuality.name
            p[LOCATION] = next.saveLocation.name
            p[SET_AFTER] = next.setAfterDownload
            p[NOTIFY_PROGRESS] = next.notifyProgress
            p[NOTIFY_RESULT] = next.notifyResult
            p[WALLHAVEN_ON] = next.wallhavenEnabled
            p[ABYSS_ON] = next.abyssEnabled
        }
        secretRevision.value++
        bootPrefs?.edit()?.putInt(BOOT_CACHE_MB, next.cacheLimitMb)?.apply()
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[THEME], d.themeMode),
            accent = enumOr(this[ACCENT], d.accent),
            amoled = this[AMOLED] ?: d.amoled,
            reduceTransparency = this[REDUCE_TRANSPARENCY] ?: d.reduceTransparency,
            aspectTolerance = (this[ASPECT_TOL] ?: d.aspectTolerance).coerceIn(0.005f, 0.10f),
            cacheLimitMb = this[CACHE_MB] ?: d.cacheLimitMb,
            downloadQuality = enumOr(this[QUALITY], d.downloadQuality),
            saveLocation = enumOr(this[LOCATION], d.saveLocation),
            setAfterDownload = this[SET_AFTER] ?: d.setAfterDownload,
            notifyProgress = this[NOTIFY_PROGRESS] ?: d.notifyProgress,
            notifyResult = this[NOTIFY_RESULT] ?: d.notifyResult,
            wallhavenEnabled = this[WALLHAVEN_ON] ?: d.wallhavenEnabled,
            abyssEnabled = this[ABYSS_ON] ?: d.abyssEnabled,
            abyssApiKey = "", // supplied by the encrypted AndroidKeyStore-backed store, never DataStore
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        const val BOOT_CACHE_MB = "cache_limit_mb"
        private val THEME = stringPreferencesKey("theme_mode")
        private val ACCENT = stringPreferencesKey("accent")
        private val AMOLED = booleanPreferencesKey("amoled")
        private val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        private val ASPECT_TOL = floatPreferencesKey("aspect_tolerance")
        private val CACHE_MB = intPreferencesKey("cache_limit_mb")
        private val QUALITY = stringPreferencesKey("download_quality")
        private val LOCATION = stringPreferencesKey("save_location")
        private val SET_AFTER = booleanPreferencesKey("set_after_download")
        private val NOTIFY_PROGRESS = booleanPreferencesKey("notify_progress")
        private val NOTIFY_RESULT = booleanPreferencesKey("notify_result")
        private val WALLHAVEN_ON = booleanPreferencesKey("wallhaven_enabled")
        private val ABYSS_ON = booleanPreferencesKey("abyss_enabled")
    }
}

/** Validation for user-supplied configuration. */
object ConfigValidation {
    sealed interface Result {
        data class Valid(val value: String) : Result
        data class Invalid(val reason: String) : Result
    }

    /** Alpha Coders documents auth as an opaque string, not a fixed character set or length. */
    fun abyssKey(input: String): Result {
        val v = input.trim()
        return when {
            v.isEmpty() -> Result.Valid("") // empty = remove the key
            v.startsWith("http", ignoreCase = true) -> Result.Invalid("Paste the key itself, not a URL.")
            v.length > 512 -> Result.Invalid("The key is unexpectedly long (maximum 512 characters).")
            v.any { it.isWhitespace() || it.isISOControl() } -> Result.Invalid("The key can't contain spaces or control characters.")
            else -> Result.Valid(v)
        }
    }
}
