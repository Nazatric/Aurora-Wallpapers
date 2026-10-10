package com.auroro.wallpapers.core.data.download

import android.content.Context
import android.net.Uri
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.model.WallpaperSource
import com.auroro.wallpapers.core.network.UrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Full-resolution image in app cache for the Set Wallpaper flow; never a Download/Room record. */
data class ApplyOriginal(val uri: String, val width: Int, val height: Int, val bytes: Long, val fromCache: Boolean)

/**
 * Reuses an existing apply-cache file or streams the provider's original to a private, disposable
 * cache file. It deliberately has no DownloadRepository, WorkManager, MediaStore or database
 * dependency: applying an image cannot create a persistent Downloads entry.
 */
class WallpaperApplyRepository(
    context: Context,
    private val clientForSource: (WallpaperSource) -> OkHttpClient,
    private val urlAllowed: (WallpaperSource, String) -> Boolean = UrlPolicy::isAllowedForNetwork,
) {
    private val directory = File(context.applicationContext.cacheDir, "wallpaper-set-originals")
    private val mutex = Mutex()

    suspend fun clearCache() = mutex.withLock {
        withContext(Dispatchers.IO) { directory.deleteRecursively() }
    }

    suspend fun prepare(wallpaper: Wallpaper): ApplyOriginal = mutex.withLock {
        require(wallpaper.setWallpaperAllowed) { "This image's licence doesn't permit setting it as a wallpaper." }
        require(wallpaper.downloadAllowed) { "This original can't be fetched for wallpaper setting." }
        require(urlAllowed(wallpaper.source, wallpaper.originalUrl)) { "This original URL isn't an approved secure image address." }

        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val destination = File(directory, safeFileName(wallpaper.key) + ".img")
            ImageValidator.inspect(destination)?.let { info ->
                destination.setLastModified(System.currentTimeMillis())
                return@withContext ApplyOriginal(Uri.fromFile(destination).toString(), info.width, info.height, destination.length(), fromCache = true)
            }

            val part = File(directory, destination.name + ".part")
            try {
                part.delete()
                fetch(wallpaper, part)
                val info = ImageValidator.inspect(part)
                    ?: throw IOException("The source didn't return a decodable JPEG, PNG or WebP original.")
                if (!part.renameTo(destination)) {
                    part.copyTo(destination, overwrite = true)
                    part.delete()
                }
                prune(destination)
                ApplyOriginal(Uri.fromFile(destination).toString(), info.width, info.height, destination.length(), fromCache = false)
            } finally {
                part.delete()
            }
        }
    }

    private suspend fun fetch(wallpaper: Wallpaper, destination: File) = coroutineScope {
        val call = clientForSource(wallpaper.source).newCall(
            Request.Builder()
                .url(wallpaper.originalUrl)
                .header("Accept", "image/jpeg,image/png,image/webp")
                .build(),
        )
        val watcher = launch(Dispatchers.Default) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        val requestScope = this
        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    when {
                        response.code == 404 -> throw IOException("The original is no longer available (HTTP 404).")
                        response.code == 429 -> throw IOException("The image source is rate limiting requests. Try again later.")
                        !response.isSuccessful -> throw IOException("The image source returned HTTP ${response.code}.")
                    }
                    val body = response.body ?: throw IOException("The image source returned an empty response.")
                    val type = body.contentType()
                    if (type != null && type.toString().substringBefore(';').lowercase() !in ImageValidator.SUPPORTED_MIME) {
                        throw IOException("The source returned $type instead of a supported image.")
                    }
                    if (body.contentLength() > DownloadExecutor.MAX_DOWNLOAD_BYTES) {
                        throw IOException("The original exceeds Auroro's 512 MiB safety limit.")
                    }
                    val jobContext = currentCoroutineContext()
                    var bytes = 0L
                    body.byteStream().use { input ->
                        destination.outputStream().buffered(64 * 1024).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                jobContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                if (bytes > DownloadExecutor.MAX_DOWNLOAD_BYTES) {
                                    throw IOException("The original exceeds Auroro's 512 MiB safety limit.")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (body.contentLength() >= 0 && bytes != body.contentLength()) {
                        throw IOException("The original download ended early.")
                    }
                }
            }
        } catch (error: IOException) {
            if (!requestScope.isActive) throw CancellationException("Wallpaper preparation canceled", error)
            throw error
        } finally {
            watcher.cancel()
        }
    }

    private fun prune(keep: File) {
        val originals = directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "img" && it != keep }
            .sortedBy(File::lastModified)
        var total = keep.length() + originals.sumOf(File::length)
        for (old in originals) {
            if (total <= MAX_CACHE_BYTES) break
            val bytes = old.length()
            if (old.delete()) total -= bytes
        }
    }

    private fun safeFileName(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8))
        .take(20)
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
    }
}
