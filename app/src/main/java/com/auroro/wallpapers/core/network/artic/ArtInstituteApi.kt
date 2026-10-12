package com.auroro.wallpapers.core.network.artic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

/** Official Art Institute of Chicago API, with its documented `params` JSON query form. */
interface ArtInstituteApi {
    @GET("artworks/search")
    suspend fun search(@Query("params") params: String): ArtInstituteSearchResponse

    companion object {
        const val BASE_URL = "https://api.artic.edu/api/v1/"
        const val IMAGE_BASE_URL = "https://www.artic.edu/"
        const val DOCUMENTATION_URL = "https://api.artic.edu/docs/"
        const val OPEN_ACCESS_URL = "https://www.artic.edu/open-access/open-access-images"
    }
}

/** IIIF Image API v2 metadata supplies the original pixel dimensions used for orientation filters. */
interface ArtInstituteIiifApi {
    @GET("iiif/2/{imageId}/info.json")
    suspend fun imageInfo(@Path("imageId") imageId: String): IiifImageInfo
}

@Serializable
data class ArtInstituteSearchResponse(
    val data: List<ArtInstituteArtworkDto> = emptyList(),
    val pagination: ArtInstitutePagination = ArtInstitutePagination(),
)

@Serializable
data class ArtInstitutePagination(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    val limit: Int = 0,
)

@Serializable
data class ArtInstituteArtworkDto(
    val id: Int = 0,
    val title: String? = null,
    @SerialName("image_id") val imageId: String? = null,
    @SerialName("is_public_domain") val isPublicDomain: Boolean = false,
    @SerialName("artist_title") val artistTitle: String? = null,
    @SerialName("artist_display") val artistDisplay: String? = null,
    @SerialName("artist_id") val artistId: Int? = null,
    @SerialName("date_display") val dateDisplay: String? = null,
    @SerialName("artwork_type_title") val artworkTypeTitle: String? = null,
    @SerialName("classification_title") val classificationTitle: String? = null,
    @SerialName("style_title") val styleTitle: String? = null,
    @SerialName("subject_titles") val subjectTitles: List<String> = emptyList(),
    @SerialName("main_reference_number") val referenceNumber: String? = null,
    @SerialName("medium_display") val mediumDisplay: String? = null,
    @SerialName("credit_line") val creditLine: String? = null,
    val dimensions: String? = null,
    val thumbnail: ArtInstituteThumbnail? = null,
)

@Serializable
data class ArtInstituteThumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("alt_text") val altText: String? = null,
)

@Serializable
data class IiifImageInfo(
    val id: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)
