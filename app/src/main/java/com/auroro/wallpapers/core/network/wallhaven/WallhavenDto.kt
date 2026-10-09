package com.auroro.wallpapers.core.network.wallhaven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WallhavenSearchResponse(
    val data: List<WallhavenWallpaperDto> = emptyList(),
    val meta: WallhavenMetaDto = WallhavenMetaDto(),
)

@Serializable
data class WallhavenMetaDto(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    // Wallhaven returns this as a number or a string depending on the endpoint; we never need it.
    @SerialName("per_page") val perPage: JsonElement? = null,
    val total: Int = 0,
    val seed: String? = null,
)

@Serializable
data class WallhavenDetailResponse(val data: WallhavenWallpaperDto)

@Serializable
data class WallhavenWallpaperDto(
    val id: String,
    val url: String = "",
    @SerialName("short_url") val shortUrl: String? = null,
    val uploader: WallhavenUploaderDto? = null,
    val views: Int? = null,
    val favorites: Int? = null,
    val source: String? = null,
    val purity: String? = null,
    val category: String? = null,
    @SerialName("dimension_x") val dimensionX: Int = 0,
    @SerialName("dimension_y") val dimensionY: Int = 0,
    val resolution: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_type") val fileType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val colors: List<String> = emptyList(),
    val path: String = "",
    val thumbs: WallhavenThumbsDto = WallhavenThumbsDto(),
    val tags: List<WallhavenTagDto> = emptyList(),
)

@Serializable
data class WallhavenUploaderDto(val username: String? = null, val group: String? = null)

@Serializable
data class WallhavenThumbsDto(
    val large: String = "",
    val original: String = "",
    val small: String = "",
)

@Serializable
data class WallhavenTagDto(
    val id: Long? = null,
    val name: String = "",
    val alias: String? = null,
    val category: String? = null,
    val purity: String? = null,
)
