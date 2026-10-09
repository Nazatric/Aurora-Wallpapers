package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException

/**
 * Unsplash is intentionally NOT integrated.
 *
 * Unsplash's API Guidelines state: "You cannot replicate the core user experience of Unsplash
 * (unofficial clients, wallpaper applications, etc.)" and list "a wallpaper app returns Unsplash images
 * for downloading" as a disallowed use (https://help.unsplash.com/en/articles/2511257-guideline-replicating-unsplash).
 * Their guidelines also require the Access Key to stay confidential (a proxy) which this
 * serverless app does not have. See BLOCKERS.md.
 *
 * This class keeps the provider slot ready: if Unsplash ever grants written approval, implement
 * [fetchPage] against the approved integration (hotlinked `photo.urls`, attribution with UTM referral
 * links, `download_location` tracking) and switch [availability]. Until then it never makes a network call;
 * the UI offers an explicit "open Unsplash in your browser" handoff instead.
 */
class UnsplashProvider : WallpaperProvider {
    override val source = WallpaperSource.UNSPLASH

    override val capabilities = ProviderCapabilities(
        sortsWithoutQuery = emptySet<SortOption>(),
        sortsWithQuery = emptySet<SortOption>(),
    )

    override suspend fun availability(): SourceAvailability = SourceAvailability.BlockedByPolicy(
        reason = "Unsplash's API terms don't permit wallpaper apps, so Auroro doesn't call the Unsplash API. " +
            "You can browse Unsplash in your browser instead.",
        handoffUrl = HANDOFF_URL,
    )

    override suspend fun fetchPage(request: FeedRequest, cursor: PageCursor): ProviderPage =
        throw ProviderException(ProviderErrorKind.NOT_CONFIGURED, "Unsplash API access isn't authorized for this app.")

    companion object {
        /** Official site with the referral parameters Unsplash asks for. */
        const val HANDOFF_URL = "https://unsplash.com/?utm_source=auroro_wallpapers&utm_medium=referral"

        fun searchUrl(query: String): String {
            val q = java.net.URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
            return if (q.isEmpty()) HANDOFF_URL else "https://unsplash.com/s/photos/$q?utm_source=auroro_wallpapers&utm_medium=referral"
        }
    }
}

/** Official-site handoff URLs for sources that can't be queried in-app. */
object Handoff {
    fun abyssSearchUrl(query: String): String {
        val q = query.trim()
        return if (q.isEmpty()) {
            "https://wall.alphacoders.com/"
        } else {
            "https://wall.alphacoders.com/search.php?search=" + java.net.URLEncoder.encode(q, "UTF-8")
        }
    }
}
