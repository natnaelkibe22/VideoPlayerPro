package com.natkibe.videoplayerpro.model

data class VideoFolderSummary(
    val folderName: String,
    val storageRoot: String,
    val videoCount: Int,
    val latestModified: Long
)
