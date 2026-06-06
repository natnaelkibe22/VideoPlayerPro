package com.natkibe.videoplayerpro.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ThumbnailLoader(
    private val context: Context,
    private val diskCache: ThumbnailDiskCache = ThumbnailDiskCache(context),
    private val memoryCache: ThumbnailMemoryPolicy = ThumbnailMemoryPolicy()
) {
    private val mutex = Mutex()

    suspend fun loadThumbnail(videoUri: String, targetWidth: Int = 128, targetHeight: Int = 72): Bitmap? {
        val key = diskCache.keyFor(videoUri)

        // Check memory cache first
        memoryCache.get(key)?.let { return it }

        // Check disk cache
        diskCache.get(videoUri)?.let { bitmap ->
            memoryCache.put(key, bitmap)
            return bitmap
        }

        // Generate from MediaStore
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                // Double-check memory after lock
                memoryCache.get(key)?.let { return@withContext it }

                val bitmap = queryMediaStoreThumbnail(videoUri, targetWidth, targetHeight)
                if (bitmap != null) {
                    diskCache.put(videoUri, bitmap)
                    memoryCache.put(key, bitmap)
                }
                bitmap
            }
        }
    }

    private fun queryMediaStoreThumbnail(videoUri: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val uri = Uri.parse(videoUri)
            val id = uri.lastPathSegment?.toLongOrNull() ?: return null

            val kind = MediaStore.Video.Thumbnails.MINI_KIND

            @Suppress("DEPRECATION")
            val bmpOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(targetWidth, targetHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                id,
                kind,
                bmpOptions
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        // Typical MINI_KIND thumbnails are ~512x384; scale down
        while ((512 / sampleSize) > targetWidth * 2 && (384 / sampleSize) > targetHeight * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun clearMemory() = memoryCache.clear()

    suspend fun clearDisk() = diskCache.clear()
}
