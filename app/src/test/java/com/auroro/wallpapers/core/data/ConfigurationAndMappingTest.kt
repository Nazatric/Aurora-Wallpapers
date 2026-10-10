package com.auroro.wallpapers.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import kotlinx.coroutines.flow.first
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

    @Test fun recentSearchesAreNormalizedBoundedAndClearableOnDevice() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "search-history.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)

        settings.rememberSearchQuery("  blue   hour   night  ")
        assertEquals(listOf("blue hour night"), settings.recentSearches.first())
        settings.rememberSearchQuery("Forest")
        settings.rememberSearchQuery("BLUE HOUR NIGHT")
        (1..10).forEach { settings.rememberSearchQuery("topic $it") }

        val recent = settings.recentSearches.first()
        assertEquals(8, recent.size)
        assertEquals("topic 10", recent.first())
        assertFalse(recent.any { it.equals("BLUE HOUR NIGHT", ignoreCase = true) })

        settings.clearSearchHistory()
        assertTrue(settings.recentSearches.first().isEmpty())
    }

    @Test fun discoverySnapshotsRepeatHistoryAndManualRefreshCooldownPersistLocally() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "discovery.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)
        val now = 1_800_000_000_000L

        val first = settings.claimManualDiscoveryRefresh(now)
        assertTrue(first.allowed)
        assertEquals(1, first.ordinal)
        val tooSoon = settings.claimManualDiscoveryRefresh(now + 1_000L)
        assertFalse(tooSoon.allowed)
        assertTrue(tooSoon.remainingMillis > 0)
        val later = settings.claimManualDiscoveryRefresh(now + SettingsRepository.MANUAL_REFRESH_COOLDOWN_MILLIS)
        assertTrue(later.allowed)
        assertEquals(2, later.ordinal)

        settings.saveDiscoverySnapshot("42:surreal:1:2", listOf("wallhaven:one", "wallhaven:two"))
        assertEquals(listOf("wallhaven:one", "wallhaven:two"), settings.discoverySnapshot("42:surreal:1:2"))
        settings.recordDiscoverySeen(listOf("wallhaven:one"), now)
        assertEquals(setOf("wallhaven:one"), settings.recentlySeenDiscoveryKeys(now + 1, 24))
        assertTrue(settings.recentlySeenDiscoveryKeys(now + 25L * 60 * 60 * 1000, 24).isEmpty())

        settings.resetDiscovery()
        assertTrue(settings.discoverySnapshot("42:surreal:1:2").isEmpty())
        assertTrue(settings.recentlySeenDiscoveryKeys(now + 2, 24).isEmpty())
        assertTrue(settings.current().rotateDiscoveryHourly)
        assertEquals(null, settings.current().pinnedDiscoveryStyleId)
    }

    @Test fun settingsPersistSourceAndDisplayPreferencesWithoutCredentialsOrQualityTransforms() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "settings.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)
        settings.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                accent = AccentTheme.EMERALD,
                appearance = AppearancePreset.FRUTIGER_FLOWER,
                fontChoice = FontChoice.SYSTEM,
                fontScale = 1.2f,
                glassQuality = GlassQuality.FULL,
                increaseContrast = true,
                reducePotentiallyExplicitContent = false,
                rotateDiscoveryHourly = false,
                avoidRecentlySeen = false,
                repeatAfterHours = 72,
                preferredDiscoveryStyleIds = setOf("surreal", "weirdcore"),
                hiddenDiscoveryStyleIds = setOf("vaporwave-y2k"),
                pinnedDiscoveryStyleId = "surreal",
                cacheLimitMb = 500,
                wallhavenEnabled = false,
                openverseEnabled = true,
            )
        }
        val saved = settings.current()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(AccentTheme.EMERALD, saved.accent)
        assertEquals(AppearancePreset.FRUTIGER_FLOWER, saved.appearance)
        assertEquals(FontChoice.SYSTEM, saved.fontChoice)
        assertEquals(1.2f, saved.fontScale, 0.001f)
        assertEquals(GlassQuality.FULL, saved.glassQuality)
        assertTrue(saved.increaseContrast)
        assertFalse(saved.reducePotentiallyExplicitContent)
        assertFalse(saved.rotateDiscoveryHourly)
        assertFalse(saved.avoidRecentlySeen)
        assertEquals(72, saved.repeatAfterHours)
        assertEquals(setOf("surreal", "weirdcore"), saved.preferredDiscoveryStyleIds)
        assertEquals(setOf("vaporwave-y2k"), saved.hiddenDiscoveryStyleIds)
        assertEquals("surreal", saved.pinnedDiscoveryStyleId)
        assertEquals(500, saved.cacheLimitMb)
        assertFalse(saved.wallhavenEnabled)
        assertTrue(saved.openverseEnabled)
    }
}
