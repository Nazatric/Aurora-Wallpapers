package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.cleveland.ClevelandApi
import com.auroro.wallpapers.core.network.cleveland.ClevelandArtwork
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cleveland Museum of Art Open Access: complete artwork records, all CC0, in one call per page. */
class ClevelandProvider(
    private val api: ClevelandApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 20, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.CLEVELAND_ART

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RANDOM),
        sortsWithQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE, SortOption.RANDOM),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 2

    override fun unsupportedReason(request: FeedRequest): String? =
        if (request.filter.sort in capabilities.sortsWithoutQuery || request.normalizedQuery.isNotEmpty()) null
        else "The Cleveland Open Access API serves relevance order for searches and its catalogue order otherwise."

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor.page)?.let { return it }
        val randomMode = request.filter.sort == SortOption.RANDOM
        val skip = if (randomMode) {
            RandomLane.offset(cursor, RANDOM_SKIP_SPAN, 37L, PAGE_SIZE)
        } else {
            (cursor.page - 1) * PAGE_SIZE
        }
        val response = RateLimitedCall.run(limiter, source.displayName) {
            api.artworks(
                query = request.normalizedQuery.takeIf { it.isNotEmpty() },
                limit = PAGE_SIZE,
                skip = skip,
            )
        }
        val items = response.data.mapNotNull(::toWallpaper)
        val total = response.info?.total ?: 0
        val next = if (cursor.page * PAGE_SIZE < total && items.isNotEmpty()) PageCursor(cursor.page + 1, cursor.seed) else null
        ProviderPage(items, next).also { pageCache?.put(request, cursor.page, it) }
    }

    private fun toWallpaper(dto: ClevelandArtwork): Wallpaper? {
        val id = dto.id ?: return null
        val image = dto.images.firstOrNull() ?: return null
        val original = image.url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.CLEVELAND_ART, original)) return null
        if (!dto.shareLicense.equals("cc0", ignoreCase = true)) return null
        val creator = dto.creators.firstOrNull()?.description?.trim()?.takeIf(String::isNotBlank)
        return Wallpaper(
            source = WallpaperSource.CLEVELAND_ART,
            sourceId = id.toString(),
            pageUrl = dto.url?.takeIf { it.startsWith("https://") }
                ?: "https://www.clevelandart.org/art/$id",
            thumbUrl = original,
            previewUrl = original,
            originalUrl = original,
            width = image.width?.coerceAtLeast(0) ?: 0,
            height = image.height?.coerceAtLeast(0) ?: 0,
            mimeType = "image/jpeg",
            creatorName = creator,
            category = "Open Access",
            createdAt = dto.creationDate?.trim()?.takeIf(String::isNotBlank),
            title = dto.title?.trim()?.takeIf(String::isNotBlank),
            attribution = buildString {
                append("Cleveland Museum of Art · CC0")
                creator?.let { append(" · ").append(it) }
            },
            licenseCode = "CC0",
            licenseVersion = "1.0",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
            downloadAllowed = true,
            setWallpaperAllowed = true,
        )
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val RANDOM_SKIP_SPAN = 100
    }
}
