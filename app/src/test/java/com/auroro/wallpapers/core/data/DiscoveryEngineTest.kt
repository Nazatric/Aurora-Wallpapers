package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.DiscoveryCatalog
import com.auroro.wallpapers.core.model.DiscoveryRecipe
import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.DiscoveryRotation
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.wallpaper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryEngineTest {
    @Test fun overlappingSessionOrGenerationCannotPublishAnObsoleteResult() {
        val gate = DiscoveryGenerationGate()
        val firstGeneration = gate.begin()
        val staleIdentity = DiscoveryQueryIdentity(
            sessionId = "hourly-session-a",
            recipeId = "fractal-art",
            source = WallpaperSource.WALLHAVEN,
            query = "fractal art",
            generation = firstGeneration,
        )
        val nextGeneration = gate.begin()

        assertFalse(gate.isCurrent(staleIdentity.generation, staleIdentity.sessionId, "hourly-session-a"))
        assertFalse(gate.isCurrent(nextGeneration, "hourly-session-a", "hourly-session-b"))
        assertTrue(gate.isCurrent(nextGeneration, "hourly-session-b", "hourly-session-b"))
    }

    private val recipeIds = listOf(
        "fractal-art",
        "vaporwave",
        "botanical-illustration",
        "glitch-art",
        "brutalist-architecture",
        "isometric-worlds",
    )

    @Test fun requestBudgetIsSafeAcrossConcurrentCandidateRequests() = runTest {
        val budget = DiscoveryRequestBudget(15)

        val permitted = withContext(Dispatchers.Default) {
            coroutineScope {
                (0 until 100).map { async { budget.tryAcquire() } }.awaitAll().count { it }
            }
        }

        assertEquals(15, permitted)
    }

    @Test fun missingSearchMetadataDoesNotRejectAUsableResultOrTriggerDetailTraffic() = runTest {
        val provider = ScriptedDiscoveryProvider(
            answerPage = { _, _ -> ProviderPage(items = listOf(tagged("from-search")), next = null) },
            answerDetail = { sourceId -> tagged(sourceId, "fractal art") },
            supportsDetail = true,
            answer = { error("The cursor-aware response must be used") },
        )

        val result = DiscoveryEngine(provider, MemoryDiscoveryPoolStore()).discover(
            session(listOf("fractal-art")), generation = 21L, preferences = AppSettings(),
        )

        assertEquals(1, provider.requests.size)
        assertTrue(provider.detailRequests.isEmpty())
        assertTrue(result.items.single().tags.isEmpty())
        assertEquals(0, result.recipeRelevantItems)
        assertEquals(1, result.locallyValidatedItems)
        assertTrue(!result.requestBudgetReached)
    }

    @Test fun incompleteMetadataDoesNotSpendTheSessionBudgetOnUnneededDetailCalls() = runTest {
        val provider = ScriptedDiscoveryProvider(
            answerPage = { request, _ ->
                val recipe = recipeFor(request.query)
                ProviderPage(items = listOf(tagged("${recipe.id}-image")), next = null)
            },
            answerDetail = { sourceId -> tagged(sourceId, "mountain") },
            supportsDetail = true,
            answer = { error("The cursor-aware response must be used") },
        )

        val result = DiscoveryEngine(provider, MemoryDiscoveryPoolStore()).discover(
            session(recipeIds), generation = 22L, preferences = AppSettings(),
        )

        assertEquals(recipeIds.size, provider.requests.size)
        assertTrue(provider.detailRequests.isEmpty())
        assertFalse(result.requestBudgetReached)
        assertEquals(recipeIds.size, result.sections.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun candidateFallbackAndPagingStopAtTheSessionRequestBudget() = runTest {
        val provider = ScriptedDiscoveryProvider(
            answer = { error("The cursor-aware response must be used") },
            answerPage = { _, cursor ->
                ProviderPage(
                    items = emptyList(),
                    next = if (cursor.page == 1) PageCursor(page = 2, seed = cursor.seed) else null,
                )
            },
        )
        val store = MemoryDiscoveryPoolStore()

        val result = DiscoveryEngine(provider, store).discover(
            session(recipeIds), generation = 20L, preferences = AppSettings(),
        )

        assertEquals(DiscoveryEngine.MAX_REQUESTS_PER_SESSION, provider.requests.size)
        assertTrue(result.requestBudgetReached)
        assertTrue(result.message()!!.contains("request budget"))
        assertTrue(result.message()!!.contains("0 mapped, 0 passed the known-dimension/720 px floor, 0 had supporting metadata (advisory only), and 0 passed local checks"))
        assertEquals(0, result.providerItemsReceived)
        assertTrue(result.errors.isEmpty())
        assertEquals(3, store.empties.size)
    }

    @Test fun emptyQueryPhraseFallsBackToAnotherMappedPhraseAndPreservesItsIdentity() = runTest {
        val seenQueries = mutableListOf<String>()
        val provider = ScriptedDiscoveryProvider(
            answer = { error("The cursor-aware response must be used") },
            answerPage = { request, _ ->
                seenQueries += request.query
                if (seenQueries.size == 1) {
                    ProviderPage(items = emptyList(), next = null)
                } else {
                    ProviderPage(items = listOf(tagged("right-1", "fractal art"), tagged("right-2", "fractal art")), next = null)
                }
            },
        )

        val result = DiscoveryEngine(provider, MemoryDiscoveryPoolStore()).discover(
            session(listOf("fractal-art")), generation = 19L, preferences = AppSettings(),
        )

        assertEquals(2, seenQueries.size)
        assertTrue(seenQueries[0] != seenQueries[1])
        assertEquals(seenQueries.last(), result.sections.single().identity.query)
        assertEquals(2, result.items.size)
        assertEquals(2, result.providerItemsReceived)
        assertEquals(2, result.sourceUsableItems)
        assertEquals(2, result.recipeRelevantItems)
        assertEquals(2, result.locallyValidatedItems)
    }

    @Test fun usablePageIsAcceptedWithoutRecipeKeywordMatch() = runTest {
        val provider = ScriptedDiscoveryProvider(
            answer = { error("The cursor-aware response must be used") },
            answerPage = { _, cursor ->
                ProviderPage(
                    items = listOf(tagged("unrelated-1", "mountain"), tagged("unrelated-2", "mountain")),
                    next = PageCursor(page = cursor.page + 1, seed = cursor.seed),
                )
            },
        )

        val result = DiscoveryEngine(provider, MemoryDiscoveryPoolStore()).discover(
            session(listOf("fractal-art")), generation = 18L, preferences = AppSettings(),
        )

        assertEquals(listOf(1), provider.cursors.map { it.page })
        assertEquals(listOf("unrelated-1", "unrelated-2"), result.items.map { it.sourceId })
        assertEquals(0, result.recipeRelevantItems)
        assertEquals(2, result.locallyValidatedItems)
        assertEquals(2, result.sections.single().items.size)
    }

    @Test fun discoveryRequestsRespectTheConfiguredConcurrencyBound() = runTest {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val provider = ScriptedDiscoveryProvider { request ->
            val running = active.incrementAndGet()
            peak.updateAndGet { currentPeak -> maxOf(currentPeak, running) }
            try {
                delay(10)
                val recipe = recipeFor(request.query)
                val evidence = recipe.sourceQueries.getValue(WallpaperSource.WALLHAVEN).evidenceTerms.first()
                ProviderPage(
                    items = listOf(tagged("${recipe.id}-a", evidence), tagged("${recipe.id}-b", evidence)),
                    next = null,
                )
            } finally {
                active.decrementAndGet()
            }
        }

        val result = DiscoveryEngine(
            provider = provider,
            pools = MemoryDiscoveryPoolStore(),
            requestConcurrency = 2,
        ).discover(session(recipeIds), generation = 17L, preferences = AppSettings())

        assertEquals(2, peak.get())
        assertEquals(recipeIds.size, provider.requests.size)
        assertEquals(recipeIds.size, result.sections.size)
    }

    @Test fun recipeEvidenceIsAdvisoryAndOneFailedQueryDoesNotEraseOtherSections() = runTest {
        val session = session(recipeIds)
        val provider = ScriptedDiscoveryProvider(
            answerPage = { request, _ ->
                val recipe = recipeFor(request.query)
                if (recipe.id == "vaporwave") {
                    throw ProviderException(ProviderErrorKind.NO_NETWORK, "Simulated provider outage.")
                }
                val tag = when (recipe.id) {
                    "fractal-art" -> "fractal art"
                    "botanical-illustration" -> "botanical illustration"
                    else -> "unrelated mountain"
                }
                ProviderPage(
                    items = listOf(
                        tagged("${recipe.id}-1", tag),
                        tagged("${recipe.id}-2", tag),
                    ),
                    next = null,
                )
            },
            answer = { error("The cursor-aware response must be used") },
        )
        val store = MemoryDiscoveryPoolStore()
        val progress = mutableListOf<DiscoveryBuildProgress>()
        val result = DiscoveryEngine(provider, store, requestConcurrency = 1).discover(
            session = session,
            generation = 41L,
            preferences = AppSettings(),
            onProgress = { progress += it },
        )

        assertEquals(session.sessionId, result.sessionId)
        assertEquals(41L, result.generation)
        assertEquals(setOf("fractal-art", "botanical-illustration", "glitch-art", "brutalist-architecture", "isometric-worlds"),
            result.sections.map { it.identity.recipeId }.toSet())
        assertEquals(10, result.items.size)
        assertEquals(0, result.emptyRecipes)
        assertEquals(1, result.errors.size)
        assertEquals(4, result.recipeRelevantItems)
        assertEquals(10, result.locallyValidatedItems)
        assertEquals(6, provider.requests.size)
        assertTrue(result.items.any { it.tags.any { tag -> tag.name == "unrelated mountain" } })
        assertTrue(result.sections.all {
            it.identity.sessionId == session.sessionId &&
                it.identity.generation == 41L &&
                it.identity.source == WallpaperSource.WALLHAVEN &&
                it.identity.query.isNotBlank()
        })
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it.sessionId == session.sessionId && it.generation == 41L })
        provider.requests.forEach { request ->
            assertEquals(setOf(WallpaperSource.WALLHAVEN), request.filter.sources)
            assertEquals(Orientation.ANY, request.filter.orientation)
            assertEquals(ResolutionFilter.Any, request.filter.resolution)
            assertEquals(SortOption.RELEVANCE, request.filter.sort)
            assertTrue(request.filter.wallhavenCategories.isEmpty())
        }
    }

    @Test fun sixFreshValidatedPoolsAreReusedWithoutNetworkCalls() = runTest {
        val session = session(recipeIds)
        val store = MemoryDiscoveryPoolStore()
        session.recipes.forEach { recipe ->
            val evidence = recipe.sourceQueries.getValue(WallpaperSource.WALLHAVEN).evidenceTerms.first()
            store.pools[recipe.id] = CachedDiscoveryPool(
                items = listOf(tagged("${recipe.id}-cached-1", evidence), tagged("${recipe.id}-cached-2", evidence)),
                lastSuccessfulAtMillis = 1_800_000_000_000L,
                stale = false,
            )
        }
        val provider = ScriptedDiscoveryProvider { error("Fresh validated pools should suppress duplicate API requests") }

        val result = DiscoveryEngine(provider, store).discover(
            session = session,
            generation = 7L,
            preferences = AppSettings(),
        )

        assertEquals(0, provider.requests.size)
        assertEquals(0, result.attemptedRecipes)
        assertEquals(6, result.sections.size)
        assertTrue(result.allFromCache)
        assertEquals(session.candidateRecipeIds.toSet(), result.sections.map { it.identity.recipeId }.toSet())
    }

    @Test fun staleValidatedPoolSurvivesATimedOrNetworkRefreshFailure() = runTest {
        val id = "fractal-art"
        val session = session(listOf(id))
        val recipe = DiscoveryCatalog.find(id)!!
        val store = MemoryDiscoveryPoolStore().apply {
            pools[id] = CachedDiscoveryPool(
                items = listOf(tagged("cached-1", "fractal"), tagged("cached-2", "fractal")),
                lastSuccessfulAtMillis = 1_799_000_000_000L,
                stale = true,
            )
        }
        val provider = ScriptedDiscoveryProvider {
            throw ProviderException(ProviderErrorKind.NO_NETWORK, "Can't reach Wallhaven.")
        }

        val result = DiscoveryEngine(provider, store).discover(session, 99L, AppSettings())

        assertEquals(1, provider.requests.size)
        assertEquals(1, result.sections.size)
        assertTrue(result.staleCacheUsed)
        assertTrue(result.allFromCache)
        assertEquals(setOf("wallhaven:cached-1", "wallhaven:cached-2"), result.items.map { it.key }.toSet())
        assertEquals(ProviderErrorKind.NO_NETWORK, result.errors.single().kind)
        assertTrue(result.message()!!.contains("Some searches could not finish"))
        assertEquals(recipe.id, result.sections.single().identity.recipeId)
    }

    @Test fun sourceDisabledStopsBeforeCacheOrSearchCanLeakResults() = runTest {
        val provider = ScriptedDiscoveryProvider { error("Disabled providers must not be queried") }.apply {
            availabilityState = SourceAvailability.DisabledByUser("Wallhaven is turned off in Settings")
        }
        val result = DiscoveryEngine(provider, MemoryDiscoveryPoolStore()).discover(
            session(listOf("fractal-art")), 2L, AppSettings(),
        )

        assertEquals("Wallhaven is turned off in Settings", result.disabledReason)
        assertTrue(result.items.isEmpty())
        assertTrue(provider.requests.isEmpty())
        assertEquals("Wallhaven is turned off in Settings", (result.sourceStatus.state as SourceState.Skipped).reason)
    }

    @Test fun cancellingAnObsoleteDiscoveryRunDoesNotWriteFailureOrAcceptLateRows() = runTest {
        val started = CompletableDeferred<Unit>()
        val store = MemoryDiscoveryPoolStore()
        val provider = ScriptedDiscoveryProvider {
            started.complete(Unit)
            awaitCancellation()
        }
        val session = session(listOf("fractal-art"))
        val engine = DiscoveryEngine(provider, store)
        val job = launch {
            engine.discover(session, generation = 123L, preferences = AppSettings())
        }
        started.await()
        job.cancelAndJoin()

        assertTrue(provider.requests.isNotEmpty())
        assertTrue(store.failures.isEmpty())
        assertTrue(store.pools.isEmpty())
    }

    @Test fun validOrdinaryWallpapersAreNotDroppedByAGlobalContentKeywordLayer() = runTest {
        val session = session(listOf("fractal-art"))
        val ordinaryTags = listOf("fractal art", "nature", "person portrait", "swimwear", "classical figure art")
        val provider = ScriptedDiscoveryProvider {
            ProviderPage(
                items = listOf(
                    tagged("ordinary-portrait-nature", *ordinaryTags.toTypedArray()),
                    tagged("ordinary-figure-study", *ordinaryTags.toTypedArray()),
                ),
                next = null,
            )
        }

        val result = DiscoveryEngine(provider, MemoryDiscoveryPoolStore()).discover(session, 4L, AppSettings())

        assertEquals(
            setOf("wallhaven:ordinary-portrait-nature", "wallhaven:ordinary-figure-study"),
            result.items.map { it.key }.toSet(),
        )
        assertEquals(2, result.locallyValidatedItems)
    }

    private fun session(ids: List<String>) = DiscoverySession(
        seed = 0x5eedL,
        createdAtMillis = 1_800_000_000_000L,
        hourBucket = DiscoveryRotation.bucketAt(1_800_000_000_000L),
        refreshOrdinal = 3,
        candidateRecipeIds = ids,
    )

    private fun recipeFor(query: String): DiscoveryRecipe = DiscoveryCatalog.recipes.single { recipe ->
        query in recipe.sourceQueries.getValue(WallpaperSource.WALLHAVEN).phrases
    }

    private fun tagged(id: String, vararg tags: String): Wallpaper = wallpaper(
        id = id,
        tags = tags.map { WallpaperTag(null, it) },
    )

    private class ScriptedDiscoveryProvider(
        private val answerPage: (suspend (FeedRequest, PageCursor) -> ProviderPage)? = null,
        private val answerDetail: (suspend (String) -> Wallpaper?)? = null,
        private val supportsDetail: Boolean = false,
        private val answer: suspend (FeedRequest) -> ProviderPage,
    ) : WallpaperProvider {
        override val source = WallpaperSource.WALLHAVEN
        override val capabilities = ProviderCapabilities(
            sortsWithoutQuery = setOf(SortOption.RELEVANCE),
            sortsWithQuery = setOf(SortOption.RELEVANCE),
            supportsCategories = true,
            supportsDetail = supportsDetail,
        )
        var availabilityState: SourceAvailability = SourceAvailability.Available
        val requests = mutableListOf<FeedRequest>()
        val cursors = mutableListOf<PageCursor>()
        val detailRequests = mutableListOf<String>()

        override suspend fun availability(): SourceAvailability = availabilityState

        override suspend fun detail(sourceId: String): Wallpaper? {
            detailRequests += sourceId
            return answerDetail?.invoke(sourceId)
        }

        override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
            requests += request
            cursors += cursor
            return answerPage?.invoke(request, cursor) ?: answer(request)
        }
    }

    private class MemoryDiscoveryPoolStore : DiscoveryPoolStore {
        val pools = mutableMapOf<String, CachedDiscoveryPool>()
        val cooldowns = mutableSetOf<String>()
        val failures = mutableListOf<ProviderException>()
        val empties = mutableListOf<String>()

        override suspend fun load(
            source: WallpaperSource,
            recipe: DiscoveryRecipe,
            nowMillis: Long,
        ): CachedDiscoveryPool? = pools[recipe.id]

        override suspend fun isCoolingDown(
            source: WallpaperSource,
            recipe: DiscoveryRecipe,
            nowMillis: Long,
        ): Boolean = recipe.id in cooldowns

        override suspend fun recordSuccess(
            source: WallpaperSource,
            recipe: DiscoveryRecipe,
            items: List<Wallpaper>,
            nowMillis: Long,
        ) {
            pools[recipe.id] = CachedDiscoveryPool(items, nowMillis, stale = false)
            cooldowns -= recipe.id
        }

        override suspend fun recordEmpty(source: WallpaperSource, recipe: DiscoveryRecipe, nowMillis: Long) {
            empties += recipe.id
            cooldowns += recipe.id
        }

        override suspend fun recordFailure(
            source: WallpaperSource,
            recipe: DiscoveryRecipe,
            error: ProviderException,
            nowMillis: Long,
        ) {
            failures += error
            cooldowns += recipe.id
        }

        override suspend fun recentlyUsedRecipeIds(nowMillis: Long, repeatAfterHours: Int): Set<String> = emptySet()
        override suspend fun recordSessionRecipeIds(recipeIds: List<String>, nowMillis: Long) = Unit
    }
}
