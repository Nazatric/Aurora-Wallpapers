package com.auroro.wallpapers

import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag

internal fun wallpaper(
    source: WallpaperSource = WallpaperSource.WALLHAVEN,
    id: String = "abc123",
    width: Int = 2160,
    height: Int = 3840,
    original: String = when (source) {
        WallpaperSource.WALLHAVEN -> "https://w.wallhaven.cc/full/${id.take(2)}/wallhaven-$id.jpg"
        WallpaperSource.NASA_APOD -> "https://images.nasa.gov/apod/$id.jpg"
        WallpaperSource.ART_INSTITUTE -> "https://www.artic.edu/iiif/2/$id/full/max,/0/default.jpg"
        WallpaperSource.REDDIT -> "https://www.reddit.com/r/wallpaper/comments/$id/"
        WallpaperSource.OPENVERSE -> "https://media.example.org/images/$id.jpg"
        WallpaperSource.WIKIMEDIA -> "https://upload.wikimedia.org/wikipedia/commons/$id.jpg"
        WallpaperSource.MET_MUSEUM -> "https://images.metmuseum.org/CRDImages/ep/original/$id.jpg"
        WallpaperSource.CLEVELAND_ART -> "https://openaccess-api.clevelandart.org/api/artworks/img/$id.jpg"
        WallpaperSource.ARCHIVED -> "https://legacy.example.org/images/$id.jpg"
    },
    page: String = when (source) {
        WallpaperSource.WALLHAVEN -> "https://wallhaven.cc/w/$id"
        WallpaperSource.NASA_APOD -> "https://science.nasa.gov/apod/"
        WallpaperSource.ART_INSTITUTE -> "https://www.artic.edu/artworks/$id"
        WallpaperSource.REDDIT -> "https://www.reddit.com/r/wallpaper/comments/$id/"
        WallpaperSource.OPENVERSE -> "https://openverse.org/image/$id"
        WallpaperSource.WIKIMEDIA -> "https://commons.wikimedia.org/wiki/File:$id"
        WallpaperSource.MET_MUSEUM -> "https://www.metmuseum.org/art/collection/search/$id"
        WallpaperSource.CLEVELAND_ART -> "https://www.clevelandart.org/art/$id"
        WallpaperSource.ARCHIVED -> "https://legacy.example.org/item/$id"
    },
    tags: List<WallpaperTag> = listOf(WallpaperTag(7, "nature")),
) = Wallpaper(
    source = source,
    sourceId = id,
    pageUrl = page,
    thumbUrl = original,
    previewUrl = original,
    originalUrl = original,
    width = width,
    height = height,
    fileSizeBytes = 2_048,
    mimeType = "image/jpeg",
    creatorName = "Uploader",
    creatorUrl = "https://wallhaven.cc/user/Uploader",
    category = if (source == WallpaperSource.OPENVERSE) "photograph" else "general",
    tags = tags,
    colors = listOf("#66cccc"),
    createdAt = "2026-01-01 00:00:00",
    views = 20,
    favorites = 3,
)
