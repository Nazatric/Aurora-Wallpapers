package com.auroro.wallpapers.core.data

import com.auroro.wallpapers.core.model.DiscoveryCatalog
import com.auroro.wallpapers.core.model.DiscoverySession
import com.auroro.wallpapers.core.model.FeedRequest
import com.auroro.wallpapers.core.model.SortOption
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperFilter
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.ProviderErrorKind
import com.auroro.wallpapers.core.network.ProviderException
import com.auroro.wallpapers.core.network.toProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Combined progress for source-specific searches and a non-keyword archive lane. */
data class MultiSourceDiscoveryProgress(
    val sessionId: String,
    val generation: Long,
    val sections: List<ValidatedDiscoverySection>,
    val items: List<Wallpaper>,
    val loading: Boolean,
)

data class ArchiveDiscoveryResult(
    val source: WallpaperSource,
    val status: SourceStatus,
    val items: List<Wallpaper>,
    val providerItemsReceived: Int = 0,
    val error: ProviderException? = null,
)

data class MultiSourceDiscoveryResult(
    val sessionId: String,
    val generation: Long,
    val searchResults: List<DiscoveryBuildResult>,
    val archiveResults: List<ArchiveDiscoveryResult>,
    val sections: List<ValidatedDiscoverySection>,
    val items: List<Wallpaper>,
    val statuses: List<SourceStatus>,
) {
    val errors: List<ProviderException>
        get() = statuses.mapNotNull { (it.state as? SourceState.Failed)?.error }
    val staleCacheUsed: Boolean get() = searchResults.any { it.staleCacheUsed }
    val requestBudgetReached: Boolean get() = searchResults.any { it.requestBudgetReached }

    /** Reports partial provider failures without making a working catalogue look broken. */
    fun message(): String? {
        val failures = statuses.mapNotNull { status ->
            (status.state as? SourceState.Failed)?.let { status.source.displayName to it.error }
        }
        if (failures.isNotEmpty()) {
            val details = failures.joinToString(" · ") { (source, error) ->
                "$source: ${error.message ?: error.kind.title}"
            }
            return if (items.isEmpty()) details
            else "Some catalogues could not finish ($details); showing ${items.size} available images."
        }
        if (requestBudgetReached && items.isNotEmpty()) {
            return "Showing ${items.size} images; at least one source reached its bounded discovery-request budget."
        }
        if (items.isEmpty() && statuses.isNotEmpty() && statuses.all { it.state is SourceState.Skipped }) {
            return "Turn on at least one catalogue in Settings → Wallpaper sources."
        }
        if (items.isEmpty()) {
            val rawRows = searchResults.sumOf { it.providerItemsReceived } + archiveResults.sumOf { it.providerItemsReceived }
            val sizedRows = searchResults.sumOf { it.sourceUsableItems }
            return when {
                searchResults.any { it.skippedCoolingDown > 0 } ->
                    "Discovery searches are cooling down after empty or failed requests. Try again later."
                rawRows > 0 && sizedRows == 0 ->
                    "No searchable result had source-reported dimensions meeting the 720 px minimum short edge. Unknown APOD dimensions remain unknown."
                rawRows > 0 -> "No source query returned a usable result for this mix. Try again later."
                else -> "No results were returned by the enabled catalogues for this discovery mix. Try again later."
            }
        }
        if (staleCacheUsed) return "Showing previously validated results while an enabled catalogue refreshes."
        return null
    }
}

/**
 * Runs one query engine per searchable catalogue plus explicit archive lanes. A source failure is
 * isolated; Wallhaven and NASA never inherit one another's query syntax, rights or dimensions.
 */
