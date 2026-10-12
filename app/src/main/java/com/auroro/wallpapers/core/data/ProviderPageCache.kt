package com.auroro.wallpapers.core.data

import android.content.SharedPreferences
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import java.security.MessageDigest
import java.util.Locale

/**
 * Persistent metadata-only page cache shared by the approved public catalogues. Room stores the
 * wallpaper facts; preferences retain only ordered keys, expiry and the next page cursor.
 */
class ProviderPageCache(
    private val source: WallpaperSource,
    private val preferences: SharedPreferences,
    private val wallpapers: WallpaperStore,
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(ttlMillis > 0)
    }

    suspend fun get(request: FeedRequest, page: Int): ProviderPage? {
        val prefix = keyPrefix(request, page)
        val fetchedAt = preferences.getLong("${prefix}_at", 0L)
        val now = clock()
        if (fetchedAt <= 0L || now < fetchedAt || now - fetchedAt >= ttlMillis) return null
        val keys = preferences.getString("${prefix}_keys", null)
            ?.lineSequence()?.filter(String::isNotBlank)?.toList() ?: return null
        if (keys.any { Wallpaper.sourceOfKey(it) != source }) return null
        val items = wallpapers.getAll(keys)
        // If Room evicted any unreferenced rows, refetch the page rather than returning a partial cache.
        if (items.size != keys.size) return null
        val nextPage = preferences.getInt("${prefix}_next", 0).takeIf { it > 0 }
        return ProviderPage(items, nextPage?.let(::PageCursor))
    }

    suspend fun put(request: FeedRequest, page: Int, result: ProviderPage) {
        wallpapers.cacheForDiscovery(result.items)
        val prefix = keyPrefix(request, page)
        preferences.edit()
            .putLong("${prefix}_at", clock())
            .putString("${prefix}_keys", result.items.joinToString("\n") { it.key })
            .putInt("${prefix}_next", result.next?.page ?: 0)
            .apply()
    }

    private fun keyPrefix(request: FeedRequest, page: Int): String {
        val normalizedQuery = request.normalizedQuery.lowercase(Locale.ROOT)
        val identity = "${source.id}:$page:$normalizedQuery"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "provider_page_$digest"
    }
}
