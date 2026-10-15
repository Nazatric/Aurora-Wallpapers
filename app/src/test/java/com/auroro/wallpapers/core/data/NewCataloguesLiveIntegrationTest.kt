package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.network.HttpClients
import com.auroro.wallpapers.core.network.artic.ArtInstituteApi
import com.auroro.wallpapers.core.network.cleveland.ClevelandApi
import com.auroro.wallpapers.core.network.met.MetApi
import com.auroro.wallpapers.core.network.openverse.OpenverseApi
import com.auroro.wallpapers.core.network.wikimedia.WikimediaApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * LIVE integration proof for the 1.8.0 catalogues: real HTTPS calls against the public APIs,
 * asserting each adapter returns renderable, rights-gated wallpapers. Skipped automatically on
 * machines without outbound network (CI runners have it, so every push re-proves the adapters).
 */
class NewCataloguesLiveIntegrationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(HttpClients.base())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Before
    fun assumeNetwork() {
        val online = try {
            java.net.Socket().connect(java.net.InetSocketAddress("api.openverse.org", 443), 4000)
            true
        } catch (e: Exception) {
            false
        }
        Assume.assumeTrue("live catalogue integration needs outbound network", online)
    }

    @Test fun openverseLiveReturnsRightsGatedWallpapers() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT90S")) {
        val page = OpenverseProvider(retrofit(OpenverseApi.BASE_URL).create(OpenverseApi::class.java))
            .fetchPage(FeedRequest(query = "aurora"), PageCursor())
        assertTrue("openverse returned nothing", page.items.isNotEmpty())
        page.items.forEach {
            assertTrue(it.originalUrl.startsWith("https://"))
            assertTrue(it.attribution.orEmpty().isNotBlank())
            assertTrue(it.licenseCode in setOf("cc0", "pdm", "by", "by-sa"))
        }
    }

    @Test fun artInstituteLiveReturnsIiifWallpapers() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT90S")) {
        val page = ArtInstituteProvider(retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java))
            .fetchPage(FeedRequest(), PageCursor())
        assertTrue("artic returned nothing", page.items.isNotEmpty())
        page.items.forEach {
            assertTrue(it.previewUrl.contains("/iiif/2/"))
            assertTrue(it.originalUrl.endsWith("/full/full/0/default.jpg"))
        }
    }

    @Test fun clevelandLiveReturnsCc0WallpapersWithDimensions() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT90S")) {
        val page = ClevelandProvider(retrofit(ClevelandApi.BASE_URL).create(ClevelandApi::class.java))
            .fetchPage(FeedRequest(), PageCursor())
        assertTrue("cleveland returned nothing", page.items.isNotEmpty())
        page.items.forEach {
            assertTrue(it.licenseCode == "CC0")
            assertTrue(it.width > 0 && it.height > 0)
        }
    }

    @Test fun metLiveReturnsPublicDomainWallpapers() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT120S")) {
        val page = MetMuseumProvider(retrofit(MetApi.BASE_URL).create(MetApi::class.java))
            .fetchPage(FeedRequest(query = "landscape"), PageCursor())
        assertTrue("met returned nothing", page.items.isNotEmpty())
        page.items.forEach {
            assertTrue(it.originalUrl.contains("metmuseum.org"))
            assertTrue(it.licenseCode == "Public Domain")
        }
    }

    @Test fun wikimediaLiveReturnsLicensedWallpapersForAKeyword() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT90S")) {
        val page = WikimediaProvider(retrofit(WikimediaApi.BASE_URL).create(WikimediaApi::class.java))
            .fetchPage(FeedRequest(query = "aurora borealis"), PageCursor())
        assertTrue("wikimedia returned nothing", page.items.isNotEmpty())
        page.items.forEach {
            assertTrue(it.originalUrl.contains("wikimedia.org"))
            assertTrue(it.width >= 1200 && it.height >= 600)
        }
    }

    @Test fun randomLaneLiveReturnsScatteredMuseumsMix() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT90S")) {
        val request = FeedRequest(filter = WallpaperFilter(sort = SortOption.RANDOM))
        val artic = ArtInstituteProvider(retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java))
            .fetchPage(request, PageCursor(seed = "slice10"))
        val cleveland = ClevelandProvider(retrofit(ClevelandApi.BASE_URL).create(ClevelandApi::class.java))
            .fetchPage(request, PageCursor(seed = "slice10"))
        assertTrue("artic random lane empty", artic.items.isNotEmpty())
        assertTrue("cleveland random lane empty", cleveland.items.isNotEmpty())
    }
}
