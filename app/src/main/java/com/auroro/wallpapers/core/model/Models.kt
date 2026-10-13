package com.auroro.wallpapers.core.model

import androidx.compose.runtime.Immutable

/** The only catalogues queried by the app, plus an internal value for records saved by older versions. */
enum class WallpaperSource(val id: String, val displayName: String, val siteUrl: String) {
    WALLHAVEN("wallhaven", "Wallhaven", "https://wallhaven.cc"),
    NASA_APOD("nasa-apod", "NASA APOD", "https://science.nasa.gov/apod/"),
    /**
     * Retired in 1.3.0: the catalogue's search results repeatedly arrived without image
     * dimensions or displayable files ("Preview unavailable" tiles) and contributed no usable
     * discovery results. The enum entry stays so previously saved records keep their CC0
     * attribution; the provider, API client and discovery lane are removed.
     */
    ART_INSTITUTE("art-institute", "Art Institute of Chicago", "https://www.artic.edu/"),
    /** Approved in principle; hidden until official Reddit API authorization is available. */
    REDDIT("reddit", "Reddit", "https://www.reddit.com/"),
    /** Legacy source retained only so older Room records and downloads stay readable. */
    OPENVERSE("openverse", "Openverse (saved)", "https://openverse.org"),
    /** Keeps older Room records and downloads readable without treating the provider as integrated. */
    ARCHIVED("archived", "Saved item", "");

    val isIntegrated: Boolean get() = this in integrated

    companion object {
        val integrated: List<WallpaperSource> = listOf(WALLHAVEN, NASA_APOD)
        fun fromId(id: String): WallpaperSource? = entries.firstOrNull { it.id == id }
    }
}

@Immutable
data class WallpaperTag(val id: Long?, val name: String)

/**
 * Provider-agnostic record. Original dimensions, attribution and licence metadata are retained so
 * filters, saved records, downloads and the wallpaper-setting flow can make decisions from facts.
 */
@Immutable
data class Wallpaper(
    val source: WallpaperSource,
    /** The provider's own stable identifier. */
    val sourceId: String,
    /** Canonical page of this wallpaper on the provider's website. */
    val pageUrl: String,
    /** Provider-supplied preview suitable for a gallery tile. */
    val thumbUrl: String,
    /** Provider-supplied preview used by the detail screen before the original is requested. */
    val previewUrl: String,
    /** Original file URL, never a locally recompressed or resized substitute. */
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
    /** Provider-supplied title. Null means the source did not give us one. */
    val title: String? = null,
    /** Source-provided rights, licence, and credit statement when supplied. */
    val attribution: String? = null,
    /** Exact source licence or rights-policy identifier, when known. */
    val licenseCode: String? = null,
    val licenseVersion: String? = null,
    val licenseUrl: String? = null,
    /** Additional source/catalogue metadata preserved for older records. */
    val providerName: String? = null,
    val catalogSource: String? = null,
    /** False unless the provider URL and known licence allow an original file to be saved. */
    val downloadAllowed: Boolean = true,
    /** Non-derivative licences may be downloaded but cannot be cropped/set as wallpaper. */
    val setWallpaperAllowed: Boolean = true,
) {
    val key: String get() = keyOf(source, sourceId)

    /** width / height. Portrait images are < 1, landscape > 1. */
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 0f

    val hasKnownDimensions: Boolean get() = width > 0 && height > 0

    companion object {
        fun keyOf(source: WallpaperSource, sourceId: String) = "${source.id}:$sourceId"
        fun sourceOfKey(key: String): WallpaperSource? = WallpaperSource.fromId(key.substringBefore(':'))
        fun idOfKey(key: String): String = key.substringAfter(':')
    }
}

/** User-facing sort choices. Provider-specific sort orders are never described as a global rank. */
enum class SortOption(val label: String) {
    SOURCE_DEFAULT("Source order"),
    RELEVANCE("Relevance"),
    NEWEST("Newest"),
    POPULAR("Popular"),
    RANDOM("Random"),
}

enum class Orientation(val label: String) {
    ANY("Any"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    SQUARE("Square"),
}

enum class WallhavenCategory(val label: String, val bitIndex: Int) {
    GENERAL("General", 0),
    ANIME("Anime", 1),
    PEOPLE("People", 2),
}

/** Everything the user can change on the filter screen. */
@Immutable
data class WallpaperFilter(
    /** Empty set means all currently integrated sources. */
    val sources: Set<WallpaperSource> = emptySet(),
    val aspect: AspectFilter = AspectFilter.Any,
    val resolution: ResolutionFilter = ResolutionFilter.Any,
    val orientation: Orientation = Orientation.ANY,
    val sort: SortOption = SortOption.SOURCE_DEFAULT,
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
            sort != SortOption.SOURCE_DEFAULT,
            wallhavenCategories.isNotEmpty(),
            colorHex != null,
        ).count { it }

    val requiredSources: Set<WallpaperSource>
        get() = buildSet {
            if (wallhavenCategories.isNotEmpty() || colorHex != null) add(WallpaperSource.WALLHAVEN)
        }

    val hasConflictingSourceFilters: Boolean get() = requiredSources.size > 1

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