class MultiSourceDiscoveryEngine(
    private val searchEngines: List<DiscoveryEngine>,
    private val archiveProviders: List<WallpaperProvider>,
    private val archiveTimeoutMillis: Long = 12_000L,
    private val archiveItemLimit: Int = 6,
) {
    private val sourceOrder = (searchEngines.map { it.source } + archiveProviders.map { it.source }).distinct()

    init {
        require(archiveTimeoutMillis > 0)
        require(archiveItemLimit > 0)
        require(sourceOrder.size == searchEngines.size + archiveProviders.size) {
            "A source can have only one discovery lane in a session."
        }
        require(searchEngines.all { it.source == WallpaperSource.WALLHAVEN }) {
            "Wallhaven is the only searchable catalogue since the Art Institute lane was retired."
        }
        require(archiveProviders.all { it.source == WallpaperSource.NASA_APOD }) {
            "Only the dated NASA APOD archive is currently configured as a non-keyword lane."
        }
    }

    suspend fun discover(
        session: DiscoverySession,
        generation: Long,
        preferences: AppSettings,
        recentlySeenKeys: Set<String> = emptySet(),
        onProgress: suspend (MultiSourceDiscoveryProgress) -> Unit = {},
    ): MultiSourceDiscoveryResult {
        val mutex = Mutex()
        val partialSections = mutableMapOf<WallpaperSource, List<ValidatedDiscoverySection>>()
        val partialItems = mutableMapOf<WallpaperSource, List<Wallpaper>>()

        suspend fun updatePreview(
            source: WallpaperSource,
            sections: List<ValidatedDiscoverySection>,
            items: List<Wallpaper>,
        ) {
            val progress = mutex.withLock {
                partialSections[source] = sections
                partialItems[source] = items
                progressSnapshot(session, generation, partialSections, partialItems, loading = true)
            }
            onProgress(progress)
        }

        onProgress(progressSnapshot(session, generation, emptyMap(), emptyMap(), loading = true))
        val (searchResults, archiveResults) = coroutineScope {
            val searches = searchEngines.map { engine ->
                async {
                    val eligible = session.candidateRecipeIds.filter { id ->
                        DiscoveryCatalog.find(id)?.sourceQueries?.containsKey(engine.source) == true
                    }
                    val sourceSession = session.copy(candidateRecipeIds = eligible)
                    val result = try {
                        engine.discover(sourceSession, generation, preferences, recentlySeenKeys) { progress ->
                            updatePreview(engine.source, progress.sections, progress.items)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        val error = failure.toProviderException(engine.source.displayName)
                        DiscoveryBuildResult(
                            sessionId = session.sessionId,
                            generation = generation,
                            sections = emptyList(),
                            items = emptyList(),
                            attemptedRecipes = 0,
                            emptyRecipes = 0,
                            skippedCoolingDown = 0,
                            errors = listOf(error),
                            source = engine.source,
                        )
                    }
                    updatePreview(engine.source, result.sections, result.items)
                    result
                }
            }
            val archives = archiveProviders.map { provider ->
                async {
                    val result = discoverArchive(provider)
                    updatePreview(provider.source, emptyList(), result.items)
                    result
                }
            }
            searches.map { it.await() } to archives.map { it.await() }
        }

        val orderedSearchResults = searchEngines.mapNotNull { engine -> searchResults.firstOrNull { it.source == engine.source } }
        val orderedArchiveResults = archiveProviders.mapNotNull { provider -> archiveResults.firstOrNull { it.source == provider.source } }
        val sections = orderedSearchResults.flatMap { it.sections }
        val itemsBySource = buildMap {
            orderedSearchResults.forEach { put(it.source, it.items) }
            orderedArchiveResults.forEach { put(it.source, it.items) }
        }
        val items = interleaveSources(itemsBySource).distinctBy { it.key }
        val statuses = orderedSearchResults.map { it.sourceStatus } + orderedArchiveResults.map { it.status }
        val result = MultiSourceDiscoveryResult(
            sessionId = session.sessionId,
            generation = generation,
            searchResults = orderedSearchResults,
            archiveResults = orderedArchiveResults,
            sections = sections,
            items = items,
            statuses = statuses,
        )
        onProgress(
            MultiSourceDiscoveryProgress(
                sessionId = result.sessionId,
                generation = result.generation,
                sections = result.sections,
                items = result.items,
                loading = false,
            ),
        )
        return result
    }

    private suspend fun discoverArchive(provider: WallpaperProvider): ArchiveDiscoveryResult {
        val source = provider.source
        return try {
            when (val availability = withTimeout(archiveTimeoutMillis) { provider.availability() }) {
                is SourceAvailability.DisabledByUser -> {
                    ArchiveDiscoveryResult(
                        source = source,
                        status = SourceStatus(source, SourceState.Skipped(availability.reason)),
                        items = emptyList(),
                    )
                }
                SourceAvailability.Available -> {
                    val sort = if (SortOption.NEWEST in provider.capabilities.sortsWithoutQuery) {
                        SortOption.NEWEST
                    } else SortOption.SOURCE_DEFAULT
                    val request = FeedRequest(filter = WallpaperFilter(sources = setOf(source), sort = sort))
                    provider.unsupportedReason(request)?.let { reason ->
                        return ArchiveDiscoveryResult(source, SourceStatus(source, SourceState.Skipped(reason)), emptyList())
                    }
                    val page = withTimeout(archiveTimeoutMillis) { provider.fetchPage(request, com.auroro.wallpapers.core.data.PageCursor()) }
                    // NASA's APOD Basic API does not report pixel dimensions. Keep these dated,
                    // view-only archive items browseable, but do not pretend they passed the
                    // source-measured 720 px discovery floor or allow set/download actions.
                    val items = page.items.asSequence()
                        .filter { it.source == source && it.sourceId.isNotBlank() && it.thumbUrl.isNotBlank() }
                        .distinctBy { it.key }
                        .take(archiveItemLimit)
                        .toList()
                    ArchiveDiscoveryResult(
                        source = source,
                        status = SourceStatus(source, if (items.isNotEmpty()) SourceState.Ok(items.size) else SourceState.Exhausted),
                        items = items,
                        providerItemsReceived = page.items.size,
                    )
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            val error = ProviderException(
                ProviderErrorKind.TIMEOUT,
                "${source.displayName} took too long to respond.",
                cause = timeout,
            )
            ArchiveDiscoveryResult(source, SourceStatus(source, SourceState.Failed(error)), emptyList(), error = error)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            val error = failure.toProviderException(source.displayName)
            ArchiveDiscoveryResult(
                source = source,
                status = SourceStatus(source, SourceState.Failed(error)),
                items = emptyList(),
                error = error,
            )
        }
    }

    private fun progressSnapshot(
        session: DiscoverySession,
        generation: Long,
        sectionsBySource: Map<WallpaperSource, List<ValidatedDiscoverySection>>,
        itemsBySource: Map<WallpaperSource, List<Wallpaper>>,
        loading: Boolean,
    ) = MultiSourceDiscoveryProgress(
        sessionId = session.sessionId,
        generation = generation,
        sections = sourceOrder.flatMap { sectionsBySource[it].orEmpty() },
        items = interleaveSources(itemsBySource),
        loading = loading,
    )

    private fun interleaveSources(itemsBySource: Map<WallpaperSource, List<Wallpaper>>): List<Wallpaper> {
        val lists = sourceOrder.mapNotNull { source -> itemsBySource[source]?.takeIf { it.isNotEmpty() } }
        val output = ArrayList<Wallpaper>(lists.sumOf { it.size })
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (index in 0 until longest) {
            lists.forEach { items -> items.getOrNull(index)?.let(output::add) }
        }
        return output
    }
}
