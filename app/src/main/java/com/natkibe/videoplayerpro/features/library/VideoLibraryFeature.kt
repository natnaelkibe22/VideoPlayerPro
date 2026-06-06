package com.natkibe.videoplayerpro.features.library

import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.library.VideoLibraryRepository
import com.natkibe.videoplayerpro.model.VideoFolderSummary
import kotlinx.coroutines.flow.Flow

class VideoLibraryFeature(
    private val repository: VideoLibraryRepository
) : FeatureModule {
    override val name = "Video Library"
    override val milestone = "v0.5-library-cache-thumbnails"

    fun folders(): Flow<List<VideoFolderSummary>> = repository.folders()
    fun allVideos(): Flow<List<VideoItemEntity>> = repository.allVideos()
    fun videosInFolder(folderName: String): Flow<List<VideoItemEntity>> = repository.videosInFolder(folderName)
    fun recentVideos(): Flow<List<VideoItemEntity>> = repository.recentVideos()
    fun isRefreshing(): Flow<Boolean> = repository.isRefreshing()
    fun refreshInBackground() = repository.refreshInBackground()
    suspend fun refreshNow(): Int = repository.refreshNow()
}
