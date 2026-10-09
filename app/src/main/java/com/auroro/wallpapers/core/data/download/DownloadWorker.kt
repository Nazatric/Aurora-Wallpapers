package com.auroro.wallpapers.core.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.auroro.wallpapers.app.AuroroApp

/** Thin WorkManager wrapper around [DownloadExecutor]. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container get() = (applicationContext as AuroroApp).container

    private suspend fun key(): String = inputData.getString(KEY_WALLPAPER) ?: ""

    override suspend fun getForegroundInfo(): ForegroundInfo =
        container.notifier.foregroundInfo(key(), "Downloading wallpaper", 0, -1)

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_WALLPAPER) ?: return Result.failure()
        var lastForeground = 0L
        val ok = container.downloadExecutor.execute(key) { bytes, total ->
            val now = System.currentTimeMillis()
            if (now - lastForeground > 700) {
                lastForeground = now
                runCatching { setForeground(container.notifier.foregroundInfo(key, "Downloading wallpaper", bytes, total)) }
            }
        }
        return if (ok) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_WALLPAPER = "wallpaper_key"
        fun uniqueName(key: String) = "download-$key"
    }
}
