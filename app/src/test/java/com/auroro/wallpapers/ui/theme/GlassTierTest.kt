package com.auroro.wallpapers.ui.theme

import com.auroro.wallpapers.core.data.GlassQuality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Glass tiers must stay ordered by rendering cost and tint strength so the Settings row
 * remains an honest performance/looks dial (verified on JVM; the GPU path needs a device).
 */
class GlassTierTest {
    @Test
    fun tintStrengthIsOrderedByTier() {
        val full = glassTintAlpha(GlassQuality.FULL)
        val balanced = glassTintAlpha(GlassQuality.BALANCED)
        val reduced = glassTintAlpha(GlassQuality.REDUCED)
        org.junit.Assert.assertTrue(full < balanced)
        org.junit.Assert.assertTrue(balanced < reduced)
        assertEquals(1f, reduced)
    }

    @Test
    fun everyTierStaysWithinVisibleAlphaRange() {
        GlassQuality.entries.forEach { quality ->
            val alpha = glassTintAlpha(quality)
            org.junit.Assert.assertTrue("alpha for $quality", alpha in 0.05f..1f)
        }
    }
}
