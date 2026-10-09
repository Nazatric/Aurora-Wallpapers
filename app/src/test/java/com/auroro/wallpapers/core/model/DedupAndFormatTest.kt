package com.auroro.wallpapers.core.model

import com.auroro.wallpapers.wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupAndFormatTest {
    @Test fun deduplicatesByStableProviderIdAndCanonicalUrlOnly() {
        val a = wallpaper(id = "abc123", original = "https://w.wallhaven.cc/full/ab/file.jpg?size=large#x")
        val sameUrl = wallpaper(
            source = WallpaperSource.ABYSS,
            id = "778899",
            original = "https://www.w.wallhaven.cc/full/ab/file.jpg?token=other",
            page = "https://wall.alphacoders.com/big.php?i=778899",
        )
        val visuallySimilarButDistinct = wallpaper(id = "def456", original = "https://w.wallhaven.cc/full/de/another.jpg")
        val dedup = Deduplicator()
        assertEquals(listOf(a), dedup.filterNew(listOf(a)))
        assertEquals(emptyList<Wallpaper>(), dedup.filterNew(listOf(a)))
        assertEquals(emptyList<Wallpaper>(), dedup.filterNew(listOf(sameUrl)))
        assertEquals(listOf(visuallySimilarButDistinct), dedup.filterNew(listOf(visuallySimilarButDistinct)))
        dedup.reset()
        assertEquals(listOf(a), dedup.filterNew(listOf(a, sameUrl))) // same canonical URL is merged even when ids differ
    }

    @Test fun canonicalUrlDropsQueryAndFragmentAndNormalizesHost() {
        assertEquals("wallhaven.cc/w/abc123", Deduplicator.canonicalUrl("https://www.wallhaven.cc/w/abc123?x=1#y"))
        assertEquals("w.wallhaven.cc/file.jpg", Deduplicator.canonicalUrl("https://w.wallhaven.cc/file.jpg"))
        assertEquals(
            "wall.alphacoders.com/big.php?i=123",
            Deduplicator.canonicalUrl("https://wall.alphacoders.com/big.php?i=123&utm_source=example"),
        )
        assertEquals("wall.alphacoders.com/big.php?i=124", Deduplicator.canonicalUrl("https://wall.alphacoders.com/big.php?i=124"))
        assertNull(Deduplicator.canonicalUrl("not a url"))
    }

    @Test fun providerIdsIncludeSourceNamespace() {
        assertNotEquals(Wallpaper.keyOf(WallpaperSource.WALLHAVEN, "1"), Wallpaper.keyOf(WallpaperSource.ABYSS, "1"))
        assertEquals(WallpaperSource.WALLHAVEN, Wallpaper.sourceOfKey("wallhaven:abc123"))
        assertEquals("abc123", Wallpaper.idOfKey("wallhaven:abc123"))
    }

    @Test fun formattingHandlesUnknownAndLargeValues() {
        assertEquals("—", Format.fileSize(null))
        assertEquals("1.0 MB", Format.fileSize(1024L * 1024))
        assertEquals("3840 × 2160", Format.dimensions(3840, 2160))
        assertTrue(Format.fileSize(0).startsWith("0"))
    }
}
