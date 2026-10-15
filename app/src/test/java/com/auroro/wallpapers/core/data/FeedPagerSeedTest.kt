package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Slice-12 randomness regression: the random-lane seed must reach every provider cursor (so the
 * lane draws a fresh slice) while sorted lanes stay seedless (so their page cache still hits).
 * This is the guard against "the same wallpapers again and again".
 */
class FeedPagerSeedTest {

    private class CapturingProvider(override val source: WallpaperSource) : WallpaperProvider {
        val cursors = mutableListOf<PageCursor>()
        override val capabilities = ProviderCapabilities(sortsWithoutQuery = emptySet(), sortsWithQuery = emptySet())
        override suspend fun availability(): SourceAvailability = SourceAvailability.Available
        override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
            cursors += cursor
            return ProviderPage(emptyList(), null)
        }
    }

    @Test fun randomSeedReachesEveryProviderCursor() = runTest {
        val wallhaven = CapturingProvider(WallpaperSource.WALLHAVEN)
        val nasa = CapturingProvider(WallpaperSource.NASA_APOD)
        val pager = FeedPager(listOf(wallhaven, nasa), FeedRequest(), initialSeed = "slice12")
        pager.loadNext()
        assertEquals("slice12", wallhaven.cursors.single().seed)
        assertEquals("slice12", nasa.cursors.single().seed)
    }

    @Test fun sortedLanesStaySeedless() = runTest {
        val provider = CapturingProvider(WallpaperSource.WALLHAVEN)
        FeedPager(listOf(provider), FeedRequest()).loadNext()
        assertNull(provider.cursors.single().seed)
    }
}
