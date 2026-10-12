package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.DiscoveryQuality
import com.auroro.wallpapers.core.model.DiscoveryRecipe
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import kotlin.math.min

/** A small, locally validated metadata pool for one source/recipe pair. */
data class CachedDiscoveryPool(
    val items: List<Wallpaper>,
    val lastSuccessfulAtMillis: Long,
    val stale: Boolean,
)

/** Persistence boundary kept small so discovery scheduling can be tested without Android/DataStore. */
interface DiscoveryPoolStore {
    suspend fun load(source: WallpaperSource, recipe: DiscoveryRecipe, nowMillis: Long): CachedDiscoveryPool?
    suspend fun isCoolingDown(source: WallpaperSource, recipe: DiscoveryRecipe, nowMillis: Long): Boolean
    suspend fun recordSuccess(source: WallpaperSource, recipe: DiscoveryRecipe, items: List<Wallpaper>, nowMillis: Long)
    suspend fun recordEmpty(source: WallpaperSource, recipe: DiscoveryRecipe, nowMillis: Long)
    suspend fun recordFailure(source: WallpaperSource, recipe: DiscoveryRecipe, error: ProviderException, nowMillis: Long)
    suspend fun recentlyUsedRecipeIds(nowMillis: Long, repeatAfterHours: Int): Set<String>
    suspend fun recordSessionRecipeIds(recipeIds: List<String>, nowMillis: Long)
}

/** DataStore-backed availability/cooldown index plus bounded Room metadata; no image bytes are persisted here. */
class SettingsDiscoveryPoolStore(
    private val settings: SettingsRepository,
    private val wallpapers: WallpaperStore,
) : DiscoveryPoolStore {
    override suspend fun load(
        source: WallpaperSource,
        recipe: DiscoveryRecipe,
        nowMillis: Long,
    ): CachedDiscoveryPool? {
        val record = settings.discoveryAvailability(source.id, recipe.id) ?: return null
        val age = nowMillis - record.lastSuccessfulAtMillis
        if (record.lastSuccessfulAtMillis <= 0L || age < 0L || age > MAX_STALE_POOL_AGE_MILLIS) return null
        val candidates = wallpapers.getAll(record.wallpaperKeys)
            .filter { it.source == source && DiscoveryQuality.isUsable(it) }
            .distinctBy { it.key }
        if (candidates.size < DiscoveryEngine.MIN_ITEMS_PER_RECIPE) return null
        return CachedDiscoveryPool(
            items = candidates.take(DiscoveryEngine.MAX_ITEMS_PER_RECIPE),
            lastSuccessfulAtMillis = record.lastSuccessfulAtMillis,
            stale = age > FRESH_POOL_AGE_MILLIS,
        )
    }

    override suspend fun isCoolingDown(
        source: WallpaperSource,
        recipe: DiscoveryRecipe,
        nowMillis: Long,
    ): Boolean = settings.discoveryAvailability(source.id, recipe.id)
        ?.retryAfterMillis?.let { it > nowMillis } == true

    override suspend fun recordSuccess(
        source: WallpaperSource,
        recipe: DiscoveryRecipe,
        items: List<Wallpaper>,
        nowMillis: Long,
    ) {
        val usable = items.asSequence()
            .filter { it.source == source && DiscoveryQuality.isUsable(it) }
            .distinctBy { it.key }
            .take(DiscoveryEngine.MAX_ITEMS_PER_RECIPE)
            .toList()
        if (usable.size < DiscoveryEngine.MIN_ITEMS_PER_RECIPE) {
            recordEmpty(source, recipe, nowMillis)
            return
        }
        wallpapers.cacheForDiscovery(usable)
        settings.saveDiscoveryAvailability(
            DiscoveryAvailabilityRecord(
                sourceId = source.id,
                recipeId = recipe.id,
                lastSuccessfulAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                wallpaperKeys = usable.map { it.key },
            ),
        )
    }

    override suspend fun recordEmpty(source: WallpaperSource, recipe: DiscoveryRecipe, nowMillis: Long) {
        val previous = settings.discoveryAvailability(source.id, recipe.id)
        settings.saveDiscoveryAvailability(
            DiscoveryAvailabilityRecord(
                sourceId = source.id,
                recipeId = recipe.id,
                lastSuccessfulAtMillis = previous?.lastSuccessfulAtMillis ?: 0L,
                updatedAtMillis = nowMillis,
                retryAfterMillis = nowMillis + EMPTY_RETRY_MILLIS,
                consecutiveFailures = 0,
                lastFailureKind = EMPTY_MATCH_KIND,
                wallpaperKeys = previous?.wallpaperKeys.orEmpty(),
            ),
        )
    }

    override suspend fun recordFailure(
        source: WallpaperSource,
        recipe: DiscoveryRecipe,
        error: ProviderException,
        nowMillis: Long,
    ) {
        val previous = settings.discoveryAvailability(source.id, recipe.id)
        val failureCount = ((previous?.consecutiveFailures ?: 0) + 1).coerceAtMost(12)
        val exponential = BASE_FAILURE_RETRY_MILLIS * (1L shl (failureCount - 1).coerceAtMost(10))
        val providerDelay = if (error.kind == ProviderErrorKind.RATE_LIMITED) {
            (error.retryAfterSeconds ?: 60L).coerceIn(1L, MAX_FAILURE_RETRY_MILLIS / 1_000L) * 1_000L
        } else BASE_FAILURE_RETRY_MILLIS
        val retryDelay = min(maxOf(exponential, providerDelay), MAX_FAILURE_RETRY_MILLIS)
        settings.saveDiscoveryAvailability(
            DiscoveryAvailabilityRecord(
                sourceId = source.id,
                recipeId = recipe.id,
                lastSuccessfulAtMillis = previous?.lastSuccessfulAtMillis ?: 0L,
                updatedAtMillis = nowMillis,
                retryAfterMillis = nowMillis + retryDelay,
                consecutiveFailures = failureCount,
                lastFailureKind = error.kind.name,
                wallpaperKeys = previous?.wallpaperKeys.orEmpty(),
            ),
        )
    }

    override suspend fun recentlyUsedRecipeIds(nowMillis: Long, repeatAfterHours: Int): Set<String> =
        settings.recentlyUsedDiscoveryRecipeIds(nowMillis, repeatAfterHours)

    override suspend fun recordSessionRecipeIds(recipeIds: List<String>, nowMillis: Long) {
        settings.recordDiscoveryRecipeIds(recipeIds, nowMillis)
    }

    companion object {
        const val FRESH_POOL_AGE_MILLIS = 24L * 60L * 60L * 1_000L
        const val MAX_STALE_POOL_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        const val EMPTY_RETRY_MILLIS = 15L * 60L * 1_000L
        const val BASE_FAILURE_RETRY_MILLIS = 45L * 1_000L
        const val MAX_FAILURE_RETRY_MILLIS = 60L * 60L * 1_000L
        private const val EMPTY_MATCH_KIND = "NO_RELEVANT_MATCHES"
    }
}
