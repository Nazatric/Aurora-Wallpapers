package com.auroro.wallpapers.core.model

import androidx.compose.runtime.Immutable

/** The wallpaper catalogues Auroro knows about. Display names match each publisher's official name. */
enum class WallpaperSource(val id: String, val displayName: String, val siteUrl: String) {
    WALLHAVEN("wallhaven", "Wallhaven", "https://wallhaven.cc"),
    ABYSS("abyss", "Wallpaper Abyss", "https://wall.alphacoders.com"),
    UNSPLASH("unsplash", "Unsplash", "https://unsplash.com");

    companion object {
        fun fromId(id: String): WallpaperSource? = entries.firstOrNull { it.id == id }
    }
}

@Immutable
data class WallpaperTag(val id: Long?, val name: String)

/**
 * Provider-agnostic wallpaper record. Nothing provider-specific is thrown away:
 * original ids, URLs, dimensions, attribution and extra metadata are all kept so the detail,
 * licensing and download flows can rely on them.
 */
@Immutable
data class Wallpaper(
    val source: WallpaperSource,
    /** The provider's own stable identifier. */
    val sourceId: String,
    /** Canonical page of this wallpaper on the provider's website. */
    val pageUrl: String,
    /** Small thumbnail suited for gallery tiles. Never the full-resolution file. */
    val thumbUrl: String,
    /** Larger preview, used by the detail screen before the user asks for the original. */
    val previewUrl: String,
    /** Original-quality file as published by the provider. */
    val originalUrl: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val creatorName: String? = null,
    val creatorUrl: String? = null,
    val category: String? = null,
    val tags: List<WallpaperTag> = emptyList(),
    /** Dominant palette as `#rrggbb` when the provider reports one. */
    val colors: List<String> = emptyList(),
    val createdAt: String? = null,
    val views: Int? = null,
    val favorites: Int? = null,
    /** Link to where the uploader says the image came from, if any. */
    val originSourceUrl: String? = null,
) {
    val key: String get() = keyOf(source, sourceId)

    /** width / height. Portrait images are < 1, landscape > 1. */
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 1f

    val hasKnownDimensions: Boolean get() = width > 0 && height > 0

    companion object {
        fun keyOf(source: WallpaperSource, sourceId: String) = "${source.id}:$sourceId"
        fun sourceOfKey(key: String): WallpaperSource? = WallpaperSource.fromId(key.substringBefore(':'))
        fun idOfKey(key: String): String = key.substringAfter(':')
    }
}

/** User-facing sort choices. Not every provider supports every choice; see [ProviderCapabilities]. */
enum class SortOption(val label: String) {
    RELEVANCE("Relevance"),
    NEWEST("Newest"),
    POPULAR("Popular"),
    RANDOM("Random"),
}

enum class Orientation(val label: String) {
    ANY("All"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

/** Wallhaven's own top-level categories (the only provider that exposes them). */
enum class WallhavenCategory(val label: String, val bitIndex: Int) {
    GENERAL("General", 0),
    ANIME("Anime", 1),
    PEOPLE("People", 2),
}

/** Everything the user can change on the filter screen. */
@Immutable
data class WallpaperFilter(
    /** Empty set means "all available sources". */
    val sources: Set<WallpaperSource> = emptySet(),
    val aspect: AspectFilter = AspectFilter.Any,
    val resolution: ResolutionFilter = ResolutionFilter.Any,
    val orientation: Orientation = Orientation.ANY,
    val sort: SortOption = SortOption.RELEVANCE,
    /** Wallhaven only. Empty = all three. */
    val wallhavenCategories: Set<WallhavenCategory> = emptySet(),
    /** Wallhaven only; lowercase `rrggbb` without '#'. */
    val colorHex: String? = null,
) {
    /** Number of non-default settings, shown as a badge on the filter button. */
    val activeCount: Int
        get() = listOf(
            sources.isNotEmpty(),
            aspect != AspectFilter.Any,
            resolution != ResolutionFilter.Any,
            orientation != Orientation.ANY,
            sort != SortOption.RELEVANCE,
            wallhavenCategories.isNotEmpty(),
            colorHex != null,
        ).count { it }

    companion object {
        val Default = WallpaperFilter()
    }
}

/** The set of values a feed is built from: text query plus filter. */
@Immutable
data class FeedRequest(
    val query: String = "",
    val filter: WallpaperFilter = WallpaperFilter.Default,
    /** Aspect-ratio tolerance (relative) applied to local filtering; see [AspectMath]. */
    val aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE,
) {
    val normalizedQuery: String get() = query.trim().replace(Regex("\\s+"), " ")
}
