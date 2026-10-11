package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.DiscoveryQuality
import com.auroro.wallpapers.core.model.DiscoveryRecipe
import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.LocalFilter
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.toProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** Identity carried from each internal query through validation and back to the active session. */
data class DiscoveryQueryIdentity(
    val sessionId: String,
    val recipeId: String,
    val source: WallpaperSource,
    val query: String,
    val generation: Long,
)

/** Monotonic request generation used to invalidate overlapping or obsolete discovery work. */
class DiscoveryGenerationGate {
    private val generation = AtomicLong(0L)

    fun begin(): Long = generation.incrementAndGet()

    fun current(): Long = generation.get()

    fun isCurrent(expectedGeneration: Long): Boolean = generation.get() == expectedGeneration

    fun isCurrent(expectedGeneration: Long, expectedSessionId: String, activeSessionId: String?): Boolean =
        isCurrent(expectedGeneration) && expectedSessionId == activeSessionId
}

data class ValidatedDiscoverySection(
    val identity: DiscoveryQueryIdentity,
    val items: List<Wallpaper>,
    val fromCache: Boolean,
    val staleCache: Boolean = false,
)

data class DiscoveryBuildProgress(
    val sessionId: String,
    val generation: Long,
    val sections: List<ValidatedDiscoverySection>,
    val items: List<Wallpaper>,
    val attemptedRecipes: Int,
    val targetRecipeCount: Int,
    val loading: Boolean,
)

data class DiscoveryBuildResult(
    val sessionId: String,
    val generation: Long,
    val sections: List<ValidatedDiscoverySection>,
    val items: List<Wallpaper>,
    val attemptedRecipes: Int,
    val emptyRecipes: Int,
    val skippedCoolingDown: Int,
    val errors: List<ProviderException>,
    val disabledReason: String? = null,
) {
    val staleCacheUsed: Boolean get() = sections.any { it.staleCache }
    val allFromCache: Boolean get() = sections.isNotEmpty() && sections.all { it.fromCache }
    val sourceStatus: SourceStatus
        get() = when {
            disabledReason != null -> SourceStatus(WallpaperSource.WALLHAVEN, SourceState.Skipped(disabledReason))
            errors.isNotEmpty() -> SourceStatus(WallpaperSource.WALLHAVEN, SourceState.Failed(errors.first()))
            items.isNotEmpty() -> SourceStatus(WallpaperSource.WALLHAVEN, SourceState.Ok(items.size))
            else -> SourceStatus(WallpaperSource.WALLHAVEN, SourceState.Exhausted)
        }

    /** Honest status for a partial or empty run; no provider count or content is synthesized. */
    fun message(): String? = when {
        disabledReason != null -> disabledReason
        errors.isNotEmpty() && items.isEmpty() -> errors.first().message ?: errors.first().kind.title
        errors.isNotEmpty() -> "Some searches could not finish (${errors.map { it.kind.title }.distinct().joinToString()}); showing ${items.size} validated wallpapers."
        items.isEmpty() && skippedCoolingDown > 0 -> "Discovery searches are cooling down after empty or failed requests. Try again later."
        items.isEmpty() -> "No suitable results matched this discovery mix. Try again later."
        staleCacheUsed -> "Showing previously validated wallpapers while Wallhaven refreshes this mix."
        allFromCache -> "Showing validated wallpapers from the on-device cache."
        else -> null
    }
}

/**
 * Builds a quality-checked, varied feed from provider-backed recipes. Each candidate is independently
 * validated; empty or mislabeled queries never become visible sections. Wallhaven remains the source
 * for curated discovery because its API has a documented SFW purity filter. Openverse remains available
 * through explicit source-labelled search and carries source-specific recipe query mappings, but is not
 * mixed into this feed where safety and query relevance cannot be asserted as strongly.
 */
