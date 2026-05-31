package com.natkibe.videoplayerpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_progress")
data class VideoProgressEntity(
    @PrimaryKey val videoUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean = false,
    val playCount: Int = 0
)
