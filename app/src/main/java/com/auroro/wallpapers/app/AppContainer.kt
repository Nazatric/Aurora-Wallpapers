package com.auroro.wallpapers.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.Uri
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.auroro.wallpapers.core.data.CollectionsRepository
import com.auroro.wallpapers.core.data.DiscoverySnapshotCache
import com.auroro.wallpapers.core.data.DiscoveryEngine
import com.auroro.wallpapers.core.data.MultiSourceDiscoveryEngine
import com.auroro.wallpapers.core.data.SettingsDiscoveryPoolStore
import com.auroro.wallpapers.core.data.WallpaperProvider
import com.auroro.wallpapers.core.data.FavoritesRepository
import com.auroro.wallpapers.core.data.HistoryRepository
import com.auroro.wallpapers.core.data.LegacyDataCleanup
import com.auroro.wallpapers.core.data.PreferenceSignals
import com.auroro.wallpapers.core.data.SettingsRepository
import com.auroro.wallpapers.core.data.WallpaperAggregator
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.data.ProviderPageCache
import com.auroro.wallpapers.core.data.NasaApodProvider
import com.auroro.wallpapers.core.data.WallhavenProvider
import com.auroro.wallpapers.core.data.download.AppStorageSaver
import com.auroro.wallpapers.core.data.download.DownloadExecutor
import com.auroro.wallpapers.core.data.download.DownloadNotifier
import com.auroro.wallpapers.core.data.download.DownloadRepository
import com.auroro.wallpapers.core.data.download.GalleryMediaSaver
import com.auroro.wallpapers.core.data.download.MediaSaver
import com.auroro.wallpapers.core.data.download.WallpaperApplier
import com.auroro.wallpapers.core.data.download.WallpaperApplyRepository
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.HttpClients
import com.auroro.wallpapers.core.network.SlidingWindowLimiter
import com.auroro.wallpapers.core.network.nasa.NasaApodApi
import com.auroro.wallpapers.core.network.wallhaven.WallhavenApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.auroro.wallpapers.core.data.ArtInstituteProvider
import com.auroro.wallpapers.core.data.ClevelandProvider
import com.auroro.wallpapers.core.data.MetMuseumProvider
import com.auroro.wallpapers.core.data.OpenverseProvider
import com.auroro.wallpapers.core.data.WikimediaProvider
import com.auroro.wallpapers.core.network.artic.ArtInstituteApi
import com.auroro.wallpapers.core.network.cleveland.ClevelandApi
import com.auroro.wallpapers.core.network.met.MetApi
import com.auroro.wallpapers.core.network.openverse.OpenverseApi
import com.auroro.wallpapers.core.network.wikimedia.WikimediaApi
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

