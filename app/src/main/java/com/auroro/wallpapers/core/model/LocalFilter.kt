package com.auroro.wallpapers.core.model

/**
 * Authoritative client-side filters. Geometry always uses the provider's real width and height;
 * the shape of a Compose card, title, or thumbnail is never used to infer an image's dimensions.
 */
object LocalFilter {
    private val commercialLicenses = setOf("cc0", "pdm", "by", "by-sa", "by-nd", "sampling+")
    private val modificationLicenses = setOf("cc0", "pdm", "by", "by-sa", "by-nc", "by-nc-sa", "sampling+", "nc-sampling+")
    private val creativeCommonsLicenses = setOf("cc0", "by", "by-sa", "by-nd", "by-nc", "by-nc-sa", "by-nc-nd", "sampling+", "nc-sampling+")

    fun matches(
        w: Wallpaper,
        filter: WallpaperFilter,
        aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE,
    ): Boolean {
        if (filter.sources.isNotEmpty() && w.source !in filter.sources) return false
        if (filter.hasConflictingSourceFilters) return false
        if (filter.requiredSources.isNotEmpty() && w.source !in filter.requiredSources) return false
        if (filter.reducePotentiallyExplicitContent && ContentSensitivity.isPotentiallyExplicit(w)) return false

        val needsGeometry = filter.aspect != AspectFilter.Any ||
            filter.resolution != ResolutionFilter.Any ||
            filter.orientation != Orientation.ANY
        if (needsGeometry && !w.hasKnownDimensions) return false

        filter.aspect.targetRatio?.let { target ->
            if (!AspectMath.matches(w.width, w.height, target, aspectTolerance)) return false
        }
        if (!filter.resolution.matches(w.width, w.height)) return false
        if (filter.orientation != Orientation.ANY &&
            AspectMath.classifyOrientation(w.width, w.height) != filter.orientation
        ) return false

        if (filter.wallhavenCategories.isNotEmpty()) {
            if (w.source != WallpaperSource.WALLHAVEN) return false
            if (filter.wallhavenCategories.none { it.label.equals(w.category, ignoreCase = true) }) return false
        }
        filter.colorHex?.let { requested ->
            if (w.source != WallpaperSource.WALLHAVEN || w.colors.none { it.removePrefix("#").equals(requested.removePrefix("#"), true) }) return false
        }
        filter.openverseCategory?.let { category ->
            if (w.source != WallpaperSource.OPENVERSE || !w.category.equals(category.apiValue, ignoreCase = true)) return false
        }
        filter.openverseLicense?.let { license ->
            if (w.source != WallpaperSource.OPENVERSE || !licenseMatches(w.licenseCode, license)) return false
        }
        filter.openverseTag?.trim()?.takeIf(String::isNotEmpty)?.let { tag ->
            if (w.source != WallpaperSource.OPENVERSE || w.tags.none { it.name.contains(tag, ignoreCase = true) }) return false
        }
        return true
    }

    fun apply(
        items: List<Wallpaper>,
        filter: WallpaperFilter,
        aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE,
    ): List<Wallpaper> = if (filter == WallpaperFilter.Default && !filter.reducePotentiallyExplicitContent) items else {
        items.filter { matches(it, filter, aspectTolerance) }
    }

    private fun licenseMatches(rawCode: String?, filter: OpenverseLicense): Boolean {
        val code = rawCode?.lowercase()?.substringBeforeLast('/') ?: return false
        return when {
            !filter.isGroup -> code == filter.apiValue
            filter == OpenverseLicense.COMMERCIAL -> code in commercialLicenses
            filter == OpenverseLicense.MODIFICATION -> code in modificationLicenses
            filter == OpenverseLicense.ALL_CC -> code in creativeCommonsLicenses
            else -> false
        }
    }
}
