package com.natkibe.videoplayerpro.fakes

import com.natkibe.videoplayerpro.data.VideoItemEntity

object FakeVideoData {
    val sampleVideos = listOf(
        VideoItemEntity(
            uri = "content://video/1",
            displayName = "sample_1080p.mp4",
            folderName = "Movies",
            relativePath = "Movies/",
            storageRoot = "System/Internal",
            mimeType = "video/mp4",
            durationMs = 120_000L,
            sizeBytes = 50_000_000L,
            dateModified = 1_700_000_000L,
            lastIndexedAt = System.currentTimeMillis()
        ),
        VideoItemEntity(
            uri = "content://video/2",
            displayName = "sample_usb_movie.mkv",
            folderName = "USB Movies",
            relativePath = "USB Movies/",
            storageRoot = "USB/SD",
            mimeType = "video/x-matroska",
            durationMs = 3_600_000L,
            sizeBytes = 2_000_000_000L,
            dateModified = 1_700_000_100L,
            lastIndexedAt = System.currentTimeMillis()
        )
    )
}
