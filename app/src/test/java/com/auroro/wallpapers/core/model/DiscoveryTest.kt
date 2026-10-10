package com.auroro.wallpapers.core.model

import com.auroro.wallpapers.wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {
    @Test fun hourlyChoiceIsDeterministicAndChangesOnlyWithBucketOrExplicitRefresh() {
        val start = 1_800_000_000_000L
        val first = DiscoveryRotation.select(start)
        assertEquals(first, DiscoveryRotation.select(start + 12_345))
        val nextHour = DiscoveryRotation.select(start + DiscoveryRotation.HOUR_MILLIS)
        assertNotEquals(first.cacheKey, nextHour.cacheKey)
        val manual = DiscoveryRotation.select(start, refreshOrdinal = 1)
        assertNotEquals(first.cacheKey, manual.cacheKey)
    }

    @Test fun pinnedAndPreferredStylesRespectExclusions() {
        val styles = setOf(DiscoveryStyle.SURREAL.id, DiscoveryStyle.WEIRDCORE.id)
        repeat(8) { step ->
            val selected = DiscoveryRotation.select(
                epochMillis = 1_800_000_000_000L + step * DiscoveryRotation.HOUR_MILLIS,
                preferredStyleIds = styles,
                hiddenStyleIds = setOf(DiscoveryStyle.SURREAL.id),
            )
            assertEquals(DiscoveryStyle.WEIRDCORE, selected.style)
        }
        val pinned = DiscoveryRotation.select(
            epochMillis = 1_800_000_000_000L,
            pinnedStyleId = DiscoveryStyle.FRUTIGER_FLOWER.id,
        )
        assertEquals(DiscoveryStyle.FRUTIGER_FLOWER, pinned.style)
        assertTrue(pinned.pinned)
    }

    @Test fun queryFallbackIsExplicitAndHappensAtMostOnce() {
        val original = DiscoveryRotation.select(0L, pinnedStyleId = DiscoveryStyle.SURREAL.id)
        val fallback = original.fallbackQuery()!!
        assertNotEquals(original.query, fallback.query)
        assertEquals(null, fallback.fallbackQuery())
    }

    @Test fun qualityRankingUsesRelevanceResolutionAndRepeatPenaltyNotPopularity() {
        val selection = DiscoveryRotation.select(0L, pinnedStyleId = DiscoveryStyle.FRUTIGER_FLOWER.id)
        val relevant = wallpaper(id = "relevant", tags = listOf(WallpaperTag(1, "flower"), WallpaperTag(2, "botanical")))
            .copy(favorites = 0)
        val popularNoise = wallpaper(id = "popular", tags = listOf(WallpaperTag(3, "mountain")))
            .copy(favorites = 99_999, views = 9_999_999)
        val largerRelevant = relevant.copy(sourceId = "larger", width = 4320, height = 7680, favorites = 0)
        val ranked = DiscoveryQuality.rank(listOf(popularNoise, relevant, largerRelevant), selection)
        assertEquals("larger", ranked.first().sourceId)
        assertEquals("relevant", ranked[1].sourceId)
        assertEquals("popular", ranked.last().sourceId)

        val repeatRanked = DiscoveryQuality.rank(listOf(relevant, popularNoise), selection, setOf(relevant.key))
        assertTrue(repeatRanked.first().key != relevant.key)
    }

    @Test fun onlySfwWallhavenWallpaperSizedKnownDimensionResultsAreKept() {
        val goodNature = wallpaper(id = "nature")
        val openverse = wallpaper(source = WallpaperSource.OPENVERSE, id = "archive")
        val missingDimensions = wallpaper(id = "unknown", width = 0, height = 0)
        val tooSmall = wallpaper(id = "small", width = 400, height = 700)
        val excludedCategory = wallpaper(id = "anime").copy(category = "Anime")
        val map = wallpaper(id = "map", tags = listOf(WallpaperTag(1, "map")))
        val selection = DiscoveryRotation.select(0L)
        assertEquals(listOf(goodNature), DiscoveryQuality.rank(
            listOf(openverse, missingDimensions, tooSmall, excludedCategory, map, goodNature),
            selection,
        ))
    }

    @Test fun qualityFilterKeepsPeopleCategoryPortraitArt() {
        val portrait = wallpaper(id = "portrait", tags = listOf(WallpaperTag(1, "portrait"))).copy(category = "People")
        val selection = DiscoveryRotation.select(0L)
        assertEquals(listOf(portrait), DiscoveryQuality.rank(listOf(portrait), selection))
    }

    @Test fun sensitivityFilterDoesNotHideOrdinaryPeopleOrClassicalFigureArt() {
        val normal = wallpaper(id = "swim", tags = listOf(WallpaperTag(1, "swimwear"), WallpaperTag(2, "person")))
        val classical = wallpaper(id = "classical", tags = listOf(WallpaperTag(1, "classical art"), WallpaperTag(2, "figure study")))
        val marked = wallpaper(id = "marked", tags = listOf(WallpaperTag(1, "nsfw")))
        assertFalse(ContentSensitivity.isPotentiallyExplicit(normal))
        assertFalse(ContentSensitivity.isPotentiallyExplicit(classical))
        assertTrue(ContentSensitivity.isPotentiallyExplicit(marked))
        assertEquals(listOf(normal, classical), LocalFilter.apply(listOf(normal, classical, marked), WallpaperFilter.Default))
    }

    @Test fun feedOrientationContinuesToUseActualDimensions() {
        val tall = wallpaper(id = "tall", width = 1440, height = 3200)
        val wide = wallpaper(id = "wide", width = 3200, height = 1440)
        assertEquals(listOf(tall), LocalFilter.apply(listOf(tall, wide), WallpaperFilter(orientation = Orientation.PORTRAIT)))
        assertEquals(listOf(wide), LocalFilter.apply(listOf(tall, wide), WallpaperFilter(orientation = Orientation.LANDSCAPE)))
    }
}
