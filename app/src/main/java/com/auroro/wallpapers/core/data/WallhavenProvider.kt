package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.RateLimitedCall
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.wallhaven.WallhavenApi
import com.auroro.wallpapers.core.network.wallhaven.WallhavenWallpaperDto
import java.util.Locale

/**
 * Wallhaven via the official public API v1.
 *
 * - Safe-for-work only: `purity=100` is hard-coded. No API key is used, so sketchy/NSFW can't be enabled.
 * - Quota: 45 requests/minute (official). A sliding-window limiter keeps us below it; HTTP 429 is never retried.
 */
class WallhavenProvider(
    private val api: WallhavenApi,
    // Each call may retry twice on transient failures; 15 call slots × 3 attempts stays below Wallhaven's 45/minute quota.
    private val limiter: SlidingWindowLimiter = SlidingWindowLimiter(maxEvents = 15, windowMs = 60_000),
    private val isEnabled: suspend () -> Boolean = { true },
) : WallpaperProvider {

    override val source = WallpaperSource.WALLHAVEN

    override val capabilities = ProviderCapabilities(
        // Wallhaven documents relevance for keyword searches, but a blank-query relevance request
        // currently returns data:[] despite meta.total > 0. Leave the default endpoint sort out of
        // the no-query sort menu; SearchSourceSelection maps a blank relevance selection to Newest.
        sortsWithoutQuery = setOf(SortOption.NEWEST, SortOption.POPULAR, SortOption.RANDOM),
        sortsWithQuery = setOf(SortOption.RELEVANCE, SortOption.NEWEST, SortOption.POPULAR, SortOption.RANDOM),
        supportsCategories = true,
        supportsColorFilter = true,
        supportsDetail = true,
        supportsRelated = true,
        serverSideResolution = true,
        serverSideAspect = true,
    )

    override suspend fun availability(): SourceAvailability =
        if (isEnabled()) SourceAvailability.Available else SourceAvailability.DisabledByUser()

    private suspend fun <T> rateLimited(block: suspend () -> T): T = try {
        RateLimitedCall.run(limiter, source.displayName, block)
    } catch (error: ProviderException) {
        val retryAfter = error.retryAfterSeconds
        if (error.kind == ProviderErrorKind.RATE_LIMITED && retryAfter != null && retryAfter > 0) {
            // Bound malformed Retry-After values before converting seconds to milliseconds.
            limiter.pauseFor(retryAfter.coerceAtMost(604_800L) * 1_000L)
        }
        throw error
    }

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
        val params = WallhavenQuery.build(request, cursor)
        val response = rateLimited { api.search(params) }
        val items = response.data.mapNotNull(WallhavenMapper::toWallpaper)
        val meta = response.meta
        val next = if (response.data.isNotEmpty() && meta.currentPage < meta.lastPage) {
            PageCursor(page = cursor.page + 1, seed = cursor.seed ?: meta.seed)
        } else {
            null
        }
        return ProviderPage(items, next)
    }

    override suspend fun detail(sourceId: String): Wallpaper? {
        val dto = rateLimited { api.wallpaper(sourceId) }.data
        return WallhavenMapper.toWallpaper(dto)
    }

    override suspend fun related(wallpaper: Wallpaper): List<Wallpaper> {
        val params = linkedMapOf(
            "q" to "like:${wallpaper.sourceId}",
            "categories" to "111",
            "purity" to "100",
            "sorting" to "relevance",
            "page" to "1",
        )
        val response = rateLimited { api.search(params) }
        return response.data.mapNotNull(WallhavenMapper::toWallpaper).filter { it.sourceId != wallpaper.sourceId }
    }
}

/** Builds the exact query parameters sent to `GET /api/v1/search`. Pure, so it's unit-tested. */
object WallhavenQuery {
    val PALETTE = listOf(
        "660000", "990000", "cc0000", "cc3333", "ea4c88", "993399", "663399", "333399", "0066cc", "0099cc",
        "66cccc", "77cc33", "669900", "336600", "666600", "999900", "cccc33", "ffff00", "ffcc33", "ff9900",
        "ff6600", "cc6633", "996633", "663300", "000000", "999999", "cccccc", "ffffff", "424153",
    )

    fun build(request: FeedRequest, cursor: PageCursor): Map<String, String> {
        val f = request.filter
        val q = request.normalizedQuery
        val params = linkedMapOf<String, String>()
        if (q.isNotEmpty()) params["q"] = q

        params["categories"] = categoriesBits(f.wallhavenCategories)
        params["purity"] = "100" // SFW only. Never widened.

        when (f.sort) {
            SortOption.RELEVANCE -> if (q.isNotEmpty()) {
                params["sorting"] = "relevance"
            } else {
                // Wallhaven's blank-query relevance endpoint has returned zero rows with a
                // nonzero total. Use its working, documented date-added ordering instead.
                params["sorting"] = "date_added"
            }
            SortOption.NEWEST -> params["sorting"] = "date_added"
            SortOption.POPULAR -> {
                params["sorting"] = "toplist"
                params["topRange"] = "1M"
            }
            SortOption.RANDOM -> params["sorting"] = "random"
        }
        params["order"] = "desc"
        params["page"] = cursor.page.toString()
        if (f.sort == SortOption.RANDOM && cursor.seed != null) params["seed"] = cursor.seed

        atLeast(request)?.let { params["atleast"] = it }
        ratios(request)?.let { params["ratios"] = it }
        f.colorHex?.lowercase(Locale.US)?.removePrefix("#")?.takeIf { it in PALETTE }?.let { params["colors"] = it }
        return params
    }

