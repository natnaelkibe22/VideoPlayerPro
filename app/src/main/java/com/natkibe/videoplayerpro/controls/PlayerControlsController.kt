package com.natkibe.videoplayerpro.controls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UI-layer controller that manages player controls visibility and auto-hide
 * behaviour. Extracted from PlayerActivity to follow the same controller+state
 * pattern used by [com.natkibe.videoplayerpro.playlist.PlaylistDrawerController].
 *
 * @param autoHideEnabled  Initial value from [com.natkibe.videoplayerpro.settings.SettingsStore.autoHideControls].
 * @param headunitSafeMode Initial value from [com.natkibe.videoplayerpro.settings.SettingsStore.headunitSafeMode].
 * @param scope            [CoroutineScope] used for auto-hide delays (typically the Activity lifecycle scope).
 * @param onStateChanged   Callback invoked whenever [state] changes — the host
 *                         (Activity / Fragment) should apply the new visibility.
 */
class PlayerControlsController(
    private var autoHideEnabled: Boolean = true,
    private var headunitSafeMode: Boolean = false,
    private val scope: CoroutineScope,
    private val onStateChanged: (ControlsVisibilityState) -> Unit
) {
    // ── Internal state ────────────────────────────────────────────────────

    private var _state: ControlsVisibilityState = ControlsVisibilityState(
        autoHideEnabled = autoHideEnabled,
        headunitSafeMode = headunitSafeMode
    )

    /** Current immutable snapshot. */
    val state: ControlsVisibilityState
        get() = _state

    /** Currently scheduled auto-hide coroutine; cancelled when not needed. */
    private var autoHideJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────

    /** Make controls visible immediately and notify. */
    fun showControls() {
        cancelAutoHide()
        _state = _state.copy(
            controlsVisible = true,
            lastInteractionTime = System.currentTimeMillis()
        )
        onStateChanged(_state)
    }

    /** Hide controls immediately, cancelling any pending auto-hide. */
    fun hideControls() {
        cancelAutoHide()
        _state = _state.copy(controlsVisible = false)
        onStateChanged(_state)
    }

    /** Toggle visibility: show if hidden, hide if visible. */
    fun toggleControls() {
        if (_state.controlsVisible) hideControls() else onUserInteraction()
    }

    /**
     * Called on any user interaction (tap, scroll, etc.).
     * - If controls are hidden, shows them.
     * - If controls are visible, resets the auto-hide timer.
     */
    fun onUserInteraction() {
        cancelAutoHide()
        _state = _state.copy(
            controlsVisible = true,
            lastInteractionTime = System.currentTimeMillis()
        )
        onStateChanged(_state)
        scheduleAutoHideIfNeeded()
    }

    /**
     * Update the playback state.
     * - Paused → keep controls visible, cancel any pending hide.
     * - Playing → start the auto-hide timer if applicable.
     */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        cancelAutoHide()
        _state = _state.copy(isPlaying = isPlaying)
        if (!isPlaying) {
            // Paused: keep controls visible, don't auto-hide
            _state = _state.copy(controlsVisible = true)
            onStateChanged(_state)
        } else {
            scheduleAutoHideIfNeeded()
        }
    }

    /** User started scrubbing the seek bar — cancel auto-hide. */
    fun onSeekStart() {
        cancelAutoHide()
        _state = _state.copy(isSeeking = true)
        onStateChanged(_state)
    }

    /** User stopped scrubbing — resume auto-hide if playing. */
    fun onSeekEnd() {
        _state = _state.copy(isSeeking = false)
        onStateChanged(_state)
        scheduleAutoHideIfNeeded()
    }

    /**
     * Refresh settings-driven properties from the outside (e.g. when
     * [com.natkibe.videoplayerpro.settings.SettingsStore.settings] emits new values).
     */
    fun updateSettings(autoHideEnabled: Boolean, headunitSafeMode: Boolean) {
        this.autoHideEnabled = autoHideEnabled
        this.headunitSafeMode = headunitSafeMode
        _state = _state.copy(
            autoHideEnabled = autoHideEnabled,
            headunitSafeMode = headunitSafeMode
        )
        // If we need to re-evaluate the timer after a settings change:
        if (_state.shouldAutoHide) {
            scheduleAutoHideIfNeeded()
        } else {
            cancelAutoHide()
        }
    }

    /** Cleanup: cancel pending work. Call when the host is destroyed. */
    fun destroy() {
        cancelAutoHide()
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun scheduleAutoHideIfNeeded() {
        if (!_state.shouldAutoHide) return
        val delayMs = _state.effectiveDelayMs
        autoHideJob = scope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            // Re-check state after delay (may have been cancelled / changed)
            if (_state.shouldAutoHide) {
                _state = _state.copy(controlsVisible = false)
                onStateChanged(_state)
            }
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }
}
