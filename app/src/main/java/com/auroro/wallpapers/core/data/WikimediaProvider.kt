package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.wikimedia.WikimediaApi
import com.auroro.wallpapers.core.network.wikimedia.WikimediaImageInfo
import com.auroro.wallpapers.core.network.wikimedia.WikimediaPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wikimedia Commons keyword search over freely-licensed files. Commons has no keyword-free feed, so
 * the source honestly reports itself unsupported without a query instead of inventing one. Only
 * Public Domain / CC0 / CC-BY / CC-BY-SA files pass; artist and licence short name are preserved.
 */
class WikimediaProvider(
    private val api: WikimediaApi,
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 20, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
    private val pageCache: ProviderPageCache? = null,
) : WallpaperProvider {
    override val source = WallpaperSource.WIKIMEDIA

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = emptySet(),
        sortsWithQuery = setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE),
        supportsDetail = false,
        supportsRelated = false,
    )

    private val fetchMutex = Mutex()

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    override fun maxPagesPerLoad(request: FeedRequest): Int = 2

    override fun unsupportedReason(request: FeedRequest): String? = when {
        request.normalizedQuery.isEmpty() ->
            "Wikimedia Commons is search-only. Type a keyword (try \"aurora\", \"galaxy\", \"mountains\") to browse its freely-licensed archive."
        request.filter.sort !in capabilities.sortsWithQuery ->
            "Wikimedia Commons only offers relevance order for its keyword search."
        else -> null
    }

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage = fetchMutex.withLock {
        pageCache?.get(request, cursor)?.let { return it }
        val response = RateLimitedCall.run(limiter, source.displayName) {
            api.searchFiles(
                search = "filetype:bitmap ${request.normalizedQuery}",
                limit = PAGE_SIZE,
            )
        }
        val items = response.query?.pages.orEmpty().mapNotNull(::toWallpaper)
        // Commons generator/search continuation is session-based; serve one high-quality page.
        ProviderPage(items, next = null).also { pageCache?.put(request, cursor, it) }
    }

    private fun toWallpaper(page: WikimediaPage): Wallpaper? {
        val info: WikimediaImageInfo = page.imageinfo.firstOrNull() ?: return null
        val title = page.title?.removePrefix("File:")?.replace('_', ' ')?.trim()?.takeIf(String::isNotBlank)
            ?: return null
        val original = info.url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!UrlPolicy.isAllowedForNetwork(WallpaperSource.WIKIMEDIA, original)) return null
        val mime = info.mime?.lowercase().orEmpty()
        if (mime != "image/jpeg" && mime != "image/png" && mime != "image/webp") return null
        val width = info.width ?: 0
        val height = info.height ?: 0
        if (width in 1 until MIN_WIDTH || height in 1 until MIN_HEIGHT) return null
        val license = cleanHtml(info.extmetadata?.licenseShortName?.value).takeIf(String::isNotBlank)
            ?: return null
        if (!isReuseOk(license)) return null
        val artist = cleanHtml(info.extmetadata?.artist?.value).takeIf(String::isNotBlank)
        val preview = info.thumburl?.takeIf(String::isNotBlank) ?: original
        return Wallpaper(
            source = WallpaperSource.WIKIMEDIA,
            sourceId = title,
            pageUrl = info.descriptionurl?.takeIf { it.startsWith("https://") }
                ?: "https://commons.wikimedia.org/wiki/File:${title.replace(' ', '_')}",
            thumbUrl = preview,
            previewUrl = preview,
            originalUrl = original,
            width = width,
            height = height,
            mimeType = mime,
            creatorName = artist,
            category = "Commons",
            title = title,
            attribution = buildString {
                append("Wikimedia Commons · ").append(license)
                artist?.let { append(" · ").append(it) }
            },
            licenseCode = license,
            licenseVersion = null,
            licenseUrl = null,
            downloadAllowed = true,
            setWallpaperAllowed = true,
        )
    }

    /**
     * Strip wiki markup without android.text.Html: extmetadata values are small HTML fragments
     * and the gate must behave identically on-device and in JVM/Robolectric tests.
     */
    private fun cleanHtml(value: String?): String = value.orEmpty()
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace('\u00a0', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    companion object {
        const val PAGE_SIZE = 20
        private const val MIN_WIDTH = 1200
        private const val MIN_HEIGHT = 600
        private val WHITESPACE = Regex("\\s+")

        internal fun isReuseOk(licenseShortName: String): Boolean {
            val l = licenseShortName.lowercase()
            val publicDomain = l.startsWith("pd") || l.contains("public domain") || l.startsWith("cc0")
            if (publicDomain) return true
            val ccBy = l.startsWith("cc-by") || l.startsWith("cc by")
            // NC (non-commercial) is out by policy; ND (no derivatives) is out because the
            // crop-to-fit step creates a derivative. SA and plain BY are fine with attribution.
            return ccBy && !l.contains("nc") && !l.contains("nd")
        }
    }
}
