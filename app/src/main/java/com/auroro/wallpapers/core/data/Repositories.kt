package com.auroro.wallpapers.core.data

import androidx.room.withTransaction
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.database.CollectionEntity
import com.auroro.wallpapers.core.database.CollectionItemEntity
import com.auroro.wallpapers.core.database.CollectionSummary
import com.auroro.wallpapers.core.database.FavoriteEntity
import com.auroro.wallpapers.core.database.HistoryEntity
import com.auroro.wallpapers.core.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoritesRepository(private val db: AppDatabase, private val store: WallpaperStore, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeKeys(): Flow<Set<String>> = db.favorites().observeKeys().map { it.toSet() }

    fun observeFavorites(): Flow<List<Wallpaper>> = db.favorites().observeFavorites().map { list -> list.mapNotNull(WallpaperMapper::toModel) }

    /** Favoriting stores metadata only. It never downloads the image. */
    suspend fun setFavorite(w: Wallpaper, favorite: Boolean) {
        if (favorite) {
            db.withTransaction {
                store.persist(w)
                db.favorites().insert(FavoriteEntity(w.key, clock()))
            }
        } else {
            db.favorites().delete(w.key)
            db.wallpapers().deleteUnreferenced()
        }
    }

    suspend fun toggle(w: Wallpaper): Boolean {
        val now = !db.favorites().isFavorite(w.key)
        setFavorite(w, now)
        return now
    }

    suspend fun isFavorite(key: String) = db.favorites().isFavorite(key)
}

sealed interface CollectionNameResult {
    data class Ok(val name: String) : CollectionNameResult
    data class Error(val message: String) : CollectionNameResult
}

class CollectionsRepository(private val db: AppDatabase, private val store: WallpaperStore, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeSummaries(): Flow<List<CollectionSummary>> = db.collections().observeSummaries()
    fun observeCollection(id: Long): Flow<CollectionEntity?> = db.collections().observeCollection(id)
    fun observeItems(id: Long): Flow<List<Wallpaper>> = db.collections().observeItems(id).map { it.mapNotNull(WallpaperMapper::toModel) }
    fun observeMembership(key: String): Flow<Set<Long>> = db.collections().observeMembership(key).map { it.toSet() }

    suspend fun validateName(raw: String, exceptId: Long = 0): CollectionNameResult {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        return when {
            name.isEmpty() -> CollectionNameResult.Error("Give the collection a name.")
            name.length > MAX_NAME -> CollectionNameResult.Error("Names are limited to $MAX_NAME characters.")
            db.collections().countByName(name, exceptId) > 0 -> CollectionNameResult.Error("You already have a collection with that name.")
            else -> CollectionNameResult.Ok(name)
        }
    }

    suspend fun create(raw: String): Result<Long> = when (val v = validateName(raw)) {
        is CollectionNameResult.Error -> Result.failure(IllegalArgumentException(v.message))
        is CollectionNameResult.Ok -> {
            val now = clock()
            Result.success(db.collections().insert(CollectionEntity(name = v.name, createdAt = now, updatedAt = now)))
        }
    }

    suspend fun rename(id: Long, raw: String): Result<Unit> = when (val v = validateName(raw, id)) {
        is CollectionNameResult.Error -> Result.failure(IllegalArgumentException(v.message))
        is CollectionNameResult.Ok -> Result.success(db.collections().rename(id, v.name, clock()))
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            db.collections().deleteItems(id)
            db.collections().delete(id)
        }
        db.wallpapers().deleteUnreferenced()
    }

    suspend fun add(id: Long, w: Wallpaper) {
        db.withTransaction {
            store.persist(w)
            val now = clock()
            db.collections().addItem(CollectionItemEntity(id, w.key, now))
            db.collections().touch(id, now)
        }
    }

    suspend fun remove(id: Long, key: String) {
        db.collections().removeItem(id, key)
        db.wallpapers().deleteUnreferenced()
    }

    companion object {
        const val MAX_NAME = 40
    }
}

class HistoryRepository(private val db: AppDatabase, private val store: WallpaperStore, private val clock: () -> Long = System::currentTimeMillis) {
    fun observeRecent(limit: Int = 200): Flow<List<Wallpaper>> = db.history().observeRecent(limit).map { it.mapNotNull(WallpaperMapper::toModel) }

    /** Called when the user deliberately opens a wallpaper's detail screen. */
    suspend fun record(w: Wallpaper) {
        db.withTransaction {
            store.persist(w)
            db.history().upsert(HistoryEntity(w.key, clock()))
            db.history().trim(KEEP)
        }
    }

    suspend fun clear() {
        db.history().clear()
        db.wallpapers().deleteUnreferenced()
    }

    companion object {
        const val KEEP = 300
    }
}

/**
 * Deterministic "For You" signal. No machine learning: it counts the tags of wallpapers the user favorited
 * (weighted higher) and recently viewed, and returns the most frequent ones.
 */
class PreferenceSignals(private val db: AppDatabase) {
    suspend fun topTags(max: Int = 3): List<String> {
        val favs = db.favorites().recent(60).mapNotNull(WallpaperMapper::toModel)
        val viewed = db.history().recent(60).mapNotNull(WallpaperMapper::toModel)
        return rank(favs, viewed, max)
    }

    companion object {
        private val IGNORED = setOf("wallpaper", "background", "hd")

        fun rank(favorites: List<Wallpaper>, viewed: List<Wallpaper>, max: Int): List<String> {
            val score = HashMap<String, Int>()
            favorites.forEach { w -> w.tags.forEach { t -> score.merge(t.name.lowercase(), 3, Int::plus) } }
            viewed.forEach { w -> w.tags.forEach { t -> score.merge(t.name.lowercase(), 1, Int::plus) } }
            return score.entries
                .filter { it.key !in IGNORED && it.key.length in 3..30 }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(max)
                .map { it.key }
        }
    }
}
