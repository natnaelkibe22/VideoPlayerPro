package com.natkibe.playerpro.core.contracts

import android.content.Context
import com.natkibe.playerpro.data.AppDatabase
import com.natkibe.playerpro.features.audioonly.PlayAsMusicFeature
import com.natkibe.playerpro.features.floating.FloatingPlayerFeature
import com.natkibe.playerpro.features.library.VideoLibraryFeature
import com.natkibe.playerpro.features.player.VideoPlayerFeature
import com.natkibe.playerpro.features.resume.ResumePlaybackFeature
import com.natkibe.playerpro.features.settings.SettingsFeature
import com.natkibe.playerpro.media.VideoLibraryRepository
import com.natkibe.playerpro.player.ProgressService
import com.natkibe.playerpro.settings.SettingsStore

/**
 * Lightweight manual dependency container. Avoids heavy DI frameworks on weak headunits.
 *
 * Exposes every micro-module so that activities never construct dependencies directly.
 * Each feature is lazy-initialised and shares the same database, repository, and settings.
 */
class PlayerProAppContainer(context: Context) {
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
