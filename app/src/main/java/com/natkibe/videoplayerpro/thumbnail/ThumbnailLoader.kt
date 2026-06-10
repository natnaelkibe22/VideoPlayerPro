package com.natkibe.videoplayerpro.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
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
        val cacheKey = cacheKeyFor(videoUri, targetWidth, targetHeight)
        val key = diskCache.keyFor(cacheKey)

        memoryCache.get(key)?.let { return it }

        diskCache.get(cacheKey)?.let { bitmap ->
            memoryCache.put(key, bitmap)
            return bitmap
        }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                memoryCache.get(key)?.let { return@withContext it }

                val bitmap = querySystemThumbnail(videoUri, targetWidth, targetHeight)
                    ?: decodeFrameThumbnail(videoUri, targetWidth, targetHeight)
                if (bitmap != null) {
                    diskCache.put(cacheKey, bitmap)
                    memoryCache.put(key, bitmap)
                }
                bitmap
            }
        }
    }

    fun memoryThumbnail(videoUri: String, targetWidth: Int = 128, targetHeight: Int = 72): Bitmap? =
        memoryCache.get(diskCache.keyFor(cacheKeyFor(videoUri, targetWidth, targetHeight)))

    private fun cacheKeyFor(videoUri: String, targetWidth: Int, targetHeight: Int): String =
        "$videoUri#$targetWidth:$targetHeight"

    private fun querySystemThumbnail(videoUri: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val uri = Uri.parse(videoUri)
            if (Build.VERSION.SDK_INT >= 29) {
                return context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), null)
            }
            val id = uri.lastPathSegment?.toLongOrNull() ?: return null
            @Suppress("DEPRECATION")
            val bmpOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(targetWidth, targetHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                bmpOptions
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeFrameThumbnail(videoUri: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val source = Uri.parse(videoUri)
            val frame = MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, source)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return null
            Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)
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
