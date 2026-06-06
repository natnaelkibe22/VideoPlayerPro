package com.natkibe.videoplayerpro.core.contracts

import android.content.Context
import com.natkibe.videoplayerpro.data.AppDatabase
import com.natkibe.videoplayerpro.favorites.FavoriteRepository
import com.natkibe.videoplayerpro.features.audioonly.PlayAsMusicFeature
import com.natkibe.videoplayerpro.features.floating.FloatingPlayerFeature
import com.natkibe.videoplayerpro.features.library.VideoLibraryFeature
import com.natkibe.videoplayerpro.features.player.VideoPlayerFeature
import com.natkibe.videoplayerpro.features.resume.ResumePlaybackFeature
import com.natkibe.videoplayerpro.features.settings.SettingsFeature
import com.natkibe.videoplayerpro.library.VideoLibraryRepository
import com.natkibe.videoplayerpro.player.ProgressService
import com.natkibe.videoplayerpro.resume.ResumeProgressWriter
import com.natkibe.videoplayerpro.resume.ResumeRepository
import com.natkibe.videoplayerpro.settings.SettingsStore
import com.natkibe.videoplayerpro.thumbnail.ThumbnailDiskCache
import com.natkibe.videoplayerpro.thumbnail.ThumbnailLoader
import com.natkibe.videoplayerpro.thumbnail.ThumbnailMemoryPolicy

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

    // ── New Milestone 5 services ────────────────────────────────────────────
    val resumeRepository: ResumeRepository by lazy { ResumeRepository(database.videoDao(), settingsStore) }
    val resumeProgressWriter: ResumeProgressWriter by lazy { ResumeProgressWriter(database.videoDao()) }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(database.videoDao()) }
    val thumbnailDiskCache: ThumbnailDiskCache by lazy { ThumbnailDiskCache(appContext) }
    val thumbnailMemoryPolicy: ThumbnailMemoryPolicy by lazy { ThumbnailMemoryPolicy() }
    val thumbnailLoader: ThumbnailLoader by lazy { ThumbnailLoader(appContext, thumbnailDiskCache, thumbnailMemoryPolicy) }

    // ── Feature modules ─────────────────────────────────────────────────────
    val libraryFeature: VideoLibraryFeature by lazy { VideoLibraryFeature(videoLibraryRepository) }
    val playerFeature: VideoPlayerFeature by lazy { VideoPlayerFeature(appContext) }
    val resumeFeature: ResumePlaybackFeature by lazy { ResumePlaybackFeature(progressService) }
    val settingsFeature: SettingsFeature by lazy { SettingsFeature(settingsStore) }

    /** Factory methods for features that need a per-call Context. */
    fun createPlayAsMusicFeature(): PlayAsMusicFeature =
        PlayAsMusicFeature(appContext)

    fun createFloatingPlayerFeature(): FloatingPlayerFeature =
        FloatingPlayerFeature(appContext)
}
