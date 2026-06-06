package com.natkibe.videoplayerpro.favorites

import com.natkibe.videoplayerpro.data.FavoriteEntity
import com.natkibe.videoplayerpro.data.VideoDao
import com.natkibe.videoplayerpro.data.VideoItemEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val dao: VideoDao) {

    fun observeFavorites(): Flow<List<VideoItemEntity>> = dao.observeFavorites()

    suspend fun isFavorite(uri: String): Boolean = dao.isFavorite(uri) > 0

    suspend fun toggle(uri: String): Boolean {
        return if (isFavorite(uri)) {
            dao.removeFavorite(uri)
            false
        } else {
            dao.addFavorite(FavoriteEntity(videoUri = uri))
            true
        }
    }
}
