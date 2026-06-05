package com.natkibe.videoplayerpro.playlist

/**
 * Immutable state snapshot of the playlist drawer UI.
 * Used by the controller to configure animations and visibility.
 */
data class PlaylistDrawerState(
    /** Whether the drawer is currently open (animated to visible position). */
    val isOpen: Boolean = false,

    /** URI of the currently-selected/playing video, used to highlight the active row. */
    val selectedVideoUri: String? = null,

    /** Fraction of the screen width the drawer should occupy (clamped 0.25 – 0.30). */
    val drawerWidthPercent: Float = 0.28f,

    /** When true, the drawer uses a compact list style (smaller row heights / smaller thumbnails). */
    val compactModeEnabled: Boolean = false,

    /** When true, video thumbnails are loaded via Coil inside each row. */
    val showThumbnails: Boolean = true
) {
    /** Returns the drawer width as a fraction clamped within the allowed range. */
    fun clampedWidthPercent(): Float = drawerWidthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT)

    companion object {
        /** Minimum drawer width as a fraction of screen width. */
        const val MIN_WIDTH_PERCENT = 0.25f

        /** Maximum drawer width as a fraction of screen width. */
        const val MAX_WIDTH_PERCENT = 0.30f

        /** Default state with drawer closed and sensible defaults. */
        val DEFAULT = PlaylistDrawerState()
    }
}
