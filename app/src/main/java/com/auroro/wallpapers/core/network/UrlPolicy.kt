package com.auroro.wallpapers.core.network

import com.auroro.wallpapers.core.model.WallpaperSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** URL checks for fixed API hosts, provider-published image files, and safe external attribution links. */
object UrlPolicy {
    /** API hosts only. Openverse's original media is hosted on many third-party domains. */
    val networkDomains: Set<String> = setOf("wallhaven.cc", "api.openverse.org")
    val browsableDomains: Set<String> = networkDomains + setOf("openverse.org")

    /** Restrictive check used for API clients. */
    fun isAllowedForNetwork(url: HttpUrl): Boolean =
        url.isHttps && url.port == 443 && hasNoCredentials(url) && isHostIn(url.host, networkDomains)

    fun isAllowedForNetwork(url: String?): Boolean = parse(url)?.let(::isAllowedForNetwork) ?: false

    /** Source-aware check before downloading a provider's original file. */
    fun isAllowedForNetwork(source: WallpaperSource, url: String?): Boolean {
        val parsed = parse(url) ?: return false
        return when (source) {
            WallpaperSource.WALLHAVEN -> isAllowedForNetwork(parsed)
            WallpaperSource.OPENVERSE -> isPublicHttpsUrl(parsed)
            WallpaperSource.ARCHIVED -> false
        }
    }

    /** Openverse image URLs are served by many external hosts; only public HTTPS destinations are accepted. */
    fun isAllowedOpenverseAsset(url: String?): Boolean = parse(url)?.let(::isPublicHttpsUrl) ?: false

    /** Attribution pages are opened only as public HTTPS links. API URLs remain separately allow-listed. */
    fun isAllowedForBrowsing(url: String?): Boolean = parse(url)?.let(::isPublicHttpsUrl) ?: false

    private fun parse(url: String?): HttpUrl? = url?.trim()?.takeIf(String::isNotEmpty)?.toHttpUrlOrNull()

    private fun hasNoCredentials(url: HttpUrl) = url.username.isEmpty() && url.password.isEmpty()

    private fun isHostIn(host: String, domains: Set<String>): Boolean {
        val normalized = host.lowercase(Locale.US)
        return domains.any { normalized == it || normalized.endsWith(".$it") }
    }

    private fun isPublicHttpsUrl(url: HttpUrl): Boolean {
        if (!url.isHttps || url.port != 443 || !hasNoCredentials(url)) return false
        val host = url.host.lowercase(Locale.US).trimEnd('.')
        if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test") ||
            host.endsWith(".invalid") || host.endsWith(".arpa") || host.contains(':')
        ) return false

        val labels = host.split('.')
        if (labels.size < 2 || labels.any { label ->
                label.isEmpty() || label.length > 63 || label.startsWith('-') || label.endsWith('-')
            }
        ) return false
        // Literal IPv4 destinations are rejected; provider URLs should use their normal HTTPS hostnames.
        if (labels.size == 4 && labels.all { label -> label.toIntOrNull()?.let { it in 0..255 } == true }) return false
        if (host.all { it.isDigit() || it == '.' }) return false
        return true
    }
}
