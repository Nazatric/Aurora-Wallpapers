package com.auroro.wallpapers.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSourceSelectionTest {
    @Test fun selectingWallhavenForAnEmptySearchUsesItsUsableDefaultOrdering() {
        val initial = WallpaperFilter(sort = SortOption.RELEVANCE)

        val selected = SearchSourceSelection.select(initial, setOf(WallpaperSource.WALLHAVEN), "   ")

        assertEquals(setOf(WallpaperSource.WALLHAVEN), selected.sources)
        assertEquals(SortOption.NEWEST, selected.sort)
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

    @Test fun selectingNasaClearsWallhavenOnlyFiltersAndUsesItsNewestOrder() {
        val initial = WallpaperFilter(
            sort = SortOption.POPULAR,
            wallhavenCategories = setOf(WallhavenCategory.ANIME),
            colorHex = "66cccc",
        )

        val selected = SearchSourceSelection.select(initial, setOf(WallpaperSource.NASA_APOD), "")

        assertEquals(setOf(WallpaperSource.NASA_APOD), selected.sources)
        assertEquals(SortOption.SOURCE_DEFAULT, selected.sort)
        assertEquals(emptySet<WallhavenCategory>(), selected.wallhavenCategories)
        assertNull(selected.colorHex)
    }

    @Test fun allSourcesClearsIncompatibleProviderOnlyFilters() {
        val initial = WallpaperFilter(
            sources = setOf(WallpaperSource.WALLHAVEN),
            wallhavenCategories = setOf(WallhavenCategory.PEOPLE),
            colorHex = "66cccc",
        )

        val selected = SearchSourceSelection.select(initial, emptySet(), "lake")

        assertEquals(emptySet<WallpaperSource>(), selected.sources)
        assertEquals(emptySet<WallhavenCategory>(), selected.wallhavenCategories)
        assertNull(selected.colorHex)
    }

    @Test fun unsupportedRetiredAndUnauthenticatedSourcesCannotBeSelected() {
        val initial = WallpaperFilter.Default

        val archived = SearchSourceSelection.select(initial, setOf(WallpaperSource.ARCHIVED), "wallpaper")
        val legacy = SearchSourceSelection.select(initial, setOf(WallpaperSource.OPENVERSE), "wallpaper")
        val reddit = SearchSourceSelection.select(initial, setOf(WallpaperSource.REDDIT), "wallpaper")

        assertEquals(setOf(WallpaperSource.ARCHIVED), archived.sources)
        assertEquals(setOf(WallpaperSource.OPENVERSE), legacy.sources)
        assertEquals(setOf(WallpaperSource.REDDIT), reddit.sources)
    }
}
