package com.auroro.wallpapers.core.model

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** Search phrases are deliberately plain Wallhaven text queries, not claims that these are provider tags. */
enum class DiscoveryStyle(
    val id: String,
    val label: String,
    val description: String,
    val queries: List<String>,
    val relevanceTerms: Set<String>,
) {
    FRUTIGER_AERO(
        "frutiger-aero",
        "Frutiger Aero",
        "Aqua environments, glass architecture and optimistic retro-futures",
        listOf("frutiger aero", "aqua glass architecture", "water sky futuristic", "nature"),
        setOf("aero", "aqua", "glass", "water", "sky", "architecture", "retro", "future", "nature"),
    ),
    FRUTIGER_FLOWER(
        "frutiger-flower",
        "Frutiger Flower",
        "Luminous botanicals, organic forms and translucent petals",
        listOf("frutiger flower", "translucent flower", "luminous botanical art", "flower"),
        setOf("flower", "floral", "botanical", "petal", "organic", "luminous", "translucent"),
    ),
    FRUTIGER_ZEN(
        "frutiger-zen",
        "Frutiger Zen",
        "Quiet water, gentle light and meditative environments",
        listOf("frutiger zen", "serene water architecture", "floating island calm", "water"),
        setOf("zen", "serene", "water", "floating", "calm", "meditative", "nature", "light"),
    ),
    DARK_AERO(
        "dark-aero",
        "Dark Aero",
        "Reflective deep blues, luminous cyan and dramatic glass",
        listOf("dark aero", "dark blue glass cyan", "deep ocean luminous", "cyan"),
        setOf("dark", "blue", "glass", "cyan", "ocean", "luminous", "reflective", "futuristic"),
    ),
    SURREAL(
        "surreal",
        "Surrealism",
        "Impossible structures and unexpected visual relationships",
        listOf("surreal architecture landscape", "impossible architecture", "dreamlike surreal environment", "surreal"),
        setOf("surreal", "impossible", "dreamlike", "architecture", "landscape", "digital", "art"),
    ),
    WEIRDCORE(
        "weirdcore",
        "Weirdcore / Liminal",
        "Uncanny quiet spaces, dreamcore and liminal environments",
        listOf("weirdcore liminal", "dreamcore environment", "liminal architecture", "liminal"),
        setOf("weirdcore", "dreamcore", "liminal", "uncanny", "empty", "space", "architecture"),
    ),
    VAPORWAVE_Y2K(
        "vaporwave-y2k",
        "Vaporwave / Y2K",
        "Retro-futurism, chromed forms and digital colour",
        listOf("vaporwave retro futurism", "y2k abstract 3d", "retro future digital art", "vaporwave"),
        setOf("vaporwave", "y2k", "retro", "futurism", "abstract", "chrome", "liquid", "metal", "digital"),
    ),
    ABSTRACT_DIGITAL(
        "abstract-digital",
        "Abstract Digital Art",
        "Experimental 3D forms, underwater worlds and liquid metal",
        listOf("abstract 3d digital art", "liquid metal abstract", "underwater abstract art", "abstract"),
        setOf("abstract", "3d", "digital", "liquid", "metal", "underwater", "experimental", "art"),
    ),
    SPACE_ART(
        "space-art",
        "Space Art",
        "Planetary horizons, deep space and imaginative astronomy",
        listOf("space art planetary landscape", "surreal planet digital art", "deep space abstract art", "space"),
        setOf("space", "planet", "astronomy", "cosmic", "digital", "art", "abstract"),
    );

    companion object {
        fun fromId(id: String?): DiscoveryStyle? = entries.firstOrNull { it.id == id }
        val allIds: Set<String> get() = entries.mapTo(linkedSetOf()) { it.id }
    }
}

/** Stable, deterministic selection for one hourly collection (or an explicit early refresh). */
data class DiscoverySelection(
    val bucket: Long,
    val style: DiscoveryStyle,
    val queryIndex: Int,
    val query: String,
    val refreshOrdinal: Int = 0,
    val pinned: Boolean = false,
    val fallbackSteps: Int = 0,
) {
    val cacheKey: String get() = "$bucket:${style.id}:$queryIndex:$refreshOrdinal"
    val bucketStartMillis: Long get() = bucket * DiscoveryRotation.HOUR_MILLIS

    /** Walk the style's search phrases at most once each before declaring an empty feed. */
    fun fallbackQuery(): DiscoverySelection? {
        if (fallbackSteps >= style.queries.size - 1) return null
        val nextIndex = (queryIndex + 1) % style.queries.size
        return copy(
            queryIndex = nextIndex,
            query = style.queries[nextIndex],
            fallbackSteps = fallbackSteps + 1,
        )
    }
}

