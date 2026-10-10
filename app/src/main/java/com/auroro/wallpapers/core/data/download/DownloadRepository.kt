package com.auroro.wallpapers.core.data.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.auroro.wallpapers.core.data.WallpaperStore
import com.auroro.wallpapers.core.database.AppDatabase
import com.auroro.wallpapers.core.database.DownloadEntity
import com.auroro.wallpapers.core.database.DownloadStatus
import com.auroro.wallpapers.core.model.Wallpaper
import com.auroro.wallpapers.core.network.UrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class StorageInfo(val savedBytes: Long, val savedCount: Int, val deviceFreeBytes: Long, val deviceTotalBytes: Long)

class DownloadRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val store: WallpaperStore,
    private val workManager: WorkManager,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = db.downloads().observeAll()
    fun observe(key: String): Flow<DownloadEntity?> = db.downloads().observe(key)

    /**
     * Queues a cancellable background download of the original image. Does nothing if the same wallpaper
     * is already downloading or saved (and its file still exists).
     */
    suspend fun enqueue(w: Wallpaper) {
        require(w.downloadAllowed) { "This image's licence or original URL doesn't permit a direct download in Auroro." }
        require(UrlPolicy.isAllowedForNetwork(w.source, w.originalUrl)) { "This image's address isn't an approved secure URL." }
        val existing = db.downloads().get(w.key)
        if (existing != null) {
            val active = existing.status == DownloadStatus.QUEUED.name || existing.status == DownloadStatus.RUNNING.name
            val saved = existing.status == DownloadStatus.COMPLETED.name &&
                withContext(Dispatchers.IO) { LocalFiles.exists(context, existing.localUri) }
            if (active || saved) return
        }
        store.persist(w)
        db.downloads().upsert(DownloadEntity(w.key, DownloadStatus.QUEUED.name, createdAt = clock()))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(DownloadWorker.KEY_WALLPAPER to w.key))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(DownloadWorker.uniqueName(w.key), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun cancel(key: String) {
        workManager.cancelUniqueWork(DownloadWorker.uniqueName(key))
        val d = db.downloads().get(key) ?: return
        if (d.status == DownloadStatus.QUEUED.name || d.status == DownloadStatus.RUNNING.name) {
            db.downloads().markEnded(key, DownloadStatus.CANCELED.name, null, "Canceled", clock())
        }
    }

    suspend fun retry(key: String) {
        val existing = db.downloads().get(key) ?: return
        val missingCompletedFile = existing.status == DownloadStatus.COMPLETED.name &&
            !withContext(Dispatchers.IO) { LocalFiles.exists(context, existing.localUri) }
        require(
            existing.status == DownloadStatus.FAILED.name ||
                existing.status == DownloadStatus.CANCELED.name || missingCompletedFile,
        ) { "Only a failed, canceled, or missing-file download can be retried." }
        val w = store.get(key) ?: throw IllegalStateException("Wallpaper details are missing; search again to refresh.")
        require(w.downloadAllowed && UrlPolicy.isAllowedForNetwork(w.source, w.originalUrl)) {
            "This source or licence no longer allows a new download. Any saved local copy is unchanged."
        }
        db.downloads().delete(key)
        enqueue(w)
    }

    /** Removes the saved file (when possible) and its record. Returns false if the file couldn't be deleted. */
    suspend fun delete(key: String): Boolean {
        cancel(key)
        val d = db.downloads().get(key)
        val deleted = withContext(Dispatchers.IO) { LocalFiles.delete(context, d?.localUri) }
        if (!deleted) return false // Keep the identity, attribution and retry context if Android denied deletion.
        db.downloads().delete(key)
        db.wallpapers().trimUnreferenced()
        return true
    }

    suspend fun fileExists(entity: DownloadEntity): Boolean = withContext(Dispatchers.IO) { LocalFiles.exists(context, entity.localUri) }

    /**
     * Called at startup: rows that claim to be downloading but have no live WorkManager job
     * (process death, force-stop) are marked interrupted instead of spinning forever.
     */
    suspend fun reconcile() {
        for (d in db.downloads().inFlight()) {
            val infos = runCatching { workManager.getWorkInfosForUniqueWorkFlow(DownloadWorker.uniqueName(d.wallpaperKey)).first() }.getOrDefault(emptyList())
            val alive = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
            if (!alive) db.downloads().markEnded(d.wallpaperKey, DownloadStatus.FAILED.name, DownloadErrorKind.INTERRUPTED.name, "The download was interrupted.", clock())
        }
    }

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val completed = db.downloads().observeAll().first()
            .filter { it.status == DownloadStatus.COMPLETED.name && LocalFiles.exists(context, it.localUri) }
        val stat = runCatching { StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull()
            ?: StatFs(context.filesDir.path)
        StorageInfo(
            savedBytes = completed.sumOf { it.savedSizeBytes ?: 0L },
            savedCount = completed.size,
            deviceFreeBytes = stat.availableBytes,
            deviceTotalBytes = stat.totalBytes,
        )
    }

    companion object {
        const val TAG = "wallpaper-download"
    }
}
