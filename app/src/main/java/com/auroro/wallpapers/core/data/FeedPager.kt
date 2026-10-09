package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.Deduplicator
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.LocalFilter
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.toProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed interface SourceState {
    data class Ok(val itemsLoaded: Int) : SourceState
    data object Exhausted : SourceState
    data class Failed(val error: ProviderException) : SourceState
    data class Skipped(val reason: String, val handoffUrl: String? = null, val needsConfiguration: Boolean = false) : SourceState
}

data class SourceStatus(val source: WallpaperSource, val state: SourceState)

data class PageResult(
    val items: List<Wallpaper>,
    val statuses: List<SourceStatus>,
    /** True when no provider can deliver anything more for this request. */
    val endReached: Boolean,
    /** True when at least one provider failed in this load and needs an explicit retry or a later attempt. */
    val hadFailure: Boolean,
)

/**
 * Pages through several providers **independently**.
 *
 * - Every provider keeps its own cursor, so "load more" continues each source where it stopped
 *   (it never merges the first N results of each source and stops).
 * - Because geometry filters are applied locally, a provider page may yield few matches; each load keeps
 *   fetching pages (bounded by [maxPagesPerLoad]) until [perProviderTarget] matching items are collected.
 * - A failing provider doesn't discard other providers' results; its status is reported instead and its
 *   cursor only advances after a successful page, so retry resumes at the failed page.
 * - 401/403/429 and configuration errors halt that provider until [retry]; they are never hammered.
 * - Results from providers are interleaved round-robin. Providers expose incomparable popularity/relevance
 *   scores, so there is no fabricated global ranking: each source's own order is preserved.
 */
class FeedPager(
    private val providers: List<WallpaperProvider>,
    private val request: FeedRequest,
    private val perProviderTarget: Int = 18,
    private val maxPagesPerLoad: Int = 4,
    concurrency: Int = 3,
) {
    private class ProviderState(val provider: WallpaperProvider) {
        var prepared = false
        var cursor: PageCursor? = PageCursor()
        var skipped: SourceState.Skipped? = null
        var halted: ProviderException? = null
        var lastError: ProviderException? = null
        var totalLoaded = 0
        val exhausted get() = cursor == null
        val canLoad get() = skipped == null && halted == null && cursor != null
    }

    private val states = providers.map { ProviderState(it) }
    private val semaphore = Semaphore(concurrency)
    private val dedupe = Deduplicator()
    private val mutex = Mutex()

    val isEmpty get() = providers.isEmpty()

    suspend fun loadNext(): PageResult = mutex.withLock {
        states.forEach { prepare(it) }
        val active = states.filter { it.canLoad }
        val batches: List<List<Wallpaper>> = coroutineScope {
            active.map { st -> async { semaphore.withPermit { loadProvider(st) } } }.awaitAll()
        }
        val merged = dedupe.filterNew(interleave(batches))
        PageResult(
            items = merged,
            statuses = states.map { status(it) },
            endReached = states.none { it.canLoad },
            hadFailure = states.any { it.lastError != null },
        )
    }

    /** Clears errors/halts so the next [loadNext] tries failed providers again. */
    fun retry() {
        states.forEach {
            it.halted = null
            it.lastError = null
            it.prepared = false
        }
    }

    private suspend fun prepare(st: ProviderState) {
        if (st.prepared) return
        st.prepared = true
        st.skipped = null
        when (val a = st.provider.availability()) {
            SourceAvailability.Available -> {
                val reason = st.provider.unsupportedReason(request)
                if (reason != null) st.skipped = SourceState.Skipped(reason)
            }
            is SourceAvailability.NeedsConfiguration -> st.skipped = SourceState.Skipped(
                a.reason,
                handoffFor(st.provider.source, request.normalizedQuery, a.handoffUrl),
                needsConfiguration = true,
            )
            is SourceAvailability.BlockedByPolicy -> st.skipped = SourceState.Skipped(
                a.reason,
                handoffFor(st.provider.source, request.normalizedQuery, a.handoffUrl),
            )
            is SourceAvailability.DisabledByUser -> st.skipped = SourceState.Skipped(a.reason)
        }
    }

    private fun handoffFor(source: WallpaperSource, query: String, fallback: String?): String? = when (source) {
        WallpaperSource.ABYSS -> Handoff.abyssSearchUrl(query)
        WallpaperSource.UNSPLASH -> UnsplashProvider.searchUrl(query)
        WallpaperSource.WALLHAVEN -> fallback
    }

    private suspend fun loadProvider(st: ProviderState): List<Wallpaper> {
        val collected = ArrayList<Wallpaper>()
        var pages = 0
        st.lastError = null
        while (st.cursor != null && collected.size < perProviderTarget && pages < maxPagesPerLoad) {
            val cursor = st.cursor!!
            try {
                val page = st.provider.fetchPage(request, cursor)
                pages++
                collected += LocalFilter.apply(page.items, request.filter, request.aspectTolerance)
                st.cursor = page.next // advanced only after success
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val err = t.toProviderException(st.provider.source.displayName)
                st.lastError = err
                if (!err.isAutoRetryable) st.halted = err
                break
            }
        }
        st.totalLoaded += collected.size
        return collected
    }

    private fun status(st: ProviderState): SourceStatus {
        val state: SourceState = when {
            st.skipped != null -> st.skipped!!
            st.lastError != null -> SourceState.Failed(st.lastError!!)
            st.halted != null -> SourceState.Failed(st.halted!!)
            st.exhausted -> SourceState.Exhausted
            else -> SourceState.Ok(st.totalLoaded)
        }
        return SourceStatus(st.provider.source, state)
    }

    companion object {
        /** Round-robin merge that preserves each list's internal order. */
        fun interleave(lists: List<List<Wallpaper>>): List<Wallpaper> {
            val out = ArrayList<Wallpaper>(lists.sumOf { it.size })
            val max = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until max) for (l in lists) if (i < l.size) out.add(l[i])
            return out
        }
    }
}

fun ProviderErrorKind.isUserFixable(): Boolean = this == ProviderErrorKind.NOT_CONFIGURED || this == ProviderErrorKind.AUTH_FAILED
