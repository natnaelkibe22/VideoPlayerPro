package com.natkibe.videoplayerpro.library

import android.content.Context
import com.natkibe.videoplayerpro.data.AppDatabase
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.model.VideoFolderSummary
import kotlinx.coroutines.flow.Flow

/**
 * Library repository using cache-first pattern:
 * - Show cached Room data immediately
 * - Refresh from MediaStore in background
 * - UI updates only changed rows via Room Flow
 */
class VideoLibraryRepository(context: Context) {
    private val dao = AppDatabase.get(context).videoDao()
    private val cache = VideoLibraryCache(dao)
    private val coordinator = LibraryRefreshCoordinator(context, cache)

    fun folders(): Flow<List<VideoFolderSummary>> = cache.cachedFolders()
    fun allVideos(): Flow<List<VideoItemEntity>> = cache.cachedVideos()
    fun videosInFolder(folderName: String): Flow<List<VideoItemEntity>> = cache.cachedVideosInFolder(folderName)
    fun recentVideos(limit: Int = 20): Flow<List<VideoItemEntity>> = cache.cachedRecent(limit)
    fun isRefreshing(): Flow<Boolean> = coordinator.isRefreshing

    suspend fun videoByUri(uri: String): VideoItemEntity? = cache.videoByUri(uri)

    /** Full refresh: scan MediaStore, replace cache. */
    suspend fun refreshNow(): Int = coordinator.refreshNow()

    /** Background refresh via WorkManager. */
    fun refreshInBackground() = coordinator.refreshInBackground()
}
