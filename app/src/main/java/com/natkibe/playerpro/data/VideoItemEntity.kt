package com.natkibe.playerpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_items")
data class VideoItemEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val folderName: String,
    val relativePath: String,
    val storageRoot: String,
    val mimeType: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val lastIndexedAt: Long
)
