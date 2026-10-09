package com.auroro.wallpapers.core.data.download

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** Access helpers for saved wallpapers (MediaStore `content://` or app-private `file://`). Never throws on bad input. */
object LocalFiles {
    fun exists(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).isFile } ?: false
                "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun delete(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return true
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).let { f -> !f.exists() || f.delete() } } ?: true
                "content" -> context.contentResolver.delete(uri, null, null) > 0 || !exists(context, uriString)
                else -> false
            }
        } catch (_: FileNotFoundException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    fun open(context: Context, uriString: String): InputStream? = try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") uri.path?.let { File(it).inputStream() } else context.contentResolver.openInputStream(uri)
    } catch (_: Exception) {
        null
    }

    /** A URI other apps may read (FileProvider for private files, the MediaStore URI otherwise). */
    fun shareableUri(context: Context, uriString: String): Uri? = try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            uri.path?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(it)) }
        } else {
            uri
        }
    } catch (_: Exception) {
        null
    }
}
