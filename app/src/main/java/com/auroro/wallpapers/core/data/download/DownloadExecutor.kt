package com.auroro.wallpapers.core.data.download

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.StatFs
import com.auroro.wallpapers.core.data.DownloadQuality
import com.auroro.wallpapers.core.data.SaveLocation
import com.auroro.wallpapers.core.data.SettingsRepository
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.Format
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.network.UrlPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class DownloadErrorKind(val label: String) {
    NOT_FOUND("Missing metadata"),
    BLOCKED_URL("Blocked URL"),
    HTTP("Server error"),
    RATE_LIMITED("Rate limited"),
    AUTH("Access denied"),
    INVALID_IMAGE("Not a valid image"),
    TIMEOUT("Timed out"),
    NETWORK("Connection lost"),
    INTERRUPTED("Interrupted"),
    STORAGE_FULL("Not enough storage"),
    SAVE_FAILED("Couldn't save"),
    UNKNOWN("Failed"),
}

class DownloadFailure(val kind: DownloadErrorKind, message: String) : Exception(message)

/**
 * The actual download pipeline, independent of WorkManager so it can be unit-tested:
 * resolve URL → stream to a temp file with real progress → validate the image → optional downscale →
 * save via the chosen [MediaSaver] → record metadata. Temp files are always cleaned up.
 */
