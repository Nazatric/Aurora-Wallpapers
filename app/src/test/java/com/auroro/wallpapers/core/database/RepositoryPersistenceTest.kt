package com.auroro.wallpapers.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroro.wallpapers.core.data.CollectionsRepository
import com.auroro.wallpapers.core.data.FavoritesRepository
import com.auroro.wallpapers.core.data.HistoryRepository
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.wallpaper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryPersistenceTest {
    private lateinit var db: AppDatabase
    private lateinit var store: WallpaperStore
    private lateinit var favorites: FavoritesRepository
    private lateinit var collections: CollectionsRepository
    private lateinit var history: HistoryRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        store = WallpaperStore(db.wallpapers())
        favorites = FavoritesRepository(db, store, clock = { 10L })
        collections = CollectionsRepository(db, store, clock = { 20L })
        history = HistoryRepository(db, store, clock = { 30L })
    }

    @After fun tearDown() = runBlocking { db.close() }

    @Test fun favoritePersistsMetadataWithoutDownloadingOriginal() = runBlocking {
        val w = wallpaper(id = "persist01")
        favorites.setFavorite(w, true)
        assertTrue(favorites.isFavorite(w.key))
        assertEquals(setOf(w.key), favorites.observeKeys().first())
        assertEquals(w.key, favorites.observeFavorites().first().single().key)
        assertEquals(null, db.downloads().get(w.key))
        assertEquals(w.originalUrl, db.wallpapers().get(w.key)?.originalUrl)

        favorites.setFavorite(w, false)
        assertFalse(favorites.isFavorite(w.key))
        assertNull(db.wallpapers().get(w.key)) // unreferenced metadata is cleaned up
    }

    @Test fun collectionCreateRenameAddRemoveAndDeleteArePersistent() = runBlocking {
        val id = collections.create("  Ocean  Light ").getOrThrow()
        assertEquals("Ocean Light", db.collections().observeCollection(id).first()?.name)
        assertTrue(collections.create("ocean light").isFailure) // names are case-insensitively unique
        val w = wallpaper(source = WallpaperSource.WALLHAVEN, id = "collect01")
        collections.add(id, w)
        val summary = collections.observeSummaries().first().single()
        assertEquals(1, summary.itemCount)
        assertEquals(setOf(id), collections.observeMembership(w.key).first())
        assertEquals(w.key, collections.observeItems(id).first().single().key)
        assertTrue(collections.rename(id, "Blue hour").isSuccess)
        assertEquals("Blue hour", db.collections().observeCollection(id).first()?.name)
        collections.remove(id, w.key)
        assertEquals(0, collections.observeSummaries().first().single().itemCount)
        collections.add(id, w)
        collections.delete(id)
        assertTrue(collections.observeSummaries().first().isEmpty())
        assertNull(db.wallpapers().get(w.key))
    }

    @Test fun historyIsSeparateAndCanBeClearedWithoutTouchingFavorites() = runBlocking {
        val favorite = wallpaper(id = "favorite02")
        val viewed = wallpaper(source = WallpaperSource.ABYSS, id = "viewed03")
        favorites.setFavorite(favorite, true)
        history.record(viewed)
        assertEquals(listOf(viewed.key), history.observeRecent().first().map { it.key })
        history.clear()
        assertTrue(history.observeRecent().first().isEmpty())
        assertTrue(favorites.isFavorite(favorite.key))
        assertNull(db.wallpapers().get(viewed.key))
    }

    @Test fun downloadRowsRecordPermanentFileMetadata() = runBlocking {
        val w = wallpaper(id = "download01")
        store.persist(w)
        db.downloads().upsert(
            DownloadEntity(
                wallpaperKey = w.key,
                status = DownloadStatus.COMPLETED.name,
                bytesDownloaded = 1234,
                totalBytes = 1234,
                localUri = "content://media/external/images/media/1",
                fileName = "auroro_wallhaven_download01.jpg",
                mimeType = "image/jpeg",
                savedSizeBytes = 1234,
                savedWidth = 2160,
                savedHeight = 3840,
                storage = "GALLERY",
                quality = "ORIGINAL",
                createdAt = 1,
                completedAt = 2,
            ),
        )
        val saved = db.downloads().get(w.key)!!
        assertEquals(DownloadStatus.COMPLETED.name, saved.status)
        assertEquals("content://media/external/images/media/1", saved.localUri)
        assertEquals(2160, saved.savedWidth)
        assertEquals(3840, saved.savedHeight)
        assertEquals(1234L, saved.savedSizeBytes)
    }
}
