package com.auroro.wallpapers.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationsTest {
    @Test fun migrationKeepsSavedFilesAndUserReferencesWhenRetiringOldProviders() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "auroro-migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `wallpaper` (" +
                                "`key` TEXT NOT NULL PRIMARY KEY, `source` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                                "`originalUrl` TEXT NOT NULL, `thumbUrl` TEXT NOT NULL, `previewUrl` TEXT NOT NULL)",
                        )
                        db.execSQL("CREATE TABLE `favorite` (`wallpaperKey` TEXT NOT NULL PRIMARY KEY, `addedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE `collection_item` (`collectionId` INTEGER NOT NULL, `wallpaperKey` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `wallpaperKey`))")
                        db.execSQL("CREATE TABLE `history` (`wallpaperKey` TEXT NOT NULL PRIMARY KEY, `viewedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE `download` (`wallpaperKey` TEXT NOT NULL PRIMARY KEY, `status` TEXT NOT NULL, `localUri` TEXT, `fileName` TEXT)")

                        db.execSQL(
                            "INSERT INTO `wallpaper` (`key`, `source`, `sourceId`, `originalUrl`, `thumbUrl`, `previewUrl`) " +
                                "VALUES ('abyss:42', 'abyss', '42', 'https://images.example.org/42.jpg', 'old-thumb', 'old-preview')",
                        )
                        db.execSQL(
                            "INSERT INTO `wallpaper` (`key`, `source`, `sourceId`, `originalUrl`, `thumbUrl`, `previewUrl`) " +
                                "VALUES ('wallhaven:abc123', 'wallhaven', 'abc123', 'https://w.wallhaven.cc/abc123.jpg', 'old-thumb', 'old-preview')",
                        )
                        db.execSQL("INSERT INTO `favorite` (`wallpaperKey`, `addedAt`) VALUES ('abyss:42', 10)")
                        db.execSQL("INSERT INTO `collection_item` (`collectionId`, `wallpaperKey`, `addedAt`) VALUES (7, 'abyss:42', 11)")
                        db.execSQL("INSERT INTO `history` (`wallpaperKey`, `viewedAt`) VALUES ('abyss:42', 12)")
                        db.execSQL(
                            "INSERT INTO `download` (`wallpaperKey`, `status`, `localUri`, `fileName`) " +
                                "VALUES ('abyss:42', 'COMPLETED', 'content://media/existing-file', 'saved-original.jpg')",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )

        try {
            val db = helper.writableDatabase
            Migrations.MIGRATION_1_2.migrate(db)

            db.query("SELECT `key`, `source`, `sourceId`, `originalUrl`, `thumbUrl`, `downloadAllowed`, `setWallpaperAllowed` FROM `wallpaper` WHERE `key` = 'archived:abyss:42'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("archived:abyss:42", row.getString(0))
                assertEquals("archived", row.getString(1))
                assertEquals("abyss:42", row.getString(2))
                assertEquals("https://images.example.org/42.jpg", row.getString(3))
                assertEquals(0, row.getInt(5)) // No new fetches from a retired source.
                assertEquals(1, row.getInt(6)) // A saved local original can still be applied.
            }
            assertEquals("", scalarText(db, "SELECT `thumbUrl` FROM `wallpaper` WHERE `key` = 'archived:abyss:42'"))
            assertEquals("", scalarText(db, "SELECT `previewUrl` FROM `wallpaper` WHERE `key` = 'archived:abyss:42'"))
            assertEquals("old-thumb", scalarText(db, "SELECT `thumbUrl` FROM `wallpaper` WHERE `key` = 'wallhaven:abc123'"))
            assertEquals("old-preview", scalarText(db, "SELECT `previewUrl` FROM `wallpaper` WHERE `key` = 'wallhaven:abc123'"))
            assertEquals("archived:abyss:42", scalarText(db, "SELECT `wallpaperKey` FROM `favorite`"))
            assertEquals("archived:abyss:42", scalarText(db, "SELECT `wallpaperKey` FROM `collection_item`"))
            assertEquals("archived:abyss:42", scalarText(db, "SELECT `wallpaperKey` FROM `history`"))
            assertEquals("archived:abyss:42", scalarText(db, "SELECT `wallpaperKey` FROM `download`"))
            assertEquals("content://media/existing-file", scalarText(db, "SELECT `localUri` FROM `download` WHERE `wallpaperKey` = 'archived:abyss:42'"))
            assertEquals("saved-original.jpg", scalarText(db, "SELECT `fileName` FROM `download` WHERE `wallpaperKey` = 'archived:abyss:42'"))
            assertFalse(scalarText(db, "SELECT `source` FROM `wallpaper` WHERE `key` = 'archived:abyss:42'") == "abyss")
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    private fun scalarText(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "No row for query: $sql" }
            cursor.getString(0)
        }
}
