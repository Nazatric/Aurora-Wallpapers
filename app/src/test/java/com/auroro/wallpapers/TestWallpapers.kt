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
        WallpaperSource.OPENVERSE -> "https://media.example.org/images/$id.jpg"
        WallpaperSource.ARCHIVED -> "https://legacy.example.org/images/$id.jpg"
    },
    page: String = when (source) {
        WallpaperSource.WALLHAVEN -> "https://wallhaven.cc/w/$id"
        WallpaperSource.OPENVERSE -> "https://openverse.org/image/$id"
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