object DiscoveryRotation {
    const val HOUR_MILLIS = 60L * 60L * 1000L

    fun bucketAt(epochMillis: Long): Long = Math.floorDiv(epochMillis, HOUR_MILLIS)

    fun select(
        epochMillis: Long,
        preferredStyleIds: Set<String> = emptySet(),
        hiddenStyleIds: Set<String> = emptySet(),
        pinnedStyleId: String? = null,
        refreshOrdinal: Int = 0,
    ): DiscoverySelection {
        val available = DiscoveryStyle.entries.filter { it.id !in hiddenStyleIds }
            .let { visible ->
                val preferred = visible.filter { it.id in preferredStyleIds }
                if (preferred.isNotEmpty()) preferred else visible
            }
            .ifEmpty { DiscoveryStyle.entries }
        val pinned = DiscoveryStyle.fromId(pinnedStyleId)?.takeIf { it in available }
        val bucket = bucketAt(epochMillis)
        val ordinal = refreshOrdinal.coerceAtLeast(0)
        val style = pinned ?: available[Math.floorMod((bucket + ordinal * 5L).toInt(), available.size)]
        val queryIndex = Math.floorMod((bucket * 3L + ordinal).toInt(), style.queries.size)
        return DiscoverySelection(bucket, style, queryIndex, style.queries[queryIndex], ordinal, pinned != null)
    }
}

/** Small, explainable local ranking: relevance and usable detail win; popularity is never used. */
object DiscoveryQuality {
    private val obviousNonWallpaperTags = setOf(
        "map", "maps", "diagram", "chart", "infographic", "screenshot", "contact sheet", "collage",
        "document", "text", "model sheet", "meme", "logo sheet",
    )

    fun isUsable(wallpaper: Wallpaper): Boolean {
        if (wallpaper.source != WallpaperSource.WALLHAVEN || !wallpaper.hasKnownDimensions) return false
        if (wallpaper.width > 32_000 || wallpaper.height > 32_000) return false
        if (wallpaper.width < 720 || wallpaper.height < 720) return false
        if (wallpaper.category.equals("anime", ignoreCase = true)) return false
        val tags = wallpaper.tags.map { it.name.trim().lowercase() }
        if (tags.any { tag -> tag in obviousNonWallpaperTags }) return false
        // Do not blanket-exclude nature photography, animals, people in artwork, or landscapes.
        return true
    }

    fun rank(
        items: List<Wallpaper>,
        selection: DiscoverySelection,
        recentlySeenKeys: Set<String> = emptySet(),
    ): List<Wallpaper> {
        val distinct = items.asSequence().filter(::isUsable).distinctBy { it.key }.toList()
        return distinct.sortedWith(
            compareByDescending<Wallpaper> { score(it, selection.style, recentlySeenKeys) }
                .thenBy { stableTieBreak(it.key, selection.cacheKey) },
        )
    }

    fun score(wallpaper: Wallpaper, style: DiscoveryStyle, recentlySeenKeys: Set<String> = emptySet()): Int {
        val shortEdge = min(wallpaper.width, wallpaper.height)
        val longEdge = max(wallpaper.width, wallpaper.height)
        val ratio = wallpaper.aspectRatio
        val text = (wallpaper.title.orEmpty() + " " + wallpaper.tags.joinToString(" ") { it.name })
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(' ')
            .filter(String::isNotBlank)
            .toSet()
        val relevance = style.relevanceTerms.count(text::contains)
        val resolution = (log10(shortEdge.toDouble().coerceAtLeast(1.0)) * 8).toInt() +
            (log10(longEdge.toDouble().coerceAtLeast(1.0)) * 4).toInt()
        val phoneShape = when {
            ratio in 0.42f..0.82f -> 18
            ratio in 0.83f..1.12f -> 7
            ratio > 1.12f && ratio <= 2.2f -> 2
            else -> -8
        }
        val recencyPenalty = if (wallpaper.key in recentlySeenKeys) 90 else 0
        return relevance * 15 + resolution + phoneShape - recencyPenalty
    }

    private fun stableTieBreak(key: String, selectionKey: String): Int =
        abs(31 * key.hashCode() + selectionKey.hashCode())
}
