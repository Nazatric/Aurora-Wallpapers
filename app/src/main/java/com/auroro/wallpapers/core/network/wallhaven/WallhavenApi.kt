package com.auroro.wallpapers.core.network.wallhaven

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** Official Wallhaven API v1 (https://wallhaven.cc/help/api). No API key is used or required for public SFW content. */
interface WallhavenApi {
    @GET("search")
    suspend fun search(@QueryMap params: Map<String, String>): WallhavenSearchResponse

    @GET("w/{id}")
    suspend fun wallpaper(@Path("id") id: String): WallhavenDetailResponse

    companion object {
        const val BASE_URL = "https://wallhaven.cc/api/v1/"
    }
}
