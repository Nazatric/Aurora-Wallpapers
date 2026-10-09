package com.auroro.wallpapers.core.network.openverse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** Current public Openverse Images API v1; anonymous read access is supported. */
interface OpenverseApi {
    @GET("images/")
    suspend fun images(@QueryMap params: Map<String, String>): Response<OpenverseSearchResponse>

    @GET("images/{id}/")
    suspend fun image(@Path("id") id: String): OpenverseImageDto

    companion object {
        const val BASE_URL = "https://api.openverse.org/v1/"
        const val DOCUMENTATION_URL = "https://api.openverse.org/v1/"
    }
}

@Serializable
data class OpenverseSearchResponse(
    @SerialName("result_count") val resultCount: Int = 0,
    @SerialName("page_count") val pageCount: Int = 0,
    @SerialName("page_size") val pageSize: Int = 20,
    val page: Int = 1,
    val results: List<OpenverseImageDto> = emptyList(),
    val warnings: List<OpenverseWarningDto> = emptyList(),
)

@Serializable
data class OpenverseWarningDto(
    val code: String = "",
    val message: String = "",
)

/** Fields used for image display, source attribution, licensing, and real local filters. */
@Serializable
data class OpenverseImageDto(
    val id: String = "",
    val title: String? = null,
    @SerialName("foreign_landing_url") val foreignLandingUrl: String = "",
    val url: String = "",
    val thumbnail: String? = null,
    val creator: String? = null,
    @SerialName("creator_url") val creatorUrl: String? = null,
    val license: String = "",
    @SerialName("license_version") val licenseVersion: String? = null,
    @SerialName("license_url") val licenseUrl: String? = null,
    val provider: String? = null,
    val source: String? = null,
    val category: String? = null,
    val filesize: Long? = null,
    val filetype: String? = null,
    val tags: List<OpenverseTagDto> = emptyList(),
    val attribution: String? = null,
    val mature: Boolean? = null,
    val height: Int? = null,
    val width: Int? = null,
    @SerialName("indexed_on") val indexedOn: String? = null,
)

@Serializable
data class OpenverseTagDto(
    val name: String = "",
)
