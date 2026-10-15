package com.auroro.wallpapers.core.network.met

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The Metropolitan Museum of Art Collection API. Search returns object IDs only, so the provider
 * fetches object records concurrently and keeps just `isPublicDomain` artworks with a primary
 * image — the Museum's Open Access guarantee.
 */
interface MetApi {
    /**
     * v1.1 paginated search: the old unbounded v1 search now answers HTTP 410. Offset+limit
     * pages of up to 500 IDs, hard ceiling of 10,000 results per query.
     */
    @GET("v1.1/search")
    suspend fun search(
        @Query("q") query: String?,
        @Query("hasImages") hasImages: Boolean = true,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 500,
    ): MetSearchResponse

    @GET("v1/objects/{id}")
    suspend fun obj(@Path("id") objectId: Int): MetObjectDto

    companion object {
        const val BASE_URL = "https://collectionapi.metmuseum.org/public/collection/"
        const val ID_CEILING = 10_000
        const val SEARCH_CHUNK = 500
    }
}

@Serializable
data class MetSearchResponse(
    val total: Int? = null,
    val objectIDs: List<Int>? = null,
)

@Serializable
data class MetObjectDto(
    val objectID: Int? = null,
    val title: String? = null,
    @SerialName("artistDisplayName") val artistDisplayName: String? = null,
    @SerialName("objectDate") val objectDate: String? = null,
    val classification: String? = null,
    val medium: String? = null,
    @SerialName("primaryImage") val primaryImage: String? = null,
    @SerialName("primaryImageSmall") val primaryImageSmall: String? = null,
    @SerialName("isPublicDomain") val isPublicDomain: Boolean? = null,
    @SerialName("objectURL") val objectUrl: String? = null,
)
