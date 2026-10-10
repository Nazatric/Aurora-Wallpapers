package com.auroro.wallpapers.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local wallpaper metadata only: user-referenced records plus a bounded identifier-backed discovery cache.
 * Original image bytes never live in this table; provider IDs, URLs, dimensions and attribution support offline recovery.
 */
@Entity(tableName = "wallpaper")
data class WallpaperEntity(
    @PrimaryKey val key: String,
    val source: String,
    val sourceId: String,
    val pageUrl: String,
    val thumbUrl: String,
    val previewUrl: String,
    val originalUrl: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long?,
    val mimeType: String?,
    val creatorName: String?,
    val creatorUrl: String?,
    val category: String?,
    /** JSON array of {id,name}. */
    val tagsJson: String,
    /** JSON array of "#rrggbb". */
    val colorsJson: String,
    val createdAtRemote: String?,
    val views: Int?,
    val favorites: Int?,
    val originSourceUrl: String?,
    val updatedAt: Long,
    val title: String?,
    val attribution: String?,
    val licenseCode: String?,
    val licenseVersion: String?,
    val licenseUrl: String?,
    val providerName: String?,
    val catalogSource: String?,
    @ColumnInfo(defaultValue = "1") val downloadAllowed: Boolean = true,
    @ColumnInfo(defaultValue = "1") val setWallpaperAllowed: Boolean = true,
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val wallpaperKey: String,
    val addedAt: Long,
)

@Entity(tableName = "collection")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "collection_item",
    primaryKeys = ["collectionId", "wallpaperKey"],
    indices = [Index("wallpaperKey")],
)
data class CollectionItemEntity(
    val collectionId: Long,
    val wallpaperKey: String,
    val addedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val wallpaperKey: String,
    val viewedAt: Long,
)

enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELED }

@Entity(tableName = "download")
data class DownloadEntity(
    @PrimaryKey val wallpaperKey: String,
    val status: String,
    val bytesDownloaded: Long = 0,
    /** -1 when the server didn't report a length. */
    val totalBytes: Long = -1,
    /** `content://` (MediaStore) or `file://` (app storage) URI of the saved file. */
    val localUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val savedSizeBytes: Long? = null,
    val savedWidth: Int? = null,
    val savedHeight: Int? = null,
    /** GALLERY or APP. */
    val storage: String? = null,
    val quality: String? = null,
    val errorKind: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)
