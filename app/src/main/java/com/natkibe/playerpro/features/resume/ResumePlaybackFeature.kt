package com.natkibe.playerpro.features.resume

import com.natkibe.playerpro.core.contracts.FeatureModule
import com.natkibe.playerpro.player.ProgressService

class ResumePlaybackFeature(
    private val progressService: ProgressService
) : FeatureModule {
    override val name = "Resume Playback"
    override val milestone = "v0.2-cache-resume"

    suspend fun getSafeResumePosition(videoUri: String): Long = progressService.resumePosition(videoUri)
    suspend fun save(videoUri: String, positionMs: Long, durationMs: Long) = progressService.save(videoUri, positionMs, durationMs)
}
