package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource

/** Holds the real provider implementations and creates pagers for a request. */
class WallpaperAggregator(val providers: List<WallpaperProvider>) {

    fun provider(source: WallpaperSource): WallpaperProvider? = providers.firstOrNull { it.source == source }

    /** Providers selected by the filter (empty selection means every provider). */
    fun selected(request: FeedRequest): List<WallpaperProvider> {
        val wanted = request.filter.sources
        return if (wanted.isEmpty()) providers else providers.filter { it.source in wanted }
    }

    fun newPager(request: FeedRequest): FeedPager = FeedPager(selected(request), request)

    suspend fun availability(): Map<WallpaperSource, SourceAvailability> =
        providers.associate { it.source to it.availability() }

    suspend fun detail(key: String): Wallpaper? {
        val source = Wallpaper.sourceOfKey(key) ?: return null
        val p = provider(source) ?: return null
        if (!p.capabilities.supportsDetail) return null
        return p.detail(Wallpaper.idOfKey(key))
    }

    suspend fun related(w: Wallpaper): List<Wallpaper> {
        val p = provider(w.source) ?: return emptyList()
        return if (p.capabilities.supportsRelated) p.related(w) else emptyList()
    }
}
