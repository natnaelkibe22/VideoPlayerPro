package com.natkibe.videoplayerpro.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ThumbnailDiskCache(private val context: Context) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "thumbnails").also { it.mkdirs() }
    }

    fun keyFor(uri: String): String =
        java.math.BigInteger(1, uri.toByteArray()).toString(16).take(40)

    suspend fun get(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, keyFor(uri))
        if (file.exists() && file.length() > 0) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {
                file.delete()
                null
            }
        } else null
    }

    suspend fun put(uri: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(File(cacheDir, keyFor(uri))).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
        } catch (_: Exception) {
            // Silently ignore cache write failures
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
