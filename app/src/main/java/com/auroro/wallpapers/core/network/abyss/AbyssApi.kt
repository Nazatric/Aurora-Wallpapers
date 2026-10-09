package com.auroro.wallpapers.core.network.abyss

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.QueryMap

/**
 * Alpha Coders API 3.0 (the programmatic interface behind Wallpaper Abyss):
 * `https://api.alphacoders.com/3.0?auth=KEY&method=...`, documented at https://api.alphacoders.com/api/instructions.
 *
 * Access requires a personal API key from a paid Alpha Coders API subscription, which Auroro never ships.
 * Every call is made with the user's own key.
 */
interface AbyssApi {
    @GET("3.0")
    suspend fun call(@QueryMap params: Map<String, String>): AbyssResponse

    companion object {
        const val BASE_URL = "https://api.alphacoders.com/"
    }
}

/** The API historically mixes numbers and numeric strings, so numeric fields are read leniently. */
@Serializable
data class AbyssResponse(
    val success: Boolean = false,
    val error: String? = null,
    val wallpapers: List<AbyssWallpaperDto> = emptyList(),
)

@Serializable
data class AbyssWallpaperDto(
    val id: JsonElement? = null,
    val width: JsonElement? = null,
    val height: JsonElement? = null,
    @SerialName("file_type") val fileType: String? = null,
    @SerialName("file_size") val fileSize: JsonElement? = null,
    @SerialName("url_image") val urlImage: String? = null,
    @SerialName("url_thumb") val urlThumb: String? = null,
    @SerialName("url_page") val urlPage: String? = null,
    val category: String? = null,
    @SerialName("sub_category") val subCategory: String? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_id") val userId: JsonElement? = null,
)
