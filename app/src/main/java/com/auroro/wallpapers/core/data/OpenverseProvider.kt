package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.openverse.OpenverseApi
import com.auroro.wallpapers.core.network.openverse.OpenverseImageDto
import com.auroro.wallpapers.core.network.toProviderException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.util.Locale

/**
 * Openverse's public Images API v1. Search works anonymously; this app deliberately does not ask for
 * client credentials. A persistent burst window and the API's own search-quota headers pace requests
 * without hard-coding an undocumented daily allowance.
 */
class OpenverseProvider(
    private val api: OpenverseApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 20, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
) : WallpaperProvider {
    override val source = WallpaperSource.OPENVERSE

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.RELEVANCE),
        sortsWithQuery = setOf(SortOption.RELEVANCE),
        supportsCategories = true,
        supportsDetail = false, // Search responses already contain attribution and licence metadata.
        supportsRelated = false, // Avoid background calls that consume the small anonymous quota.
        serverSideResolution = true,
        serverSideAspect = true,
    )

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 1

    override fun unsupportedReason(request: FeedRequest): String? {
        if (request.normalizedQuery.isNotEmpty() && !request.filter.openverseTag.isNullOrBlank()) {
            return "Openverse keyword and tag-only searches are separate. Clear the keyword to filter by an Openverse tag."
        }
        return null
    }

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
        val httpResponse = try {
            RateLimitedCall.run(limiter, source.displayName) {
                api.images(OpenverseQuery.build(request, cursor))
            }
        } catch (error: ProviderException) {
            val headers = (error.cause as? HttpException)?.response()?.headers()
            if (headers != null) limiter.observeProviderHeaders(headers)
            if (error.kind == ProviderErrorKind.RATE_LIMITED && error.retryAfterSeconds != null && error.retryAfterSeconds > 0) {
                limiter.pauseFor(error.retryAfterSeconds.coerceAtMost(604_800L) * 1_000L)
            }
            throw error
        }
        limiter.observeProviderHeaders(httpResponse.headers())
        if (!httpResponse.isSuccessful) {
            val error = HttpException(httpResponse).toProviderException(source.displayName)
            if (error.kind == ProviderErrorKind.RATE_LIMITED && error.retryAfterSeconds != null && error.retryAfterSeconds > 0) {
                limiter.pauseFor(error.retryAfterSeconds.coerceAtMost(604_800L) * 1_000L)
            }
            throw error
        }
        val response = httpResponse.body()
            ?: throw ProviderException(ProviderErrorKind.BAD_RESPONSE, "Openverse returned an empty image search response.")

        val items = response.results.mapNotNull(OpenverseMapper::toWallpaper)
        val lastPage = minOf(response.pageCount, OpenverseQuery.MAX_ANONYMOUS_PAGES)
        val next = if (response.results.isNotEmpty() && cursor.page < lastPage) {
            PageCursor(page = cursor.page + 1)
        } else {
            null
        }
        return ProviderPage(items, next)
    }
}

/** Builds documented, stable Openverse image-search parameters; pixel thresholds are still checked locally. */
object OpenverseQuery {
    const val PAGE_SIZE = 20
    const val MAX_ANONYMOUS_PAGES = 20
    const val ANONYMOUS_MIN_SHORT_EDGE = 720
    private const val OPENVERSE_LARGE_PIXELS = 1_440_000L

    fun build(request: FeedRequest, cursor: PageCursor): Map<String, String> {
        val filter = request.filter
        val params = linkedMapOf(
            "page" to cursor.page.coerceIn(1, MAX_ANONYMOUS_PAGES).toString(),
            "page_size" to PAGE_SIZE.toString(),
            "filter_dead" to "true",
            "mature" to "false",
            "extension" to "jpg,jpeg,png,webp",
            // The least restrictive Openverse band that can contain a 720 px short-edge image.
            "size" to "medium,large",
        )
        val query = request.normalizedQuery.take(200)
        if (query.isNotEmpty()) params["q"] = query
        filter.openverseTag?.trim()?.takeIf(String::isNotEmpty)?.take(200)?.let { params["tags"] = it }
        filter.openverseCategory?.let { params["category"] = it.apiValue }
        filter.openverseLicense?.let { license ->
            params[if (license.isGroup) "license_type" else "license"] = license.apiValue
        }
        aspectBucket(request)?.let { params["aspect_ratio"] = it }
        if (requiresLargeBand(filter.resolution)) params["size"] = "large"
        return params
    }

    /**
     * Openverse's indexed buckets classify height > width as tall and width > height as wide;
     * square is exact equality. Request a bucket only when every locally accepted ratio has that
     * orientation. A near-square ratio spans both buckets, unless an explicit orientation narrows it.
     */
    private fun aspectBucket(request: FeedRequest): String? {
        val target = request.filter.aspect.targetRatio
        if (target != null) {
            val tolerance = request.aspectTolerance.coerceIn(0f, 0.25f)
            when {
                target * (1 + tolerance) < 1f -> return "tall"
                target * (1 - tolerance) > 1f -> return "wide"
            }
        }
        return when (request.filter.orientation) {
            Orientation.PORTRAIT -> "tall"
            Orientation.LANDSCAPE -> "wide"
            // The API's square bucket is exact equality, while Auroro's square band includes near-squares.
            Orientation.SQUARE, Orientation.ANY -> null
        }
    }

