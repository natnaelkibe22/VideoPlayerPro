package com.natkibe.videoplayerpro.audioonly

/**
 * Immutable state for the "Play as Music" audio-only mode.
 */
data class AudioOnlyState(
    /** Whether audio-only mode is currently active. */
    val isActive: Boolean = false,

    /** URI of the video being played as audio-only. */
    val videoUri: String? = null,

    /** Display title shown in the notification. */
    val displayTitle: String = ""
)
