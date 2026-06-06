package com.natkibe.videoplayerpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_folders")
data class PinnedFolderEntity(
    @PrimaryKey val folderName: String,
    val pinnedAt: Long = System.currentTimeMillis()
)
