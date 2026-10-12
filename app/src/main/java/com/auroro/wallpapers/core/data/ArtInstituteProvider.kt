package com.auroro.wallpapers.core.data

import android.content.SharedPreferences
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.artic.ArtInstituteApi
import com.auroro.wallpapers.core.network.artic.ArtInstituteArtworkDto
import com.auroro.wallpapers.core.network.artic.ArtInstituteIiifApi
import com.auroro.wallpapers.core.network.artic.ArtInstituteSearchResponse
import com.auroro.wallpapers.core.network.artic.IiifImageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Art Institute of Chicago search restricted at the API boundary to public-domain Open Access works. */
class ArtInstituteProvider(
    private val api: ArtInstituteApi,
    private val iiifApi: ArtInstituteIiifApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 24, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.ART_INSTITUTE

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT),
        sortsWithQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()
    private val imageInfoSemaphore = Semaphore(MAX_PARALLEL_IMAGE_INFO)

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    /** Each search page is small and is expanded using the official IIIF info.json for exact pixels. */
    override fun maxPagesPerLoad(request: FeedRequest): Int = 1

    override fun unsupportedReason(request: FeedRequest): String? = when {
        request.filter.sort !in capabilities.sortsWithQuery && request.normalizedQuery.isNotEmpty() ->
            "The Art Institute search supports relevance order only for keyword searches."
        request.normalizedQuery.isEmpty() && request.filter.sort !in capabilities.sortsWithoutQuery ->
            "The Art Institute catalogue provides its own order; popular, random and newest sorts are unavailable."
        else -> null
    }

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor.page)?.let { return it }
        val response = RateLimitedCall.run(limiter, source.displayName) {
            api.search(buildParams(request, cursor.page))
        }
        val works = response.data.filter { it.id > 0 && it.isPublicDomain && !it.imageId.isNullOrBlank() }
        val items = coroutineScope {
            works.map { artwork ->
                async {
                    imageInfoSemaphore.withPermit {
                        val info = readImageInfo(artwork.imageId.orEmpty())
                        toWallpaper(artwork, info)
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val currentPage = response.pagination.currentPage.takeIf { it > 0 } ?: cursor.page
        val totalPages = response.pagination.totalPages.coerceAtLeast(currentPage)
        val next = if (items.isNotEmpty() && currentPage < totalPages) PageCursor(currentPage + 1) else null
        ProviderPage(items, next).also { pageCache?.put(request, cursor.page, it) }
    }

    private suspend fun readImageInfo(imageId: String): IiifImageInfo? {
        if (!IMAGE_ID.matches(imageId)) return null
        return try {
            RateLimitedCall.run(limiter, source.displayName) { iiifApi.imageInfo(imageId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Preserve the artwork when IIIF dimensions are temporarily unavailable; geometry filters
            // will treat those dimensions as unknown instead of guessing from a thumbnail.
            null
        }
    }

    private fun toWallpaper(dto: ArtInstituteArtworkDto, info: IiifImageInfo?): Wallpaper? {
        val id = dto.id.takeIf { it > 0 }?.toString() ?: return null
        val imageId = dto.imageId?.takeIf(IMAGE_ID::matches) ?: return null
        if (!dto.isPublicDomain) return null

        val page = "https://www.artic.edu/artworks/$id"
        val thumbnail = iiifUrl(imageId, THUMB_EDGE.toString())
        val preview = iiifUrl(imageId, PREVIEW_EDGE.toString())
        val original = iiifUrl(imageId, "max")
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.ART_INSTITUTE, original)) return null
        if (!UrlPolicy.isAllowedForBrowsing(page)) return null

        val width = info?.width?.takeIf { it in 1..MAX_PIXEL_EDGE } ?: 0
        val height = info?.height?.takeIf { it in 1..MAX_PIXEL_EDGE } ?: 0
        val title = dto.title?.trim()?.takeIf(String::isNotBlank)
        val artist = (dto.artistTitle ?: dto.artistDisplay)?.trim()?.takeIf(String::isNotBlank)
        val credit = listOfNotNull(title, artist, "Art Institute of Chicago").joinToString(" · ")
        val tags = dto.subjectTitles.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_SUBJECT_TAGS)
            .map { WallpaperTag(id = null, name = it) }
            .toList()

        return Wallpaper(
            source = WallpaperSource.ART_INSTITUTE,
            sourceId = id,
            pageUrl = page,
            thumbUrl = thumbnail,
            previewUrl = preview,
            originalUrl = original,
            width = width,
            height = height,
            mimeType = "image/jpeg",
            creatorName = artist,
            category = dto.artworkTypeTitle?.trim()?.takeIf(String::isNotBlank),
            tags = tags,
            createdAt = dto.dateDisplay?.trim()?.takeIf(String::isNotBlank),
            originSourceUrl = page,
            title = title,
            attribution = credit,
            licenseCode = "CC0-1.0",
            licenseVersion = "1.0",
            licenseUrl = CC0_URL,
            providerName = "Art Institute of Chicago",
            catalogSource = "Open Access",
            downloadAllowed = true,
            setWallpaperAllowed = true,
        )
    }

    private fun iiifUrl(imageId: String, maxWidth: String): String =
        "${ArtInstituteApi.IMAGE_BASE_URL}iiif/2/$imageId/full/$maxWidth,/0/default.jpg"

    companion object {
        const val PAGE_SIZE = 12
        const val CC0_URL = "https://creativecommons.org/publicdomain/zero/1.0/"
        private const val THUMB_EDGE = 960
        private const val PREVIEW_EDGE = 2_048
        private const val MAX_PARALLEL_IMAGE_INFO = 3
        private const val MAX_PIXEL_EDGE = 100_000
        private const val MAX_SUBJECT_TAGS = 16
        private val IMAGE_ID = Regex("[0-9a-fA-F-]{16,64}")
        private val FIELDS = listOf(
            "id", "title", "image_id", "is_public_domain", "artist_title", "artist_display",
            "artist_id", "date_display", "artwork_type_title", "classification_title", "style_title",
            "subject_titles", "main_reference_number", "medium_display", "credit_line", "dimensions", "thumbnail",
        ).joinToString(",")

        /** The official API's `params` form accepts Elasticsearch Query DSL. */
        fun buildParams(request: FeedRequest, page: Int): String {
            val must = buildJsonArray {
                add(buildJsonObject { put("term", buildJsonObject { put("is_public_domain", true) }) })
                add(buildJsonObject { put("exists", buildJsonObject { put("field", "image_id") }) })
            }
            val root = buildJsonObject {
                request.normalizedQuery.takeIf(String::isNotBlank)?.let { put("q", it) }
                put("query", buildJsonObject { put("bool", buildJsonObject { put("must", must) }) })
                put("limit", PAGE_SIZE)
                put("page", page.coerceAtLeast(1))
                put("fields", FIELDS)
            }
            return root.toString()
        }
    }
}