/** Application-scoped dependencies. Provider endpoints are public; no API credentials are bundled or requested. */
class AppContainer(context: Context) {
    val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Retired credentials are unrelated to first-frame UI; remove them off the main thread.
        applicationScope.launch { LegacyDataCleanup.run(appContext) }
    }
    private val bootPrefs = appContext.getSharedPreferences("auroro_boot", Context.MODE_PRIVATE)
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = { appContext.preferencesDataStoreFile("auroro_preferences") },
    )
    val settings = SettingsRepository(dataStore, bootPrefs)

    val database: AppDatabase = AppDatabase.build(appContext)
    val wallpaperStore = WallpaperStore(database.wallpapers())
    val discoveryCache = DiscoverySnapshotCache(settings, wallpaperStore)
    val discoveryPools = SettingsDiscoveryPoolStore(settings, wallpaperStore)
    val favorites = FavoritesRepository(database, wallpaperStore)
    val collections = CollectionsRepository(database, wallpaperStore)
    val history = HistoryRepository(database, wallpaperStore)
    val preferenceSignals = PreferenceSignals(database)

    private val apiCache = Cache(File(appContext.cacheDir, "auroro-api-http-cache"), 10L * 1024 * 1024)
    val httpClient = HttpClients.base().newBuilder().cache(apiCache).build()
    private val imageHttpClient = HttpClients.images(HttpClients.base())
    private val wallhavenDownloadClient = HttpClients.downloads(HttpClients.base())
    private val officialMediaDownloadClient = HttpClients.downloads(HttpClients.base())
    private val providerPagePrefs = appContext.getSharedPreferences("auroro_provider_pages", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val converterFactory = json.asConverterFactory("application/json".toMediaType())

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(converterFactory)
        .build()

    private val wallhavenApi: WallhavenApi = retrofit(WallhavenApi.BASE_URL).create(WallhavenApi::class.java)
    private val nasaApodApi: NasaApodApi = retrofit(NasaApodApi.BASE_URL).create(NasaApodApi::class.java)
    private val openverseApi: OpenverseApi = retrofit(OpenverseApi.BASE_URL).create(OpenverseApi::class.java)
    private val wikimediaApi: WikimediaApi = retrofit(WikimediaApi.BASE_URL).create(WikimediaApi::class.java)
    private val metApi: MetApi = retrofit(MetApi.BASE_URL).create(MetApi::class.java)
    private val clevelandApi: ClevelandApi = retrofit(ClevelandApi.BASE_URL).create(ClevelandApi::class.java)
    private val artInstituteApi: ArtInstituteApi =
        retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java)

    val wallhaven = WallhavenProvider(
        api = wallhavenApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 15,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "wallhaven_api_events",
        ),
        isEnabled = { WallpaperSource.WALLHAVEN.id in settings.current().enabledSources },
    )

    val nasaApod = NasaApodProvider(
        api = nasaApodApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 12,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "nasa_apod_api_events",
        ),
        isEnabled = { WallpaperSource.NASA_APOD.id in settings.current().enabledSources },
        pageCache = ProviderPageCache(
            source = WallpaperSource.NASA_APOD,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 6L * 60 * 60 * 1_000,
        ),
    )

    val openverse = OpenverseProvider(
        api = openverseApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 6,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "openverse_api_events",
        ),
        isEnabled = { WallpaperSource.OPENVERSE.id in settings.current().enabledSources },
        pageCache = ProviderPageCache(
            source = WallpaperSource.OPENVERSE,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 6L * 60 * 60 * 1_000,
        ),
    )

    val wikimedia = WikimediaProvider(
        api = wikimediaApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 20,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "wikimedia_api_events",
        ),
        isEnabled = { WallpaperSource.WIKIMEDIA.id in settings.current().enabledSources },
        pageCache = ProviderPageCache(
            source = WallpaperSource.WIKIMEDIA,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 3L * 60 * 60 * 1_000,
        ),
    )

    val metMuseum = MetMuseumProvider(
        api = metApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 24,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "met_api_events",
        ),
        isEnabled = { WallpaperSource.MET_MUSEUM.id in settings.current().enabledSources },
        pageCache = ProviderPageCache(
            source = WallpaperSource.MET_MUSEUM,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 6L * 60 * 60 * 1_000,
        ),
    )

    val clevelandArt = ClevelandProvider(
        api = clevelandApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 20,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "cleveland_api_events",
        ),
        isEnabled = { WallpaperSource.CLEVELAND_ART.id in settings.current().enabledSources },
        pageCache = ProviderPageCache(
            source = WallpaperSource.CLEVELAND_ART,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 6L * 60 * 60 * 1_000,
        ),
    )

    val artInstitute = ArtInstituteProvider(
        api = artInstituteApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 20,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "artic_api_events",
        ),
        isEnabled = { WallpaperSource.ART_INSTITUTE.id in settings.current().enabledSources },
        pageCache = ProviderPageCache(
            source = WallpaperSource.ART_INSTITUTE,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 6L * 60 * 60 * 1_000,
        ),
    )

    /** Every integrated live catalogue, in feed priority order. */
    val feedProviders: List<WallpaperProvider> =
        listOf(wallhaven, nasaApod, openverse, artInstitute, wikimedia, metMuseum, clevelandArt)

    val aggregator = WallpaperAggregator(feedProviders)
    private val wallhavenDiscovery = DiscoveryEngine(wallhaven, discoveryPools)
    val discoveryEngine = MultiSourceDiscoveryEngine(
        searchEngines = listOf(wallhavenDiscovery),
        // NASA APOD is the engine's only non-keyword archive lane by contract (it validates
        // this at construction). The 1.8.0 catalogues serve Trending/Newest/Random/Search and
        // cover-flow through `feedProviders`; widening discovery lanes is a separate change.
        archiveProviders = listOf(nasaApod),
    )

    val notifier = DownloadNotifier(appContext, settings)
    val wallpaperApplier = WallpaperApplier(appContext)
    val wallpaperApplyRepository = WallpaperApplyRepository(appContext, ::downloadClient)

    private val tempDir = File(appContext.cacheDir, "wallpaper-downloads")
    private val appWallpaperDir = File(appContext.filesDir, "wallpapers")

    private fun mediaSaver(location: com.auroro.wallpapers.core.data.SaveLocation): MediaSaver = when (location) {
        com.auroro.wallpapers.core.data.SaveLocation.GALLERY -> GalleryMediaSaver(appContext)
        com.auroro.wallpapers.core.data.SaveLocation.APP_STORAGE -> AppStorageSaver(appWallpaperDir)
    }

    private fun downloadClient(source: WallpaperSource) = when (source) {
        WallpaperSource.WALLHAVEN -> wallhavenDownloadClient
        // Official open-access media: TLS-authenticated catalogue URLs only; no scraper hosts.
        WallpaperSource.OPENVERSE,
        WallpaperSource.NASA_APOD,
        WallpaperSource.ART_INSTITUTE,
        WallpaperSource.WIKIMEDIA,
        WallpaperSource.MET_MUSEUM,
        WallpaperSource.CLEVELAND_ART,
        WallpaperSource.REDDIT,
        WallpaperSource.ARCHIVED,
        -> officialMediaDownloadClient
    }

    val downloadExecutor = DownloadExecutor(
        tempDir = tempDir,
        db = database,
        store = wallpaperStore,
        settings = settings,
        clientForSource = ::downloadClient,
        saver = ::mediaSaver,
        notifier = notifier,
    )

    val downloads = DownloadRepository(
        context = appContext,
        db = database,
        store = wallpaperStore,
        workManagerProvider = { WorkManager.getInstance(appContext) },
    )

    /** Evicts the disposable HTTP response cache and the wallpaper-apply preparation cache. */
    /** One Coil loader per process. Source-validated public HTTPS image URLs are decoded only as displayed. */
    val imageLoader: ImageLoader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val cacheMb = bootPrefs.getInt(SettingsRepository.BOOT_CACHE_MB, com.auroro.wallpapers.core.data.AppSettings.DEFAULT_CACHE_MB)
            .coerceIn(50, 2_000)
        ImageLoader.Builder(appContext)
            .crossfade(160)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.2)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "auroro-image-cache").toOkioPath())
                    .maxSizeBytes(cacheMb.toLong() * 1024 * 1024)
                    .build()
            }
            .components(
                ComponentRegistry.Builder()
                    .apply { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }), Uri::class) }
                    .build(),
            )
            .build()
    }

    suspend fun clearImageCache() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        runCatching { httpClient.cache?.evictAll() }
        wallpaperApplyRepository.clearCache()
    }
}
