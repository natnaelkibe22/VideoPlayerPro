package com.natkibe.playerpro.media

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.natkibe.playerpro.data.AppDatabase
import com.natkibe.playerpro.data.VideoItemEntity
import com.natkibe.playerpro.model.VideoFolderSummary
import kotlinx.coroutines.flow.Flow

class VideoLibraryRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).videoDao()
    private val scanner = MediaStoreVideoScanner(context)

    fun folders(): Flow<List<VideoFolderSummary>> = dao.observeFolders()
    fun allVideos(): Flow<List<VideoItemEntity>> = dao.observeVideos()
    fun videosInFolder(folderName: String): Flow<List<VideoItemEntity>> = dao.observeVideosInFolder(folderName)
    fun recentVideos(): Flow<List<VideoItemEntity>> = dao.observeRecentlyWatched()

    suspend fun refreshNow(): Int {
        val videos = scanner.scan()
        dao.replaceVideos(videos)
        return videos.size
    }

    fun refreshInBackground() {
        val req = OneTimeWorkRequestBuilder<LibraryRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork("player-pro-video-refresh", ExistingWorkPolicy.REPLACE, req)
    }
}
