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
        val page = articTolerant {
            ArtInstituteProvider(retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java))
                .fetchPage(FeedRequest(), PageCursor())
        }
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
        val api = retrofit(WikimediaApi.BASE_URL).create(WikimediaApi::class.java)
        val raw = api.searchFiles(search = "filetype:bitmap aurora borealis", limit = 5)
        val rawPages = raw.query?.pages?.size ?: -1
        assertTrue("wikimedia raw response had no pages ($rawPages)", rawPages > 0)
        val page = WikimediaProvider(api).fetchPage(FeedRequest(query = "aurora borealis"), PageCursor())
        assertTrue("wikimedia mapped 0 of $rawPages raw pages", page.items.isNotEmpty())
        page.items.forEach {
            assertTrue(it.originalUrl.contains("wikimedia.org"))
            assertTrue(it.width >= 1200 && it.height >= 600)
        }
    }

    @Test fun randomLaneLiveReturnsScatteredMuseumsMix() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT90S")) {
        val request = FeedRequest(filter = WallpaperFilter(sort = SortOption.RANDOM))
        val artic = articTolerant {
            ArtInstituteProvider(retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java))
                .fetchPage(request, PageCursor(seed = "slice10"))
        }
        val cleveland = ClevelandProvider(retrofit(ClevelandApi.BASE_URL).create(ClevelandApi::class.java))
            .fetchPage(request, PageCursor(seed = "slice10"))
        assertTrue("artic random lane empty", artic.items.isNotEmpty())
        assertTrue("cleveland random lane empty", cleveland.items.isNotEmpty())
    }

    /**
     * Slice-11 black-screen proof: the URL every feed card now downloads (thumbUrl, with the
     * preview as fallback) must actually serve image bytes through the SAME OkHttp client the
     * Coil image pipeline uses — including its allow-list and redirect policy. A thumb that 404s
     * or serves HTML is exactly what rendered as a black card.
     */
    @Test fun feedThumbsAreServedAsImagesThroughTheImageClient() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT180S")) {
        val imageClient = HttpClients.images(HttpClients.base())
        suspend fun assertThumbServed(label: String, url: String) {
            assertTrue("$label thumb url is blank", url.isNotBlank())
            val head = imageClient.newCall(
                okhttp3.Request.Builder().url(url).head().build(),
            ).execute()
            head.use { response ->
                if (response.code == 405) {
                    val ranged = imageClient.newCall(
                        okhttp3.Request.Builder().url(url).header("Range", "bytes=0-0").get().build(),
                    ).execute()
                    ranged.use {
                        assertTrue("$label thumb GET ${it.code} for $url", it.isSuccessful)
                        assertTrue(
                            "$label thumb is not an image (${it.header("Content-Type")}) for $url",
                            it.header("Content-Type")?.startsWith("image/") == true,
                        )
                    }
                } else {
                    assertTrue("$label thumb HEAD ${response.code} for $url", response.isSuccessful)
                    assertTrue(
                        "$label thumb is not an image (${response.header("Content-Type")}) for $url",
                        response.header("Content-Type")?.startsWith("image/") == true,
                    )
                }
            }
        }

        val openverse = OpenverseProvider(retrofit(OpenverseApi.BASE_URL).create(OpenverseApi::class.java))
            .fetchPage(FeedRequest(query = "aurora"), PageCursor())
        openverse.items.take(2).forEach {
            assertThumbServed("openverse", it.thumbUrl.takeIf(String::isNotBlank) ?: it.previewUrl)
        }

        val cleveland = ClevelandProvider(retrofit(ClevelandApi.BASE_URL).create(ClevelandApi::class.java))
            .fetchPage(FeedRequest(), PageCursor())
        cleveland.items.take(2).forEach {
            assertThumbServed("cleveland", it.thumbUrl.takeIf(String::isNotBlank) ?: it.previewUrl)
        }

        val met = MetMuseumProvider(retrofit(MetApi.BASE_URL).create(MetApi::class.java))
            .fetchPage(FeedRequest(query = "landscape"), PageCursor())
        met.items.take(2).forEach {
            assertThumbServed("met", it.thumbUrl.takeIf(String::isNotBlank) ?: it.previewUrl)
        }

        val wikimedia = WikimediaProvider(retrofit(WikimediaApi.BASE_URL).create(WikimediaApi::class.java))
            .fetchPage(FeedRequest(query = "aurora borealis"), PageCursor())
        wikimedia.items.take(2).forEach {
            assertThumbServed("wikimedia", it.thumbUrl.takeIf(String::isNotBlank) ?: it.previewUrl)
        }
    }

    /**
     * The Art Institute IIIF host sits behind the same front-end protection as the API and
     * intermittently answers datacenter runners with HTTP 403. Proved when reachable; an honest
     * SKIP when the WAF denies this runner — never a fake failure, never a fake pass.
     */
    @Test fun articFeedThumbsAreServedWhenTheWafAllowsTheRunner() = runTest(timeout = kotlin.time.Duration.parseIsoString("PT120S")) {
        val page = articTolerant {
            ArtInstituteProvider(retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java))
                .fetchPage(FeedRequest(), PageCursor())
        }
        assertTrue("artic returned nothing", page.items.isNotEmpty())
        val url = page.items.first().thumbUrl.takeIf(String::isNotBlank) ?: page.items.first().previewUrl
        val imageClient = HttpClients.images(HttpClients.base())
        val response = imageClient.newCall(okhttp3.Request.Builder().url(url).head().build()).execute()
        response.use {
            Assume.assumeTrue("AIC IIIF WAF denies this runner's network path (HTTP 403)", it.code != 403)
            assertTrue("artic thumb HEAD ${it.code} for $url", it.isSuccessful)
            assertTrue(
                "artic thumb is not an image (${it.header("Content-Type")}) for $url",
                it.header("Content-Type")?.startsWith("image/") == true,
            )
        }
    }

    /**
     * The Art Institute front-end protection intermittently denies datacenter runners (HTTP 403)
     * even for perfectly valid requests. One polite retry, then an honest SKIP — a blocked
     * network path must never masquerade as a broken adapter.
     */
    private suspend fun <T> articTolerant(block: suspend () -> T): T = try {
        block()
    } catch (first: com.auroro.wallpapers.core.network.ProviderException) {
        if (first.message?.contains("403") != true) throw first
        kotlinx.coroutines.delay(1_500)
        try {
            block()
        } catch (second: com.auroro.wallpapers.core.network.ProviderException) {
            if (second.message?.contains("403") != true) throw second
            Assume.assumeNoException("AIC WAF denies this runner's network path", second)
            throw second
        }
    }
}
