package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SearchSourceSelection
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.wallhaven.WallhavenApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class WallhavenHttpSearchIntegrationTest {
    @Test fun emptyWallhavenSearchProducesAUsableHttpRequestAndRenderableWallpaper() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body(
                        """
                        {
                          "data": [{
                            "id": "abc123",
                            "url": "https://wallhaven.cc/w/abc123",
                            "purity": "sfw",
                            "category": "general",
                            "dimension_x": 3840,
                            "dimension_y": 2160,
                            "path": "https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg",
                            "thumbs": {
                              "large": "https://th.wallhaven.cc/lg/ab/abc123.jpg",
                              "original": "https://th.wallhaven.cc/orig/ab/abc123.jpg"
                            }
                          }],
                          "meta": { "current_page": 1, "last_page": 2, "total": 41 }
                        }
                        """.trimIndent(),
                    )
                    .build(),
            )
            val retrofit = Retrofit.Builder()
                .baseUrl(server.url("/api/v1/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(
                    Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()),
                )
                .build()
            val api = retrofit.create(WallhavenApi::class.java)
            val filter = SearchSourceSelection.select(
                WallpaperFilter.Default,
                setOf(WallpaperSource.WALLHAVEN),
                query = "",
            )

            val page = WallhavenProvider(api).fetchPage(FeedRequest(filter = filter), PageCursor())
            val request = server.takeRequest()

            assertEquals("/api/v1/search", request.url.encodedPath)
            assertEquals("date_added", request.url.queryParameter("sorting"))
            assertEquals("100", request.url.queryParameter("purity"))
            assertEquals("111", request.url.queryParameter("categories"))
            assertFalse(request.url.queryParameterNames.contains("q"))
            assertEquals(1, page.items.size)
            assertEquals("abc123", page.items.single().sourceId)
            assertEquals(3840, page.items.single().width)
            assertEquals(2160, page.items.single().height)
            assertEquals("https://th.wallhaven.cc/orig/ab/abc123.jpg", page.items.single().thumbUrl)
            assertEquals("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg", page.items.single().originalUrl)
            assertTrue(page.next != null)
        } finally {
            server.close()
        }
    }
}