class DownloadExecutor(
    private val tempDir: File,
    private val db: AppDatabase,
    private val store: WallpaperStore,
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
    private val saver: (SaveLocation) -> MediaSaver,
    private val screenLongEdge: () -> Int,
    private val applier: WallpaperApplier? = null,
    private val notifier: DownloadNotifier? = null,
    /** Overridden only by isolated mock-HTTP tests; production always uses the strict provider allow-list. */
    private val urlAllowed: (String) -> Boolean = UrlPolicy::isAllowedForNetwork,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** @return true when the file was saved. Rethrows [CancellationException] after recording CANCELED. */
    suspend fun execute(key: String, onProgress: suspend (bytes: Long, total: Long) -> Unit = { _, _ -> }): Boolean {
        val dao = db.downloads()
        val wallpaper = store.get(key)
        if (wallpaper == null) {
            fail(key, "Wallpaper", DownloadFailure(DownloadErrorKind.NOT_FOUND, "Wallpaper details are missing. Open it again and retry."))
            return false
        }
        val title = "${wallpaper.source.displayName} #${wallpaper.sourceId}"
        val prefs = settings.current()
        val base = dao.get(key) ?: DownloadEntity(key, DownloadStatus.QUEUED.name, createdAt = clock())
        dao.upsert(base.copy(status = DownloadStatus.RUNNING.name, bytesDownloaded = 0, totalBytes = -1, errorKind = null, errorMessage = null))

        tempDir.mkdirs()
        val part = File(tempDir, safeName(key) + ".part")
        var scaled: File? = null
        try {
            part.delete()
            var lastDb = 0L
            val total = fetch(wallpaper.originalUrl, part) { bytes, tot ->
                val t = clock()
                if (t - lastDb >= 300 || bytes == tot) {
                    lastDb = t
                    dao.updateProgress(key, DownloadStatus.RUNNING.name, bytes, tot)
                    onProgress(bytes, tot)
                }
            }
            currentCoroutineContext().ensureActive()

            val info = ImageValidator.inspect(part)
                ?: throw DownloadFailure(DownloadErrorKind.INVALID_IMAGE, "The server didn't return a valid JPEG, PNG or WebP image.")

            var toSave = part
            var mime = info.mime
            var outW = info.width
            var outH = info.height
            if (prefs.downloadQuality == DownloadQuality.SCREEN_FIT) {
                Downscaler.plan(info.width, info.height, screenLongEdge() * 2)?.let { (nw, nh) ->
                    val out = File(tempDir, safeName(key) + ".scaled")
                    scaled = out
                    mime = downscale(part, out, info.mime, nw, nh)
                    toSave = out
                    outW = nw
                    outH = nh
                }
            }

            val name = fileNameFor(wallpaper, mime)
            val saved = try {
                saver(prefs.saveLocation).save(toSave, name, mime)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw DownloadFailure(
                    if (isNoSpace(e)) DownloadErrorKind.STORAGE_FULL else DownloadErrorKind.SAVE_FAILED,
                    if (isNoSpace(e)) "Not enough free storage to save this wallpaper." else "Couldn't save the file: ${e.message ?: "I/O error"}.",
                )
            } catch (e: SecurityException) {
                throw DownloadFailure(DownloadErrorKind.SAVE_FAILED, "Android denied access to the save location.")
            }

            if (!wallpaper.hasKnownDimensions) store.persist(wallpaper.copy(width = info.width, height = info.height, mimeType = info.mime))
            val current = dao.get(key) ?: base
            dao.upsert(
                current.copy(
                    status = DownloadStatus.COMPLETED.name,
                    bytesDownloaded = saved.sizeBytes,
                    totalBytes = if (total > 0) total else saved.sizeBytes,
                    localUri = saved.uri,
                    fileName = saved.fileName,
                    mimeType = mime,
                    savedSizeBytes = saved.sizeBytes,
                    savedWidth = outW,
                    savedHeight = outH,
                    storage = prefs.saveLocation.name,
                    quality = prefs.downloadQuality.name,
                    errorKind = null,
                    errorMessage = null,
                    completedAt = clock(),
                ),
            )

            var note: String? = null
            if (prefs.setAfterDownload && applier != null) {
                note = when (val r = applier.apply(saved.uri, ApplyTarget.BOTH)) {
                    ApplyResult.Success -> "Saved and set as wallpaper"
                    is ApplyResult.Failure -> "Saved, but couldn't set it: ${r.message}"
                }
            }
            notifier?.completed(key, title, note)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                dao.markEnded(key, DownloadStatus.CANCELED.name, null, "Canceled", clock())
            }
            throw e
        } catch (e: DownloadFailure) {
            fail(key, title, e)
            return false
        } catch (e: Exception) {
            fail(key, title, DownloadFailure(DownloadErrorKind.UNKNOWN, e.message ?: "Unexpected error"))
            return false
        } finally {
            withContext(NonCancellable) {
                part.delete()
                scaled?.delete()
            }
        }
    }

    private suspend fun fail(key: String, title: String, f: DownloadFailure) {
        withContext(NonCancellable) {
            db.downloads().markEnded(key, DownloadStatus.FAILED.name, f.kind.name, f.message, clock())
        }
        notifier?.failed(key, title, f.message ?: f.kind.label)
    }

    /** Streams [url] into [dest]. Returns Content-Length (or -1). Cancels the HTTP call when the coroutine is cancelled. */
    private suspend fun fetch(url: String, dest: File, onProgress: suspend (Long, Long) -> Unit): Long = coroutineScope {
        if (!urlAllowed(url)) {
            throw DownloadFailure(DownloadErrorKind.BLOCKED_URL, "This image's address isn't on the allowed provider list, so it was not downloaded.")
        }
        val call = client.newCall(Request.Builder().url(url).header("Accept", "image/*").build())
        // A blocking socket read can't observe coroutine cancellation, so a watcher cancels the call instead.
        val watcher = launch(Dispatchers.Default) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        val scope = this
        try {
            withContext(Dispatchers.IO) { transfer(call, dest, onProgress) }
        } catch (e: IOException) {
            if (!scope.isActive) throw CancellationException("Canceled")
            throw when {
                isNoSpace(e) -> DownloadFailure(DownloadErrorKind.STORAGE_FULL, "Not enough free storage for this download.")
                e is SocketTimeoutException -> DownloadFailure(DownloadErrorKind.TIMEOUT, "The connection stalled. Check your network and retry.")
                e is UnknownHostException || e is ConnectException ->
                    DownloadFailure(DownloadErrorKind.NETWORK, "Couldn't reach the source. Check your internet connection.")
                else -> DownloadFailure(DownloadErrorKind.NETWORK, "The connection was interrupted: ${e.message ?: "network error"}.")
            }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun transfer(call: Call, dest: File, onProgress: suspend (Long, Long) -> Unit): Long {
        val ctx = currentCoroutineContext()
        return call.execute().use { resp ->
            when {
                resp.code == 404 -> throw DownloadFailure(DownloadErrorKind.HTTP, "The original image is no longer available (HTTP 404).")
                resp.code == 429 -> throw DownloadFailure(DownloadErrorKind.RATE_LIMITED, "The source is rate limiting downloads. Try again shortly.")
                resp.code == 401 || resp.code == 403 -> throw DownloadFailure(DownloadErrorKind.AUTH, "The source denied access to this image (HTTP ${resp.code}).")
                !resp.isSuccessful -> throw DownloadFailure(DownloadErrorKind.HTTP, "The source returned HTTP ${resp.code}.")
            }
            val body = resp.body
            val type = body.contentType()
            if (type != null && type.type != "image") {
                throw DownloadFailure(DownloadErrorKind.INVALID_IMAGE, "The server returned $type instead of an image.")
            }
            val total = body.contentLength()
            ensureSpace(dest, total)
            var downloaded = 0L
            body.byteStream().use { input ->
                dest.outputStream().buffered(64 * 1024).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ctx.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }
            if (total > 0 && downloaded != total) {
                throw DownloadFailure(DownloadErrorKind.INTERRUPTED, "The download ended early ($downloaded of $total bytes).")
            }
            total
        }
    }

    private fun ensureSpace(dest: File, total: Long) {
        val dir = dest.parentFile ?: return
        val available = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        // Need room for the temp copy and the final copy, plus a safety margin.
        val needed = if (total > 0) total * 2 + SAFETY_MARGIN else SAFETY_MARGIN * 4
        if (available < needed) {
            throw DownloadFailure(
                DownloadErrorKind.STORAGE_FULL,
                "Not enough free storage: need about ${Format.fileSize(needed)}, have ${Format.fileSize(available)}.",
            )
        }
    }

    /** Memory-safe downscale: ImageDecoder decodes straight to the target size. Returns the output MIME type. */
    private suspend fun downscale(src: File, dst: File, mime: String, w: Int, h: Int): String = withContext(Dispatchers.IO) {
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(src)) { decoder, _, _ ->
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                val png = mime == "image/png"
                dst.outputStream().buffered().use { out ->
                    if (!bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, out)) {
                        throw IOException("Encoding failed")
                    }
                }
                if (png) "image/png" else "image/jpeg"
            } finally {
                bmp.recycle()
            }
        } catch (e: IOException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw DownloadFailure(DownloadErrorKind.STORAGE_FULL, "Not enough memory to downscale this image. Use the Original quality setting.")
        }
    }

    private fun isNoSpace(e: Throwable): Boolean {
        val m = e.message?.lowercase().orEmpty()
        return "enospc" in m || "no space left" in m
    }

    companion object {
        private const val SAFETY_MARGIN = 16L * 1024 * 1024

        fun fileNameFor(w: Wallpaper, mime: String): String =
            "auroro_${w.source.id}_${safeName(w.sourceId)}.${Format.extensionForMime(mime)}"

        fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    }
}
