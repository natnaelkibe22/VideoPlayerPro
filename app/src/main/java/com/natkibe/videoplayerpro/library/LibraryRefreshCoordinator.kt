package com.natkibe.videoplayerpro.library

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.media.LibraryRefreshWorker
import com.natkibe.videoplayerpro.media.MediaStoreVideoScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LibraryRefreshCoordinator(
    private val context: Context,
    private val cache: VideoLibraryCache
) {
    private val scanner = MediaStoreVideoScanner(context)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: Flow<Boolean> = _isRefreshing.asStateFlow()

    /** Full blocking refresh: scan MediaStore, replace Room data. */
    suspend fun refreshNow(): Int = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        try {
            val videos = scanner.scan()
            cache.replaceAll(videos)
            videos.size
        } finally {
            _isRefreshing.value = false
        }
    }

    /** Enqueue background refresh via WorkManager. */
    fun refreshInBackground() {
        val req = OneTimeWorkRequestBuilder<LibraryRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "player-pro-video-refresh",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    /** Get videos for a tab filter. */
    suspend fun videosForStorage(filterLabel: String): List<VideoItemEntity> {
        val all = cache.cachedVideos()
        // We need a snapshot — collect first emission
        var result = emptyList<VideoItemEntity>()
        all.collect { list ->
            result = if (filterLabel == "All") list
            else list.filter { it.storageRoot == filterLabel }
            return@collect
        }
        return result
    }
}
