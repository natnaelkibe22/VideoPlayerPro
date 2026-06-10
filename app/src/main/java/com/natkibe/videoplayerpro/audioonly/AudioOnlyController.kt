package com.natkibe.videoplayerpro.audioonly

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
 * Controls the "Play as Music" / audio-only mode.
 *
 * - Enter: detaches all video surfaces, keeps audio, starts notification service.
 * - Exit: re-attaches fullscreen video surface, stops notification service.
 * - Uses the shared [PlayerEngine] — no duplicate player.
 */
class AudioOnlyController(private val context: Context) {
    private val _state = MutableStateFlow(AudioOnlyState())
    val state: StateFlow<AudioOnlyState> = _state.asStateFlow()

    /** Enter audio-only mode. */
    fun enter() {
        val engine = PlayerEngine.get()
        val current = engine.state.value
        if (_state.value.isActive && current.isAudioOnly) return
        if (current.isAudioOnly) {
            _state.update {
                AudioOnlyState(
                    isActive = true,
                    videoUri = current.currentVideoUri?.toString(),
                    displayTitle = current.currentTitle.ifBlank { "Playing as Music" }
                )
            }
            return
        }

        // Turn off floating if active
        if (current.isFloating) {
            engine.dispatch(PlaybackCommand.ToggleFloating)
        }

        // Detach all video surfaces and mark audio-only
        engine.dispatch(PlaybackCommand.ToggleAudioOnly)

        // Start foreground notification
        val intent = Intent(context, AudioOnlyNotificationController::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        _state.update {
            AudioOnlyState(
                isActive = true,
                videoUri = current.currentVideoUri?.toString(),
                displayTitle = current.currentTitle.ifBlank { "Playing as Music" }
            )
        }
    }

    /** Exit audio-only mode, re-attach video surface. */
    fun exit() {
        val engine = PlayerEngine.get()
        if (!_state.value.isActive && !engine.state.value.isAudioOnly) return

        // Re-attach fullscreen
        engine.returnToFullscreen()

        // Stop notification
        context.stopService(Intent(context, AudioOnlyNotificationController::class.java))

        _state.update { AudioOnlyState() }
    }
}
