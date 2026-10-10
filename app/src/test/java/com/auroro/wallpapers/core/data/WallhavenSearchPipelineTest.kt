package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SearchSourceSelection
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.wallhaven.WallhavenApi
import com.auroro.wallpapers.core.network.wallhaven.WallhavenDetailResponse
import com.auroro.wallpapers.core.network.wallhaven.WallhavenMetaDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenSearchResponse
import com.auroro.wallpapers.core.network.wallhaven.WallhavenThumbsDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenWallpaperDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallhavenSearchPipelineTest {
    @Test fun selectingWallhavenFetchesAndMapsVisibleResultsForAnEmptyQuery() = runTest {
        val api = RecordingWallhavenApi(
            WallhavenSearchResponse(
                data = listOf(wallhavenRow()),
                meta = WallhavenMetaDto(currentPage = 1, lastPage = 3, total = 121),
            ),
        )
        val filter = SearchSourceSelection.select(
            current = WallpaperFilter.Default,
            requested = setOf(WallpaperSource.WALLHAVEN),
            query = "",
        )
        val result = FeedPager(
            providers = listOf(WallhavenProvider(api)),
            request = FeedRequest(filter = filter),
            perProviderTarget = 1,
            maxPagesPerLoad = 1,
        ).loadNext()

        assertEquals("date_added", api.searchParams?.get("sorting"))
        assertFalse("q" in api.searchParams.orEmpty())
        assertEquals("100", api.searchParams?.get("purity"))
        assertEquals(1, result.items.size)
        assertEquals("abc123", result.items.single().sourceId)
        assertEquals(3840, result.items.single().width)
        assertEquals(2160, result.items.single().height)
        assertEquals("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg", result.items.single().originalUrl)
        assertEquals(SourceState.Ok(1), result.statuses.single().state)
        assertFalse(result.endReached)
    }

    @Test fun providerSortCapabilitiesMatchBlankAndKeywordEndpointBehavior() {
        val provider = WallhavenProvider(RecordingWallhavenApi(WallhavenSearchResponse()))

        assertFalse(SortOption.RELEVANCE in provider.capabilities.sortsWithoutQuery)
        assertTrue(SortOption.RELEVANCE in provider.capabilities.sortsWithQuery)
    }

    @Test fun blankRelevanceNeverSendsTheKnownEmptyWallhavenSortButKeywordRelevanceIsPreserved() {
        val blank = WallhavenQuery.build(
            FeedRequest(filter = WallpaperFilter(sort = SortOption.RELEVANCE)),
            PageCursor(),
        )
        val keyword = WallhavenQuery.build(
            FeedRequest(
                query = "northern lights",
                filter = SearchSourceSelection.select(
                    WallpaperFilter.Default,
                    setOf(WallpaperSource.WALLHAVEN),
                    "northern lights",
                ),
            ),
            PageCursor(),
        )

        assertEquals("date_added", blank["sorting"])
        assertFalse("relevance" == blank["sorting"])
        assertEquals("relevance", keyword["sorting"])
        assertEquals("northern lights", keyword["q"])
    }

    @Test fun wallhavenPaginationUsesReturnedPageMetadataAndPreservesProviderSeed() = runTest {
        val api = RecordingWallhavenApi(
            WallhavenSearchResponse(
                data = listOf(wallhavenRow()),
                meta = WallhavenMetaDto(currentPage = 4, lastPage = 6, total = 120, seed = "stable-seed"),
            ),
        )
        val provider = WallhavenProvider(api)

        val page = provider.fetchPage(FeedRequest(filter = WallpaperFilter(sort = SortOption.NEWEST)), PageCursor(page = 4))

        assertEquals(PageCursor(page = 5, seed = "stable-seed"), page.next)
        assertEquals("4", api.searchParams?.get("page"))
        assertEquals(1, page.items.size)
        assertNull(page.items.single().licenseCode) // Wallhaven does not claim a Creative Commons licence.
    }

    private fun wallhavenRow() = WallhavenWallpaperDto(
        id = "abc123",
        url = "https://wallhaven.cc/w/abc123",
        purity = "sfw",
        category = "general",
        dimensionX = 3840,
        dimensionY = 2160,
        path = "https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg",
        thumbs = WallhavenThumbsDto(
            large = "https://th.wallhaven.cc/lg/ab/abc123.jpg",
            original = "https://th.wallhaven.cc/orig/ab/abc123.jpg",
        ),
    )

    private inner class RecordingWallhavenApi(
        private val response: WallhavenSearchResponse,
    ) : WallhavenApi {
        var searchParams: Map<String, String>? = null

        override suspend fun search(params: Map<String, String>): WallhavenSearchResponse {
            searchParams = params.toMap()
            return response
        }

        override suspend fun wallpaper(id: String): WallhavenDetailResponse =
            WallhavenDetailResponse(wallhavenRow())
    }
}
