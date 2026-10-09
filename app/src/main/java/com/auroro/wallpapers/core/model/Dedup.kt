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
         * Normalizes the host/path, removes fragments and non-identity query parameters, and trims
         * a trailing slash. Only content selectors used by provider page URLs (`i`/`id`) survive;
         * display sizing, credentials, tracking, and referral query strings don't distinguish files.
         */
        fun canonicalUrl(url: String): String? = runCatching {
            if (url.isBlank()) return null
            val u = URI(url.trim())
            val host = u.host?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
            val path = (u.path ?: "").trimEnd('/')
            val query = u.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }
                .filter { part -> part.substringBefore('=').lowercase(Locale.US) in setOf("i", "id") }
                .sorted()
                .joinToString("&")
                .takeIf { it.isNotEmpty() }
            "$host$path${query?.let { "?$it" }.orEmpty()}"
        }.getOrNull()
    }
}