    fun categoriesBits(selected: Set<com.auroro.wallpapers.core.model.WallhavenCategory>): String {
        if (selected.isEmpty()) return "111"
        val chars = CharArray(3) { '0' }
        selected.forEach { chars[it.bitIndex] = '1' }
        return String(chars)
    }

    /** Server-side pre-filter for minimum resolution. The authoritative check still happens locally. */
    internal fun atLeast(request: FeedRequest): String? {
        val res = request.filter.resolution
        val targetRatio = request.filter.aspect.targetRatio
        val tolerance = request.aspectTolerance.coerceIn(0f, 0.25f)
        val portraitByRatio = targetRatio != null && targetRatio * (1f + tolerance) < 1f
        val landscapeByRatio = targetRatio != null && targetRatio * (1f - tolerance) > 1f
        val portrait = request.filter.orientation == Orientation.PORTRAIT || portraitByRatio
        val landscape = request.filter.orientation == Orientation.LANDSCAPE || landscapeByRatio
        val (shortEdge, longEdge) = when (res) {
            ResolutionFilter.Any -> return null
            is ResolutionFilter.Custom -> res.shortEdge to res.longEdge
            is ResolutionFilter.Preset -> res.preset.shortEdge to res.preset.longEdge
        }
        return when {
            portrait && !landscape -> "${shortEdge}x$longEdge"
            landscape && !portrait -> "${longEdge}x$shortEdge"
            // A square lower bound is a safe broad pre-filter in either orientation; local checks
            // enforce the requested short and long edges independently.
            else -> "${shortEdge}x$shortEdge"
        }
    }

    internal fun ratios(request: FeedRequest): String? {
        val aspect = request.filter.aspect
        if (aspect is AspectFilter.Preset) {
            WALLHAVEN_RATIO_TOKENS[aspect.preset]?.let { return it }
        }
        // Generic orientation and custom ratios aren't accepted by Wallhaven's ratio parameter.
        // Those are applied locally from the actual image dimensions instead of sending guessed tokens.
        return null
    }

    private val WALLHAVEN_RATIO_TOKENS = mapOf(
        AspectPreset.R9_16 to "9x16",
        AspectPreset.R16_9 to "16x9",
        AspectPreset.R4_3 to "4x3",
        AspectPreset.R1_1 to "1x1",
        AspectPreset.R21_9 to "21x9",
    )
}

object WallhavenMapper {
    /** Returns null for records that are unusable or whose URLs fail the provider allow-list. */
    fun toWallpaper(dto: WallhavenWallpaperDto): Wallpaper? {
        if (dto.id.isBlank() || dto.path.isBlank()) return null
        if (dto.purity != null && dto.purity != "sfw") return null // defence in depth: never surface non-SFW
        if (dto.dimensionX !in 1..32_000 || dto.dimensionY !in 1..32_000) return null
        val ratio = dto.dimensionX.toFloat() / dto.dimensionY
        if (!ratio.isFinite() || ratio !in 0.05f..20f) return null
        if (!UrlPolicy.isAllowedForNetwork(dto.path)) return null
        // Use Wallhaven's own original-size preview for gallery cells; the detail view and downloads
        // retain the full media URL. Never trust a thumbnail URL outside the provider's HTTPS hosts.
        val cardPreview = sequenceOf(dto.thumbs.original, dto.thumbs.large)
            .firstOrNull(UrlPolicy::isAllowedForNetwork)
            ?: dto.path
        val page = dto.url.ifBlank { "https://wallhaven.cc/w/${dto.id}" }
        if (!UrlPolicy.isAllowedForBrowsing(page)) return null
        val username = dto.uploader?.username?.takeIf { it.isNotBlank() }
        return Wallpaper(
            source = WallpaperSource.WALLHAVEN,
            sourceId = dto.id,
            pageUrl = page,
            thumbUrl = cardPreview,
            previewUrl = dto.path,
            originalUrl = dto.path,
            width = dto.dimensionX,
            height = dto.dimensionY,
            fileSizeBytes = dto.fileSize,
            mimeType = dto.fileType,
            creatorName = username,
            creatorUrl = username?.let { "https://wallhaven.cc/user/${java.net.URLEncoder.encode(it, "UTF-8")}" },
            category = dto.category?.replaceFirstChar { it.uppercase() },
            tags = dto.tags.filter { it.name.isNotBlank() }.map { WallpaperTag(it.id, it.name) },
            colors = dto.colors,
            createdAt = dto.createdAt,
            views = dto.views,
            favorites = dto.favorites,
            originSourceUrl = dto.source?.takeIf { it.startsWith("https://") },
        )
    }
}
