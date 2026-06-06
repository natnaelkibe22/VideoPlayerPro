package com.natkibe.videoplayerpro.floating

/**
 * Immutable state of the floating video window.
 */
data class FloatingWindowState(
    /** Whether the floating overlay is currently visible. */
    val isVisible: Boolean = false,

    /** X position of the window (ignored when [isSnapped] is true). */
    val x: Int = 80,

    /** Y position of the window (ignored when [isSnapped] is true). */
    val y: Int = 80,

    /** Width in pixels. */
    val width: Int = 640,

    /** Height in pixels. */
    val height: Int = 360,

    /** Whether the window has been snapped to a corner. */
    val isSnapped: Boolean = false,

    /** Which corner the window is snapped to, or [Corner.NONE]. */
    val snappedCorner: Corner = Corner.NONE
) {
    enum class Corner { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
}
