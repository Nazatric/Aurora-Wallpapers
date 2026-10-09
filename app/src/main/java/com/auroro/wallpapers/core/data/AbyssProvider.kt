package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.abyss.AbyssApi
import com.auroro.wallpapers.core.network.abyss.AbyssResponse
import com.auroro.wallpapers.core.network.abyss.AbyssWallpaperDto
import com.auroro.wallpapers.core.network.toProviderException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Wallpaper Abyss via the Alpha Coders API 3.0.
 *
 * Verified against the official docs (https://api.alphacoders.com/api/instructions):
 * - `method=newest` and `method=search&term=...`, `type=desktop|phone`, `page` (1..200).
 * - Requires the *user's own* paid API key. Without one the provider reports [SourceAvailability.NeedsConfiguration]
 *   and the UI offers an official-website handoff instead. Nothing is scraped.
 * - Popular/Random are not documented for these methods, so they're reported as unsupported.
 *
 * NOTE: this implementation follows the published documentation and is covered by mocked-HTTP tests.
 * It has not been exercised against the live service because no subscription key was available.
 */
class AbyssProvider(
    private val api: AbyssApi,
    private val apiKey: suspend () -> String,
    private val isEnabled: suspend () -> Boolean = { true },
) : WallpaperProvider {

    override val source = WallpaperSource.ABYSS

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = setOf(SortOption.NEWEST),
        sortsWithQuery = setOf(SortOption.RELEVANCE),
    )

    override suspend fun availability(): SourceAvailability {
        if (!isEnabled()) return SourceAvailability.DisabledByUser()
        return if (apiKey().isBlank()) {
            SourceAvailability.NeedsConfiguration(
                reason = "Alpha Coders' API needs your own API key (paid API subscription). Add it in Settings → Sources.",
                handoffUrl = "https://wall.alphacoders.com/",
            )
        } else {
            SourceAvailability.Available
        }
    }

    override fun unsupportedReason(request: FeedRequest): String? {
        val filter = request.filter
        if (filter.wallhavenCategories.isNotEmpty()) return "Categories are Wallhaven-specific; Wallpaper Abyss can't apply them."
        if (filter.colorHex != null) return "Dominant-color filtering is only available on Wallhaven."
        val sort = filter.sort
        val hasQuery = request.normalizedQuery.isNotEmpty()
        val supported = if (hasQuery) capabilities.sortsWithQuery else capabilities.sortsWithoutQuery
        return if (sort in supported) {
            null
        } else {
            "Wallpaper Abyss's API can't sort by ${sort.label.lowercase()}${if (hasQuery) " while searching" else ""}."
        }
    }

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage {
        val key = apiKey().trim()
        if (key.isEmpty()) throw ProviderException(ProviderErrorKind.NOT_CONFIGURED, "Wallpaper Abyss needs an API key.")
        if (cursor.page > MAX_PAGE) return ProviderPage(emptyList(), null)
        val variants = AbyssQuery.variants(request)
        if (cursor.variant !in variants.indices) return ProviderPage(emptyList(), null)
        val type = variants[cursor.variant]
        val params = AbyssQuery.build(request, cursor, key, type)
        val response = try {
            api.call(params)
        } catch (t: Throwable) {
            throw t.toProviderException(source.displayName)
        }
        if (!response.success) throw errorFor(response)
        val items = response.wallpapers.mapNotNull(AbyssMapper::toWallpaper)
        val next = when {
            cursor.variant < variants.lastIndex -> PageCursor(cursor.page, variant = cursor.variant + 1)
            response.wallpapers.isEmpty() -> null
            cursor.page < MAX_PAGE -> PageCursor(cursor.page + 1, variant = 0)
            else -> null
        }
        return ProviderPage(items, next)
    }

    private fun errorFor(r: AbyssResponse): ProviderException {
        val code = r.error.orEmpty().lowercase()
        return when {
            "auth" in code || "key" in code || "invalid" in code || "denied" in code || "forbidden" in code ->
                ProviderException(ProviderErrorKind.AUTH_FAILED, "Wallpaper Abyss rejected the API key (${r.error}).")
            "limit" in code || "quota" in code || "too many" in code ->
                ProviderException(ProviderErrorKind.RATE_LIMITED, "Wallpaper Abyss API limit reached (${r.error}).")
            else -> ProviderException(ProviderErrorKind.BAD_RESPONSE, "Wallpaper Abyss returned an error: ${r.error ?: "unknown"}.")
        }
    }

    companion object {
        /** Documented page range is 1..200. */
        const val MAX_PAGE = 200
    }
}

object AbyssQuery {
    /**
     * `phone` wallpapers when the user asks for portrait content, `desktop` otherwise.
     * (The API has no "any" type, so landscape/any share `desktop`.)
     */
    fun variants(request: FeedRequest): List<String> {
        val target = request.filter.aspect.targetRatio
        val portrait = request.filter.orientation == Orientation.PORTRAIT ||
            (request.filter.aspect != AspectFilter.Any && target != null && target < 1f)
        val landscape = request.filter.orientation == Orientation.LANDSCAPE ||
            (request.filter.aspect != AspectFilter.Any && target != null && target > 1f)
        return when {
            portrait && !landscape -> listOf("phone")
            landscape && !portrait -> listOf("desktop")
            else -> listOf("desktop", "phone")
        }
    }

    fun type(request: FeedRequest): String = variants(request).first()

    fun build(request: FeedRequest, cursor: PageCursor, key: String, type: String = type(request)): Map<String, String> {
        val q = request.normalizedQuery
        val params = linkedMapOf("auth" to key)
        if (q.isEmpty()) {
            params["method"] = "newest"
        } else {
            params["method"] = "search"
            params["term"] = q.take(128)
        }
        params["type"] = type(request)
        params["page"] = cursor.page.toString()
        return params
    }
}

object AbyssMapper {
    fun toWallpaper(dto: AbyssWallpaperDto): Wallpaper? {
        val id = dto.id.asString()?.takeIf { it.isNotBlank() } ?: return null
        val image = dto.urlImage?.takeIf { UrlPolicy.isAllowedForNetwork(it) } ?: return null
        val thumb = dto.urlThumb?.takeIf { UrlPolicy.isAllowedForNetwork(it) } ?: return null
        val page = dto.urlPage?.takeIf { UrlPolicy.isAllowedForBrowsing(it) }
            ?: "https://wall.alphacoders.com/big.php?i=$id"
        val tags = listOfNotNull(dto.category, dto.subCategory)
            .filter { it.isNotBlank() }
            .distinct()
            .map { WallpaperTag(null, it) }
        val mime = when (dto.fileType?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> null
        }
        return Wallpaper(
            source = WallpaperSource.ABYSS,
            sourceId = id,
            pageUrl = page,
            thumbUrl = thumb,
            previewUrl = thumb,
            originalUrl = image,
            width = dto.width.asString()?.toIntOrNull() ?: 0,
            height = dto.height.asString()?.toIntOrNull() ?: 0,
            fileSizeBytes = dto.fileSize.asString()?.toLongOrNull(),
            mimeType = mime,
            creatorName = dto.userName?.takeIf { it.isNotBlank() },
            creatorUrl = null, // no profile URL is documented for the API, so none is invented
            category = dto.category,
            tags = tags,
        )
    }

    private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
}
