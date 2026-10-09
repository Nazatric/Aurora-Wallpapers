package com.auroro.wallpapers.core.model

import androidx.compose.runtime.Immutable

/** The only catalogues queried by the app, plus an internal value for records saved by older versions. */
enum class WallpaperSource(val id: String, val displayName: String, val siteUrl: String) {
    WALLHAVEN("wallhaven", "Wallhaven", "https://wallhaven.cc"),
    OPENVERSE("openverse", "Openverse", "https://openverse.org"),
    /** Keeps old Room records and downloads readable without treating their former provider as integrated. */
    ARCHIVED("archived", "Saved item", "");

    val isIntegrated: Boolean get() = this == WALLHAVEN || this == OPENVERSE

    companion object {
        val integrated: List<WallpaperSource> = listOf(WALLHAVEN, OPENVERSE)
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
    /** Openverse attribution string, when supplied. */
    val attribution: String? = null,
    /** Exact Openverse licence code, e.g. `by-sa`. */
    val licenseCode: String? = null,
    val licenseVersion: String? = null,
    val licenseUrl: String? = null,
    /** Openverse metadata for the indexed publisher and the content-source slug. */
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

enum class WallhavenCategory(val label: String, val bitIndex: Int) {
    GENERAL("General", 0),
    ANIME("Anime", 1),
    PEOPLE("People", 2),
}

/** Real image categories returned by Openverse's image endpoint. */
enum class OpenverseCategory(val label: String, val apiValue: String) {
    PHOTOGRAPH("Photographs", "photograph"),
    ILLUSTRATION("Illustrations", "illustration"),
    DIGITIZED_ARTWORK("Digitized artwork", "digitized_artwork"),
}

/** Exact image licences and Openverse's documented licence groups. */
enum class OpenverseLicense(val label: String, val apiValue: String, val isGroup: Boolean = false) {
    CC0("CC0", "cc0"),
    PUBLIC_DOMAIN("Public domain", "pdm"),
    CC_BY("CC BY", "by"),
    CC_BY_SA("CC BY-SA", "by-sa"),
    CC_BY_ND("CC BY-ND", "by-nd"),
    CC_BY_NC("CC BY-NC", "by-nc"),
    CC_BY_NC_SA("CC BY-NC-SA", "by-nc-sa"),
    CC_BY_NC_ND("CC BY-NC-ND", "by-nc-nd"),
    CC_SAMPLING_PLUS("CC Sampling+", "sampling+"),
    CC_NC_SAMPLING_PLUS("CC NC Sampling+", "nc-sampling+"),
    COMMERCIAL("Commercial-use group", "commercial", true),
    MODIFICATION("Adaptation group", "modification", true),
    ALL_CC("All CC incl. CC0 & sampling", "all-cc", true),
}

/** Everything the user can change on the filter screen. */
@Immutable
data class WallpaperFilter(
    /** Empty set means both integrated sources. */
    val sources: Set<WallpaperSource> = emptySet(),
    val aspect: AspectFilter = AspectFilter.Any,
    val resolution: ResolutionFilter = ResolutionFilter.Any,
    val orientation: Orientation = Orientation.ANY,
    val sort: SortOption = SortOption.RELEVANCE,
    /** Wallhaven only. Empty = all three. */
    val wallhavenCategories: Set<WallhavenCategory> = emptySet(),
    /** Wallhaven only; lowercase `rrggbb` without '#'. */
    val colorHex: String? = null,
    /** Openverse only. A tag must be reported on the returned image. */
    val openverseTag: String? = null,
    val openverseCategory: OpenverseCategory? = null,
    val openverseLicense: OpenverseLicense? = null,
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
            !openverseTag.isNullOrBlank(),
            openverseCategory != null,
            openverseLicense != null,
        ).count { it }

    val requiredSources: Set<WallpaperSource>
        get() = buildSet {
            if (wallhavenCategories.isNotEmpty() || colorHex != null) add(WallpaperSource.WALLHAVEN)
            if (!openverseTag.isNullOrBlank() || openverseCategory != null || openverseLicense != null) add(WallpaperSource.OPENVERSE)
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
