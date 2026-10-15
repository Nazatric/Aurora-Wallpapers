package com.auroro.wallpapers.core.network.artic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Headers
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Art Institute of Chicago public API, restricted to `is_public_domain` works. The 1.3.0 removal
 * happened because search results carried no dimensions and no displayable files; this integration
 * fixes both by building deterministic IIIF URLs from `image_id` (server-side resize for previews,
 * `/full/full/` for the original) — unknown pixel sizes are handled by measured-aspect cards.
 */
interface ArtInstituteApi {
    @Headers(
        "Accept: application/json",
        "User-Agent: Mozilla/5.0 (Android 15; Mobile; AuroroWallpapers/1.11.0; +https://github.com/Nazatric/Aurora-Wallpapers)",
    )
    @GET("api/v1/artworks/search")
    suspend fun searchPublicDomain(
        @Query("q") query: String?,
        @Query("limit") limit: Int,
        @Query("page") page: Int,
        @Query("query[term][is_public_domain]") publicDomain: Boolean = true,
        @Query("fields") fields: String = "id,title,artist_display,date_display,image_id,classification_title,department_title",
    ): ArtSearchResponse

    companion object {
        const val BASE_URL = "https://api.artic.edu/"
        const val IIIF_BASE = "https://www.artic.edu/iiif/2/"
    }
}

@Serializable
data class ArtSearchResponse(
    val data: List<ArtworkDto> = emptyList(),
    val pagination: ArtPagination? = null,
)

@Serializable
data class ArtworkDto(
    val id: Long? = null,
    val title: String? = null,
    @SerialName("artist_display") val artistDisplay: String? = null,
    @SerialName("date_display") val dateDisplay: String? = null,
    @SerialName("image_id") val imageId: String? = null,
    @SerialName("classification_title") val classificationTitle: String? = null,
    @SerialName("department_title") val departmentTitle: String? = null,
)

@Serializable
data class ArtPagination(
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
)
