package com.auroro.wallpapers.core.data.download

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.auroro.wallpapers.core.data.SettingsRepository
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.WallpaperSource
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadPipelineTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var settings: SettingsRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer().apply { start() }
        val prefs = PreferenceDataStoreFactory.create(produceFile = { File(temp.root, "settings.preferences_pb") })
        settings = SettingsRepository(prefs)
    }

    @After fun tearDown() = runBlocking {
        server.close()
        db.close()
    }

    @Test fun originalIsStreamedValidatedAndSavedAsARealOfflineFile() = runBlocking {
        val png = pngBytes()
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(png)).addHeader("Content-Type", "image/png").build())
        val w = com.auroro.wallpapers.wallpaper(
            source = WallpaperSource.WALLHAVEN,
            id = "download01",
            original = server.url("/full-resolution.png").toString(),
        ).copy(
            // The gallery thumbnail is intentionally different; only the original endpoint may be requested.
            thumbUrl = server.url("/thumbnail.jpg").toString(),
            previewUrl = server.url("/preview.jpg").toString(),
        )
        val store = WallpaperStore(db.wallpapers())
        store.persist(w)
        val destination = File(temp.root, "saved")
        val executor = DownloadExecutor(
            tempDir = File(temp.root, "temp"),
            db = db,
            store = store,
            settings = settings,
            client = OkHttpClient.Builder().build(),
            saver = { AppStorageSaver(destination) },
            screenLongEdge = { 1080 },
            urlAllowed = { true }, // Isolated local mock server; release builds retain UrlPolicy's strict allow-list.
        )

        // Robolectric's StatFs defaults to zero free blocks unless a fixture is registered.
        ShadowStatFs.registerStats(File(temp.root, "temp"), 100_000, 100_000, 100_000)
        val progress = ArrayList<Pair<Long, Long>>()
        val completed = executor.execute(w.key) { bytes, total -> progress += bytes to total }
        val failure = db.downloads().get(w.key)
        assertTrue("Download failed: ${failure?.errorKind}: ${failure?.errorMessage}", completed)
        val request = server.takeRequest()
        assertEquals("/full-resolution.png", request.url.encodedPath)
        assertTrue(progress.isNotEmpty())
        val row = db.downloads().get(w.key)!!
        assertEquals(DownloadStatus.COMPLETED.name, row.status)
        assertEquals("image/png", row.mimeType)
        assertEquals(2, row.savedWidth)
        assertEquals(2, row.savedHeight)
        assertEquals(png.size.toLong(), row.savedSizeBytes)
        assertNotNull(row.localUri)
        val saved = File(android.net.Uri.parse(row.localUri).path!!)
        assertTrue(saved.isFile)
        assertEquals(png.toList(), saved.readBytes().toList())
        assertTrue(ImageValidator.inspect(saved) != null)
        assertTrue(File(temp.root, "temp").listFiles().orEmpty().isEmpty()) // no partial files remain
    }

    @Test fun invalidImageResponseIsRecordedAsFailureAndNeverPresentedOffline() = runBlocking {
        server.enqueue(MockResponse(code = 200, body = "this is HTML, not a wallpaper").newBuilder().addHeader("Content-Type", "text/html").build())
        val w = com.auroro.wallpapers.wallpaper(id = "notimage", original = server.url("/not-an-image").toString())
        val store = WallpaperStore(db.wallpapers())
        store.persist(w)
        val executor = executor(store)
        assertFalse(executor.execute(w.key))
        assertEquals(1, server.requestCount)
        val row = db.downloads().get(w.key)!!
        assertEquals(DownloadStatus.FAILED.name, row.status)
        assertEquals(DownloadErrorKind.INVALID_IMAGE.name, row.errorKind)
        assertEquals(null, row.localUri)
    }

    @Test fun rateLimitIsNotRetriedAndFailureCanBeExplained() = runBlocking {
        server.enqueue(MockResponse(code = 429, body = "slow down"))
        val w = com.auroro.wallpapers.wallpaper(id = "limited", original = server.url("/rate-limited").toString())
        val store = WallpaperStore(db.wallpapers())
        store.persist(w)
        assertFalse(executor(store).execute(w.key))
        assertEquals(1, server.requestCount)
        val row = db.downloads().get(w.key)!!
        assertEquals(DownloadErrorKind.RATE_LIMITED.name, row.errorKind)
        assertTrue(row.errorMessage!!.contains("rate limiting"))
    }

    @Test fun imageBoundsValidationReportsDimensionsAndRejectsCorruptData() {
        val file = File(temp.root, "tiny.png").apply { writeBytes(pngBytes()) }
        val info = ImageValidator.inspect(file)!!
        assertEquals("image/png", info.mime)
        assertEquals(2, info.width)
        assertEquals(2, info.height)
        assertEquals(null, ImageValidator.inspect(File(temp.root, "missing.jpg")))
        val corrupt = File(temp.root, "corrupt.png").apply { writeText("not a decodable image") }
        assertEquals(null, ImageValidator.inspect(corrupt))
    }

    @Test fun fitToScreenDownscalePlanPreservesOrientationAndAspect() {
        assertEquals(1080 to 1920, Downscaler.plan(2160, 3840, 1920))
        assertEquals(1920 to 1080, Downscaler.plan(3840, 2160, 1920))
        assertEquals(null, Downscaler.plan(1920, 1080, 1920))
    }

    @Test fun cropGeometryHandlesPortraitAndPanning() {
        val centered = CropMath.centerCrop(2160, 3840, 1080, 2400)
        assertTrue(centered.left > 0f)
        assertTrue(centered.right < 1f)
        assertTrue(centered.top == 0f)
        assertTrue(centered.bottom == 1f)
        val panned = CropMath.visibleRect(2160, 3840, 1080f, 2400f, 2f, 500f, 0f)
        assertTrue(panned.left > centered.left)
        assertTrue(panned.width < centered.width)
        assertTrue(panned.left >= 0f && panned.right <= 1f)
    }

    private fun executor(store: WallpaperStore) = DownloadExecutor(
        tempDir = File(temp.root, "temp"),
        db = db,
        store = store,
        settings = settings,
        client = OkHttpClient.Builder().build(),
        saver = { AppStorageSaver(File(temp.root, "saved")) },
        screenLongEdge = { 1080 },
        urlAllowed = { true },
    )

    private fun pngBytes(): ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR4nGNg+P//PwMIMoBYQAYAVbwJ9wRbMhwAAAAASUVORK5CYII=",
    )
}
