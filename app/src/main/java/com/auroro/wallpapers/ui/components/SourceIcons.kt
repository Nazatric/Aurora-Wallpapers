package com.auroro.wallpapers.ui.components

import androidx.annotation.DrawableRes
import com.auroro.wallpapers.R
import com.auroro.wallpapers.core.model.WallpaperSource

/**
 * Catalogue marks for chips and cards. All vectors come from google/material-design-icons
 * (Apache-2.0), tint stripped and pinned to #FFFFFFFF so the chip content colour drives them.
 */
@DrawableRes
fun WallpaperSource.iconRes(): Int = when (this) {
    WallpaperSource.WALLHAVEN -> R.drawable.ic_source_wallhaven
    WallpaperSource.NASA_APOD -> R.drawable.ic_source_nasa
    WallpaperSource.OPENVERSE -> R.drawable.ic_source_openverse
    WallpaperSource.ART_INSTITUTE -> R.drawable.ic_source_artic
    WallpaperSource.WIKIMEDIA -> R.drawable.ic_source_wikimedia
    WallpaperSource.MET_MUSEUM -> R.drawable.ic_source_met
    WallpaperSource.CLEVELAND_ART -> R.drawable.ic_source_cleveland
    WallpaperSource.REDDIT, WallpaperSource.ARCHIVED -> R.drawable.ic_source_openverse
}
