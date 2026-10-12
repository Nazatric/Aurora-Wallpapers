package com.auroro.wallpapers.core.model

/** Normalizes enabled source selection without carrying Wallhaven-only filters into other catalogues. */
object SearchSourceSelection {
    fun select(
        current: WallpaperFilter,
        requested: Set<WallpaperSource>,
        query: String,
    ): WallpaperFilter {
        val selected = requested.intersect(WallpaperSource.integrated.toSet())
        if (requested.isNotEmpty() && selected.isEmpty()) {
            // Keep an unavailable selection visibly unavailable; an empty set means "All sources".
            return current.copy(sources = requested, wallhavenCategories = emptySet(), colorHex = null)
        }
        val normalizedQuery = query.trim()
        val wallhavenOnly = selected == setOf(WallpaperSource.WALLHAVEN)
        val nasaOnly = selected == setOf(WallpaperSource.NASA_APOD)
        val artOnly = selected == setOf(WallpaperSource.ART_INSTITUTE)
        return when {
            selected.isEmpty() -> current.copy(
                sources = emptySet(),
                wallhavenCategories = emptySet(),
                colorHex = null,
            )
            wallhavenOnly -> current.copy(
                sources = selected,
                sort = if (normalizedQuery.isEmpty() && current.sort == SortOption.RELEVANCE) {
                    SortOption.NEWEST
                } else current.sort,
            )
            nasaOnly -> current.copy(
                sources = selected,
                sort = if (normalizedQuery.isEmpty() && current.sort !in setOf(SortOption.SOURCE_DEFAULT, SortOption.NEWEST)) {
                    SortOption.SOURCE_DEFAULT
                } else current.sort,
                wallhavenCategories = emptySet(),
                colorHex = null,
            )
            artOnly -> current.copy(
                sources = selected,
                sort = if (normalizedQuery.isEmpty()) {
                    SortOption.SOURCE_DEFAULT
                } else if (current.sort !in setOf(SortOption.SOURCE_DEFAULT, SortOption.RELEVANCE)) {
                    SortOption.SOURCE_DEFAULT
                } else current.sort,
                wallhavenCategories = emptySet(),
                colorHex = null,
            )
            else -> current.copy(
                sources = selected,
                sort = if (normalizedQuery.isEmpty() && current.sort in setOf(SortOption.RELEVANCE, SortOption.POPULAR, SortOption.RANDOM)) {
                    SortOption.SOURCE_DEFAULT
                } else current.sort,
                wallhavenCategories = emptySet(),
                colorHex = null,
            )
        }
    }
}
