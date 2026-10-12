package com.auroro.wallpapers.core.network.nasa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * NASA's current APOD Basic endpoint. The former api.nasa.gov/planetary/apod endpoint returns
 * stale/placeholder data following NASA's site migration; date lookups use the yyMMdd path.
 */
interface NasaApodApi {
    @GET("wp-json/wp/v2/apod-basic")
    suspend fun latest(
        @Query("per_page") pageSize: Int,
        @Query("page") page: Int,
    ): List<NasaApodDto>

    @GET("wp-json/wp/v2/apod-basic/{date}")
    suspend fun byDate(@Path("date") nasaDate: String): NasaApodDto

    companion object {
        const val BASE_URL = "https://science.nasa.gov/"
        const val DOCUMENTATION_URL = "https://github.com/nasa/apod-api"
    }
}

@Serializable
data class NasaApodDto(
    @SerialName("post_id") val postId: Int? = null,
    val date: String? = null,
    val title: String? = null,
    val permalink: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    /** On the current endpoint `url` can be an article URL; `hdurl` is the actual media URL. */
    val url: String? = null,
    val hdurl: String? = null,
    val explanation: String? = null,
    val copyright: String? = null,
    val credit: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

@Serializable
data class NasaApodPage(
    val items: List<NasaApodDto> = emptyList(),
)
