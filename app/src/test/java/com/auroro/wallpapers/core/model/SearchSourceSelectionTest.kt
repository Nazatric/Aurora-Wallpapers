package com.auroro.wallpapers.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSourceSelectionTest {
    @Test fun selectingWallhavenForAnEmptySearchUsesItsUsableDefaultOrdering() {
        val initial = WallpaperFilter(
            sort = SortOption.RELEVANCE,
            openverseTag = "forest",
            openverseLicense = OpenverseLicense.CC0,
        )

        val selected = SearchSourceSelection.select(initial, setOf(WallpaperSource.WALLHAVEN), "   ")

        assertEquals(setOf(WallpaperSource.WALLHAVEN), selected.sources)
        assertEquals(SortOption.NEWEST, selected.sort)
        assertNull(selected.openverseTag)
        assertNull(selected.openverseLicense)
    }

    @Test fun keywordSearchCanKeepWallhavenRelevance() {
        val selected = SearchSourceSelection.select(
            WallpaperFilter(sort = SortOption.RELEVANCE),
            setOf(WallpaperSource.WALLHAVEN),
            "  northern lights  ",
        )

        assertEquals(SortOption.RELEVANCE, selected.sort)
        assertEquals(setOf(WallpaperSource.WALLHAVEN), selected.sources)
    }

    @Test fun selectingOpenverseKeepsItsRealRelevanceSortAndDropsWallhavenOnlyFilters() {
        val initial = WallpaperFilter(
            sort = SortOption.POPULAR,
            wallhavenCategories = setOf(WallhavenCategory.ANIME),
            colorHex = "66cccc",
        )

        val selected = SearchSourceSelection.select(initial, setOf(WallpaperSource.OPENVERSE), "forest")

        assertEquals(setOf(WallpaperSource.OPENVERSE), selected.sources)
        assertEquals(SortOption.RELEVANCE, selected.sort)
        assertEquals(emptySet<WallhavenCategory>(), selected.wallhavenCategories)
        assertNull(selected.colorHex)
    }

    @Test fun selectingAllSourcesClearsIncompatibleProviderOnlyFilters() {
        val initial = WallpaperFilter(
            sources = setOf(WallpaperSource.OPENVERSE),
            openverseCategory = OpenverseCategory.PHOTOGRAPH,
            openverseLicense = OpenverseLicense.CC0,
        )

        val selected = SearchSourceSelection.select(initial, emptySet(), "lake")

        assertEquals(emptySet<WallpaperSource>(), selected.sources)
        assertNull(selected.openverseCategory)
        assertNull(selected.openverseLicense)
    }
}
