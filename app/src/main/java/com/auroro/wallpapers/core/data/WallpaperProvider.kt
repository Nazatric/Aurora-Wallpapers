package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource

/** What a provider can *genuinely* do. The UI only offers what's supported. */
data class ProviderCapabilities(
    /** Sort orders the provider can honour when the query is blank. */
    val sortsWithoutQuery: Set<SortOption>,
    /** Sort orders the provider can honour when a text query is present. */
    val sortsWithQuery: Set<SortOption>,
    val supportsCategories: Boolean = false,
    val supportsColorFilter: Boolean = false,
    val supportsDetail: Boolean = false,
    val supportsRelated: Boolean = false,
    /** True when the provider can narrow by minimum resolution on the server. Local filtering always runs too. */
    val serverSideResolution: Boolean = false,
    val serverSideAspect: Boolean = false,
)

/** Pagination state for a single provider. Providers paginate independently. */
data class PageCursor(val page: Int = 1, val seed: String? = null, /** Provider-specific lane index (e.g. desktop vs phone catalogues). */ val variant: Int = 0)

data class ProviderPage(
    val items: List<Wallpaper>,
    /** Cursor for the following page, or null when the provider has no more results. */
    val next: PageCursor?,
)

/** Why a source is (not) usable right now. */
sealed interface SourceAvailability {
    data object Available : SourceAvailability
    data class DisabledByUser(val reason: String = "Turned off in Settings") : SourceAvailability
}

/**
 * Contract for a wallpaper catalogue. A provider exposes only the operations it genuinely supports
 * and returns results in the common [Wallpaper] model without discarding provider-specific data.
 */
interface WallpaperProvider {
    val source: WallpaperSource
    val capabilities: ProviderCapabilities

    /** Current availability; may depend on a source toggle. */
    suspend fun availability(): SourceAvailability

    /** Pagination budget for one explicit feed load. Conservative sources can opt into one page at a time. */
    fun maxPagesPerLoad(request: FeedRequest): Int = 4

    /**
     * Explains why this provider can't serve [request] (e.g. unsupported sort), or null if it can.
     * The aggregator surfaces this as a "skipped" status instead of silently dropping the source.
     */
    fun unsupportedReason(request: FeedRequest): String? = null

    /** Fetch one page. Throws [com.auroro.wallpapers.core.network.ProviderException] on failure. */
    suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage

    /** Optional richer metadata for one wallpaper (tags, uploader, ...). */
    suspend fun detail(sourceId: String): Wallpaper? = null

    /** Optional "more like this" recommendations; only implemented where the provider really supports it. */
    suspend fun related(wallpaper: Wallpaper): List<Wallpaper> = emptyList()
}
