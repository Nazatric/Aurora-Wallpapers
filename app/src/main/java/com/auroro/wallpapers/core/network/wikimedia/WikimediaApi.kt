package com.auroro.wallpapers.core.network.wikimedia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Wikimedia Commons action API. One call returns file pages with pixel size, mime type and
 * extmetadata (licence short name + artist), which is everything rights gating needs. The
 * iiurlwidth parameter makes Commons render a sane preview instead of shipping multi-MB originals.
 */
interface WikimediaApi {
    @GET("w/api.php")
    suspend fun searchFiles(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("formatversion") formatVersion: Int = 2,
        @Query("generator") generator: String = "search",
        @Query("gsrsearch") search: String,
        @Query("gsrnamespace") namespace: Int = 6,
        @Query("gsrlimit") limit: Int,
        @Query("prop") prop: String = "imageinfo",
        @Query("iiprop") infoProps: String = "url|size|mime|extmetadata",
        @Query("iiurlwidth") thumbWidth: Int = 1600,
    ): WikimediaResponse

    companion object {
        const val BASE_URL = "https://commons.wikimedia.org/"
    }
}

@Serializable
data class WikimediaResponse(val query: WikimediaQuery? = null)

@Serializable
data class WikimediaQuery(val pages: List<WikimediaPage> = emptyList())

@Serializable
data class WikimediaPage(
    val title: String? = null,
    val imageinfo: List<WikimediaImageInfo> = emptyList(),
)

@Serializable
data class WikimediaImageInfo(
    val url: String? = null,
    val thumburl: String? = null,
    val descriptionurl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mime: String? = null,
    val extmetadata: WikimediaExtMetadata? = null,
)

@Serializable
data class WikimediaExtMetadata(
    @SerialName("LicenseShortName") val licenseShortName: WikimediaMetaValue? = null,
    @SerialName("Artist") val artist: WikimediaMetaValue? = null,
)

@Serializable
data class WikimediaMetaValue(val value: String? = null)
