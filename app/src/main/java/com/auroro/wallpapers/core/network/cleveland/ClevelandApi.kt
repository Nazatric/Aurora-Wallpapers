package com.auroro.wallpapers.core.network.cleveland

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Cleveland Museum of Art Open Access API. One call returns complete artwork records — image URL,
 * pixel dimensions, creators — all under CC0 when requested with cc0=1.
 */
interface ClevelandApi {
    @GET("api/artworks/")
    suspend fun artworks(
        @Query("q") query: String?,
        @Query("has_image") hasImage: Int = 1,
        @Query("cc0") cc0: Int = 1,
        @Query("limit") limit: Int,
        @Query("skip") skip: Int,
    ): ClevelandResponse

    companion object {
        const val BASE_URL = "https://openaccess-api.clevelandart.org/"
    }
}

@Serializable
data class ClevelandResponse(
    val info: ClevelandInfo? = null,
    val data: List<ClevelandArtwork> = emptyList(),
)

@Serializable
data class ClevelandInfo(val total: Int? = null)

@Serializable
data class ClevelandArtwork(
    val id: Int? = null,
    val title: String? = null,
    val url: String? = null,
    @SerialName("creation_date") val creationDate: String? = null,
    @SerialName("share_license") val shareLicense: String? = null,
    val creators: List<ClevelandCreator> = emptyList(),
    val images: List<ClevelandImage> = emptyList(),
)

@Serializable
data class ClevelandCreator(val description: String? = null)

@Serializable
data class ClevelandImage(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
