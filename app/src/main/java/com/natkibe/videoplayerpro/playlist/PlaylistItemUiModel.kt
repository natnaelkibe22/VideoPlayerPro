package com.natkibe.videoplayerpro.playlist

/**
 * UI model for a single item in the playlist drawer.
 * Contains only display data — no player-engine references.
 */
data class PlaylistItemUiModel(
    /** Unique identifier (typically the URI). */
    val id: String,

    /** Media URI used for playback. */
    val uri: String,

    /** Human-readable title / display name. */
    val title: String,

    /** Duration in milliseconds (0 if unknown). */
    val durationMs: Long = 0L,

    /** Optional URI for a thumbnail image (loaded via Coil). */
    val thumbnailUri: String? = null,

    /** Folder / album name shown below the title. Null to hide. */
    val folderName: String? = null,

    /** Whether this item is the one currently playing (shows highlight + indicator). */
    val isCurrentlyPlaying: Boolean = false,

    /** Saved resume position in ms (>0 shows a thin progress bar at row bottom). */
    val resumePositionMs: Long = 0L,

    /** Timestamp (epoch ms) of the last time this item was played. 0 = never played. */
    val lastPlayedTimeMs: Long = 0L
)
