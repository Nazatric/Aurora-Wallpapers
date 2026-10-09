package com.auroro.wallpapers.core.model

import java.net.URI
import java.util.Locale

/**
 * De-duplication based on stable provider identifiers and canonical URLs only.
 * Visually similar but distinct wallpapers are deliberately kept.
 */
class Deduplicator {
    private val seen = HashSet<String>()

    /** Returns only the wallpapers not seen before, in order, and remembers them. */
    fun filterNew(items: List<Wallpaper>): List<Wallpaper> {
        val out = ArrayList<Wallpaper>(items.size)
        for (w in items) {
            val ids = identities(w)
            if (ids.any { it in seen }) continue
            seen.addAll(ids)
            out.add(w)
        }
        return out
    }

    fun reset() = seen.clear()

    companion object {
        fun identities(w: Wallpaper): List<String> = buildList {
            add("id:${w.key}")
            canonicalUrl(w.originalUrl)?.let { add("url:$it") }
            canonicalUrl(w.pageUrl)?.let { add("url:$it") }
        }

        /** Lowercases scheme/host, drops query, fragment, trailing slash and a leading `www.`. */
        fun canonicalUrl(url: String): String? = runCatching {
            if (url.isBlank()) return null
            val u = URI(url.trim())
            val host = u.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
            val path = (u.path ?: "").trimEnd('/')
            "$host$path"
        }.getOrNull()
    }
}