    /**
     * Openverse indexes broad pixel-area bands: small < 307,200 px, medium < 1,440,000 px,
     * large >= 1,440,000 px. The server filter is only a safe pre-filter; edge dimensions are verified locally.
     */
    private fun requiresLargeBand(resolution: ResolutionFilter): Boolean {
        val minimumPixels = when (resolution) {
            ResolutionFilter.Any -> ANONYMOUS_MIN_SHORT_EDGE.toLong() * ANONYMOUS_MIN_SHORT_EDGE
            is ResolutionFilter.Preset -> resolution.preset.shortEdge.toLong() * resolution.preset.longEdge
            is ResolutionFilter.Custom -> resolution.shortEdge.toLong() * resolution.longEdge
        }
        return minimumPixels >= OPENVERSE_LARGE_PIXELS
    }
}

/** Validates untrusted search rows and retains the original media URL plus source/creator/licence credit. */
object OpenverseMapper {
    private val safelyUsableLicenses = setOf(
        "cc0", "pdm", "by", "by-sa", "by-nd", "by-nc", "by-nc-sa", "by-nc-nd",
    )
    private val noDerivatives = setOf("by-nd", "by-nc-nd")

    fun toWallpaper(dto: OpenverseImageDto): Wallpaper? {
        val width = dto.width ?: return null
        val height = dto.height ?: return null
        if (dto.id.isBlank() || width < OpenverseQuery.ANONYMOUS_MIN_SHORT_EDGE || height < OpenverseQuery.ANONYMOUS_MIN_SHORT_EDGE) return null
        if (width > 32_000 || height > 32_000) return null
        val ratio = width.toFloat() / height
        if (!ratio.isFinite() || ratio !in 0.05f..20f) return null
        if (dto.mature != false) return null

        val original = dto.url.trim()
        val landing = dto.foreignLandingUrl.trim()
        if (!UrlPolicy.isAllowedOpenverseAsset(original) || !UrlPolicy.isAllowedForBrowsing(landing)) return null

        val license = dto.license.trim().lowercase(Locale.US).substringBeforeLast('/')
        val knownLicense = license in safelyUsableLicenses
        val creatorUrl = dto.creatorUrl?.trim()?.takeIf(UrlPolicy::isAllowedForBrowsing)
        val licenseUrl = dto.licenseUrl?.trim()?.takeIf(UrlPolicy::isAllowedForBrowsing)
        val tags = dto.tags.asSequence()
            .map { it.name.trim() }
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
            .map { WallpaperTag(null, it) }
            .toList()
        val mime = mimeType(dto.filetype) ?: mimeTypeFromUrl(original)
        val supportedImage = mime != null && mime in WALLPAPER_MIME_TYPES
        val cardThumbnail = dto.thumbnail?.trim()?.takeIf {
            knownLicense && license !in noDerivatives && UrlPolicy.isAllowedOpenverseAsset(it)
        } ?: original

        return Wallpaper(
            source = WallpaperSource.OPENVERSE,
            sourceId = dto.id,
            pageUrl = landing,
            thumbUrl = cardThumbnail,
            previewUrl = original,
            originalUrl = original,
            width = width,
            height = height,
            fileSizeBytes = dto.filesize?.takeIf { it > 0 },
            mimeType = mime,
            creatorName = dto.creator?.trim()?.takeIf(String::isNotEmpty),
            creatorUrl = creatorUrl,
            category = dto.category?.trim()?.takeIf(String::isNotEmpty),
            tags = tags,
            title = dto.title?.trim()?.takeIf(String::isNotEmpty),
            attribution = dto.attribution?.trim()?.takeIf(String::isNotEmpty),
            licenseCode = license.takeIf(String::isNotEmpty),
            licenseVersion = dto.licenseVersion?.trim()?.takeIf(String::isNotEmpty),
            licenseUrl = licenseUrl,
            providerName = dto.provider?.trim()?.takeIf(String::isNotEmpty),
            catalogSource = dto.source?.trim()?.takeIf(String::isNotEmpty),
            downloadAllowed = knownLicense && supportedImage,
            setWallpaperAllowed = knownLicense && supportedImage && license !in noDerivatives,
        )
    }

    private val WALLPAPER_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    private fun mimeTypeFromUrl(url: String): String? {
        val extension = url.toHttpUrlOrNull()?.encodedPath
            ?.substringAfterLast('/', "")
            ?.substringAfterLast('.', "")
            ?.takeIf(String::isNotBlank)
        return mimeType(extension)
    }

    private fun mimeType(filetype: String?): String? = when (filetype?.trim()?.lowercase(Locale.US)?.removePrefix(".")) {
        "jpg", "jpeg", "image/jpeg" -> "image/jpeg"
        "png", "image/png" -> "image/png"
        "webp", "image/webp" -> "image/webp"
        "gif", "image/gif" -> "image/gif"
        "avif", "image/avif" -> "image/avif"
        "heic", "heif", "image/heic", "image/heif" -> "image/heic"
        "bmp", "image/bmp" -> "image/bmp"
        "tif", "tiff", "image/tiff" -> "image/tiff"
        "svg", "image/svg+xml" -> "image/svg+xml"
        else -> null
    }
}
