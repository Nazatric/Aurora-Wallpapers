package com.auroro.wallpapers.core.data.download

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperApplyRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() = runBlocking {
        server.close()
        database.close()
    }

    @Test fun setWallpaperOriginalIsTemporaryReusableAndNeverCreatesDownloadRecord() = runBlocking {
        val originalBytes = pngBytes()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(Buffer().write(originalBytes))
                .addHeader("Content-Type", "image/png")
                .build(),
        )
        val wallpaper = com.auroro.wallpapers.wallpaper(
            source = WallpaperSource.WALLHAVEN,
            id = "apply-only-${System.nanoTime()}",
            original = server.url("/full/original.png").toString(),
        )
        val repository = WallpaperApplyRepository(
            context = context,
            clientForSource = { OkHttpClient.Builder().build() },
            urlAllowed = { _, _ -> true }, // Isolated MockWebServer only; production uses UrlPolicy.
        )

        val first = repository.prepare(wallpaper)
        assertEquals(2, first.width)
        assertEquals(2, first.height)
        assertFalse(first.fromCache)
        assertTrue(first.uri.startsWith("file:"))
        val file = File(Uri.parse(first.uri).path!!)
        assertTrue(file.isFile)
        assertEquals(originalBytes.toList(), file.readBytes().toList())
        assertEquals(null, database.downloads().get(wallpaper.key))

        val second = repository.prepare(wallpaper)
        assertTrue(second.fromCache)
        assertEquals(first.uri, second.uri)
        assertEquals(1, server.requestCount)
        assertEquals(null, database.downloads().get(wallpaper.key))

        assertTrue(file.delete())
    }

    private fun pngBytes(): ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR4nGNg+P//PwMIMoBYQAYAVbwJ9wRbMhwAAAAASUVORK5CYII=",
    )
}
