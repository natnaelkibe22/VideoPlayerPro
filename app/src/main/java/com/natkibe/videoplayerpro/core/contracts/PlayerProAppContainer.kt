package com.natkibe.videoplayerpro.core.contracts

import android.content.Context
import com.natkibe.videoplayerpro.data.AppDatabase
import com.natkibe.videoplayerpro.features.audioonly.PlayAsMusicFeature
import com.natkibe.videoplayerpro.features.floating.FloatingPlayerFeature
import com.natkibe.videoplayerpro.features.library.VideoLibraryFeature
import com.natkibe.videoplayerpro.features.player.VideoPlayerFeature
import com.natkibe.videoplayerpro.features.resume.ResumePlaybackFeature
import com.natkibe.videoplayerpro.features.settings.SettingsFeature
import com.natkibe.videoplayerpro.media.VideoLibraryRepository
import com.natkibe.videoplayerpro.player.ProgressService
import com.natkibe.videoplayerpro.settings.SettingsStore

/**
 * Lightweight manual dependency container. Avoids heavy DI frameworks on weak headunits.
 *
 * Exposes every micro-module so that activities never construct dependencies directly.
 * Each feature is lazy-initialised and shares the same database, repository, and settings.
 */
class VideoPlayerProAppContainer(context: Context) {
    private val appContext = context.applicationContext

    // ── Data layer ──────────────────────────────────────────────────────────
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val videoLibraryRepository: VideoLibraryRepository by lazy { VideoLibraryRepository(appContext) }
    val progressService: ProgressService by lazy { ProgressService(database.videoDao()) }

    // ── Feature modules ─────────────────────────────────────────────────────
    val libraryFeature: VideoLibraryFeature by lazy { VideoLibraryFeature(videoLibraryRepository) }
    val playerFeature: VideoPlayerFeature by lazy { VideoPlayerFeature(appContext) }
    val resumeFeature: ResumePlaybackFeature by lazy { ResumePlaybackFeature(progressService) }
    val settingsFeature: SettingsFeature by lazy { SettingsFeature(settingsStore) }

    /** Factory methods for features that need a per-call Context or player reference. */
    fun createPlayAsMusicFeature(player: androidx.media3.exoplayer.ExoPlayer): PlayAsMusicFeature =
        PlayAsMusicFeature(appContext, player)

    fun createFloatingPlayerFeature(): FloatingPlayerFeature =
        FloatingPlayerFeature(appContext)
}
