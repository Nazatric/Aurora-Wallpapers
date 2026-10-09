package com.auroro.wallpapers.core.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class SavedMedia(val uri: String, val fileName: String, val sizeBytes: Long)

interface MediaSaver {
    /** Copies the finished temp [file] to its permanent, user-visible home. */
    suspend fun save(file: File, displayName: String, mime: String): SavedMedia
}

/** Saves into Pictures/Auroro Wallpapers using MediaStore (scoped storage, no storage permission needed). */
class GalleryMediaSaver(private val context: Context) : MediaSaver {
    override suspend fun save(file: File, displayName: String, mime: String): SavedMedia = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused to create the file")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                file.inputStream().use { it.copyTo(out, 64 * 1024) }
            } ?: throw IOException("Could not open the destination for writing")
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            var finalName = displayName
            resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) finalName = c.getString(0) ?: displayName
            }
            SavedMedia(uri.toString(), finalName, file.length())
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    companion object {
        const val FOLDER = "Auroro Wallpapers"
    }
}

/** App-private storage: not shown in the gallery, removed on uninstall. */
class AppStorageSaver(private val dir: File) : MediaSaver {
    override suspend fun save(file: File, displayName: String, mime: String): SavedMedia = withContext(Dispatchers.IO) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create ${dir.path}")
        var target = File(dir, displayName)
        var n = 1
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        while (target.exists()) target = File(dir, "$base-${n++}${if (ext.isEmpty()) "" else ".$ext"}")
        try {
            file.copyTo(target, overwrite = false, bufferSize = 64 * 1024)
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        SavedMedia(android.net.Uri.fromFile(target).toString(), target.name, target.length())
    }
}
