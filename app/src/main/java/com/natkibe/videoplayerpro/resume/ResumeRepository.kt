package com.natkibe.videoplayerpro.resume

import com.natkibe.videoplayerpro.data.VideoDao
import com.natkibe.videoplayerpro.data.VideoProgressEntity
import com.natkibe.videoplayerpro.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ResumeRepository(
    private val dao: VideoDao,
    private val settingsStore: SettingsStore
) {
    /** Returns a valid resume position (>10s, not near end), or 0L. */
    suspend fun safeResumePosition(videoUri: String): Long = withContext(Dispatchers.IO) {
        val enabled = settingsStore.settings.first().resumePlayback
        if (!enabled) return@withContext 0L

        val saved = dao.getProgress(videoUri) ?: return@withContext 0L
        if (saved.completed) return@withContext 0L
        val pos = saved.positionMs
        val dur = saved.durationMs
        if (pos > 10_000 && dur > 0 && pos < dur - 10_000) pos else 0L
    }

    /** Returns the raw progress entity (may be completed or near end). */
    suspend fun getProgress(videoUri: String): VideoProgressEntity? = withContext(Dispatchers.IO) {
        dao.getProgress(videoUri)
    }

    /** Observe recently watched videos. */
    fun observeRecent(limit: Int = 20): Flow<List<com.natkibe.videoplayerpro.data.VideoItemEntity>> =
        dao.observeRecentlyWatched(limit)
}
