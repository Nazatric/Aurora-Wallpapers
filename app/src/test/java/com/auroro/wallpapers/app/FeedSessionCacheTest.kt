package com.auroro.wallpapers.app

import com.auroro.wallpapers.core.data.FeedPager
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedSessionCacheTest {
    @Test fun homeAndSearchKeepIndependentRequestsResultsCursorsAndResumeFlags() {
        val homeRequest = FeedRequest(filter = WallpaperFilter(sources = setOf(WallpaperSource.WALLHAVEN)))
        val searchRequest = FeedRequest(query = "aurora", filter = WallpaperFilter(sources = setOf(WallpaperSource.OPENVERSE)))
        val homePager = FeedPager(emptyList(), homeRequest)
        val searchPager = FeedPager(emptyList(), searchRequest)
        val homeSession = FeedSession(
            state = FeedUiState(request = homeRequest, items = listOf(wallpaper(id = "home")), initialLoadFinished = true),
            pager = homePager,
            resumeOnReturn = false,
        )
        val searchSession = FeedSession(
            state = FeedUiState(request = searchRequest, items = listOf(wallpaper(source = WallpaperSource.OPENVERSE, id = "search")), initialLoadFinished = true),
            pager = searchPager,
            resumeOnReturn = true,
        )
        val cache = FeedSessionCache()

        cache.saveHome(HomeTab.FOR_YOU, homeSession)
        cache.saveHome(HomeTab.POPULAR, homeSession.copy(state = homeSession.state.copy(request = FeedRequest(query = "popular"))))
        cache.saveSearch(searchSession)

        val restoredSearch = cache.takeSearch()!!
        val restoredHome = cache.takeHome(HomeTab.FOR_YOU)!!

        assertEquals("aurora", restoredSearch.state.request.query)
        assertEquals("search", restoredSearch.state.items.single().sourceId)
        assertSame(searchPager, restoredSearch.pager)
        assertTrue(restoredSearch.resumeOnReturn)
        assertEquals("home", restoredHome.state.items.single().sourceId)
        assertSame(homePager, restoredHome.pager)
        assertFalse(restoredHome.resumeOnReturn)
        assertNull(cache.takeSearch())
        assertNull(cache.takeHome(HomeTab.FOR_YOU))
        assertEquals("popular", cache.takeHome(HomeTab.POPULAR)?.state?.request?.query)
    }
}
