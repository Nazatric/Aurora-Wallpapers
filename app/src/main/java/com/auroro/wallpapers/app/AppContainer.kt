package com.auroro.wallpapers.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.Uri
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import com.auroro.wallpapers.core.data.CollectionsRepository
import com.auroro.wallpapers.core.data.DiscoverySnapshotCache
import com.auroro.wallpapers.core.data.DiscoveryEngine
import com.auroro.wallpapers.core.data.SettingsDiscoveryPoolStore
import com.auroro.wallpapers.core.data.FavoritesRepository
import com.auroro.wallpapers.core.data.HistoryRepository
import com.auroro.wallpapers.core.data.LegacyDataCleanup
import com.auroro.wallpapers.core.data.PreferenceSignals
import com.auroro.wallpapers.core.data.SettingsRepository
import com.auroro.wallpapers.core.data.WallpaperAggregator
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.data.ProviderPageCache
import com.auroro.wallpapers.core.data.NasaApodProvider
import com.auroro.wallpapers.core.data.ArtInstituteProvider
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
import com.auroro.wallpapers.core.network.artic.ArtInstituteApi
import com.auroro.wallpapers.core.network.artic.ArtInstituteIiifApi
import com.auroro.wallpapers.core.network.wallhaven.WallhavenApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val artInstituteApi: ArtInstituteApi = retrofit(ArtInstituteApi.BASE_URL).create(ArtInstituteApi::class.java)
    private val artInstituteIiifApi: ArtInstituteIiifApi = retrofit(ArtInstituteApi.IMAGE_BASE_URL).create(ArtInstituteIiifApi::class.java)

    val wallhaven = WallhavenProvider(
        api = wallhavenApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 15,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "wallhaven_api_events",
        ),
        isEnabled = { settings.current().wallhavenEnabled },
    )

    val nasaApod = NasaApodProvider(
        api = nasaApodApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 12,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "nasa_apod_api_events",
        ),
        isEnabled = { settings.current().nasaApodEnabled },
        pageCache = ProviderPageCache(
            source = WallpaperSource.NASA_APOD,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 6L * 60 * 60 * 1_000,
        ),
    )

    val artInstitute = ArtInstituteProvider(
        api = artInstituteApi,
        iiifApi = artInstituteIiifApi,
        limiter = SlidingWindowLimiter(
            maxEvents = 24,
            windowMs = 60_000,
            preferences = bootPrefs,
            storageKey = "art_institute_api_events",
        ),
        isEnabled = { settings.current().artInstituteEnabled },
        pageCache = ProviderPageCache(
            source = WallpaperSource.ART_INSTITUTE,
            preferences = providerPagePrefs,
            wallpapers = wallpaperStore,
            ttlMillis = 24L * 60 * 60 * 1_000,
        ),
    )

    val aggregator = WallpaperAggregator(listOf(wallhaven, nasaApod, artInstitute))
    val discoveryEngine = DiscoveryEngine(wallhaven, discoveryPools)

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
        WallpaperSource.OPENVERSE -> officialMediaDownloadClient // Legacy rows have no allowed network URL.
        WallpaperSource.NASA_APOD, WallpaperSource.ART_INSTITUTE -> officialMediaDownloadClient
        WallpaperSource.REDDIT, WallpaperSource.ARCHIVED -> officialMediaDownloadClient
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

    /** One Coil loader per process. Source-validated public HTTPS image URLs are decoded only as displayed. */
    val imageLoader: ImageLoader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val cacheMb = bootPrefs.getInt(SettingsRepository.BOOT_CACHE_MB, com.auroro.wallpapers.core.data.AppSettings.DEFAULT_CACHE_MB)
            .coerceIn(50, 2_000)
        ImageLoader.Builder(appContext)
            .crossfade(180)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.14)
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
