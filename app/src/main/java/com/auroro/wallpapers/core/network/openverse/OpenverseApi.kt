package com.auroro.wallpapers.core.network.openverse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Openverse public catalogue API (api.openverse.org). Results carry licence, creator and pixel
 * dimensions, so rights gating and measured-aspect cards can work from facts. The `commercial`
 * licence type excludes NC-licensed work; maturity filtering stays on.
 */
interface OpenverseApi {
    @GET("v1/images/")
    suspend fun images(
        @Query("q") query: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("license_type") licenseType: String = "commercial",
        @Query("mature") mature: String = "false",
    ): OpenverseImagePage

    companion object {
        const val BASE_URL = "https://api.openverse.org/"
    }
}

@Serializable
data class OpenverseImagePage(
    @SerialName("result_count") val resultCount: Long? = null,
    @SerialName("page_count") val pageCount: Int? = null,
    val results: List<OpenverseImageDto> = emptyList(),
)

@Serializable
data class OpenverseImageDto(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    @SerialName("foreign_landing_url") val foreignLandingUrl: String? = null,
    val creator: String? = null,
    @SerialName("creator_url") val creatorUrl: String? = null,
    val license: String? = null,
    @SerialName("license_version") val licenseVersion: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val category: String? = null,
    val tags: List<OpenverseTag> = emptyList(),
    val provider: String? = null,
    val source: String? = null,
)

@Serializable
data class OpenverseTag(val name: String? = null)
