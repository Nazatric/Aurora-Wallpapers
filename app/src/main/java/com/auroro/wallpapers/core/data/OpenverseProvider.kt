package com.auroro.wallpapers.core.data

import android.text.Html
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.openverse.OpenverseApi
import com.auroro.wallpapers.core.network.openverse.OpenverseImageDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Openverse catalogue: freely-licensed photos and art. Only CC0/PDM/BY/BY-SA items survive the
 * rights gate (the API is also asked for the `commercial` licence type, which drops NC work);
 * BY/BY-SA items keep creator attribution on the card and detail screen.
 */
class OpenverseProvider(
    private val api: OpenverseApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 20, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.OPENVERSE

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.SOURCE_DEFAULT),
        sortsWithQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 3

    override fun unsupportedReason(request: FeedRequest): String? =
        if (request.filter.sort in capabilities.sortsWithoutQuery || request.normalizedQuery.isNotEmpty()) null
        else "Openverse serves relevance order for searches and its default catalogue order otherwise."

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor.page)?.let { return it }
        val response = RateLimitedCall.run(limiter, source.displayName) {
            api.images(
                query = request.normalizedQuery.takeIf { it.isNotEmpty() },
                page = cursor.page,
                pageSize = PAGE_SIZE,
            )
        }
        val items = response.results.mapNotNull(::toWallpaper)
        val hasMore = cursor.page < (response.pageCount ?: 1) && response.results.size == PAGE_SIZE
        val next = if (hasMore && items.isNotEmpty()) PageCursor(cursor.page + 1) else null
        ProviderPage(items, next).also { pageCache?.put(request, cursor.page, it) }
    }

    private fun toWallpaper(dto: OpenverseImageDto): Wallpaper? {
        val id = dto.id?.takeIf(String::isNotBlank) ?: return null
        val original = dto.url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.OPENVERSE, original)) return null
        val pageUrl = dto.foreignLandingUrl?.trim()?.takeIf { it.startsWith("https://") }
            ?: "https://openverse.org/image/$id"
        val license = dto.license?.lowercase()?.trim().orEmpty()
        if (license !in REUSE_OK_LICENSES) return null
        val creator = cleanText(dto.creator).takeIf(String::isNotBlank)
        val version = dto.licenseVersion?.takeIf(String::isNotBlank)
        return Wallpaper(
            source = WallpaperSource.OPENVERSE,
            sourceId = id,
            pageUrl = pageUrl,
            thumbUrl = dto.thumbnail?.takeIf(String::isNotBlank) ?: original,
            previewUrl = original,
            originalUrl = original,
            width = dto.width?.coerceAtLeast(0) ?: 0,
            height = dto.height?.coerceAtLeast(0) ?: 0,
            mimeType = null,
            creatorName = creator,
            creatorUrl = dto.creatorUrl?.takeIf { it.startsWith("https://") },
            category = dto.category?.takeIf(String::isNotBlank)?.replaceFirstChar(Char::uppercase),
            tags = dto.tags.mapNotNull { it.name?.takeIf(String::isNotBlank)?.let { n -> WallpaperTag(id = null, name = n) } }.take(8),
            title = cleanText(dto.title).takeIf(String::isNotBlank),
            attribution = buildString {
                append("Via Openverse")
                creator?.let { append(" · ").append(it) }
                append(" · ").append(license.uppercase())
                version?.takeIf(String::isNotBlank)?.let { append(" ").append(it) }
            },
            licenseCode = license,
            licenseVersion = version,
            licenseUrl = licenseUrlFor(license, version),
            downloadAllowed = true,
            setWallpaperAllowed = true,
        )
    }

    private fun cleanText(value: String?): String = value.orEmpty()
        .let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString() }
        .replace('\u00a0', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    companion object {
        const val PAGE_SIZE = 20
        private val WHITESPACE = Regex("\\s+")
        private val REUSE_OK_LICENSES = setOf("cc0", "pdm", "by", "by-sa")

        internal fun licenseUrlFor(license: String, version: String?): String? = when (license) {
            "cc0" -> "https://creativecommons.org/publicdomain/zero/1.0/"
            "pdm" -> "https://creativecommons.org/publicdomain/mark/1.0/"
            "by", "by-sa" -> version?.takeIf(String::isNotBlank)
                ?.let { "https://creativecommons.org/licenses/$license/$it/" }
            else -> null
        }
    }
}
