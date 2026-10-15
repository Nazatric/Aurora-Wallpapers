package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.met.MetApi
import com.auroro.wallpapers.core.network.met.MetObjectDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Met Open Access. Search returns object IDs; the provider resolves one page of IDs
 * concurrently and keeps only `isPublicDomain` records with a primary image. The Met has no resize
 * service, so previews are the full Open Access JPEGs — the card decode cap keeps that cheap.
 */
class MetMuseumProvider(
    private val api: MetApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 40, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.MET_MUSEUM

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT),
        sortsWithQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 2

    override fun unsupportedReason(request: FeedRequest): String? =
        if (request.filter.sort in capabilities.sortsWithoutQuery || request.normalizedQuery.isNotEmpty()) null
        else "The Met serves relevance order for searches and its catalogue order otherwise."

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor.page)?.let { return it }
        val ids = RateLimitedCall.run(limiter, source.displayName) {
            api.search(query = request.normalizedQuery.takeIf { it.isNotEmpty() })
        }.objectIDs.orEmpty()
        val pageIds = ids.drop((cursor.page - 1) * PAGE_SIZE).take(PAGE_SIZE)
        val items = coroutineScope {
            pageIds.map { id ->
                async {
                    RateLimitedCall.run(limiter, source.displayName) { api.obj(id) }
                        .let(::toWallpaper)
                }
            }.map { it.await() }
        }.filterNotNull()
        val next = if (ids.size > cursor.page * PAGE_SIZE && items.isNotEmpty()) PageCursor(cursor.page + 1) else null
        ProviderPage(items, next).also { pageCache?.put(request, cursor.page, it) }
    }

    private fun toWallpaper(dto: MetObjectDto): Wallpaper? {
        val id = dto.objectID ?: return null
        if (dto.isPublicDomain != true) return null
        val original = dto.primaryImage?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.MET_MUSEUM, original)) return null
        val artist = dto.artistDisplayName?.trim()?.takeIf(String::isNotBlank)
        return Wallpaper(
            source = WallpaperSource.MET_MUSEUM,
            sourceId = id.toString(),
            pageUrl = dto.objectUrl?.takeIf { it.startsWith("https://") }
                ?: "https://www.metmuseum.org/art/collection/search/$id",
            thumbUrl = dto.primaryImageSmall?.takeIf(String::isNotBlank) ?: original,
            previewUrl = original,
            originalUrl = original,
            width = 0,
            height = 0,
            mimeType = "image/jpeg",
            creatorName = artist,
            category = dto.classification?.trim()?.takeIf(String::isNotBlank),
            createdAt = dto.objectDate?.trim()?.takeIf(String::isNotBlank),
            title = dto.title?.trim()?.takeIf(String::isNotBlank),
            attribution = buildString {
                append("The Metropolitan Museum of Art · Public Domain")
                artist?.let { append(" · ").append(it) }
            },
            licenseCode = "Public Domain",
            licenseVersion = null,
            licenseUrl = "https://www.metmuseum.org/art/collection/open-access",
            downloadAllowed = true,
            setWallpaperAllowed = true,
        )
    }

    companion object {
        const val PAGE_SIZE = 16
    }
}
