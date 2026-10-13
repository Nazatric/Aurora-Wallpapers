package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.wallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceDiscoveryEngineTest {
    @Test fun failedSearchSourceDoesNotBlockUnknownDimensionViewOnlyApod() = runTest {
        val session = session("abstract-geometry")
        val wallhaven = FakeProvider(WallpaperSource.WALLHAVEN) { _, _ ->
            throw ProviderException(ProviderErrorKind.NO_NETWORK, "Wallhaven is unreachable.")
        }
        val apod = FakeProvider(WallpaperSource.NASA_APOD) { request, cursor ->
            assertEquals(setOf(WallpaperSource.NASA_APOD), request.filter.sources)
            assertEquals(SortOption.NEWEST, request.filter.sort)
            assertTrue(request.normalizedQuery.isEmpty())
            assertEquals(1, cursor.page)
            ProviderPage(listOf(image(WallpaperSource.NASA_APOD, "2026-10-12", 0, 0)), next = null)
        }
        val progress = mutableListOf<MultiSourceDiscoveryProgress>()
        val engine = MultiSourceDiscoveryEngine(
            searchEngines = listOf(DiscoveryEngine(wallhaven, MemoryDiscoveryPoolStore())),
            archiveProviders = listOf(apod),
        )

        val result = engine.discover(session, generation = 42L, preferences = AppSettings()) { progress += it }

        assertEquals(listOf(WallpaperSource.WALLHAVEN, WallpaperSource.NASA_APOD),
            result.statuses.map { it.source })
        assertTrue(result.statuses[0].state is SourceState.Failed)
        assertTrue(result.statuses[1].state is SourceState.Ok)
        assertEquals(listOf("2026-10-12"), result.items.map { it.sourceId })
        assertEquals(listOf(WallpaperSource.NASA_APOD), result.items.map { it.source })
        assertEquals(0, result.sections.size)
        val apodItem = result.items.single { it.source == WallpaperSource.NASA_APOD }
        assertEquals(0, apodItem.width)
        assertEquals(0, apodItem.height)
        assertFalse(apodItem.downloadAllowed)
        assertFalse(apodItem.setWallpaperAllowed)
        assertEquals(1, result.archiveResults.single().providerItemsReceived)
        assertTrue(result.message()!!.contains("Wallhaven"))
        assertTrue(result.message()!!.contains("showing 1 available images"))
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it.sessionId == session.sessionId && it.generation == 42L })
        assertFalse(progress.last().loading)
        assertEquals(result.items.map { it.key }, progress.last().items.map { it.key })
        assertEquals(1, wallhaven.requests.size)
        assertEquals(1, apod.requests.size)
    }

    @Test fun archiveTimeoutIsSourceLocalAndSearchResultsStillReturn() = runTest {
        val session = session("abstract-geometry")
        val wallhaven = FakeProvider(WallpaperSource.WALLHAVEN) { _, _ ->
            ProviderPage(
                items = listOf(image(WallpaperSource.WALLHAVEN, "wall-1"), image(WallpaperSource.WALLHAVEN, "wall-2")),
                next = null,
            )
        }
        val apod = FakeProvider(WallpaperSource.NASA_APOD) { _, _ ->
            delay(100)
            ProviderPage(emptyList(), next = null)
        }
        val engine = MultiSourceDiscoveryEngine(
            searchEngines = listOf(DiscoveryEngine(wallhaven, MemoryDiscoveryPoolStore())),
            archiveProviders = listOf(apod),
            archiveTimeoutMillis = 10,
        )

        val result = engine.discover(session, generation = 7L, preferences = AppSettings())

        assertEquals(2, result.items.size)
        assertTrue(result.items.all { it.source == WallpaperSource.WALLHAVEN })
        assertTrue(result.statuses.single { it.source == WallpaperSource.NASA_APOD }.state is SourceState.Failed)
        assertEquals(ProviderErrorKind.TIMEOUT, result.errors.single().kind)
        assertTrue(result.message()!!.contains("NASA APOD"))
    }

    @Test fun disabledSourcesAreReportedAndNeverQueried() = runTest {
        val disabled = SourceAvailability.DisabledByUser("Turned off for test")
        val providers = listOf(
            FakeProvider(WallpaperSource.WALLHAVEN, disabled) { _, _ -> error("Disabled source must not be queried") },
            FakeProvider(WallpaperSource.NASA_APOD, disabled) { _, _ -> error("Disabled source must not be queried") },
        )
        val engine = MultiSourceDiscoveryEngine(
            searchEngines = providers.take(1).map { DiscoveryEngine(it, MemoryDiscoveryPoolStore()) },
            archiveProviders = providers.drop(1),
        )

        val result = engine.discover(session("abstract-geometry"), generation = 3L, preferences = AppSettings())

        assertTrue(result.items.isEmpty())
        assertEquals(2, result.statuses.size)
        assertTrue(result.statuses.all { it.state is SourceState.Skipped })
        assertTrue(result.message()!!.contains("Turn on at least one catalogue"))
        assertTrue(providers.all { it.requests.isEmpty() })
    }

    private fun session(recipeId: String) = DiscoverySession(
        seed = 101L,
        createdAtMillis = 1_800_000_000_000L,
        hourBucket = 500_000L,
        refreshOrdinal = 0,
        candidateRecipeIds = listOf(recipeId),
    )

    private fun image(
        source: WallpaperSource,
        id: String,
        width: Int = 1_440,
        height: Int = 2_880,
        tags: List<WallpaperTag> = emptyList(),
    ): Wallpaper = wallpaper(source = source, id = id, width = width, height = height, tags = tags).copy(
        downloadAllowed = source != WallpaperSource.NASA_APOD,
        setWallpaperAllowed = source != WallpaperSource.NASA_APOD,
    )

    private class FakeProvider(
        override val source: WallpaperSource,
        private val availabilityState: SourceAvailability = SourceAvailability.Available,
        private val answer: suspend (FeedRequest, PageCursor) -> ProviderPage,
    ) : WallpaperProvider {
        override val capabilities = ProviderCapabilities(
            sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.NEWEST, SortOption.RELEVANCE),
            sortsWithQuery = setOf(SortOption.RELEVANCE),
        )
        val requests = mutableListOf<FeedRequest>()

        override suspend fun availability(): SourceAvailability = availabilityState

        override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
            requests += request
            return answer(request, cursor)
        }
    }

    private class MemoryDiscoveryPoolStore : DiscoveryPoolStore {
        override suspend fun load(source: WallpaperSource, recipe: com.auroro.wallpapers.core.model.DiscoveryRecipe, nowMillis: Long) = null
        override suspend fun isCoolingDown(source: WallpaperSource, recipe: com.auroro.wallpapers.core.model.DiscoveryRecipe, nowMillis: Long) = false
        override suspend fun recordSuccess(source: WallpaperSource, recipe: com.auroro.wallpapers.core.model.DiscoveryRecipe, items: List<Wallpaper>, nowMillis: Long) = Unit
        override suspend fun recordEmpty(source: WallpaperSource, recipe: com.auroro.wallpapers.core.model.DiscoveryRecipe, nowMillis: Long) = Unit
        override suspend fun recordFailure(source: WallpaperSource, recipe: com.auroro.wallpapers.core.model.DiscoveryRecipe, error: ProviderException, nowMillis: Long) = Unit
        override suspend fun recentlyUsedRecipeIds(nowMillis: Long, repeatAfterHours: Int) = emptySet<String>()
        override suspend fun recordSessionRecipeIds(recipeIds: List<String>, nowMillis: Long) = Unit
    }
}
