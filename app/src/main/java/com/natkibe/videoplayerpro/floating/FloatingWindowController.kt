package com.natkibe.videoplayerpro.floating

import android.content.Context
import android.content.Intent
import android.os.Build
import com.natkibe.videoplayerpro.player.PlaybackCommand
import com.natkibe.videoplayerpro.player.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coordinates floating-window lifecycle: enter, exit, permission checks, and
 * surface-routing. Owns the [FloatingWindowState] flow.
 *
 * All surface attachment/detachment is delegated to [PlayerEngine.surfaceRouter].
 */
class FloatingWindowController(
    private val context: Context
) {
    private val permissionHelper = OverlayPermissionHelper(context)

    private val _state = MutableStateFlow(FloatingWindowState())
    val state: StateFlow<FloatingWindowState> = _state.asStateFlow()

    /** Whether the overlay permission is currently granted. */
    fun canDrawOverApps(): Boolean = permissionHelper.canDrawOverApps()

    /** Build a settings intent so the user can grant overlay permission. */
    fun permissionIntent(): Intent? = permissionHelper.permissionIntent()

    /** Enter floating mode. Returns false if permission is missing. */
    fun enter(): Boolean {
        if (!canDrawOverApps()) return false

        val engine = PlayerEngine.get()

        // Turn off audio-only if it was on
        if (engine.state.value.isAudioOnly) {
            engine.dispatch(PlaybackCommand.ToggleAudioOnly)
        }

        // Toggle floating ON (detaches surfaces, marks state)
        engine.dispatch(PlaybackCommand.ToggleFloating)

        // Start the foreground service that shows the overlay
        val intent = Intent(context, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        _state.update { it.copy(isVisible = true) }
        return true
    }

    /** Exit floating mode and re-attach fullscreen. */
    fun exit() {
        val engine = PlayerEngine.get()

        // Stop the service (which removes overlay and re-broadcasts)
        context.stopService(Intent(context, FloatingWindowService::class.java))

        // Re-attach fullscreen
        engine.returnToFullscreen()

        _state.update { FloatingWindowState() }
    }

    /** Called by the service when it has fully started and the overlay is visible. */
    fun onServiceStarted() {
        _state.update { it.copy(isVisible = true) }
    }

    /** Called by the service when it stops (user tapped close or system killed it). */
    fun onServiceStopped() {
        _state.update { FloatingWindowState() }
    }
}
