package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.wallpaper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPagerTest {
    @Test fun providerPaginationIsIndependentAndFailedPagesWaitForExplicitRetry() = runTest {
        val wallhaven = ScriptedProvider(WallpaperSource.WALLHAVEN).apply {
            pages[1] = ProviderPage(listOf(wallpaper(id = "w1")), PageCursor(2))
            pages[2] = ProviderPage(listOf(wallpaper(id = "w2")), null)
            failOnceAt = 2
        }
        val openverse = ScriptedProvider(WallpaperSource.OPENVERSE).apply {
            pages[1] = ProviderPage(listOf(wallpaper(WallpaperSource.OPENVERSE, "a1")), PageCursor(2))
            pages[2] = ProviderPage(listOf(wallpaper(WallpaperSource.OPENVERSE, "a2")), null)
        }
        val pager = FeedPager(
            listOf(wallhaven, openverse),
            FeedRequest(filter = WallpaperFilter(sort = SortOption.NEWEST)),
            perProviderTarget = 1,
            maxPagesPerLoad = 1,
        )

        val first = pager.loadNext()
        assertEquals(setOf("wallhaven:w1", "openverse:a1"), first.items.map { it.key }.toSet())
        assertFalse(first.endReached)

        val second = pager.loadNext()
        assertEquals(listOf("openverse:a2"), second.items.map { it.key })
        assertTrue(second.hadFailure)
        assertEquals(1, wallhaven.calls.count { it.page == 2 }) // failed page was not advanced or re-requested
        assertEquals(2, openverse.calls.size) // the other source advanced independently
        assertTrue(second.statuses.any { it.source == WallpaperSource.WALLHAVEN && it.state is SourceState.Failed })

        pager.retry()
        val third = pager.loadNext()
        assertEquals(listOf("wallhaven:w2"), third.items.map { it.key })
        assertTrue(third.endReached)
        assertEquals(2, wallhaven.calls.count { it.page == 2 })
    }

    @Test fun disabledSourceDoesNotDiscardResultsFromAnAvailableSource() = runTest {
        val working = ScriptedProvider(WallpaperSource.WALLHAVEN).apply {
            pages[1] = ProviderPage(listOf(wallpaper(id = "w1")), null)
        }
        val disabled = ScriptedProvider(WallpaperSource.OPENVERSE).apply {
            availabilityValue = SourceAvailability.DisabledByUser("Openverse is turned off")
        }
        val result = FeedPager(listOf(working, disabled), FeedRequest(), perProviderTarget = 4).loadNext()
        assertEquals(listOf("wallhaven:w1"), result.items.map { it.key })
        val skipped = result.statuses.first { it.source == WallpaperSource.OPENVERSE }.state as SourceState.Skipped
        assertEquals("Openverse is turned off", skipped.reason)
        assertTrue(result.endReached)
    }

    @Test fun unsupportedSortCanBeReportedWithoutSendingARequest() = runTest {
        val provider = ScriptedProvider(WallpaperSource.OPENVERSE).apply {
            capabilities = ProviderCapabilities(
                sortsWithoutQuery = setOf(SortOption.RELEVANCE),
                sortsWithQuery = setOf(SortOption.RELEVANCE),
            )
            unsupported = "Only relevance order is supported"
        }
        val result = FeedPager(listOf(provider), FeedRequest(filter = WallpaperFilter(sort = SortOption.POPULAR))).loadNext()
        assertTrue(provider.calls.isEmpty())
        assertEquals(provider.unsupported, (result.statuses.single().state as SourceState.Skipped).reason)
    }

    @Test fun roundRobinMergePreservesEachProvidersOrder() {
        val a1 = wallpaper(id = "a1")
        val a2 = wallpaper(id = "a2")
        val b1 = wallpaper(WallpaperSource.OPENVERSE, "b1")
        assertEquals(listOf(a1, b1, a2), FeedPager.interleave(listOf(listOf(a1, a2), listOf(b1))))
    }

    @Test fun sourceSpecificFiltersSelectOnlyTheirActualProvider() {
        val wallhaven = ScriptedProvider(WallpaperSource.WALLHAVEN)
        val openverse = ScriptedProvider(WallpaperSource.OPENVERSE)
        val filter = WallpaperFilter(openverseLicense = com.auroro.wallpapers.core.model.OpenverseLicense.CC0)
        val selected = WallpaperAggregator(listOf(wallhaven, openverse)).selected(FeedRequest(filter = filter))
        assertEquals(listOf(WallpaperSource.OPENVERSE), selected.map { it.source })
    }

    private class ScriptedProvider(override val source: WallpaperSource) : WallpaperProvider {
        var capabilities = ProviderCapabilities(
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
