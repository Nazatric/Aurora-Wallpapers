package com.auroro.wallpapers.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WallpaperEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WallpaperEntity>)

    @Query("SELECT * FROM wallpaper WHERE `key` = :key")
    suspend fun get(key: String): WallpaperEntity?

    @Query("SELECT * FROM wallpaper WHERE `key` IN (:keys)")
    suspend fun getAll(keys: List<String>): List<WallpaperEntity>

    /** Keeps a bounded metadata-only discovery cache while preserving rows referenced by user data. */
    @Query(
        """DELETE FROM wallpaper WHERE `key` IN (
             SELECT w.`key` FROM wallpaper w
             WHERE w.`key` NOT IN (SELECT wallpaperKey FROM favorite)
               AND w.`key` NOT IN (SELECT wallpaperKey FROM collection_item)
               AND w.`key` NOT IN (SELECT wallpaperKey FROM download)
               AND w.`key` NOT IN (SELECT wallpaperKey FROM history)
             ORDER BY w.updatedAt DESC LIMIT -1 OFFSET 600
           )""",
    )
    suspend fun trimUnreferenced()

    /** Explicit discovery reset only; user-referenced favorites, collections, downloads and history survive. */
    @Query(
        """DELETE FROM wallpaper WHERE `key` NOT IN (SELECT wallpaperKey FROM favorite)
           AND `key` NOT IN (SELECT wallpaperKey FROM collection_item)
           AND `key` NOT IN (SELECT wallpaperKey FROM download)
           AND `key` NOT IN (SELECT wallpaperKey FROM history)""",
    )
    suspend fun clearUnreferenced()

    @Query(
        """SELECT COUNT(*) FROM wallpaper w
           WHERE w.`key` NOT IN (SELECT wallpaperKey FROM favorite)
             AND w.`key` NOT IN (SELECT wallpaperKey FROM collection_item)
             AND w.`key` NOT IN (SELECT wallpaperKey FROM download)
             AND w.`key` NOT IN (SELECT wallpaperKey FROM history)""",
    )
    suspend fun countUnreferenced(): Int
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE wallpaperKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE wallpaperKey = :key)")
    suspend fun isFavorite(key: String): Boolean

    @Query("SELECT wallpaperKey FROM favorite")
    fun observeKeys(): Flow<List<String>>

    @Query(
        """SELECT w.* FROM wallpaper w INNER JOIN favorite f ON f.wallpaperKey = w.`key`
           ORDER BY f.addedAt DESC""",
    )
    fun observeFavorites(): Flow<List<WallpaperEntity>>

    @Query(
        """SELECT w.* FROM wallpaper w INNER JOIN favorite f ON f.wallpaperKey = w.`key`
           ORDER BY f.addedAt DESC LIMIT :limit""",
    )
    suspend fun recent(limit: Int): List<WallpaperEntity>
}

data class CollectionSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val cover1: String?,
    val cover2: String?,
    val cover3: String?,
    val cover4: String?,
)

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(entity: CollectionEntity): Long

    @Query("UPDATE collection SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM collection WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM collection_item WHERE collectionId = :id")
    suspend fun deleteItems(id: Long)

    @Query("SELECT COUNT(*) FROM collection WHERE LOWER(name) = LOWER(:name) AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_item WHERE collectionId = :collectionId AND wallpaperKey = :key")
    suspend fun removeItem(collectionId: Long, key: String)

    @Query("SELECT * FROM collection WHERE id = :id")
    fun observeCollection(id: Long): Flow<CollectionEntity?>

    @Query(
        """SELECT c.id AS id, c.name AS name, c.createdAt AS createdAt,
            (SELECT COUNT(*) FROM collection_item i WHERE i.collectionId = c.id) AS itemCount,
            (SELECT w.thumbUrl FROM collection_item i JOIN wallpaper w ON w.`key` = i.wallpaperKey
               WHERE i.collectionId = c.id ORDER BY i.addedAt DESC LIMIT 1 OFFSET 0) AS cover1,
            (SELECT w.thumbUrl FROM collection_item i JOIN wallpaper w ON w.`key` = i.wallpaperKey
               WHERE i.collectionId = c.id ORDER BY i.addedAt DESC LIMIT 1 OFFSET 1) AS cover2,
            (SELECT w.thumbUrl FROM collection_item i JOIN wallpaper w ON w.`key` = i.wallpaperKey
               WHERE i.collectionId = c.id ORDER BY i.addedAt DESC LIMIT 1 OFFSET 2) AS cover3,
            (SELECT w.thumbUrl FROM collection_item i JOIN wallpaper w ON w.`key` = i.wallpaperKey
               WHERE i.collectionId = c.id ORDER BY i.addedAt DESC LIMIT 1 OFFSET 3) AS cover4
           FROM collection c ORDER BY c.updatedAt DESC, c.id DESC""",
    )
    fun observeSummaries(): Flow<List<CollectionSummary>>

    @Query(
        """SELECT w.* FROM wallpaper w INNER JOIN collection_item i ON i.wallpaperKey = w.`key`
           WHERE i.collectionId = :id ORDER BY i.addedAt DESC""",
    )
    fun observeItems(id: Long): Flow<List<WallpaperEntity>>

    @Query("SELECT collectionId FROM collection_item WHERE wallpaperKey = :key")
    fun observeMembership(key: String): Flow<List<Long>>

    @Query("UPDATE collection SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query(
        """SELECT w.* FROM wallpaper w INNER JOIN history h ON h.wallpaperKey = w.`key`
           ORDER BY h.viewedAt DESC LIMIT :limit""",
    )
    fun observeRecent(limit: Int): Flow<List<WallpaperEntity>>

    @Query(
        """SELECT w.* FROM wallpaper w INNER JOIN history h ON h.wallpaperKey = w.`key`
           ORDER BY h.viewedAt DESC LIMIT :limit""",
    )
    suspend fun recent(limit: Int): List<WallpaperEntity>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE wallpaperKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM history WHERE wallpaperKey NOT IN (SELECT wallpaperKey FROM history ORDER BY viewedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("SELECT * FROM download WHERE wallpaperKey = :key")
    suspend fun get(key: String): DownloadEntity?

    @Query("SELECT * FROM download WHERE wallpaperKey = :key")
    fun observe(key: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM download ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("DELETE FROM download WHERE wallpaperKey = :key")
    suspend fun delete(key: String)

    @Query("UPDATE download SET status = :status, bytesDownloaded = :bytes, totalBytes = :total WHERE wallpaperKey = :key")
    suspend fun updateProgress(key: String, status: String, bytes: Long, total: Long)

    @Query(
        """UPDATE download SET status = :status, errorKind = :kind, errorMessage = :message,
           completedAt = :now WHERE wallpaperKey = :key""",
    )
    suspend fun markEnded(key: String, status: String, kind: String?, message: String?, now: Long)

    /** Anything left RUNNING/QUEUED with no worker alive (e.g. after process death) can be re-evaluated. */
    @Query("SELECT * FROM download WHERE status IN ('QUEUED','RUNNING')")
    suspend fun inFlight(): List<DownloadEntity>

    @Query("SELECT COALESCE(SUM(savedSizeBytes), 0) FROM download WHERE status = 'COMPLETED'")
    fun observeTotalSavedBytes(): Flow<Long>
}