class DiscoveryEngine(
    private val provider: WallpaperProvider,
    private val pools: DiscoveryPoolStore,
    private val perRecipeTimeoutMillis: Long = DEFAULT_QUERY_TIMEOUT_MILLIS,
    private val requestConcurrency: Int = 3,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(perRecipeTimeoutMillis > 0)
        require(requestConcurrency > 0)
    }

    suspend fun discover(
        session: DiscoverySession,
        generation: Long,
        preferences: AppSettings,
        recentlySeenKeys: Set<String> = emptySet(),
        onProgress: suspend (DiscoveryBuildProgress) -> Unit = {},
    ): DiscoveryBuildResult {
        val recipes = session.recipes.take(MAX_CANDIDATES)
        if (recipes.isEmpty()) {
            return DiscoveryBuildResult(session.sessionId, generation, emptyList(), emptyList(), 0, 0, 0, emptyList())
        }

        val availability = try {
            provider.availability()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val providerError = error.toProviderException(provider.source.displayName)
            return DiscoveryBuildResult(
                session.sessionId,
                generation,
                emptyList(),
                emptyList(),
                0,
                0,
                0,
                listOf(providerError),
            )
        }
        if (availability is SourceAvailability.DisabledByUser) {
            return DiscoveryBuildResult(
                sessionId = session.sessionId,
                generation = generation,
                sections = emptyList(),
                items = emptyList(),
                attemptedRecipes = 0,
                emptyRecipes = 0,
                skippedCoolingDown = 0,
                errors = emptyList(),
                disabledReason = availability.reason,
            )
        }

        val queryDetails = recipes.associateWith { recipe -> queryIdentity(session, recipe, generation) }
        val sections = linkedMapOf<String, ValidatedDiscoverySection>()
        val stalePools = mutableMapOf<String, CachedDiscoveryPool>()
        val freshCacheIds = mutableSetOf<String>()
        val filter = discoveryFilter(preferences)
        val now = clock()

        for (recipe in recipes) {
            val cached = try {
                pools.load(provider.source, recipe, now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (cached != null) {
                val valid = validate(cached.items, recipe, filter, preferences.aspectTolerance, recentlySeenKeys)
                if (valid.size >= MIN_ITEMS_PER_RECIPE) {
                    sections[recipe.id] = ValidatedDiscoverySection(
                        identity = queryDetails.getValue(recipe),
                        items = valid.take(MAX_ITEMS_PER_RECIPE),
                        fromCache = true,
                        staleCache = cached.stale,
                    )
                    if (cached.stale) stalePools[recipe.id] = cached else freshCacheIds += recipe.id
                }
            }
            if (sections.size >= TARGET_VALIDATED_RECIPES && sections.values.none { it.staleCache }) break
        }

        if (sections.size >= TARGET_VALIDATED_RECIPES && sections.values.none { it.staleCache }) {
            val result = result(session, generation, recipes, sections, 0, 0, 0, emptyList())
            onProgress(result.toProgress(loading = false))
            return result
        }

        var skippedCoolingDown = 0
        val toFetch = mutableListOf<DiscoveryRecipe>()
        for (recipe in recipes) {
            if (recipe.id in freshCacheIds) continue
            val coolingDown = try {
                pools.isCoolingDown(provider.source, recipe, now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (coolingDown) {
                skippedCoolingDown++
            } else {
                toFetch += recipe
            }
        }

        onProgress(progress(session, generation, recipes, sections, attempted = 0, loading = toFetch.isNotEmpty()))
        if (toFetch.isEmpty()) {
            val result = result(session, generation, recipes, sections, 0, 0, skippedCoolingDown, emptyList())
            onProgress(result.toProgress(loading = false))
            return result
        }

        val errors = mutableListOf<ProviderException>()
        var attempted = 0
        var emptyRecipes = 0
        val semaphore = Semaphore(requestConcurrency)
        for (batch in toFetch.chunked(requestConcurrency.coerceAtMost(3))) {
            val outcomes = coroutineScope {
                batch.map { recipe ->
                    async {
                        semaphore.withPermit {
                            fetchRecipe(
                                recipe = recipe,
                                identity = queryDetails.getValue(recipe),
                                request = request(queryDetails.getValue(recipe).query, filter, preferences.aspectTolerance),
                                seed = requestSeed(session.seed, recipe.id),
                                recentlySeenKeys = recentlySeenKeys,
                            )
                        }
                    }
                }.awaitAll()
            }
            attempted += outcomes.size
            outcomes.forEach { outcome ->
                if (outcome.error != null) errors += outcome.error
                if (outcome.empty) emptyRecipes++
                val section = outcome.section
                if (section != null) {
                    sections[section.identity.recipeId] = section
                } else if (outcome.error != null && section == null) {
                    // A stale, validated pool remains useful if the refresh fails; do not erase it.
                    stalePools[outcome.identity.recipeId]?.let { cached ->
                        sections[outcome.identity.recipeId] = ValidatedDiscoverySection(
                            identity = outcome.identity,
                            items = validate(cached.items, outcome.recipe, filter, preferences.aspectTolerance, recentlySeenKeys),
                            fromCache = true,
                            staleCache = true,
                        )
                    }
                }
            }
            onProgress(progress(session, generation, recipes, sections, attempted, loading = true))
            if (sections.size >= TARGET_VALIDATED_RECIPES) break
        }

        val result = result(session, generation, recipes, sections, attempted, emptyRecipes, skippedCoolingDown, errors)
        onProgress(result.toProgress(loading = false))
        return result
    }

    private suspend fun fetchRecipe(
        recipe: DiscoveryRecipe,
        identity: DiscoveryQueryIdentity,
        request: FeedRequest,
        seed: String,
        recentlySeenKeys: Set<String>,
    ): QueryOutcome {
        return try {
            val page = withTimeout(perRecipeTimeoutMillis) {
                provider.fetchPage(request, PageCursor(page = 1, seed = seed))
            }
            val valid = validate(page.items, recipe, request.filter, request.aspectTolerance, recentlySeenKeys)
            if (valid.size < MIN_ITEMS_PER_RECIPE) {
                pools.recordEmpty(provider.source, recipe, clock())
                QueryOutcome(identity, recipe, empty = true)
            } else {
                val sectionItems = valid.take(MAX_ITEMS_PER_RECIPE)
                pools.recordSuccess(provider.source, recipe, sectionItems, clock())
                QueryOutcome(
                    identity = identity,
                    recipe = recipe,
                    section = ValidatedDiscoverySection(identity, sectionItems, fromCache = false),
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            val error = ProviderException(
                ProviderErrorKind.TIMEOUT,
                "${provider.source.displayName} took too long to respond.",
                cause = timeout,
            )
            pools.recordFailure(provider.source, recipe, error, clock())
            QueryOutcome(identity, recipe, error = error)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val error = failure.toProviderException(provider.source.displayName)
            pools.recordFailure(provider.source, recipe, error, clock())
            QueryOutcome(identity, recipe, error = error)
        }
    }

    private fun queryIdentity(session: DiscoverySession, recipe: DiscoveryRecipe, generation: Long): DiscoveryQueryIdentity {
        val sourceQuery = recipe.sourceQueries[provider.source]
            ?: return DiscoveryQueryIdentity(session.sessionId, recipe.id, provider.source, "", generation)
        val phraseIndex = Math.floorMod(requestSeed(session.seed, recipe.id).hashCode(), sourceQuery.phrases.size)
        return DiscoveryQueryIdentity(
            sessionId = session.sessionId,
            recipeId = recipe.id,
            source = provider.source,
            query = sourceQuery.phraseAt(phraseIndex),
            generation = generation,
        )
    }

    private fun request(query: String, filter: WallpaperFilter, tolerance: Float) = FeedRequest(
        query = query,
        filter = filter,
        aspectTolerance = tolerance,
    )

    private fun discoveryFilter(preferences: AppSettings) = WallpaperFilter(
        sources = setOf(provider.source),
        orientation = Orientation.PORTRAIT,
        resolution = ResolutionFilter.Preset(ResolutionPreset.P1080),
        sort = SortOption.RANDOM,
        wallhavenCategories = if (provider.source == WallpaperSource.WALLHAVEN) {
            setOf(WallhavenCategory.GENERAL, WallhavenCategory.PEOPLE)
        } else emptySet(),
        reducePotentiallyExplicitContent = preferences.reducePotentiallyExplicitContent,
    )

    private fun validate(
        items: List<Wallpaper>,
        recipe: DiscoveryRecipe,
        filter: WallpaperFilter,
        tolerance: Float,
        recentlySeenKeys: Set<String>,
    ): List<Wallpaper> = DiscoveryQuality.rank(items, recipe, recentlySeenKeys)
        .filter { it.source == provider.source && recipe.isRelevant(it) && LocalFilter.matches(it, filter, tolerance) }

    private fun result(
        session: DiscoverySession,
        generation: Long,
        recipes: List<DiscoveryRecipe>,
        sections: Map<String, ValidatedDiscoverySection>,
        attempted: Int,
        empty: Int,
        skipped: Int,
        errors: List<ProviderException>,
    ) = DiscoveryBuildResult(
        sessionId = session.sessionId,
        generation = generation,
        sections = recipes.mapNotNull { sections[it.id] },
        items = mixItems(session, sections),
        attemptedRecipes = attempted,
        emptyRecipes = empty,
        skippedCoolingDown = skipped,
        errors = errors.distinctBy { it.kind },
    )

    private fun progress(
        session: DiscoverySession,
        generation: Long,
        recipes: List<DiscoveryRecipe>,
        sections: Map<String, ValidatedDiscoverySection>,
        attempted: Int,
        loading: Boolean,
    ) = DiscoveryBuildProgress(
        sessionId = session.sessionId,
        generation = generation,
        sections = recipes.mapNotNull { sections[it.id] },
        items = mixItems(session, sections),
        attemptedRecipes = attempted,
        targetRecipeCount = TARGET_VALIDATED_RECIPES,
        loading = loading,
    )

    private fun DiscoveryBuildResult.toProgress(loading: Boolean) = DiscoveryBuildProgress(
        sessionId = sessionId,
        generation = generation,
        sections = sections,
        items = items,
        attemptedRecipes = attemptedRecipes,
        targetRecipeCount = TARGET_VALIDATED_RECIPES,
        loading = loading,
    )

    private fun mixItems(
        session: DiscoverySession,
        sections: Map<String, ValidatedDiscoverySection>,
    ): List<Wallpaper> {
        val orderedSections = session.candidateRecipeIds.mapNotNull(sections::get)
        val output = ArrayList<Wallpaper>(orderedSections.size * ITEMS_PER_RECIPE_IN_MIX)
        for (index in 0 until ITEMS_PER_RECIPE_IN_MIX) {
            orderedSections.forEach { section -> section.items.getOrNull(index)?.let(output::add) }
        }
        return output.distinctBy { it.key }
    }

    private fun requestSeed(seed: Long, recipeId: String): String =
        Math.floorMod(seed xor (recipeId.hashCode().toLong() shl 32) xor recipeId.length.toLong(), Int.MAX_VALUE.toLong()).toString()

    private data class QueryOutcome(
        val identity: DiscoveryQueryIdentity,
        val recipe: DiscoveryRecipe,
        val section: ValidatedDiscoverySection? = null,
        val error: ProviderException? = null,
        val empty: Boolean = false,
    )

    companion object {
        const val TARGET_VALIDATED_RECIPES = 6
        const val MIN_ITEMS_PER_RECIPE = 2
        const val MAX_ITEMS_PER_RECIPE = 24
        const val MAX_CANDIDATES = 12
        const val ITEMS_PER_RECIPE_IN_MIX = 8
        const val DEFAULT_QUERY_TIMEOUT_MILLIS = 10_000L
    }
}
