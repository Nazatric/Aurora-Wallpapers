package com.auroro.wallpapers.core.data.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.auroro.wallpapers.R
import com.auroro.wallpapers.app.MainActivity
import com.auroro.wallpapers.core.data.SettingsRepository
import kotlin.math.abs

/** Download notifications. Result notifications and progress detail are user-controllable in Settings. */
class DownloadNotifier(private val context: Context, private val settings: SettingsRepository) {

    fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.download_channel_description) }
        nm.createNotificationChannel(channel)
    }

    private fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, "offline")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Foreground notification used while a download runs. Android requires one for background transfers on
     * older versions; when progress notifications are off only a minimal text is shown.
     */
    suspend fun foregroundInfo(key: String, title: String, bytes: Long, total: Long): ForegroundInfo {
        ensureChannel()
        val showProgress = settings.current().notifyProgress
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_auroro)
            .setContentTitle(title)
            .setContentText(if (showProgress) progressText(bytes, total) else "Downloading wallpaper…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (showProgress) {
            if (total > 0) b.setProgress(100, ((bytes * 100) / total).toInt().coerceIn(0, 100), false) else b.setProgress(0, 0, true)
        }
        return ForegroundInfo(idFor(key), b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    @SuppressLint("MissingPermission") // guarded by canPost()
    suspend fun completed(key: String, title: String, note: String? = null) {
        if (!settings.current().notifyResult || !canPost()) return
        ensureChannel()
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_auroro)
            .setContentTitle("Wallpaper saved")
            .setContentText(note ?: title)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(context).notify(idFor(key), n)
    }

    @SuppressLint("MissingPermission") // guarded by canPost()
    suspend fun failed(key: String, title: String, message: String) {
        if (!settings.current().notifyResult || !canPost()) return
        ensureChannel()
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_auroro)
            .setContentTitle("Download failed")
            .setContentText("$title: $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title: $message"))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(context).notify(idFor(key), n)
    }

    private fun progressText(bytes: Long, total: Long): String =
        if (total > 0) "${(bytes * 100 / total).coerceIn(0, 100)}%" else "Downloading…"

    private fun idFor(key: String) = 1000 + abs(key.hashCode() % 100_000)

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
