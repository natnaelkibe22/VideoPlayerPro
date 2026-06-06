package com.natkibe.videoplayerpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_videos")
data class FavoriteEntity(
    @PrimaryKey val videoUri: String,
    val addedAt: Long = System.currentTimeMillis()
)
