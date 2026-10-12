package com.auroro.wallpapers.core.data

import android.content.SharedPreferences
import android.text.Html
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.nasa.NasaApodApi
import com.auroro.wallpapers.core.network.nasa.NasaApodDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** NASA's current APOD Basic API; records remain view-only until item-specific reuse rights are confirmed. */
class NasaApodProvider(
    private val api: NasaApodApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 12, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.NASA_APOD

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.NEWEST),
        sortsWithQuery = emptySet(),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 2

    override fun unsupportedReason(request: FeedRequest): String? = when {
        request.normalizedQuery.isNotEmpty() ->
            "NASA APOD is a dated, newest-first archive and does not offer keyword search. Clear the query or choose another source."
        request.filter.sort !in capabilities.sortsWithoutQuery ->
            "NASA APOD only supports its newest-first archive order."
        else -> null
    }

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor.page)?.let { return it }
        val response = RateLimitedCall.run(limiter, source.displayName) {
            api.latest(pageSize = PAGE_SIZE, page = cursor.page)
        }
        val items = response.mapNotNull(::toWallpaper)
        val next = if (response.size == PAGE_SIZE && items.isNotEmpty()) PageCursor(cursor.page + 1) else null
        ProviderPage(items, next).also { pageCache?.put(request, cursor.page, it) }
    }

    private fun toWallpaper(dto: NasaApodDto): Wallpaper? {
        val date = dto.date?.substringBefore('T')?.takeIf(DATE_PATTERN::matches) ?: return null
        if (!dto.mediaType.equals("image", ignoreCase = true)) return null
        val original = dto.hdurl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.NASA_APOD, original)) return null

        // APOD Basic does not report pixel dimensions. CDN URL parameters are rendition requests,
        // not authoritative source metadata, so keep the original size unknown.
        val width = 0
        val height = 0
        val title = cleanText(dto.title).takeIf(String::isNotBlank)
        val copyright = cleanText(dto.copyright).takeIf(String::isNotBlank)
        val credit = cleanText(dto.credit).takeIf(String::isNotBlank)
        val permalink = dto.permalink?.trim()?.takeIf(UrlPolicy::isAllowedForBrowsing)
            ?: "https://science.nasa.gov/apod/"
        val mimeType = original.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp") }
            ?.let { if (it == "jpg") "image/jpeg" else "image/$it" }
        // APOD credit/copyright fields identify provenance; they do not themselves grant a reuse licence.
        val allowedForReuse = false

        return Wallpaper(
            source = WallpaperSource.NASA_APOD,
            sourceId = date,
            pageUrl = permalink,
            thumbUrl = sizeDynamicImage(original, THUMB_MAX_EDGE),
            previewUrl = sizeDynamicImage(original, PREVIEW_MAX_EDGE),
            originalUrl = original,
            width = width,
            height = height,
            mimeType = mimeType,
            creatorName = credit,
            category = "Astronomy",
            createdAt = date,
            title = title,
            attribution = buildString {
                append("NASA Astronomy Picture of the Day")
                credit?.let { append(" · ").append(it) }
                copyright?.let { append(" · Copyright · ").append(it) }
            },
            licenseCode = null,
            licenseVersion = null,
            licenseUrl = null,
            downloadAllowed = allowedForReuse,
            setWallpaperAllowed = allowedForReuse,
        )
    }

    private fun cleanText(value: String?): String = value.orEmpty()
        .let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() }
        .replace('\u00a0', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    companion object {
        const val PAGE_SIZE = 20
        private const val THUMB_MAX_EDGE = 960
        private const val PREVIEW_MAX_EDGE = 1_920
        private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val WHITESPACE = Regex("\\s+")

        /** Resize NASA's dynamic image CDN copy for browsing, without changing the preserved original. */
        internal fun sizeDynamicImage(raw: String, maxEdge: Int): String {
            val uri = raw.toHttpUrlOrNull() ?: return raw
            val width = uri.queryParameter("w")?.toIntOrNull()
            val height = uri.queryParameter("h")?.toIntOrNull()
            if (width == null || height == null || width <= 0 || height <= 0) return raw
            val scale = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
            if (scale >= 1.0) return raw
            return uri.newBuilder()
                .setQueryParameter("w", (width * scale).toInt().coerceAtLeast(1).toString())
                .setQueryParameter("h", (height * scale).toInt().coerceAtLeast(1).toString())
                .build()
                .toString()
        }

    }
}
