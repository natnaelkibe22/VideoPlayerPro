package com.natkibe.videoplayerpro.library

import com.natkibe.videoplayerpro.data.VideoDao
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.model.VideoFolderSummary
import kotlinx.coroutines.flow.Flow

/**
 * Simple cache over Room for the video library.
 *
 * The database IS the cache. This class provides convenience access
 * patterns and ensures cached data is shown before MediaStore refresh.
 */
class VideoLibraryCache(private val dao: VideoDao) {

    fun cachedFolders(): Flow<List<VideoFolderSummary>> = dao.observeFolders()

    fun cachedVideos(): Flow<List<VideoItemEntity>> = dao.observeVideos()

    fun cachedVideosInFolder(folderName: String): Flow<List<VideoItemEntity>> =
        dao.observeVideosInFolder(folderName)

    fun cachedRecent(limit: Int = 20): Flow<List<VideoItemEntity>> =
        dao.observeRecentlyWatched(limit)

    suspend fun videoByUri(uri: String): VideoItemEntity? = dao.videoByUri(uri)

    suspend fun replaceAll(videos: List<VideoItemEntity>) = dao.replaceVideos(videos)
}
