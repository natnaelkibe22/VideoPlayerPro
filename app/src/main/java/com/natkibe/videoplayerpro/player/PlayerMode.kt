package com.natkibe.videoplayerpro.player

/**
 * Player mode enum for the single-ExoPlayer architecture.
 *
 * All modes share the same PlayerHolder ExoPlayer instance.
 * Only one mode is active at a time (except PLAYLIST_DRAWER which can overlay).
 */
enum class PlayerMode {
    /** Fullscreen video in PlayerActivity with controls overlay. */
    FULLSCREEN,

    /** Fullscreen with playlist drawer open (overlay on FULLSCREEN). */
    PLAYLIST_DRAWER,

    /** Floating video overlay window (separate service, same player). */
    FLOATING_VIDEO,

    /** Floating controls-only mode (no video surface, lightweight). */
    FLOATING_CONTROLS,

    /** Audio-only mode (video surface detached, audio continues). */
    AUDIO_ONLY
}
