package com.auroro.wallpapers.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigurationAndMappingTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun wallpaperDatabaseNormalizationRoundTripsOpenverseAttributionAndLicenceMetadata() {
        val wallpaper = com.auroro.wallpapers.wallpaper(
            source = WallpaperSource.OPENVERSE,
            id = "4bc43a04-ef46-4544-a0c1-63c63f56e276",
            original = "https://images.example.org/bark.jpg",
            page = "https://stocksnap.io/photo/XNVBVXO3B7",
        ).copy(
            creatorName = "Tim Sullivan",
            creatorUrl = "https://www.secretagencygroup.com",
            originSourceUrl = "https://stocksnap.io/photo/XNVBVXO3B7",
            title = "Tree Bark Photo",
            attribution = "Tree Bark Photo by Tim Sullivan is marked with CC0 1.0.",
            licenseCode = "cc0",
            licenseVersion = "1.0",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
            providerName = "stocksnap",
            catalogSource = "stocksnap",
            downloadAllowed = true,
            setWallpaperAllowed = true,
            tags = listOf(WallpaperTag(null, "trees"), WallpaperTag(null, "texture")),
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
        assertEquals(wallpaper.attribution, restored.attribution)
        assertEquals(wallpaper.licenseCode, restored.licenseCode)
        assertEquals(wallpaper.licenseUrl, restored.licenseUrl)
        assertEquals(wallpaper.providerName, restored.providerName)
        assertEquals(wallpaper.catalogSource, restored.catalogSource)
        assertTrue(restored.downloadAllowed)
        assertTrue(restored.setWallpaperAllowed)
        assertEquals(1234L, entity.updatedAt)
    }

    @Test fun settingsPersistSourceAndDisplayPreferencesWithoutCredentialsOrQualityTransforms() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "settings.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)
        settings.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                accent = AccentTheme.EMERALD,
                cacheLimitMb = 500,
                wallhavenEnabled = false,
                openverseEnabled = true,
            )
        }
        val saved = settings.current()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(AccentTheme.EMERALD, saved.accent)
        assertEquals(500, saved.cacheLimitMb)
        assertFalse(saved.wallhavenEnabled)
        assertTrue(saved.openverseEnabled)
    }
}
