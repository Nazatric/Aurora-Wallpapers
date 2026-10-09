package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.database.WallpaperDao
import com.auroro.wallpapers.core.database.WallpaperEntity
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.model.WallpaperTag
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Resolves wallpapers by key: recent feed results from memory (bounded LRU), everything the user
 * saved/favorited/viewed from Room. Only persists on intentional actions, never for plain browsing.
 */
class WallpaperStore(private val dao: WallpaperDao, private val clock: () -> Long = System::currentTimeMillis) {
    private val memory = object : LinkedHashMap<String, Wallpaper>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Wallpaper>?) = size > MAX_MEMORY
    }

    fun remember(items: List<Wallpaper>) {
        synchronized(memory) { items.forEach { memory[it.key] = it } }
    }

    suspend fun get(key: String): Wallpaper? {
        synchronized(memory) { memory[key] }?.let { return it }
        val fromDb = dao.get(key)?.let(WallpaperMapper::toModel) ?: return null
        remember(listOf(fromDb))
        return fromDb
    }

    /** Writes the wallpaper's metadata to Room (required before favoriting, collecting or downloading). */
    suspend fun persist(w: Wallpaper) {
        remember(listOf(w))
        dao.upsert(WallpaperMapper.toEntity(w, clock()))
    }

    companion object {
        const val MAX_MEMORY = 600
    }
}

object WallpaperMapper {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TagJson(val id: Long? = null, val name: String)

    fun toEntity(w: Wallpaper, now: Long) = WallpaperEntity(
        key = w.key,
        source = w.source.id,
        sourceId = w.sourceId,
        pageUrl = w.pageUrl,
        thumbUrl = w.thumbUrl,
        previewUrl = w.previewUrl,
        originalUrl = w.originalUrl,
        width = w.width,
        height = w.height,
        fileSizeBytes = w.fileSizeBytes,
        mimeType = w.mimeType,
        creatorName = w.creatorName,
        creatorUrl = w.creatorUrl,
        category = w.category,
        tagsJson = json.encodeToString(ListSerializer(TagJson.serializer()), w.tags.map { TagJson(it.id, it.name) }),
        colorsJson = json.encodeToString(ListSerializer(String.serializer()), w.colors),
        createdAtRemote = w.createdAt,
        views = w.views,
        favorites = w.favorites,
        originSourceUrl = w.originSourceUrl,
        updatedAt = now,
    )

    fun toModel(e: WallpaperEntity): Wallpaper? {
        val source = WallpaperSource.fromId(e.source) ?: return null
        val tags = runCatching { json.decodeFromString(ListSerializer(TagJson.serializer()), e.tagsJson) }
            .getOrDefault(emptyList()).map { WallpaperTag(it.id, it.name) }
        val colors = runCatching { json.decodeFromString(ListSerializer(String.serializer()), e.colorsJson) }.getOrDefault(emptyList())
        return Wallpaper(
            source = source,
            sourceId = e.sourceId,
            pageUrl = e.pageUrl,
            thumbUrl = e.thumbUrl,
            previewUrl = e.previewUrl,
            originalUrl = e.originalUrl,
            width = e.width,
            height = e.height,
            fileSizeBytes = e.fileSizeBytes,
            mimeType = e.mimeType,
            creatorName = e.creatorName,
            creatorUrl = e.creatorUrl,
            category = e.category,
            tags = tags,
            colors = colors,
            createdAt = e.createdAtRemote,
            views = e.views,
            favorites = e.favorites,
            originSourceUrl = e.originSourceUrl,
        )
    }
}
