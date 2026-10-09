package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallhavenCategory
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import com.auroro.wallpapers.core.network.abyss.AbyssWallpaperDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenTagDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenThumbsDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenUploaderDto
import com.auroro.wallpapers.core.network.wallhaven.WallhavenWallpaperDto
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderNormalizationTest {
    @Test fun wallhavenSfwRecordPreservesProviderMetadataAndUrls() {
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
                path = "https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg",
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
        assertEquals("https://th.wallhaven.cc/lg/ab/abc123.jpg", mapped.thumbUrl)
        assertEquals("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg", mapped.originalUrl)
        assertEquals(3840, mapped.width)
        assertEquals(2160, mapped.height)
        assertEquals("test-uploader", mapped.creatorName)
        assertEquals("ocean", mapped.tags.single().name)
        assertEquals("general", mapped.category?.lowercase())
        assertEquals(4_000L, mapped.fileSizeBytes)
        assertEquals(10, mapped.views)
        assertEquals("https://example.com/original", mapped.originSourceUrl)
    }

    @Test fun wallhavenNeverMapsNonSfwOrUntrustedUrls() {
        val base = WallhavenWallpaperDto(
            id = "abc123",
            url = "https://wallhaven.cc/w/abc123",
            purity = "sketchy",
            path = "https://w.wallhaven.cc/full/ab/file.jpg",
            thumbs = WallhavenThumbsDto(large = "https://th.wallhaven.cc/lg/ab/thumb.jpg"),
        )
        assertNull(WallhavenMapper.toWallpaper(base))
        assertNull(WallhavenMapper.toWallpaper(base.copy(purity = "sfw", path = "http://w.wallhaven.cc/file.jpg")))
        assertNull(WallhavenMapper.toWallpaper(base.copy(purity = "sfw", thumbs = WallhavenThumbsDto(large = "https://attacker.example/thumb.jpg"))))
    }

    @Test fun abyssRecordIsNormalizedWithoutInventingCreatorProfileLinks() {
        val mapped = AbyssMapper.toWallpaper(
            AbyssWallpaperDto(
                id = JsonPrimitive(931204),
                width = JsonPrimitive("2160"),
                height = JsonPrimitive(3840),
                fileType = "jpg",
                fileSize = JsonPrimitive("8192"),
                urlImage = "https://images2.alphacoders.com/931/931204.jpg",
                urlThumb = "https://images2.alphacoders.com/931/thumb-350-931204.jpg",
                urlPage = "https://wall.alphacoders.com/big.php?i=931204",
                category = "Nature",
                subCategory = "Oceans",
                userName = "artist",
                userId = JsonPrimitive(7),
            ),
        )!!
        assertEquals(WallpaperSource.ABYSS, mapped.source)
        assertEquals("931204", mapped.sourceId)
        assertEquals(2160, mapped.width)
        assertEquals(3840, mapped.height)
        assertEquals("image/jpeg", mapped.mimeType)
        assertEquals(8192L, mapped.fileSizeBytes)
        assertEquals("artist", mapped.creatorName)
        assertNull(mapped.creatorUrl) // Official response docs do not guarantee a profile URL.
        assertEquals(listOf("Nature", "Oceans"), mapped.tags.map { it.name })
        assertEquals("https://wall.alphacoders.com/big.php?i=931204", mapped.pageUrl)
    }

    @Test fun abyssMapperRejectsUntrustedImageAndPageUrls() {
        val dto = AbyssWallpaperDto(
            id = JsonPrimitive(1),
            urlImage = "https://evil.example/image.jpg",
            urlThumb = "https://images.alphacoders.com/thumb.jpg",
            urlPage = "https://wall.alphacoders.com/big.php?i=1",
        )
        assertNull(AbyssMapper.toWallpaper(dto))
        assertFalse(UrlPolicy.isAllowedForNetwork("http://wallhaven.cc/api/v1/search"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://wallhaven.cc.evil.example/image.jpg"))
        assertTrue(UrlPolicy.isAllowedForNetwork("https://images2.alphacoders.com/image.jpg"))
        assertTrue(UrlPolicy.isAllowedForBrowsing("https://unsplash.com/s/photos/ocean"))
        assertFalse(UrlPolicy.isAllowedForNetwork("https://unsplash.com/api/photos"))
    }

    @Test fun wallhavenQueryUsesOnlyDocumentedSafeParameters() {
        val default = WallhavenQuery.build(FeedRequest(), com.auroro.wallpapers.core.data.PageCursor(),)
        assertEquals("100", default["purity"])
        assertEquals("111", default["categories"])
        assertEquals("date_added", default["sorting"])
        assertFalse("apikey" in default)
        assertFalse("seed" in default)

        val request = FeedRequest(
            query = "  ocean  light ",
            filter = com.auroro.wallpapers.core.model.WallpaperFilter(
                sort = SortOption.POPULAR,
                wallhavenCategories = setOf(WallhavenCategory.ANIME, WallhavenCategory.PEOPLE),
                colorHex = "#66cccc",
            ),
        )
        val query = WallhavenQuery.build(request, com.auroro.wallpapers.core.data.PageCursor(page = 4))
        assertEquals("ocean light", query["q"])
        assertEquals("011", query["categories"])
        assertEquals("100", query["purity"])
        assertEquals("toplist", query["sorting"])
        assertEquals("1M", query["topRange"])
        assertEquals("4", query["page"])
        assertEquals("66cccc", query["colors"])
        assertEquals(null, query["ratios"])
    }

    @Test fun unsupportedWallhavenRatioAndResolutionFiltersStayLocalOrConservative() {
        val portrait = FeedRequest(filter = com.auroro.wallpapers.core.model.WallpaperFilter(orientation = com.auroro.wallpapers.core.model.Orientation.PORTRAIT))
        assertNull(WallhavenQuery.ratios(portrait)) // API doesn't accept made-up "portrait" tokens.
        assertEquals("2160x3840", WallhavenQuery.atLeast(portrait.copy(filter = portrait.filter.copy(resolution = com.auroro.wallpapers.core.model.ResolutionFilter.Preset(com.auroro.wallpapers.core.model.ResolutionPreset.K4))))
    }

    @Test fun abyssQueriesDesktopAndPhoneIndependentlyWhenOrientationIsUnspecified() {
        val request = FeedRequest(query = "sea glass")
        assertEquals(listOf("desktop", "phone"), AbyssQuery.variants(request))
        assertEquals(listOf("phone"), AbyssQuery.variants(request.copy(filter = request.filter.copy(orientation = com.auroro.wallpapers.core.model.Orientation.PORTRAIT))))
        assertEquals(listOf("desktop"), AbyssQuery.variants(request.copy(filter = request.filter.copy(orientation = com.auroro.wallpapers.core.model.Orientation.LANDSCAPE))))
        assertEquals("search", AbyssQuery.build(request, PageCursor(page = 3), "abc", "phone")["method"])
        assertEquals("sea glass", AbyssQuery.build(request, PageCursor(page = 3), "abc", "phone")["term"])
        assertEquals("3", AbyssQuery.build(request, PageCursor(page = 3), "abc", "phone")["page"])
        assertEquals("phone", AbyssQuery.build(request, PageCursor(page = 3), "abc", "phone")["type"])
        assertEquals(SortOption.NEWEST, SortOption.valueOf("NEWEST"))
    }
}
