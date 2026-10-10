package com.auroro.wallpapers.core.model

/**
 * Normalizes source selection without carrying source-specific filters into an incompatible feed.
 * Empty selection means every enabled integrated catalogue. Search has no implicit provider other
 * than the choice made by this policy, so blank-query Wallhaven feeds use its date-added endpoint
 * ordering rather than the API's empty relevance response.
 */
object SearchSourceSelection {
    fun select(
        current: WallpaperFilter,
        requested: Set<WallpaperSource>,
        query: String,
    ): WallpaperFilter {
        val selected = requested.intersect(WallpaperSource.integrated.toSet())
        val normalizedQuery = query.trim()

        return when {
            selected.isEmpty() -> current.copy(
                sources = emptySet(),
                wallhavenCategories = emptySet(),
                colorHex = null,
                openverseTag = null,
                openverseCategory = null,
                openverseLicense = null,
            )

            selected == setOf(WallpaperSource.WALLHAVEN) -> current.copy(
                sources = selected,
                sort = if (normalizedQuery.isEmpty() && current.sort == SortOption.RELEVANCE) {
                    SortOption.NEWEST
                } else {
                    current.sort
                },
                openverseTag = null,
                openverseCategory = null,
                openverseLicense = null,
            )

            selected == setOf(WallpaperSource.OPENVERSE) -> current.copy(
                sources = selected,
                sort = SortOption.RELEVANCE,
                wallhavenCategories = emptySet(),
                colorHex = null,
            )

            else -> current.copy(
                sources = selected,
                wallhavenCategories = emptySet(),
                colorHex = null,
                openverseTag = null,
                openverseCategory = null,
                openverseLicense = null,
            )
        }
    }
}
