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
        WallpaperSource.ABYSS -> "https://images.alphacoders.com/${id.take(2)}/$id.jpg"
        WallpaperSource.UNSPLASH -> "https://images.unsplash.com/photo-$id"
    },
    page: String = when (source) {
        WallpaperSource.WALLHAVEN -> "https://wallhaven.cc/w/$id"
        WallpaperSource.ABYSS -> "https://wall.alphacoders.com/big.php?i=$id"
        WallpaperSource.UNSPLASH -> "https://unsplash.com/photos/$id?utm_source=auroro_wallpapers&utm_medium=referral"
    },
    tags: List<WallpaperTag> = listOf(WallpaperTag(7, "nature")),
) = Wallpaper(
    source = source,
    sourceId = id,
    pageUrl = page,
    thumbUrl = "https://th.wallhaven.cc/lg/ab/abc123.jpg",
    previewUrl = "https://th.wallhaven.cc/orig/ab/abc123.jpg",
    originalUrl = original,
    width = width,
    height = height,
    fileSizeBytes = 2_048,
    mimeType = "image/jpeg",
    creatorName = "Uploader",
    creatorUrl = "https://wallhaven.cc/user/Uploader",
    category = "general",
    tags = tags,
    colors = listOf("#66cccc"),
    createdAt = "2026-01-01 00:00:00",
    views = 20,
    favorites = 3,
)
