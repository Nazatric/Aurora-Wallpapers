package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.artic.ArtInstituteApi
import com.auroro.wallpapers.core.network.artic.ArtworkDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Art Institute of Chicago, public-domain works only. Image URLs are deterministic IIIF paths built
 * from `image_id`: `/full/{w},/0/default.jpg` previews (server-side resize = fast loads) and
 * `/full/full/0/default.jpg` originals. Pixel dimensions are not reported by search; measured-aspect
 * cards resolve the true ratio once the preview decodes — the exact gap that retired the 1.2-era
 * integration is now covered by the pipeline instead of guessed metadata.
 */
class ArtInstituteProvider(
    private val api: ArtInstituteApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 20, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.ART_INSTITUTE

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RANDOM),
        sortsWithQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE, SortOption.RANDOM),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 3

    override fun unsupportedReason(request: FeedRequest): String? =
        if (request.filter.sort in capabilities.sortsWithoutQuery || request.normalizedQuery.isNotEmpty()) null
        else "The Art Institute serves relevance order for searches and its catalogue order otherwise."

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor)?.let { return it }
        val randomMode = request.filter.sort == SortOption.RANDOM
        val page = if (randomMode) RandomLane.page(cursor, RANDOM_PAGE_SPAN, 23L) else cursor.page
        val response = RateLimitedCall.run(limiter, source.displayName) {
            api.searchPublicDomain(
                query = request.normalizedQuery.takeIf { it.isNotEmpty() },
                limit = PAGE_SIZE,
                page = page,
            )
        }
        val items = response.data.mapNotNull(::toWallpaper)
        val totalPages = response.pagination?.totalPages ?: 1
        val next = if (cursor.page < totalPages && items.isNotEmpty()) PageCursor(cursor.page + 1, cursor.seed) else null
        ProviderPage(items, next).also { pageCache?.put(request, cursor, it) }
    }

    private fun toWallpaper(dto: ArtworkDto): Wallpaper? {
        val id = dto.id ?: return null
        val imageId = dto.imageId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val original = "${ArtInstituteApi.IIIF_BASE}$imageId/full/full/0/default.jpg"
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.ART_INSTITUTE, original)) return null
        val preview = "${ArtInstituteApi.IIIF_BASE}$imageId/full/$PREVIEW_WIDTH,/0/default.jpg"
        val artist = dto.artistDisplay?.trim()?.takeIf(String::isNotBlank)
        return Wallpaper(
            source = WallpaperSource.ART_INSTITUTE,
            sourceId = id.toString(),
            pageUrl = "https://www.artic.edu/artworks/$id",
            thumbUrl = preview,
            previewUrl = preview,
            originalUrl = original,
            width = 0,
            height = 0,
            mimeType = "image/jpeg",
            creatorName = artist,
            category = dto.departmentTitle?.trim()?.takeIf(String::isNotBlank),
            createdAt = dto.dateDisplay?.trim()?.takeIf(String::isNotBlank),
            title = dto.title?.trim()?.takeIf(String::isNotBlank),
            attribution = buildString {
                append("Art Institute of Chicago · Public Domain")
                artist?.let { append(" · ").append(it) }
            },
            licenseCode = "Public Domain",
            licenseVersion = null,
            licenseUrl = "https://www.artic.edu/terms-and-conditions/public-domain-artwork-images",
            downloadAllowed = true,
            setWallpaperAllowed = true,
        )
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val RANDOM_PAGE_SPAN = 120
        private const val PREVIEW_WIDTH = 1680
    }
}
