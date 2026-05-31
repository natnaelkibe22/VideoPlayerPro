package com.natkibe.videoplayerpro.features.library

import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.media.VideoLibraryRepository
import com.natkibe.videoplayerpro.model.VideoFolderSummary
import kotlinx.coroutines.flow.Flow

class VideoLibraryFeature(
    private val repository: VideoLibraryRepository
) : FeatureModule {
    override val name = "Video Library"
    override val milestone = "v0.2-cache-resume"

    fun folders(): Flow<List<VideoFolderSummary>> = repository.folders()
    fun allVideos(): Flow<List<VideoItemEntity>> = repository.allVideos()
    fun videosInFolder(folderName: String): Flow<List<VideoItemEntity>> = repository.videosInFolder(folderName)
    fun recentVideos(): Flow<List<VideoItemEntity>> = repository.recentVideos()
    fun refreshInBackground() = repository.refreshInBackground()
}
