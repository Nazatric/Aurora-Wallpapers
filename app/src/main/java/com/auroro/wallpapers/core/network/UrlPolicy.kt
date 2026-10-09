package com.auroro.wallpapers.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Central allow-list. Provider metadata is untrusted input: before any image/API request leaves the
 * device, or any link is opened, the destination must be HTTPS and belong to a known provider domain.
 */
object UrlPolicy {
    /** Domains we may fetch API data and images from (exact host or any subdomain). */
    val networkDomains: Set<String> = setOf("wallhaven.cc", "alphacoders.com")

    /** Domains we may *open in the user's browser* (includes handoff-only sources). */
    val browsableDomains: Set<String> = networkDomains + setOf("unsplash.com")

    fun isAllowedForNetwork(url: HttpUrl): Boolean = isAllowed(url, networkDomains)

    fun isAllowedForNetwork(url: String?): Boolean = parse(url)?.let(::isAllowedForNetwork) ?: false

    fun isAllowedForBrowsing(url: String?): Boolean = parse(url)?.let { isAllowed(it, browsableDomains) } ?: false

    private fun parse(url: String?): HttpUrl? = url?.trim()?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()

    private fun isAllowed(url: HttpUrl, domains: Set<String>): Boolean {
        if (!url.isHttps) return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        if (url.port != 443) return false
        val host = url.host.lowercase(Locale.US)
        return domains.any { host == it || host.endsWith(".$it") }
    }
}
