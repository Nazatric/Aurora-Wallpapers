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
import com.auroro.wallpapers.core.data.AbyssProvider
import com.auroro.wallpapers.core.data.CollectionsRepository
import com.auroro.wallpapers.core.data.FavoritesRepository
import com.auroro.wallpapers.core.data.HistoryRepository
import com.auroro.wallpapers.core.data.PreferenceSignals
import com.auroro.wallpapers.core.data.SettingsRepository
import com.auroro.wallpapers.core.data.SecureSecretStore
import com.auroro.wallpapers.core.data.WallpaperAggregator
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.data.WallhavenProvider
import com.auroro.wallpapers.core.data.UnsplashProvider
import com.auroro.wallpapers.core.data.download.AppStorageSaver
import com.auroro.wallpapers.core.data.download.DownloadExecutor
import com.auroro.wallpapers.core.data.download.DownloadNotifier
import com.auroro.wallpapers.core.data.download.DownloadRepository
import com.auroro.wallpapers.core.data.download.GalleryMediaSaver
import com.auroro.wallpapers.core.data.download.MediaSaver
import com.auroro.wallpapers.core.data.download.WallpaperApplier
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.network.HttpClients
import com.auroro.wallpapers.core.network.abyss.AbyssApi
import com.auroro.wallpapers.core.network.wallhaven.WallhavenApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import java.io.File

/** Application-scoped dependencies. No credentials are embedded in the APK. */
class AppContainer(context: Context) {
    val appContext = context.applicationContext

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
        produceFile = { appContext.preferencesDataStoreFile("auroro_preferences") },
    )

    private val bootPrefs = appContext.getSharedPreferences("auroro_boot", Context.MODE_PRIVATE)
    private val secretStore = SecureSecretStore(appContext)
    val settings = SettingsRepository(dataStore, bootPrefs, secretStore)

    val database: AppDatabase = AppDatabase.build(appContext)
    val wallpaperStore = WallpaperStore(database.wallpapers())
    val favorites = FavoritesRepository(database, wallpaperStore)
    val collections = CollectionsRepository(database, wallpaperStore)
    val history = HistoryRepository(database, wallpaperStore)
    val preferenceSignals = PreferenceSignals(database)

    val httpClient = HttpClients.base()
    private val imageHttpClient = HttpClients.downloads(httpClient)

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
    private val abyssApi: AbyssApi = retrofit(AbyssApi.BASE_URL).create(AbyssApi::class.java)

    val wallhaven = WallhavenProvider(
        api = wallhavenApi,
        isEnabled = { settings.current().wallhavenEnabled },
    )

    val abyss = AbyssProvider(
        api = abyssApi,
        apiKey = { settings.current().abyssApiKey },
        isEnabled = { settings.current().abyssEnabled },
    )

    val unsplash = UnsplashProvider()
    val aggregator = WallpaperAggregator(listOf(wallhaven, abyss, unsplash))

    val notifier = DownloadNotifier(appContext, settings)
    val wallpaperApplier = WallpaperApplier(appContext)
    private val workManager = WorkManager.getInstance(appContext)

    private val tempDir = File(appContext.cacheDir, "wallpaper-downloads")
    private val appWallpaperDir = File(appContext.filesDir, "wallpapers")

    private fun mediaSaver(location: com.auroro.wallpapers.core.data.SaveLocation): MediaSaver = when (location) {
        com.auroro.wallpapers.core.data.SaveLocation.GALLERY -> GalleryMediaSaver(appContext)
        com.auroro.wallpapers.core.data.SaveLocation.APP_STORAGE -> AppStorageSaver(appWallpaperDir)
    }

    val downloadExecutor = DownloadExecutor(
        tempDir = tempDir,
        db = database,
        store = wallpaperStore,
        settings = settings,
        client = imageHttpClient,
        saver = ::mediaSaver,
        screenLongEdge = {
            val dm = appContext.resources.displayMetrics
            maxOf(dm.widthPixels, dm.heightPixels)
        },
        applier = wallpaperApplier,
        notifier = notifier,
    )

    val downloads = DownloadRepository(appContext, database, wallpaperStore, workManager)

    /** One Coil loader per process. Network requests use the same HTTPS/domain allow-list as the API. */
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

    fun clearImageCache() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }
}

