package com.natkibe.playerpro.media

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.natkibe.playerpro.core.StorageClassifier
import com.natkibe.playerpro.data.VideoItemEntity
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
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.VOLUME_NAME)
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

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val relativePath = if (pathCol >= 0) c.getString(pathCol).orEmpty() else ""
                val volumeName = if (volumeCol >= 0) c.getString(volumeCol) else null
                val name = c.getString(nameCol) ?: "Video $id"
                val itemUri = ContentUris.withAppendedId(collection, id).toString()
                val duration = safeLong(c, durationCol)
                val size = safeLong(c, sizeCol)
                if (duration <= 0L && size <= 0L) continue

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
                    lastIndexedAt = System.currentTimeMillis()
                )
            }
        }
        out
    }

    private fun safeLong(cursor: android.database.Cursor, index: Int): Long =
        if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
}
