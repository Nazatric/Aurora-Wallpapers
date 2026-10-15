package com.auroro.wallpapers.ui.components

import com.auroro.wallpapers.wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Slice-11 grid stability: the measured-aspect cache is what keeps lazy-grid cards static when
 * they are recycled back into view (no per-composition re-measure, no animated column reflow).
 */
@RunWith(RobolectricTestRunner::class)
class AspectCacheTest {

    @Test fun unknownKeysHaveNoCachedAspect() {
        assertNull(AspectCache.get("no-such-wallpaper-key"))
    }

    @Test fun decodedAspectsSurviveRecycling() {
        val key = "wallhaven:aspect-cache-test"
        AspectCache.put(key, 0.5625f)
        assertEquals(0.5625f, AspectCache.get(key), 1e-6f)
        // Re-put is idempotent — a recycled card never fights its own cached ratio.
        AspectCache.put(key, 0.5625f)
        assertEquals(0.5625f, AspectCache.get(key), 1e-6f)
    }

    @Test fun cacheIsBoundedAndEvictsOldestEntries() {
        repeat(700) { AspectCache.put("eviction-probe:$it", 1f + it * 0.001f) }
        assertNull("oldest entry should have been evicted", AspectCache.get("eviction-probe:0"))
        assertEquals(1.699f, AspectCache.get("eviction-probe:699"), 1e-6f)
    }

    @Test fun artAspectFallsBackToOneAndClampsExtremes() {
        assertEquals(0.5625f, artAspect(wallpaper(width = 2160, height = 3840)), 1e-6f)
        val extreme = wallpaper(width = 100, height = 10_000)
        assertEquals(0.4f, artAspect(extreme), 1e-6f)
    }
}
