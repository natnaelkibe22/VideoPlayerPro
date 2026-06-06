package com.natkibe.videoplayerpro.resume

import com.natkibe.videoplayerpro.data.VideoDao
import com.natkibe.videoplayerpro.data.VideoProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes resume progress with proper throttling.
 * Saves on pause, on stop, and every 15-30 seconds while playing.
 */
class ResumeProgressWriter(private val dao: VideoDao) {

    suspend fun save(videoUri: String, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        val safeDuration = durationMs.coerceAtLeast(0)
        val completed = safeDuration > 0 && positionMs > safeDuration - 15_000
        val old = dao.getProgress(videoUri)
        dao.saveProgress(
            VideoProgressEntity(
                videoUri = videoUri,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = safeDuration,
                updatedAt = System.currentTimeMillis(),
                completed = completed,
                playCount = (old?.playCount ?: 0) + 1
            )
        )
    }

    suspend fun getPosition(videoUri: String): Long = withContext(Dispatchers.IO) {
        dao.getProgress(videoUri)?.positionMs ?: 0L
    }
}
