package com.auroro.wallpapers.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigurationAndMappingTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun abyssKeyValidationAllowsOpaqueTokensAndRejectsUnsafeInput() {
        assertEquals(ConfigValidation.Result.Valid(""), ConfigValidation.abyssKey("  "))
        assertEquals(ConfigValidation.Result.Valid("a+/==._-"), ConfigValidation.abyssKey("a+/==._-"))
        assertTrue(ConfigValidation.abyssKey("https://api.alphacoders.com/key").let { it is ConfigValidation.Result.Invalid })
        assertTrue(ConfigValidation.abyssKey("has spaces").let { it is ConfigValidation.Result.Invalid })
        assertTrue(ConfigValidation.abyssKey("a".repeat(513)).let { it is ConfigValidation.Result.Invalid })
    }

    @Test fun wallpaperDatabaseNormalizationRoundTripsAttributionAndProviderMetadata() {
        val wallpaper = com.auroro.wallpapers.wallpaper(
            source = WallpaperSource.ABYSS,
            id = "931204",
            original = "https://images2.alphacoders.com/931/931204.jpg",
            page = "https://wall.alphacoders.com/big.php?i=931204",
        ).copy(
            creatorUrl = null,
            originSourceUrl = "https://example.org/source",
            tags = listOf(com.auroro.wallpapers.core.model.WallpaperTag(null, "oceans"), com.auroro.wallpapers.core.model.WallpaperTag(12, "water")),
        )
        val entity = WallpaperMapper.toEntity(wallpaper, now = 1234)
        val restored = WallpaperMapper.toModel(entity)!!
        assertEquals(wallpaper.key, restored.key)
        assertEquals(wallpaper.originalUrl, restored.originalUrl)
        assertEquals(wallpaper.pageUrl, restored.pageUrl)
        assertEquals(wallpaper.tags, restored.tags)
        assertEquals(wallpaper.colors, restored.colors)
        assertEquals(wallpaper.creatorName, restored.creatorName)
        assertEquals(wallpaper.originSourceUrl, restored.originSourceUrl)
        assertEquals(1234L, entity.updatedAt)
    }

    @Test fun settingsPersistFunctionalPreferencesAndKeepSecretOutOfDataStore() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "settings.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null, secrets = null)
        settings.update { it.copy(themeMode = ThemeMode.DARK, accent = AccentTheme.EMERALD, cacheLimitMb = 500, abyssEnabled = false) }
        val saved = settings.current()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(AccentTheme.EMERALD, saved.accent)
        assertEquals(500, saved.cacheLimitMb)
        assertFalse(saved.abyssEnabled)
        // No key is persisted when no encrypted SecretStore has been configured.
        assertTrue(saved.abyssApiKey.isEmpty())
    }
}
