package com.natkibe.playerpro.media

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.natkibe.playerpro.data.AppDatabase
import com.natkibe.playerpro.data.VideoItemEntity
import com.natkibe.playerpro.model.VideoFolderSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VideoLibraryRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).videoDao()
    private val scanner = MediaStoreVideoScanner(context)

    fun folders(): Flow<List<VideoFolderSummary>> = dao.observeFolders()
    fun allVideos(): Flow<List<VideoItemEntity>> = dao.observeVideos()
    fun videosInFolder(folderName: String): Flow<List<VideoItemEntity>> = dao.observeVideosInFolder(folderName)
    fun recentVideos(): Flow<List<VideoItemEntity>> = dao.observeRecentlyWatched()

    /** Full refresh: scan MediaStore on IO, then replace Room data. */
    suspend fun refreshNow(): Int = withContext(Dispatchers.IO) {
        val videos = scanner.scan()
        dao.replaceVideos(videos)
        videos.size
    }

    /** Enqueue a background refresh via WorkManager. */
    fun refreshInBackground() {
        val req = OneTimeWorkRequestBuilder<LibraryRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "player-pro-video-refresh",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }
}
