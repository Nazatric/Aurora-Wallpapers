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
        const val VERSION = 1
        const val NAME = "auroro.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Real migrations only. The database is never silently wiped on a schema change.
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Add one [Migration] per schema bump here (and commit the new `schemas/*.json`).
 * `MigrationsTest` verifies the chain is contiguous up to [AppDatabase.VERSION].
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
