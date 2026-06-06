package com.natkibe.videoplayerpro.controls

/**
 * Immutable state snapshot for player controls visibility.
 * Used by [PlayerControlsController] to manage auto-hide behavior.
 */
data class ControlsVisibilityState(
    /** Whether the controls overlay is currently visible. */
    val controlsVisible: Boolean = false,

    /** [System.currentTimeMillis] timestamp of the last user interaction. */
    val lastInteractionTime: Long = 0L,

    /** Whether auto-hide is enabled (mirrors [com.natkibe.videoplayerpro.settings.SettingsStore.autoHideControls]). */
    val autoHideEnabled: Boolean = true,

    /** Delay in milliseconds before controls auto-hide after the last interaction. */
    val autoHideDelayMs: Long = DEFAULT_AUTO_HIDE_DELAY_MS,

    /** Whether head-unit safe mode is active (disables animations when true). */
    val headunitSafeMode: Boolean = false,

    /** Current playback state — true when video is playing. */
    val isPlaying: Boolean = false,

    /** Whether the user is currently scrubbing the seek bar. */
    val isSeeking: Boolean = false
) {
    // ── Computed ──────────────────────────────────────────────────────────

    /**
     * Returns `true` when the controls **should** schedule an auto-hide:
     * visible + auto-hide enabled + playing + not seeking.
     */
    val shouldAutoHide: Boolean
        get() = controlsVisible && autoHideEnabled && isPlaying && !isSeeking

    /**
     * Effective delay for auto-hide.
     * Returns 0 when head-unit safe mode is on (no animations / no delayed hiding),
     * otherwise the configured [autoHideDelayMs].
     */
    val effectiveDelayMs: Long
        get() = if (headunitSafeMode) 0L else autoHideDelayMs

    companion object {
        /** Default auto-hide delay: 3 seconds. */
        const val DEFAULT_AUTO_HIDE_DELAY_MS = 3000L
    }
}
