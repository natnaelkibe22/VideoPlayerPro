package com.natkibe.playerpro.fakes

import com.natkibe.playerpro.data.VideoItemEntity

object FakeVideoData {
    val sampleVideos = listOf(
        VideoItemEntity(
            uri = "content://video/1",
            displayName = "sample_1080p.mp4",
            folderName = "Movies",
            relativePath = "Movies/",
            storageType = "System/Internal",
            mimeType = "video/mp4",
            durationMs = 120_000L,
            sizeBytes = 50_000_000L,
            dateModified = 1_700_000_000L
        ),
        VideoItemEntity(
            uri = "content://video/2",
            displayName = "sample_usb_movie.mkv",
            folderName = "USB Movies",
            relativePath = "USB Movies/",
            storageType = "USB/External",
            mimeType = "video/x-matroska",
            durationMs = 3_600_000L,
            sizeBytes = 2_000_000_000L,
            dateModified = 1_700_000_100L
        )
    )
}
