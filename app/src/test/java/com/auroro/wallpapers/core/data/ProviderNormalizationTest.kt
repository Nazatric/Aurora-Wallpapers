package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.AspectFilter
import com.auroro.wallpapers.core.model.AspectMath
import com.auroro.wallpapers.core.model.AspectPreset
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.LocalFilter
import com.auroro.wallpapers.core.model.OpenverseCategory
import com.auroro.wallpapers.core.model.OpenverseLicense
import com.auroro.wallpapers.core.model.Orientation
import com.auroro.wallpapers.core.model.ResolutionFilter
import com.auroro.wallpapers.core.model.ResolutionPreset
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.openverse.OpenverseImageDto
import com.auroro.wallpapers.core.network.openverse.OpenverseTagDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenTagDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenThumbsDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenUploaderDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenWallpaperDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderNormalizationTest {
    @Test fun wallhavenSfwRecordPreservesRealMetadataAndDisplaysTheOriginalAtFullDetail() {
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
        assertEquals("https://th.wallhaven.cc/orig/ab/abc123.jpg", mapped.thumbUrl)
        assertEquals(original, mapped.previewUrl)
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

    @Test fun wallhavenRejectsNonSfwOrUntrustedOriginalsAndDoesNotUseUntrustedThumbs() {
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
        val mapped = WallhavenMapper.toWallpaper(base.copy(purity = "sfw", thumbs = WallhavenThumbsDto(large = "https://attacker.example/thumb.jpg")))!!
        assertEquals("https://w.wallhaven.cc/full/ab/file.jpg", mapped.thumbUrl)
    }

    @Test fun openverseMapsAttributionTagsSourceAndExactLicence() {
        val mapped = OpenverseMapper.toWallpaper(openverseDto())!!
        assertEquals(WallpaperSource.OPENVERSE, mapped.source)
        assertEquals("4bc43a04-ef46-4544-a0c1-63c63f56e276", mapped.sourceId)
        assertEquals("Tree Bark Photo", mapped.title)
        assertEquals("Tim Sullivan", mapped.creatorName)
        assertEquals("https://www.example.org/creator", mapped.creatorUrl)
        assertEquals("stocksnap", mapped.providerName)
        assertEquals("stocksnap", mapped.catalogSource)
        assertEquals("photograph", mapped.category)
        assertEquals(listOf("tree", "bark"), mapped.tags.map { it.name })
        assertEquals("cc0", mapped.licenseCode)
        assertEquals("1.0", mapped.licenseVersion)
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/", mapped.licenseUrl)
        assertTrue(mapped.attribution!!.contains("Tim Sullivan"))
        assertEquals("https://stocksnap.io/photo/XNVBVXO3B7", mapped.pageUrl)
        assertEquals("https://api.openverse.org/v1/images/4bc43a04-ef46-4544-a0c1-63c63f56e276/thumb/", mapped.thumbUrl)
        assertEquals("https://cdn.example.org/tree.jpg", mapped.previewUrl)
        assertEquals("https://cdn.example.org/tree.jpg", mapped.originalUrl)
        assertEquals(6016, mapped.width)
        assertEquals(4016, mapped.height)
        assertTrue(mapped.downloadAllowed)
        assertTrue(mapped.setWallpaperAllowed)
    }

    @Test fun openverseRejectsSensitiveLowResolutionAndUnsafeRowsAndHonoursNoDerivatives() {
        val base = openverseDto()
        assertNull(OpenverseMapper.toWallpaper(base.copy(mature = true)))
        assertNull(OpenverseMapper.toWallpaper(base.copy(mature = null)))
        assertNull(OpenverseMapper.toWallpaper(base.copy(height = 500)))
        assertNull(OpenverseMapper.toWallpaper(base.copy(url = "http://cdn.example.org/tree.jpg")))
        assertNull(OpenverseMapper.toWallpaper(base.copy(foreignLandingUrl = "https://unsafe.local/photo")))

        val noDerivatives = OpenverseMapper.toWallpaper(base.copy(license = "by-nd"))!!
        assertTrue(noDerivatives.downloadAllowed) // saving the original is distinct from making a crop
        assertFalse(noDerivatives.setWallpaperAllowed)
        assertEquals(noDerivatives.originalUrl, noDerivatives.thumbUrl) // Never preview an ND work via a generated derivative.
        val deprecatedSamplingLicence = OpenverseMapper.toWallpaper(base.copy(license = "sampling+"))!!
        assertFalse(deprecatedSamplingLicence.downloadAllowed)
        assertFalse(deprecatedSamplingLicence.setWallpaperAllowed)

        // Openverse legitimately omits filetype for many source rows; the direct URL extension is
        // only a hint, and DownloadExecutor validates the returned bytes before saving.
        val inferred = OpenverseMapper.toWallpaper(base.copy(filetype = null))!!
        assertEquals("image/jpeg", inferred.mimeType)
        assertTrue(inferred.downloadAllowed)
    }

    @Test fun openverseLicenceGroupsMirrorThePublishedApiGroupsWithoutGrantingDownloadRights() {
        val cc0 = OpenverseMapper.toWallpaper(openverseDto().copy(license = "cc0"))!!
        val pdm = OpenverseMapper.toWallpaper(openverseDto().copy(license = "pdm"))!!
        val sampling = OpenverseMapper.toWallpaper(openverseDto().copy(license = "sampling+"))!!
        val ncSampling = OpenverseMapper.toWallpaper(openverseDto().copy(license = "nc-sampling+"))!!
        val allCc = WallpaperFilter(openverseLicense = OpenverseLicense.ALL_CC)
        val commercial = WallpaperFilter(openverseLicense = OpenverseLicense.COMMERCIAL)
        val modification = WallpaperFilter(openverseLicense = OpenverseLicense.MODIFICATION)

        assertTrue(LocalFilter.matches(cc0, allCc))
        assertTrue(LocalFilter.matches(sampling, allCc))
        assertTrue(LocalFilter.matches(ncSampling, allCc))
        assertFalse(LocalFilter.matches(pdm, allCc))
        assertTrue(LocalFilter.matches(sampling, commercial))
        assertFalse(LocalFilter.matches(ncSampling, commercial))
        assertTrue(LocalFilter.matches(ncSampling, modification))
        assertFalse(LocalFilter.matches(OpenverseMapper.toWallpaper(openverseDto().copy(license = "by-nd"))!!, modification))
        assertFalse(sampling.downloadAllowed) // Deprecated sampling licences are not treated as permission to save the whole image.

        val groupedQuery = OpenverseQuery.build(FeedRequest(filter = allCc), PageCursor())
        assertEquals("all-cc", groupedQuery["license_type"])
    }

    @Test fun openverseQueryUsesDocumentedFiltersAndConservativeCoarseBuckets() {
        val query = OpenverseQuery.build(
            FeedRequest(
                query = "  mountain lake ",
                aspectTolerance = 0.03f,
                filter = WallpaperFilter(
                    sources = setOf(WallpaperSource.OPENVERSE),
                    aspect = AspectFilter.Preset(AspectPreset.R9_19_5),
                    resolution = ResolutionFilter.Preset(ResolutionPreset.P1080),
                    orientation = Orientation.PORTRAIT,
                    sort = SortOption.NEWEST,
                    openverseCategory = OpenverseCategory.PHOTOGRAPH,
                    openverseLicense = OpenverseLicense.CC_BY_SA,
                ),
            ),
            PageCursor(page = 3),
        )
        assertEquals("mountain lake", query["q"])
        assertEquals("3", query["page"])
        assertEquals("20", query["page_size"])
        assertEquals("false", query["mature"])
        assertEquals("jpg,jpeg,png,webp", query["extension"])
        assertEquals("true", query["filter_dead"])
        assertEquals("photograph", query["category"])
        assertEquals("by-sa", query["license"])
        assertEquals("tall", query["aspect_ratio"])
        assertEquals("large", query["size"])
        assertFalse("categories" in query)
        assertFalse("unstable__sort_by" in query) // Openverse stays relevance-ranked
        assertFalse(query.keys.any { it.contains("key", ignoreCase = true) })

        val tagSearch = OpenverseQuery.build(
            FeedRequest(filter = WallpaperFilter(openverseTag = "  autumn leaves  ")),
            PageCursor(),
        )
        assertEquals("autumn leaves", tagSearch["tags"])
        assertFalse("q" in tagSearch)
    }

    @Test fun openverseAspectAndSizePrefiltersPreserveLocalToleranceChecks() {
        val request = FeedRequest(
            filter = WallpaperFilter(
                aspect = AspectFilter.Preset(AspectPreset.R1_1),
                resolution = ResolutionFilter.Preset(ResolutionPreset.P720),
            ),
        )
        val query = OpenverseQuery.build(request, PageCursor())
        assertNull(query["aspect_ratio"]) // the API's square bucket is exact; local tolerance admits near-square images
        assertEquals("medium,large", query["size"])
        val portraitOnly = OpenverseQuery.build(
            FeedRequest(filter = WallpaperFilter(orientation = Orientation.PORTRAIT)),
            PageCursor(),
        )
        assertEquals("tall", portraitOnly["aspect_ratio"]) // the API bucket is exactly width < height
        val landscapeOnly = OpenverseQuery.build(
            FeedRequest(filter = WallpaperFilter(orientation = Orientation.LANDSCAPE)),
            PageCursor(),
        )
        assertEquals("wide", landscapeOnly["aspect_ratio"])
        val squareBand = OpenverseQuery.build(
            FeedRequest(filter = WallpaperFilter(orientation = Orientation.SQUARE)),
            PageCursor(),
        )
        assertNull(squareBand["aspect_ratio"]) // Openverse's exact-square bucket would drop near-square matches.
        val nearSquarePortrait = OpenverseQuery.build(
            FeedRequest(
                aspectTolerance = 0.03f,
                filter = WallpaperFilter(
                    aspect = AspectFilter.Preset(AspectPreset.R1_1),
                    orientation = Orientation.PORTRAIT,
                ),
            ),
            PageCursor(),
        )
        assertEquals("tall", nearSquarePortrait["aspect_ratio"])
        val fourByThree = OpenverseQuery.build(
            FeedRequest(filter = WallpaperFilter(aspect = AspectFilter.Preset(AspectPreset.R4_3))),
            PageCursor(),
        )
        assertEquals("wide", fourByThree["aspect_ratio"])
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.OPENVERSE, "https://cdn.example.org/image.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork(WallpaperSource.OPENVERSE, "http://cdn.example.org/image.jpg"))
    }

    @Test fun urlPolicyKeepsApiHostsNarrowAndAttributionLinksPublicHttpsOnly() {
        assertFalse(UrlPolicy.isAllowedForNetwork("http://wallhaven.cc/api/v1/search"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://wallhaven.cc.evil.example/image.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://images.example.org/image.jpg"))
        assertTrue(UrlPolicy.isAllowedForNetwork("https://api.openverse.org/v1/images/"))
        assertTrue(UrlPolicy.isAllowedForBrowsing("https://openverse.org/image/123"))
        assertFalse(UrlPolicy.isAllowedForBrowsing("http://openverse.org/image/123"))
        assertFalse(UrlPolicy.isAllowedForBrowsing("https://127.0.0.1/image"))
    }

    @Test fun wallhavenQueryUsesDocumentedSafeParameters() {
        val default = WallhavenQuery.build(FeedRequest(), PageCursor())
        assertEquals("100", default["purity"])
        assertEquals("111", default["categories"])
        assertEquals("relevance", default["sorting"])
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

        // Wallhaven's current 21x9 response includes the common 3440x1440 (43:18) size.
        // It is within 3% locally, but a narrower tolerance rejects it even though the server bucket returns it.
        assertTrue(AspectMath.matches(3440, 1440, AspectPreset.R21_9.ratio, tolerance = 0.03f))
        assertFalse(AspectMath.matches(3440, 1440, AspectPreset.R21_9.ratio, tolerance = 0.02f))
    }

    @Test fun unsupportedWallhavenRatioStaysLocalAndResolutionServerFilterRespectsOrientation() {
        val portrait = FeedRequest(filter = WallpaperFilter(orientation = Orientation.PORTRAIT))
        assertNull(WallhavenQuery.ratios(portrait))
        val minimum4k = WallhavenQuery.atLeast(
            portrait.copy(filter = portrait.filter.copy(resolution = ResolutionFilter.Preset(ResolutionPreset.K4))),
        )
        assertEquals("2160x3840", minimum4k)
        val landscapeMinimum = WallhavenQuery.atLeast(
            portrait.copy(filter = portrait.filter.copy(orientation = Orientation.LANDSCAPE, resolution = ResolutionFilter.Custom(1080, 1920))),
        )
        assertEquals("1920x1080", landscapeMinimum)
        val squareMinimum = WallhavenQuery.atLeast(
            portrait.copy(filter = portrait.filter.copy(orientation = Orientation.SQUARE, resolution = ResolutionFilter.Custom(1080, 1920))),
        )
        assertEquals("1080x1080", squareMinimum) // Broad server hint; the 5% square band is checked on real dimensions.

        val nearSquareWithTolerance = FeedRequest(
            aspectTolerance = 0.03f,
            filter = WallpaperFilter(
                aspect = AspectFilter.Custom(AspectMath.parseCustom("1.01:1")!!),
                resolution = ResolutionFilter.Custom(1080, 1920),
            ),
        )
        assertEquals("1080x1080", WallhavenQuery.atLeast(nearSquareWithTolerance))
    }

    private fun openverseDto() = OpenverseImageDto(
        id = "4bc43a04-ef46-4544-a0c1-63c63f56e276",
        title = "Tree Bark Photo",
        foreignLandingUrl = "https://stocksnap.io/photo/XNVBVXO3B7",
        url = "https://cdn.example.org/tree.jpg",
        thumbnail = "https://api.openverse.org/v1/images/4bc43a04-ef46-4544-a0c1-63c63f56e276/thumb/",
        creator = "Tim Sullivan",
        creatorUrl = "https://www.example.org/creator",
        license = "cc0",
        licenseVersion = "1.0",
        licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        provider = "stocksnap",
        source = "stocksnap",
        category = "photograph",
        filesize = 896_128,
        filetype = "jpg",
        tags = listOf(OpenverseTagDto("tree"), OpenverseTagDto("bark")),
        attribution = "Tree Bark Photo by Tim Sullivan is marked with CC0 1.0.",
        mature = false,
        height = 4016,
        width = 6016,
        indexedOn = "2022-08-27T17:39:48Z",
    )
}
