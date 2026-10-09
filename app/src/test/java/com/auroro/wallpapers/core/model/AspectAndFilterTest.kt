package com.auroro.wallpapers.core.model

import com.auroro.wallpapers.wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AspectAndFilterTest {
    @Test fun portraitAndLandscapeRatiosUseActualDimensions() {
        assertEquals(0.5625f, AspectMath.ratio(2160, 3840), 0.0001f)
        assertTrue(AspectMath.matches(2160, 3840, AspectPreset.R9_16.ratio))
        assertTrue(AspectMath.matches(3840, 2160, AspectPreset.R16_9.ratio))
        assertFalse(AspectMath.matches(2160, 3840, AspectPreset.R16_9.ratio))
        assertEquals("9:16", AspectMath.describe(2160, 3840))
        assertEquals("16:9", AspectMath.describe(3840, 2160))
    }

    @Test fun ratioToleranceIsRelativeAndConfigurable() {
        // 1080 × 1900 is about 1.56% from 9:16: accepted at 3%, rejected at 1%.
        assertTrue(AspectMath.matches(1080, 1900, 9f / 16f, tolerance = 0.03f))
        assertFalse(AspectMath.matches(1080, 1900, 9f / 16f, tolerance = 0.01f))
        assertFalse(AspectMath.matches(0, 100, 9f / 16f))
    }

    @Test fun customRatiosParseWidthHeightAndDecimals() {
        assertEquals(9f / 20f, AspectMath.parseCustom("9:20")!!.ratio, 0.0001f)
        assertEquals(9f / 19.5f, AspectMath.parseCustom("9 × 19.5")!!.ratio, 0.0001f)
        assertEquals(1.5f, AspectMath.parseCustom("3/2")!!.ratio, 0.0001f)
        assertEquals(1.75f, AspectMath.parseCustom("1.75")!!.ratio, 0.0001f)
        assertEquals(null, AspectMath.parseCustom("0:16"))
        assertEquals(null, AspectMath.parseCustom("9:0"))
        assertEquals(null, AspectMath.parseCustom("wide"))
        assertEquals(null, AspectMath.parseCustom("1:2:3"))
    }

    @Test fun fourAndEightKAreOrientationIndependent() {
        val fourK = ResolutionFilter.Preset(ResolutionPreset.K4)
        val eightK = ResolutionFilter.Preset(ResolutionPreset.K8)
        assertTrue(fourK.matches(3840, 2160))
        assertTrue(fourK.matches(2160, 3840))
        assertTrue(eightK.matches(7680, 4320))
        assertTrue(eightK.matches(4320, 7680))
        assertFalse(eightK.matches(3840, 2160))
        assertTrue(ResolutionFilter.Preset(ResolutionPreset.P1080).matches(1080, 1920))
        assertFalse(ResolutionFilter.Preset(ResolutionPreset.P1080).matches(1079, 1920))
    }

    @Test fun customResolutionComparesShortAndLongEdgesForEitherOrientation() {
        val minimum = ResolutionFilter.Custom(1080, 1920)
        assertTrue(minimum.matches(1080, 2400))
        assertTrue(minimum.matches(2400, 1080))
        assertFalse(minimum.matches(1079, 2400))
        assertFalse(minimum.matches(1919, 1080))
        assertTrue(ResolutionFilter.Any.matches(0, 0))
    }

    @Test fun localFiltersCombineAspectResolutionAndOrientation() {
        val portrait = wallpaper(width = 2160, height = 3840)
        val landscape = wallpaper(id = "def456", width = 3840, height = 2160)
        val filter = WallpaperFilter(
            aspect = AspectFilter.Preset(AspectPreset.R9_16),
            resolution = ResolutionFilter.Preset(ResolutionPreset.K4),
            orientation = Orientation.PORTRAIT,
        )
        assertTrue(LocalFilter.matches(portrait, filter))
        assertFalse(LocalFilter.matches(landscape, filter))
        assertEquals(listOf(portrait), LocalFilter.apply(listOf(landscape, portrait), filter))
    }

    @Test fun sourceSelectionAndActiveFilterCountAreExplicit() {
        val filter = WallpaperFilter(
            sources = setOf(WallpaperSource.WALLHAVEN),
            aspect = AspectFilter.Preset(AspectPreset.R9_16),
            resolution = ResolutionFilter.Preset(ResolutionPreset.K4),
            orientation = Orientation.PORTRAIT,
            sort = SortOption.NEWEST,
            wallhavenCategories = setOf(WallhavenCategory.GENERAL),
            colorHex = "66cccc",
        )
        assertEquals(7, filter.activeCount)
        assertEquals(0, WallpaperFilter.Default.activeCount)
    }
}
