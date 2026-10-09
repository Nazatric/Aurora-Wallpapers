package com.auroro.wallpapers.core.model

/**
 * Client-side filtering using the *real* image dimensions reported by each provider.
 * Providers may also pre-filter on the server for efficiency, but this is the authoritative check,
 * so results never depend on a provider's own rounding or on titles/categories.
 */
object LocalFilter {

    fun matches(w: Wallpaper, filter: WallpaperFilter, aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE): Boolean {
        // Dimensions are required for any geometry filter; unknown size can't be verified, so it's excluded.
        val needsGeometry = filter.aspect != AspectFilter.Any ||
            filter.resolution != ResolutionFilter.Any ||
            filter.orientation != Orientation.ANY
        if (needsGeometry && !w.hasKnownDimensions) return false

        filter.aspect.targetRatio?.let { target ->
            if (!AspectMath.matches(w.width, w.height, target, aspectTolerance)) return false
        }
        if (!filter.resolution.matches(w.width, w.height)) return false
        when (filter.orientation) {
            Orientation.ANY -> Unit
            Orientation.PORTRAIT -> if (w.height <= w.width) return false
            Orientation.LANDSCAPE -> if (w.width <= w.height) return false
        }
        return true
    }

    fun apply(items: List<Wallpaper>, filter: WallpaperFilter, aspectTolerance: Float = AspectMath.DEFAULT_TOLERANCE): List<Wallpaper> =
        if (filter.aspect == AspectFilter.Any && filter.resolution == ResolutionFilter.Any && filter.orientation == Orientation.ANY) {
            items
        } else {
            items.filter { matches(it, filter, aspectTolerance) }
        }
}
