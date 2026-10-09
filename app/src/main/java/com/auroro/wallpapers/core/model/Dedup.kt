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

        /**
         * Lowercases scheme/host, drops the fragment and known tracking/credential query parameters,
         * preserves source identifiers such as Wallpaper Abyss's `?i=123`, sorts remaining parameters,
         * trims a trailing slash and a leading `www.`. Dropping *all* queries would incorrectly merge
         * every `big.php?i=...` wallpaper from Alpha Coders.
         */
        fun canonicalUrl(url: String): String? = runCatching {
            if (url.isBlank()) return null
            val u = URI(url.trim())
            val host = u.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
            val path = (u.path ?: "").trimEnd('/')
            val ignored = setOf("utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid", "apikey", "auth", "token", "access_token", "ref")
            val query = u.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }
                .filter { part -> part.substringBefore('=').lowercase(Locale.US) !in ignored }
                .sorted()
                .joinToString("&")
                .takeIf { it.isNotEmpty() }
            "$host$path${query?.let { "?$it" }.orEmpty()}"
        }.getOrNull()
    }
}
