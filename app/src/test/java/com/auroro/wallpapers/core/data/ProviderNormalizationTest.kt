package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.LocalFilter
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.nasa.NasaApodApi
import com.auroro.wallpapers.core.network.nasa.NasaApodDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenTagDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenThumbsDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenUploaderDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenWallpaperDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProviderNormalizationTest {
    @Test fun wallhavenRecordPreservesProviderMetadataAndOriginalImage() {
        val original = "https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg"
        val mapped = WallhavenMapper.toWallpaper(
            WallhavenWallpaperDto(
                id = "abc123",
                url = "https://wallhaven.cc/w/abc123",
                uploader = WallhavenUploaderDto("test-uploader", "User"),
                views = 10,
                favorites = 2,
                source = "https://example.com/original",
                purity = "sfw",
                category = "general",
                dimensionX = 3840,
                dimensionY = 2160,
                fileSize = 4_000,
                fileType = "image/jpeg",
                createdAt = "2026-01-02 03:04:05",
                colors = listOf("#66cccc"),
                path = original,
                thumbs = WallhavenThumbsDto(
                    large = "https://th.wallhaven.cc/lg/ab/abc123.jpg",
                    original = "https://th.wallhaven.cc/orig/ab/abc123.jpg",
                    small = "https://th.wallhaven.cc/small/ab/abc123.jpg",
                ),
                tags = listOf(WallhavenTagDto(1, "ocean", category = "Nature")),
            ),
        )!!

        assertEquals(WallpaperSource.WALLHAVEN, mapped.source)
        assertEquals("abc123", mapped.sourceId)
        assertEquals("https://wallhaven.cc/w/abc123", mapped.pageUrl)
        // Cards/carousels ride the large thumb for speed; the 4K original is reserved for detail/apply.
        assertEquals("https://th.wallhaven.cc/lg/ab/abc123.jpg", mapped.thumbUrl)
        assertEquals("https://th.wallhaven.cc/lg/ab/abc123.jpg", mapped.previewUrl)
        assertEquals(original, mapped.originalUrl)
        assertEquals(3840, mapped.width)
        assertEquals(2160, mapped.height)
        assertEquals("test-uploader", mapped.creatorName)
        assertEquals("ocean", mapped.tags.single().name)
        assertEquals("general", mapped.category?.lowercase())
        assertEquals(4_000L, mapped.fileSizeBytes)
        assertEquals(10, mapped.views)
        assertEquals("https://example.com/original", mapped.originSourceUrl)
    }

    @Test fun wallhavenValidatesItsReportedPurityAndTrustedOriginals() {
        val base = WallhavenWallpaperDto(
            id = "abc123",
            url = "https://wallhaven.cc/w/abc123",
            purity = "sketchy",
            path = "https://w.wallhaven.cc/full/ab/file.jpg",
            dimensionX = 1920,
            dimensionY = 1080,
            thumbs = WallhavenThumbsDto(large = "https://th.wallhaven.cc/lg/ab/thumb.jpg"),
        )
        assertNull(WallhavenMapper.toWallpaper(base))
        assertNull(WallhavenMapper.toWallpaper(base.copy(purity = "sfw", path = "http://w.wallhaven.cc/file.jpg")))
        assertNull(WallhavenMapper.toWallpaper(base.copy(purity = "sfw", path = "https://attacker.example/file.jpg")))
        val mapped = WallhavenMapper.toWallpaper(
            base.copy(purity = "sfw", thumbs = WallhavenThumbsDto(large = "https://attacker.example/thumb.jpg")),
        )!!
        assertEquals("https://w.wallhaven.cc/full/ab/file.jpg", mapped.thumbUrl)
    }

    @Test fun nasaApodUsesOnlyDatedImagesAndKeepsItemsViewOnlyWithoutExplicitRightsClearance() = runTest {
        val api = FakeNasaApi(
            listOf(
                apod(date = "2026-09-20", copyright = null, original = "https://images.nasa.gov/apod.jpg?w=4032&h=3024"),
                apod(date = "2026-09-19", copyright = "© Example Photographer", original = "https://apod.nasa.gov/apod/image.jpg?w=1080&h=1920"),
                apod(date = "2026-09-18", mediaType = "video"),
                apod(date = "not-a-date"),
                apod(date = "2026-09-16", original = "https://example.org/not-nasa.jpg?w=1080&h=1920"),
            ),
        )
        val provider = NasaApodProvider(api)
        val page = provider.fetchPage(FeedRequest(), PageCursor())

        assertEquals(20, api.requestedPageSize)
        assertEquals(1, api.requestedPage)
        assertEquals(listOf("2026-09-20", "2026-09-19"), page.items.map { it.sourceId })
        val publicItem = page.items.first()
        assertEquals(0, publicItem.width)
        assertEquals(0, publicItem.height)
        assertEquals("https://science.nasa.gov/apod/2026-09-20/", publicItem.pageUrl)
        assertNull(publicItem.licenseCode)
        assertFalse(publicItem.downloadAllowed)
        assertFalse(publicItem.setWallpaperAllowed)
        assertEquals("https://images.nasa.gov/apod.jpg?w=960&h=720", publicItem.thumbUrl)
        // APOD previews cap at 1440 px on the long edge (4032×3024 -> 1440×1080).
        assertEquals("https://images.nasa.gov/apod.jpg?w=1440&h=1080", publicItem.previewUrl)
        assertFalse(LocalFilter.matches(publicItem, WallpaperFilter(orientation = Orientation.LANDSCAPE)))

        val copyrighted = page.items.last()
        assertNull(copyrighted.licenseCode)
        assertFalse(copyrighted.downloadAllowed)
        assertFalse(copyrighted.setWallpaperAllowed)
        assertTrue(copyrighted.attribution.orEmpty().contains("Example Photographer"))
        assertFalse(LocalFilter.matches(copyrighted, WallpaperFilter(orientation = Orientation.PORTRAIT)))
    }

    @Test fun nasaApodKeepsUnsupportedAnimationViewableButDoesNotOfferItAsDownloadOrWallpaper() = runTest {
        val api = FakeNasaApi(
            listOf(apod(date = "2026-09-21", original = "https://images.nasa.gov/apod/animation.gif?w=800&h=600")),
        )
        val item = NasaApodProvider(api).fetchPage(FeedRequest(), PageCursor()).items.single()

        assertEquals("image/gif", item.mimeType)
        assertFalse(item.downloadAllowed)
        assertFalse(item.setWallpaperAllowed)
    }

    @Test fun nasaApodDoesNotGuessDimensionsOrAcceptUnknownImageHosts() = runTest {
        val api = FakeNasaApi(
            listOf(
                apod(date = "2026-09-20", original = "https://images.nasa.gov/apod/no-dimensions.jpg"),
                apod(date = "2026-09-19", original = "https://attacker.example/image.jpg?w=1080&h=1920"),
            ),
        )
        val page = NasaApodProvider(api).fetchPage(FeedRequest(), PageCursor())

        assertEquals(1, page.items.size)
        assertEquals(0, page.items.single().width)
        assertEquals(0, page.items.single().height)
        assertFalse(LocalFilter.matches(page.items.single(), WallpaperFilter(orientation = Orientation.PORTRAIT)))
    }

    @Test fun urlsAreRestrictedToCurrentProviderApiAndMediaHosts() {
        assertFalse(UrlPolicy.isAllowedForNetwork("http://wallhaven.cc/api/v1/search"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://wallhaven.cc.evil.example/image.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://api.openverse.org/v1/images/"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://images.example.org/image.jpg"))
        assertTrue(UrlPolicy.isAllowedForNetwork("https://science.nasa.gov/wp-json/wp/v2/apod-basic"))
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.NASA_APOD, "https://images-assets.nasa.gov/image.jpg"))
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.ART_INSTITUTE, "https://api.artic.edu/api/v1/artworks/search"))
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.ART_INSTITUTE, "https://www.artic.edu/iiif/2/image/full/max,/0/default.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork(WallpaperSource.REDDIT, "https://www.reddit.com/api/v1"))
        assertFalse(UrlPolicy.isAllowedForNetwork(WallpaperSource.OPENVERSE, "https://media.example.org/legacy-image.jpg"))
        assertTrue(UrlPolicy.isAllowedForBrowsing("https://www.artic.edu/artworks/123"))
        assertFalse(UrlPolicy.isAllowedForBrowsing("https://127.0.0.1/image"))
    }

    @Test fun wallhavenQueryUsesDocumentedProviderParameters() {
        val default = WallhavenQuery.build(FeedRequest(), PageCursor())
        assertEquals("100", default["purity"])
        assertEquals("111", default["categories"])
        assertEquals("date_added", default["sorting"])
        assertFalse("apikey" in default)
        assertFalse("seed" in default)

        val request = FeedRequest(
            query = "  ocean  light ",
            filter = WallpaperFilter(
                sort = SortOption.POPULAR,
                wallhavenCategories = setOf(WallhavenCategory.ANIME, WallhavenCategory.PEOPLE),
                colorHex = "#66cccc",
            ),
        )
        val query = WallhavenQuery.build(request, PageCursor(page = 4))
        assertEquals("ocean light", query["q"])
        assertEquals("011", query["categories"])
        assertEquals("100", query["purity"])
        assertEquals("toplist", query["sorting"])
        assertEquals("1M", query["topRange"])
        assertEquals("4", query["page"])
        assertEquals("66cccc", query["colors"])
        assertNull(query["ratios"])
    }

    @Test fun wallhavenRatioTokensAreBroadPrefiltersAndLocalToleranceRemainsAuthoritative() {
        val tokens = mapOf(
            AspectPreset.R9_16 to "9x16",
            AspectPreset.R16_9 to "16x9",
            AspectPreset.R4_3 to "4x3",
            AspectPreset.R1_1 to "1x1",
            AspectPreset.R21_9 to "21x9",
        )
        tokens.forEach { (preset, token) ->
            val request = FeedRequest(filter = WallpaperFilter(aspect = AspectFilter.Preset(preset)))
            assertEquals(token, WallhavenQuery.ratios(request))
        }
        assertNull(WallhavenQuery.ratios(FeedRequest(filter = WallpaperFilter(aspect = AspectFilter.Preset(AspectPreset.R9_19_5)))))
        assertTrue(AspectMath.matches(3440, 1440, AspectPreset.R21_9.ratio, tolerance = 0.03f))
        assertFalse(AspectMath.matches(3440, 1440, AspectPreset.R21_9.ratio, tolerance = 0.02f))
    }

    @Test fun wallhavenResolutionPrefiltersRespectOrientationAndUseRealPixelEdges() {
        val portrait = FeedRequest(filter = WallpaperFilter(orientation = Orientation.PORTRAIT))
        assertNull(WallhavenQuery.ratios(portrait))
        assertEquals(
            "2160x3840",
            WallhavenQuery.atLeast(portrait.copy(filter = portrait.filter.copy(resolution = ResolutionFilter.Preset(ResolutionPreset.K4)))),
        )
        assertEquals(
            "1920x1080",
            WallhavenQuery.atLeast(portrait.copy(filter = portrait.filter.copy(orientation = Orientation.LANDSCAPE, resolution = ResolutionFilter.Custom(1080, 1920)))),
        )
        assertEquals(
            "1080x1080",
            WallhavenQuery.atLeast(portrait.copy(filter = portrait.filter.copy(orientation = Orientation.SQUARE, resolution = ResolutionFilter.Custom(1080, 1920)))),
        )
    }

    private class FakeNasaApi(private val items: List<NasaApodDto>) : NasaApodApi {
        var requestedPageSize = 0
        var requestedPage = 0
        override suspend fun latest(pageSize: Int, page: Int): List<NasaApodDto> {
            requestedPageSize = pageSize
            requestedPage = page
            return items
        }

        override suspend fun byDate(nasaDate: String) = items.first()
    }

    private fun apod(
        date: String = "2026-09-20",
        mediaType: String = "image",
        copyright: String? = null,
        original: String = "https://images.nasa.gov/apod.jpg?w=1080&h=1920",
    ) = NasaApodDto(
        date = date,
        title = "APOD title",
        permalink = "https://science.nasa.gov/apod/$date/",
        mediaType = mediaType,
        url = original,
        hdurl = original,
        copyright = copyright,
        credit = "NASA / Example Observatory",
    )
}
