package com.natkibe.videoplayerpro.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.natkibe.videoplayerpro.core.StorageClassifier
import com.natkibe.videoplayerpro.data.VideoItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreVideoScanner(private val context: Context) {
    suspend fun scan(): List<VideoItemEntity> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
                add(MediaStore.Video.Media.VOLUME_NAME)
                add(MediaStore.Video.Media.WIDTH)
                add(MediaStore.Video.Media.HEIGHT)
            }
        }.toTypedArray()

        val sort = "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} COLLATE NOCASE ASC, ${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
        val out = mutableListOf<VideoItemEntity>()

        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val folderCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val pathCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1
            val volumeCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Video.Media.VOLUME_NAME) else -1
            val widthCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Video.Media.WIDTH) else -1
            val heightCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Video.Media.HEIGHT) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val relativePath = if (pathCol >= 0) c.getString(pathCol).orEmpty() else ""
                val volumeName = if (volumeCol >= 0) c.getString(volumeCol) else null
                val name = c.getString(nameCol) ?: "Video $id"
                val itemUri = ContentUris.withAppendedId(collection, id).toString()
                val duration = safeLong(c, durationCol)
                val size = safeLong(c, sizeCol)
                if (duration <= 0L && size <= 0L) continue

                // Extract resolution from MediaStore or fallback to MediaMetadataRetriever
                var width = safeInt(c, widthCol)
                var height = safeInt(c, heightCol)
                var resolution = if (width > 0 && height > 0) "${width}x${height}" else null

                // Fallback: try MediaMetadataRetriever if MediaStore didn't report resolution
                if (resolution == null) {
                    try {
                        val retriever = MediaMetadataRetriever().apply {
                            setDataSource(context, Uri.parse(itemUri))
                        }
                        val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        retriever.release()
                        val w = wStr?.toIntOrNull() ?: 0
                        val h = hStr?.toIntOrNull() ?: 0
                        if (w > 0 && h > 0) {
                            width = w
                            height = h
                            resolution = "${w}x${h}"
                        }
                    } catch (_: Exception) {
                        // Silent fallback
                    }
                }

                out += VideoItemEntity(
                    uri = itemUri,
                    displayName = name,
                    folderName = c.getString(folderCol) ?: relativePath.trim('/').substringAfterLast('/').ifBlank { "Videos" },
                    relativePath = relativePath,
                    storageRoot = StorageClassifier.classify(relativePath, volumeName),
                    mimeType = c.getString(mimeCol),
                    durationMs = duration,
                    sizeBytes = size,
                    dateModified = safeLong(c, modifiedCol),
                    lastIndexedAt = System.currentTimeMillis(),
                    resolution = resolution
                )
            }
        }
        out
    }

    private fun safeInt(cursor: android.database.Cursor, index: Int): Int =
        if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else 0

    private fun safeLong(cursor: android.database.Cursor, index: Int): Long =
        if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
}
