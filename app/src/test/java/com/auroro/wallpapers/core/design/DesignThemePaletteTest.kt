package com.auroro.wallpapers.core.design

import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.DesignTheme
import com.auroro.wallpapers.core.data.GlassQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignThemePaletteTest {
    @Test fun allAdvancedThemesHaveDistinctLightAndDarkPalettes() {
        DesignTheme.entries.forEach { theme ->
            val light = designColors(
                dark = false,
                accent = AccentTheme.AQUA,
                amoled = false,
                reduceTransparency = false,
                designTheme = theme,
            )
            val dark = designColors(
                dark = true,
                accent = AccentTheme.AQUA,
                amoled = false,
                reduceTransparency = false,
                designTheme = theme,
            )
            assertEquals(theme, light.designTheme)
            assertEquals(theme, dark.designTheme)
            assertEquals(false, light.isDark)
            assertTrue(dark.isDark)
            assertNotEquals(light.backdropTop, dark.backdropTop)
            assertNotEquals(light.surfaceSolid, dark.surfaceSolid)
        }
    }

    @Test fun amoledBlackIsOnlyAppliedWhenDarkModeIsActive() {
        val dark = designColors(
            dark = true,
            accent = AccentTheme.AQUA,
            amoled = true,
            reduceTransparency = false,
            designTheme = DesignTheme.FRUTIGER_AERO,
            glassQuality = GlassQuality.FULL,
        )
        val light = designColors(
            dark = false,
            accent = AccentTheme.AQUA,
            amoled = true,
            reduceTransparency = false,
            designTheme = DesignTheme.FRUTIGER_AERO,
        )
        assertEquals(androidx.compose.ui.graphics.Color.Black, dark.backdropTop)
        assertNotEquals(androidx.compose.ui.graphics.Color.Black, light.backdropTop)
        assertEquals(GlassQuality.FULL, dark.glassQuality)
    }
}
