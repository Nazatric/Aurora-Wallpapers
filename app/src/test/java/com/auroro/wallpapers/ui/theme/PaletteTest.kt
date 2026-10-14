package com.auroro.wallpapers.ui.theme

import androidx.compose.ui.graphics.Color
import com.auroro.wallpapers.core.data.AccentTheme
import com.auroro.wallpapers.core.data.DesignTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Palette resolution is pure math over settings — verified on the JVM so a settings
 * combination can never silently produce invisible text or a dead accent again.
 */
class PaletteTest {

    private fun palette(
        dark: Boolean = true,
        amoled: Boolean = false,
        contrast: Boolean = false,
        accent: AccentTheme = AccentTheme.AQUA,
        design: DesignTheme = DesignTheme.LIQUID_GLASS,
    ) = auroroPalette(dark, amoled, contrast, accent, design)

    @Test
    fun defaultDarkPaletteReproducesTheLaunchLook() {
        val p = palette()
        assertEquals(Color(0xFF0A0A0F), p.background)
        assertEquals(Color(0xFF8B7CF6), p.accent)
        assertEquals(Color(0xFFF2F2F7), p.textPrimary)
    }

    @Test
    fun amoledDarkensSurfacesToBlack() {
        val p = palette(amoled = true)
        assertEquals(Color.Black, p.background)
        assertNotEquals(palette().surface, p.surface)
    }

    @Test
    fun amoledNeverAppliesToLightMode() {
        val p = palette(dark = false, amoled = true)
        assertNotEquals(Color.Black, p.background)
    }

    @Test
    fun lightModeFlipsTextAndSurfaces() {
        val p = palette(dark = false)
        assertEquals(Color(0xFFF4F4F9), p.background)
        assertEquals(Color(0xFF17171F), p.textPrimary)
        // Text must stay readable: luminance ordering background > text is inverted vs dark.
        assertEquals(Color.White, p.surface)
    }

    @Test
    fun contrastBoostPushesTextToTheExtremes() {
        assertEquals(Color.White, palette(contrast = true).textPrimary)
        assertEquals(Color.Black, palette(dark = false, contrast = true).textPrimary)
    }

    @Test
    fun accentAndDesignChoicesChangeTheAccentColour() {
        val emerald = palette(accent = AccentTheme.EMERALD).accent
        val sky = palette(accent = AccentTheme.SKY).accent
        val violet = palette(accent = AccentTheme.AQUA).accent
        assertNotEquals(emerald, sky)
        assertNotEquals(emerald, violet)
        // Material 3 keeps its own identity regardless of accent choice.
        assertEquals(violet, palette(design = DesignTheme.MATERIAL_3, accent = AccentTheme.SKY).accent)
        assertEquals(violet, palette(design = DesignTheme.MATERIAL_3, accent = AccentTheme.EMERALD).accent)
    }

    @Test
    fun everyPaletteKeepsAccentLegibleAgainstItsSurfaces() {
        listOf(true, false).forEach { dark ->
            listOf(true, false).forEach { amoled ->
                val p = palette(dark = dark, amoled = amoled)
                // Accent must differ from background and surface, or UI highlights vanish.
                assertNotEquals(p.accent, p.background)
                assertNotEquals(p.accent, p.surface)
                assertNotEquals(p.textPrimary, p.background)
            }
        }
    }
}
