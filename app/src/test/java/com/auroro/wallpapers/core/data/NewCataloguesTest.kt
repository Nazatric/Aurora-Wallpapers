package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.artic.ArtInstituteApi
import com.auroro.wallpapers.core.network.artic.ArtPagination
import com.auroro.wallpapers.core.network.artic.ArtSearchResponse
import com.auroro.wallpapers.core.network.artic.ArtworkDto
import com.auroro.wallpapers.core.network.cleveland.ClevelandApi
import com.auroro.wallpapers.core.network.cleveland.ClevelandArtwork
import com.auroro.wallpapers.core.network.cleveland.ClevelandCreator
import com.auroro.wallpapers.core.network.cleveland.ClevelandImages
import com.auroro.wallpapers.core.network.cleveland.ClevelandRendition
import com.auroro.wallpapers.core.network.cleveland.ClevelandInfo
import com.auroro.wallpapers.core.network.cleveland.ClevelandResponse
import com.auroro.wallpapers.core.network.met.MetApi
import com.auroro.wallpapers.core.network.met.MetObjectDto
import com.auroro.wallpapers.core.network.met.MetSearchResponse
import com.auroro.wallpapers.core.network.openverse.OpenverseApi
import com.auroro.wallpapers.core.network.openverse.OpenverseImageDto
import com.auroro.wallpapers.core.network.openverse.OpenverseImagePage
import com.auroro.wallpapers.core.network.wikimedia.WikimediaApi
import com.auroro.wallpapers.core.network.wikimedia.WikimediaExtMetadata
import com.auroro.wallpapers.core.network.wikimedia.WikimediaImageInfo
import com.auroro.wallpapers.core.network.wikimedia.WikimediaMetaValue
import com.auroro.wallpapers.core.network.wikimedia.WikimediaPage
import com.auroro.wallpapers.core.network.wikimedia.WikimediaQuery
import com.auroro.wallpapers.core.network.wikimedia.WikimediaResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Offline fixture tests for the 1.8.0 catalogue expansion: Openverse (revived), Art Institute of
 * Chicago (revived via deterministic IIIF URLs), Wikimedia Commons, The Met and Cleveland. They
 * pin the rights gates, host rules and pagination contracts without touching the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewCataloguesTest {

    // --- Openverse ---

    @Test fun openverseKeepsCc0AndAttributionLicencesAndDropsEverythingElse() = runTest {
        val api = FakeOpenverseApi(
            OpenverseImageDto(
                id = "ov-1",
                title = "Aurora over fjord",
                url = "https://live.staticflickr.com/123/aurora_o.jpg",
                thumbnail = "https://api.openverse.org/v1/images/ov-1/thumb/",
                foreignLandingUrl = "https://www.flickr.com/photos/x/123",
                creator = "Photographer Name",
                license = "cc0",
                licenseVersion = "1.0",
                width = 3840,
                height = 2160,
            ),
            OpenverseImageDto(
                id = "ov-2",
                url = "https://example.org/by-sa.jpg",
                license = "by-sa",
                licenseVersion = "4.0",
                creator = "Sharer",
                width = 1920,
                height = 1080,
            ),
            OpenverseImageDto(id = "ov-3", url = "https://example.org/nc.jpg", license = "by-nc", width = 800, height = 600),
            OpenverseImageDto(id = "ov-4", url = "http://insecure.example/x.jpg", license = "cc0", width = 800, height = 600),
        )
        val page = OpenverseProvider(api).fetchPage(FeedRequest(query = "aurora"), PageCursor())
        val items = page.items
        assertEquals(listOf("ov-1", "ov-2"), items.map { it.sourceId })
        val cc0 = items.first()
        assertEquals(3840, cc0.width)
        assertEquals(2160, cc0.height)
        assertTrue(cc0.downloadAllowed && cc0.setWallpaperAllowed)
        assertTrue(cc0.attribution!!.contains("Photographer Name"))
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/", cc0.licenseUrl)
        val bySa = items[1]
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", bySa.licenseUrl)
        assertTrue(bySa.attribution!!.contains("Sharer"))
    }

    // --- Wikimedia Commons ---

    @Test fun wikimediaGatesLicenceSizeAndMimeAndNeedsAKeyword() = runTest {
        val provider = WikimediaProvider(
            FakeWikimediaApi(
                wikimediaPage("File:Aurora_Borealis.jpg", license = "CC BY-SA 4.0", artist = "<b>Jane Doe</b>", width = 4000, height = 2600),
                wikimediaPage("File:Noncommercial.jpg", license = "CC BY-NC 2.0", width = 4000, height = 2600),
                wikimediaPage("File:Public_Domain_scan.png", license = "Public domain", width = 3000, height = 2000),
                wikimediaPage("File:Too_Small.jpg", license = "CC0 1.0", width = 640, height = 480),
                wikimediaPage("File:Vector.svg", license = "CC0 1.0", width = 4000, height = 4000, mime = "image/svg+xml"),
            ),
        )
        // Search-only source: without a keyword it must say so instead of inventing results.
        assertNotNull(provider.unsupportedReason(FeedRequest()))
        assertNull(provider.unsupportedReason(FeedRequest(query = "aurora")))

        val page = provider.fetchPage(FeedRequest(query = "aurora"), PageCursor())
        assertEquals(listOf("Aurora Borealis.jpg", "Public Domain scan.png"), page.items.map { it.sourceId })
        val bysa = page.items.first()
        assertEquals(4000, bysa.width)
        assertTrue(bysa.downloadAllowed)
        assertTrue(bysa.attribution!!.contains("Jane Doe"))
        assertTrue(bysa.thumbUrl.contains("upload.wikimedia.org"))
    }

    private fun wikimediaPage(
        title: String,
        license: String,
        artist: String = "Unknown",
        width: Int,
        height: Int,
        mime: String = "image/jpeg",
    ) = WikimediaPage(
        title = title,
        imageinfo = listOf(
            WikimediaImageInfo(
                url = "https://upload.wikimedia.org/wikipedia/commons/a/ab/" + title.removePrefix("File:"),
                thumburl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/" + title.removePrefix("File:") + "/1600px.jpg",
                descriptionurl = "https://commons.wikimedia.org/wiki/$title",
                width = width,
                height = height,
                mime = mime,
                extmetadata = WikimediaExtMetadata(
                    licenseShortName = WikimediaMetaValue(license),
                    artist = WikimediaMetaValue(artist),
                ),
            ),
        ),
    )

    // --- The Met ---

    @Test fun metKeepsOnlyPublicDomainObjectsWithImages() = runTest {
        val api = FakeMetApi(
            ids = listOf(1, 2, 3),
            objects = mapOf(
                1 to MetObjectDto(
                    objectID = 1,
                    title = "Wheat Field with Cypresses",
                    artistDisplayName = "Vincent van Gogh",
                    primaryImage = "https://images.metmuseum.org/CRDImages/ep/original/DT1567.jpg",
                    primaryImageSmall = "https://images.metmuseum.org/CRDImages/ep/web-large/DT1567.jpg",
                    isPublicDomain = true,
                    objectUrl = "https://www.metmuseum.org/art/collection/search/1",
                ),
                2 to MetObjectDto(objectID = 2, title = "In copyright", primaryImage = "https://images.metmuseum.org/x.jpg", isPublicDomain = false),
                3 to MetObjectDto(objectID = 3, title = "No image", isPublicDomain = true),
            ),
        )
        val page = MetMuseumProvider(api).fetchPage(FeedRequest(), PageCursor())
        val item = page.items.single()
        assertEquals("1", item.sourceId)
        assertTrue(item.attribution!!.contains("Vincent van Gogh"))
        assertEquals("Public Domain", item.licenseCode)
        assertTrue(item.thumbUrl.contains("web-large"))
        assertEquals(
            "https://images.metmuseum.org/CRDImages/ep/original/DT1567.jpg",
            item.originalUrl,
        )
    }

    // --- Cleveland Museum of Art ---

    @Test fun clevelandKeepsCc0RecordsWithImagesAndRealDimensions() = runTest {
        val api = FakeClevelandApi(
            ClevelandArtwork(
                id = 101,
                title = "Water Serpents",
                url = "https://www.clevelandart.org/art/101",
                shareLicenseStatus = "CC0",
                creators = listOf(ClevelandCreator("Gustav Klimt (Austrian, 1862-1918)")),
                images = ClevelandImages(
                    web = ClevelandRendition(url = "https://openaccess-cdn.clevelandart.org/101/101_web.jpg", width = "748", height = "583"),
                    print = ClevelandRendition(url = "https://openaccess-cdn.clevelandart.org/101/101_print.jpg", width = "5000", height = "3900"),
                ),
            ),
            ClevelandArtwork(id = 102, title = "No image", shareLicenseStatus = "CC0"),
        )
        val page = ClevelandProvider(api).fetchPage(FeedRequest(), PageCursor())
        val item = page.items.single()
        assertEquals("101", item.sourceId)
        assertEquals(5000, item.width)
        assertEquals(3900, item.height)
        assertTrue(item.thumbUrl.contains("_web.jpg"))
        assertEquals("CC0", item.licenseCode)
        assertTrue(item.attribution!!.contains("Gustav Klimt"))
    }

    // --- Art Institute of Chicago (revived) ---

    @Test fun articBuildsDeterministicIiifUrlsFromImageIds() = runTest {
        val api = FakeArtInstituteApi(
            ArtworkDto(id = 202, title = "A Sunday on La Grande Jatte", artistDisplay = "Georges Seurat", dateDisplay = "1884-86", imageId = "b5c1e0f9-abc"),
            ArtworkDto(id = 203, title = "No image record", imageId = null),
        )
        val page = ArtInstituteProvider(api).fetchPage(FeedRequest(), PageCursor())
        val item = page.items.single()
        assertEquals("202", item.sourceId)
        assertEquals(
            "https://www.artic.edu/iiif/2/b5c1e0f9-abc/full/1680,/0/default.jpg",
            item.previewUrl,
        )
        assertEquals(
            "https://www.artic.edu/iiif/2/b5c1e0f9-abc/full/full/0/default.jpg",
            item.originalUrl,
        )
        assertEquals("Public Domain", item.licenseCode)
        assertTrue(item.attribution!!.contains("Georges Seurat"))
        // Dimensions stay unknown (search reports none); measured-aspect cards resolve them.
        assertEquals(0, item.width)
    }

    // --- Host policy for the new catalogues ---

    @Test fun urlPolicyCoversTheNewCatalogueHosts() {
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.WIKIMEDIA, "https://upload.wikimedia.org/wikipedia/commons/a/b.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork(WallpaperSource.WIKIMEDIA, "https://evil.example/wikipedia.jpg"))
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.MET_MUSEUM, "https://images.metmuseum.org/CRDImages/x.jpg"))
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.CLEVELAND_ART, "https://openaccess-api.clevelandart.org/api/artworks/img/1.jpg"))
        // Openverse media lives on arbitrary hosting sites; TLS-only is the documented rule.
        assertTrue(UrlPolicy.isAllowedForNetwork(WallpaperSource.OPENVERSE, "https://live.staticflickr.com/1/2_o.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork(WallpaperSource.OPENVERSE, "http://live.staticflickr.com/1/2_o.jpg"))
        assertFalse(UrlPolicy.isAllowedForNetwork(WallpaperSource.REDDIT, "https://www.reddit.com/x.jpg"))
    }

    @Test fun newSourcesAreIntegratedAndDisabledSourcesReportHonestly() = runTest {
        assertTrue(WallpaperSource.integrated.containsAll(
            listOf(
                WallpaperSource.OPENVERSE,
                WallpaperSource.ART_INSTITUTE,
                WallpaperSource.WIKIMEDIA,
                WallpaperSource.MET_MUSEUM,
                WallpaperSource.CLEVELAND_ART,
            ),
        ))
        val disabled = OpenverseProvider(FakeOpenverseApi(), isEnabled = { false })
        assertTrue(disabled.availability() is SourceAvailability.DisabledByUser)
        // Wikimedia never pretends to serve a keyword-free feed.
        val wiki = WikimediaProvider(FakeWikimediaApi())
        assertTrue(wiki.capabilities.sortsWithoutQuery.isEmpty())
        val request = FeedRequest(filter = WallpaperFilter(sort = SortOption.NEWEST), query = "aurora")
        assertNotNull(wiki.unsupportedReason(request))
    }

    // --- Fakes ---

    private class FakeOpenverseApi(private vararg val results: OpenverseImageDto) : OpenverseApi {
        override suspend fun images(query: String?, page: Int, pageSize: Int, licenseType: String, mature: String) =
            OpenverseImagePage(resultCount = results.size.toLong(), pageCount = 1, results = results.toList())
    }

    private class FakeWikimediaApi(private vararg val pages: WikimediaPage) : WikimediaApi {
        override suspend fun searchFiles(
            action: String,
            format: String,
            formatVersion: Int,
            generator: String,
            search: String,
            namespace: Int,
            limit: Int,
            prop: String,
            infoProps: String,
            thumbWidth: Int,
        ) = WikimediaResponse(query = WikimediaQuery(pages = pages.toList()))
    }

    private class FakeMetApi(private val ids: List<Int>, private val objects: Map<Int, MetObjectDto>) : MetApi {
        override suspend fun search(query: String?, hasImages: Boolean, offset: Int, limit: Int) =
            MetSearchResponse(total = ids.size, objectIDs = ids.drop(offset).take(limit))
        override suspend fun obj(objectId: Int): MetObjectDto = objects.getValue(objectId)
    }

    private class FakeClevelandApi(private vararg val artworks: ClevelandArtwork) : ClevelandApi {
        override suspend fun artworks(query: String?, hasImage: Int, cc0: Int, limit: Int, skip: Int) =
            ClevelandResponse(info = ClevelandInfo(total = artworks.size), data = artworks.toList())
    }

    private class FakeArtInstituteApi(private vararg val artworks: ArtworkDto) : ArtInstituteApi {
        override suspend fun searchPublicDomain(
            query: String?,
            limit: Int,
            page: Int,
            publicDomain: Boolean,
            fields: String,
        ) = ArtSearchResponse(data = artworks.toList(), pagination = ArtPagination(totalPages = 1, currentPage = 1))
    }
}
