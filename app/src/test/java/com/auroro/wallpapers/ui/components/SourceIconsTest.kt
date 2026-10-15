package com.auroro.wallpapers.ui.components

import com.auroro.wallpapers.core.model.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every selectable catalogue must carry its own distinct mark for chips and tags. */
class SourceIconsTest {
    @Test fun everyIntegratedCatalogueHasItsOwnDistinctMark() {
        val marks = WallpaperSource.integrated.map { it.iconRes() }
        assertEquals(WallpaperSource.integrated.size, marks.toSet().size)
        assertTrue(marks.all { it != 0 })
    }
}
