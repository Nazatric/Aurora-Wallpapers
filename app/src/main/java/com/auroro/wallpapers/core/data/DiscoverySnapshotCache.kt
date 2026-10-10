package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.DiscoverySelection
import com.auroro.wallpapers.core.model.Wallpaper

/** Identifier-only snapshots keep feed state local; image bytes remain in Coil and are disposable. */
class DiscoverySnapshotCache(
    private val settings: SettingsRepository,
    private val wallpapers: WallpaperStore,
) {
    suspend fun restore(cacheKey: String): List<Wallpaper> =
        wallpapers.getAll(settings.discoverySnapshot(cacheKey))

    suspend fun restoreLatest(): Pair<String, List<Wallpaper>>? {
        val (key, ids) = settings.latestDiscoverySnapshot() ?: return null
        val items = wallpapers.getAll(ids)
        return (key to items).takeIf { items.isNotEmpty() }
    }

    suspend fun save(selection: DiscoverySelection, items: List<Wallpaper>, now: Long) {
        val usableKeys = items.distinctBy { it.key }.map { it.key }
        if (usableKeys.isEmpty()) return
        wallpapers.cacheForDiscovery(items)
        settings.saveDiscoverySnapshot(selection.cacheKey, usableKeys)
        settings.recordDiscoverySeen(usableKeys, now)
    }

    suspend fun recentlySeen(now: Long, repeatAfterHours: Int): Set<String> =
        settings.recentlySeenDiscoveryKeys(now, repeatAfterHours)

    suspend fun clear() {
        settings.resetDiscovery()
        wallpapers.clearDiscoveryCache()
    }
}
