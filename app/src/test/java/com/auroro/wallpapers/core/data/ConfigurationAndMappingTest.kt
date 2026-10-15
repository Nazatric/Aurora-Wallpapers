package com.auroro.wallpapers.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    @Test fun retiredGlobalContentAndOpenversePreferencesAreRemovedFromDataStore() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "retired-preferences.preferences_pb") })
        val oldContentSetting = booleanPreferencesKey("reduce_explicit_content")
        val oldOpenverseToggle = booleanPreferencesKey("openverse_enabled")
        store.edit {
            it[oldContentSetting] = true
            it[oldOpenverseToggle] = true
        }

        val settings = SettingsRepository(store, bootPrefs = null)
        val migrated = kotlinx.coroutines.withTimeout(10_000L) {
            settings.current()
            store.data.first()
        }

        assertFalse(migrated.contains(oldContentSetting))
        assertFalse(migrated.contains(oldOpenverseToggle))
        assertTrue(settings.current().enabledSources.contains("wallhaven"))
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
    }

    @Test fun validatedDiscoveryPoolsAndRecipeRepeatHistoryPersistAndResetLocally() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "discovery-pools.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)
        val now = 1_800_000_000_000L
        val record = DiscoveryAvailabilityRecord(
            sourceId = "wallhaven",
            recipeId = "fractal-art",
            lastSuccessfulAtMillis = now,
            updatedAtMillis = now,
            wallpaperKeys = listOf("wallhaven:first", "wallhaven:second"),
        )

        settings.saveDiscoveryAvailability(record)
        settings.recordDiscoveryRecipeIds(listOf("fractal-art", "vaporwave"), now)
        assertEquals(record.wallpaperKeys, settings.discoveryAvailability("wallhaven", "fractal-art")?.wallpaperKeys)
        assertEquals(now, settings.discoveryAvailability("wallhaven", "fractal-art")?.lastSuccessfulAtMillis)
        assertEquals(setOf("fractal-art", "vaporwave"), settings.recentlyUsedDiscoveryRecipeIds(now + 1, 24))

        settings.resetDiscovery()
        assertEquals(null, settings.discoveryAvailability("wallhaven", "fractal-art"))
        assertTrue(settings.recentlyUsedDiscoveryRecipeIds(now + 1, 24).isEmpty())
    }

    @Test fun appearanceDefaultsToFollowSystemAndLiquidGlass() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "appearance-defaults.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null).current()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals("Follow system", settings.themeMode.label)
        assertEquals(DesignTheme.LIQUID_GLASS, settings.designTheme)
        assertEquals(
            listOf(DesignTheme.LIQUID_GLASS, DesignTheme.FRUTIGER_AERO, DesignTheme.MATERIAL_3),
            DesignTheme.entries,
        )
    }

    @Test fun basicModeAndAdvancedThemePersistIndependently() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "separate-themes.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)
        settings.update { it.copy(themeMode = ThemeMode.DARK, designTheme = DesignTheme.MATERIAL_3) }
        assertEquals(ThemeMode.DARK, settings.current().themeMode)
        assertEquals(DesignTheme.MATERIAL_3, settings.current().designTheme)

        settings.update { it.copy(themeMode = ThemeMode.SYSTEM) }
        assertEquals(ThemeMode.SYSTEM, settings.current().themeMode)
        assertEquals(DesignTheme.MATERIAL_3, settings.current().designTheme)
        settings.update { it.copy(designTheme = DesignTheme.LIQUID_GLASS) }
        assertEquals(ThemeMode.SYSTEM, settings.current().themeMode)
        assertEquals(DesignTheme.LIQUID_GLASS, settings.current().designTheme)
    }

    @Test fun legacyCombinedAppearanceAndStylePreferencesRemainReadableWithoutExposingRetiredPreset() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "legacy-theme.preferences_pb") })
        val legacyAppearance = stringPreferencesKey("appearance_preset")
        val oldPreferred = stringSetPreferencesKey("preferred_discovery_styles")
        val oldHidden = stringSetPreferencesKey("hidden_discovery_styles")
        val oldPinned = stringPreferencesKey("pinned_discovery_style")
        store.edit {
            it[legacyAppearance] = "FRUTIGER_FLOWER"
            it[oldPreferred] = setOf("surreal", "frutiger-flower")
            it[oldHidden] = setOf("weirdcore")
            it[oldPinned] = "surreal"
        }

        val settings = SettingsRepository(store, bootPrefs = null)
        val migrated = kotlinx.coroutines.withTimeout(10_000L) {
            settings.current()
            store.data.first()
        }
        assertEquals(ThemeMode.LIGHT, settings.current().themeMode)
        assertEquals(DesignTheme.MATERIAL_3, settings.current().designTheme)
        assertFalse(migrated.contains(legacyAppearance))
        assertEquals(setOf("surreal", "frutiger-flower"), migrated[oldPreferred])
        assertEquals(setOf("weirdcore"), migrated[oldHidden])
        assertEquals("surreal", migrated[oldPinned])
        assertEquals(setOf("surreal", "botanical-art"), settings.current().preferredDiscoveryStyleIds)
        assertEquals(setOf("weirdcore"), settings.current().hiddenDiscoveryStyleIds)
        assertEquals("surreal", settings.current().pinnedDiscoveryStyleId)
        settings.update { it.copy(fontScale = 1.05f) }
        val normalized = store.data.first()
        assertEquals(setOf("surreal", "botanical-art"), normalized[oldPreferred])
        assertEquals(listOf("Liquid Glass", "Frutiger Aero", "Material 3"), DesignTheme.entries.map { it.label })
    }

    @Test fun settingsPersistSourceAndDisplayPreferencesWithoutCredentialsOrQualityTransforms() = runBlocking {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "settings.preferences_pb") })
        val settings = SettingsRepository(store, bootPrefs = null)
        settings.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                designTheme = DesignTheme.MATERIAL_3,
                accent = AccentTheme.EMERALD,
                fontChoice = FontChoice.SYSTEM,
                fontScale = 1.2f,
                glassQuality = GlassQuality.FULL,
                increaseContrast = true,
                rotateDiscoveryHourly = false,
                avoidRecentlySeen = false,
                repeatAfterHours = 72,
                cacheLimitMb = 500,
                enabledSources = emptySet(),
            )
        }
        val saved = settings.current()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(DesignTheme.MATERIAL_3, saved.designTheme)
        assertEquals(AccentTheme.EMERALD, saved.accent)
        assertEquals(FontChoice.SYSTEM, saved.fontChoice)
        assertEquals(1.2f, saved.fontScale, 0.001f)
        assertEquals(GlassQuality.FULL, saved.glassQuality)
        assertTrue(saved.increaseContrast)
        assertFalse(saved.rotateDiscoveryHourly)
        assertFalse(saved.avoidRecentlySeen)
        assertEquals(72, saved.repeatAfterHours)
        assertEquals(500, saved.cacheLimitMb)
        assertFalse(saved.enabledSources.contains("wallhaven"))
        assertFalse(saved.enabledSources.contains("nasa-apod"))
    }
}
