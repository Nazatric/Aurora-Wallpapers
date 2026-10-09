package com.auroro.wallpapers.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        WallpaperEntity::class,
        FavoriteEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpapers(): WallpaperDao
    abstract fun favorites(): FavoriteDao
    abstract fun collections(): CollectionDao
    abstract fun history(): HistoryDao
    abstract fun downloads(): DownloadDao

    companion object {
        const val VERSION = 2
        const val NAME = "auroro.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Real migrations only. The database is never silently wiped on a schema change.
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Add one [Migration] per schema bump here (and commit the generated schema JSON).
 * `MigrationsTest` verifies the chain is contiguous up to [AppDatabase.VERSION].
 */
object Migrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `title` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `attribution` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `licenseCode` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `licenseVersion` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `licenseUrl` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `providerName` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `catalogSource` TEXT")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `downloadAllowed` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `wallpaper` ADD COLUMN `setWallpaperAllowed` INTEGER NOT NULL DEFAULT 1")

            // Keep old saved metadata and all memberships, history and download rows readable,
            // while ensuring retired providers are no longer treated as live catalogue sources.
            val retiredRows = "SELECT `key` FROM `wallpaper` WHERE `source` NOT IN ('wallhaven', 'openverse', 'archived')"
            db.execSQL("UPDATE `favorite` SET `wallpaperKey` = 'archived:' || `wallpaperKey` WHERE `wallpaperKey` IN ($retiredRows)")
            db.execSQL("UPDATE `collection_item` SET `wallpaperKey` = 'archived:' || `wallpaperKey` WHERE `wallpaperKey` IN ($retiredRows)")
            db.execSQL("UPDATE `history` SET `wallpaperKey` = 'archived:' || `wallpaperKey` WHERE `wallpaperKey` IN ($retiredRows)")
            db.execSQL("UPDATE `download` SET `wallpaperKey` = 'archived:' || `wallpaperKey` WHERE `wallpaperKey` IN ($retiredRows)")
            db.execSQL(
                "UPDATE `wallpaper` SET `key` = 'archived:' || `source` || ':' || `sourceId`, " +
                    "`sourceId` = `source` || ':' || `sourceId`, `source` = 'archived' " +
                    "WHERE `source` NOT IN ('wallhaven', 'openverse', 'archived')",
            )
            // Old records may have a saved original on device. Keep local wallpaper setting
            // available, but never refetch an archived provider URL as a new download.
            db.execSQL("UPDATE `wallpaper` SET `downloadAllowed` = 0 WHERE `source` = 'archived'")
            // Keep old local file identity, but don't fetch preview URLs from retired providers.
            db.execSQL("UPDATE `wallpaper` SET `thumbUrl` = '', `previewUrl` = '' WHERE `source` = 'archived'")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
