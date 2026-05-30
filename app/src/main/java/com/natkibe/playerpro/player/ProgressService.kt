package com.natkibe.playerpro.player

import com.natkibe.playerpro.data.VideoDao
import com.natkibe.playerpro.data.VideoProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProgressService(private val dao: VideoDao) {
    suspend fun save(uri: String, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        val safeDuration = durationMs.coerceAtLeast(0)
        val completed = safeDuration > 0 && positionMs > safeDuration - 15_000
        val old = dao.getProgress(uri)
        dao.saveProgress(
            VideoProgressEntity(
                videoUri = uri,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = safeDuration,
                updatedAt = System.currentTimeMillis(),
                completed = completed,
                playCount = (old?.playCount ?: 0) + 1
            )
        )
    }

    suspend fun resumePosition(uri: String): Long = withContext(Dispatchers.IO) {
        val saved = dao.getProgress(uri) ?: return@withContext 0L
        if (saved.completed) return@withContext 0L
        if (saved.positionMs > 10_000 && saved.positionMs < saved.durationMs - 10_000) saved.positionMs else 0L
    }
}
