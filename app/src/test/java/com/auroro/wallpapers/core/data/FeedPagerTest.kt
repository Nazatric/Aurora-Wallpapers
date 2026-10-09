package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.wallpaper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPagerTest {
    @Test fun providerPaginationIsIndependentAndContinuesAfterOneSourceFails() = runTest {
        val wallhaven = ScriptedProvider(WallpaperSource.WALLHAVEN).apply {
            pages[1] = ProviderPage(listOf(wallpaper(id = "w1")), PageCursor(2))
            pages[2] = ProviderPage(listOf(wallpaper(id = "w2")), null)
            failOnceAt = 2
        }
        val abyss = ScriptedProvider(WallpaperSource.ABYSS).apply {
            pages[1] = ProviderPage(listOf(wallpaper(WallpaperSource.ABYSS, "a1")), PageCursor(2))
            pages[2] = ProviderPage(listOf(wallpaper(WallpaperSource.ABYSS, "a2")), null)
        }
        val pager = FeedPager(listOf(wallhaven, abyss), FeedRequest(filter = com.auroro.wallpapers.core.model.WallpaperFilter(sort = SortOption.NEWEST)), perProviderTarget = 1, maxPagesPerLoad = 1)

        val first = pager.loadNext()
        assertEquals(setOf("wallhaven:w1", "abyss:a1"), first.items.map { it.key }.toSet())
        assertFalse(first.endReached)

        val second = pager.loadNext()
        assertEquals(listOf("abyss:a2"), second.items.map { it.key })
        assertTrue(second.hadFailure)
        assertEquals(1, wallhaven.calls.count { it.page == 2 }) // failed page was not advanced
        assertEquals(2, abyss.calls.size) // Abyss advanced independently
        assertTrue(second.statuses.any { it.source == WallpaperSource.WALLHAVEN && it.state is SourceState.Failed })

        val third = pager.loadNext()
        assertEquals(listOf("wallhaven:w2"), third.items.map { it.key })
        assertTrue(third.endReached)
        assertEquals(2, wallhaven.calls.count { it.page == 2 })
    }

    @Test fun skippedProviderDoesNotDiscardAnAvailableProvidersResults() = runTest {
        val working = ScriptedProvider(WallpaperSource.WALLHAVEN).apply {
            pages[1] = ProviderPage(listOf(wallpaper(id = "w1")), null)
        }
        val needsKey = ScriptedProvider(WallpaperSource.ABYSS).apply {
            availabilityValue = SourceAvailability.NeedsConfiguration("Add an API key", "https://wall.alphacoders.com/")
        }
        val result = FeedPager(listOf(working, needsKey), FeedRequest(), perProviderTarget = 4).loadNext()
        assertEquals(listOf("wallhaven:w1"), result.items.map { it.key })
        val skipped = result.statuses.first { it.source == WallpaperSource.ABYSS }.state as SourceState.Skipped
        assertTrue(skipped.needsConfiguration)
        assertEquals("Add an API key", skipped.reason)
        assertTrue(result.endReached)
    }

    @Test fun providerSpecificSortLimitationsAreVisibleInsteadOfSilentlyMisrepresented() = runTest {
        val abyss = ScriptedProvider(WallpaperSource.ABYSS).apply {
            capabilitiesValue = ProviderCapabilities(
                sortsWithoutQuery = setOf(SortOption.NEWEST),
                sortsWithQuery = setOf(SortOption.RELEVANCE),
            )
            unsupported = "Popular ranking isn't available here"
        }
        val result = FeedPager(listOf(abyss), FeedRequest(filter = com.auroro.wallpapers.core.model.WallpaperFilter(sort = SortOption.POPULAR))).loadNext()
        assertEquals(0, abyss.calls.size)
        assertEquals(abyss.unsupported, (result.statuses.single().state as SourceState.Skipped).reason)
    }

    @Test fun roundRobinMergePreservesEachProvidersOrder() {
        val a1 = wallpaper(id = "a1")
        val a2 = wallpaper(id = "a2")
        val b1 = wallpaper(WallpaperSource.ABYSS, "b1")
        assertEquals(listOf(a1, b1, a2), FeedPager.interleave(listOf(listOf(a1, a2), listOf(b1))))
    }

    private class ScriptedProvider(override val source: WallpaperSource) : WallpaperProvider {
        override var capabilities: ProviderCapabilities = ProviderCapabilities(
            sortsWithoutQuery = setOf(SortOption.RELEVANCE, SortOption.NEWEST, SortOption.POPULAR, SortOption.RANDOM),
            sortsWithQuery = setOf(SortOption.RELEVANCE, SortOption.NEWEST, SortOption.POPULAR, SortOption.RANDOM),
        )
        var availabilityValue: SourceAvailability = SourceAvailability.Available
        var unsupported: String? = null
        var failOnceAt: Int? = null
        val pages = mutableMapOf<Int, ProviderPage>()
        val calls = mutableListOf<PageCursor>()

        override suspend fun availability() = availabilityValue
        override fun unsupportedReason(request: FeedRequest) = unsupported
        override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
            calls += cursor
            if (failOnceAt == cursor.page) {
                failOnceAt = null
                throw ProviderException(ProviderErrorKind.NO_NETWORK, "temporary failure")
            }
            return pages[cursor.page] ?: ProviderPage(emptyList(), null)
        }
    }
}
