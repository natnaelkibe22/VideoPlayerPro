package com.natkibe.videoplayerpro.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.natkibe.videoplayerpro.controls.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerEngine private constructor(
    private val context: Context,
    private val progressCallback: ((Uri, Long, Long) -> Unit)? = null
) {
    private var player: ExoPlayer? = null
    private var videoSurfaceAttached = false

    /**
     * Custom repeat mode that cannot be represented by ExoPlayer's native modes.
     * When set to [RepeatMode.FOLDER], ExoPlayer is kept at REPEAT_MODE_OFF and
     * folder-aware next-video logic is triggered on playback end.
     */
    private var customRepeatMode: Int? = null

    /**
     * Callback invoked when playback ends while [customRepeatMode] is FOLDER.
     * The callback should select and play the next video from the same folder.
     */
    private var onFolderNextRequested: (() -> Unit)? = null

    private val _state = MutableStateFlow(PlayerEngineState())
    val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

    val surfaceRouter: PlayerSurfaceRouter by lazy {
        PlayerSurfaceRouter(context) { player }
    }

    companion object {
        @Volatile
        private var instance: PlayerEngine? = null

        fun init(context: Context, progressCallback: ((Uri, Long, Long) -> Unit)? = null): PlayerEngine {
            return instance ?: synchronized(this) {
                instance ?: PlayerEngine(context.applicationContext, progressCallback).also {
                    instance = it
                }
            }
        }

        fun get(): PlayerEngine {
            return instance ?: throw IllegalStateException(
                "PlayerEngine not initialized. Call PlayerEngine.init(context) first."
            )
        }

        fun isInitialized(): Boolean = instance != null

        fun release() {
            synchronized(this) {
                instance?.destroyPlayer()
                instance?.surfaceRouter?.release()
                instance = null
            }
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exoPlayer ->
                player = exoPlayer
                setupPlayerListener(exoPlayer)
            }
    }

    private fun setupPlayerListener(exoPlayer: ExoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateStateFromPlayer()
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    // If custom repeat mode is FOLDER, request the next video from same folder
                    if (customRepeatMode == RepeatMode.FOLDER) {
                        onFolderNextRequested?.invoke()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorInfo = PlayerErrorMapper.mapError(error)
                _state.update { it.copy(error = errorInfo) }
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (error == null) {
                    _state.update { it.copy(error = null) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateStateFromPlayer()
            }
        })
    }

    private fun updateStateFromPlayer() {
        val p = player ?: return
        val effectiveRepeat = customRepeatMode ?: p.repeatMode
        _state.update { current ->
            current.copy(
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = if (p.duration > 0) p.duration else current.durationMs,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                speed = p.playbackParameters.speed,
                repeatMode = effectiveRepeat
            )
        }
    }

    // ---- Command handlers ----

    fun dispatch(command: PlaybackCommand) {
        when (command) {
            is PlaybackCommand.Play -> play(command.uri, command.title, command.startPositionMs)
            is PlaybackCommand.Pause -> pause()
            is PlaybackCommand.Resume -> resume()
            is PlaybackCommand.SeekTo -> seekTo(command.positionMs)
            is PlaybackCommand.Next -> next()
            is PlaybackCommand.Previous -> previous()
            is PlaybackCommand.SetSpeed -> setSpeed(command.speed)
            is PlaybackCommand.CycleRepeatMode -> cycleRepeatMode()
            is PlaybackCommand.ToggleFloating -> toggleFloating()
            is PlaybackCommand.ToggleAudioOnly -> toggleAudioOnly()
            is PlaybackCommand.Retry -> retry()
            is PlaybackCommand.SkipNext -> skipNext()
        }
    }

    fun play(uri: Uri, title: String = "", startPositionMs: Long = 0L) {
        val exoPlayer = getOrCreatePlayer()
        saveProgress()

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true

        _state.update {
            it.copy(
                currentVideoUri = uri,
                currentTitle = title,
                positionMs = startPositionMs,
                error = null,
                isPlaying = true
            )
        }
    }

    fun pause() {
        player?.playWhenReady = false
        _state.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        player?.playWhenReady = true
        _state.update { it.copy(isPlaying = true) }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun next() {
        player?.seekToNextMediaItem()
    }

    fun previous() {
        val p = player ?: return
        if (p.currentPosition > 3000L) {
            p.seekTo(0L)
        } else {
            p.seekToPreviousMediaItem()
        }
    }

    fun setSpeed(speed: Float) {
        val p = player ?: return
        val params = androidx.media3.common.PlaybackParameters(speed, p.playbackParameters.pitch)
        p.playbackParameters = params
        _state.update { it.copy(speed = speed) }
    }

    fun cycleRepeatMode() {
        val p = player ?: return
        val current = customRepeatMode ?: p.repeatMode
        val newMode = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> RepeatMode.FOLDER
            RepeatMode.FOLDER -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        applyRepeatMode(newMode)
    }

    /**
     * Set the repeat mode directly (used by the sheet UI).
     */
    fun setRepeatMode(mode: Int) {
        applyRepeatMode(mode)
    }

    /**
     * Register a callback for folder-aware "next video" selection.
     * Called when the current video ends and [RepeatMode.FOLDER] is active.
     */
    fun setOnFolderNextRequested(callback: (() -> Unit)?) {
        onFolderNextRequested = callback
    }

    private fun applyRepeatMode(mode: Int) {
        val p = player ?: return
        when (mode) {
            RepeatMode.FOLDER -> {
                customRepeatMode = mode
                p.repeatMode = Player.REPEAT_MODE_OFF
            }
            else -> {
                customRepeatMode = null
                p.repeatMode = mode
            }
        }
        _state.update { it.copy(repeatMode = mode) }
    }

    // ---- Toggle behavior ----

    /**
     * Toggle floating mode ON/OFF.
     * Tap #1: floating ON (detach fullscreen, attach to floating)
     * Tap #2: floating OFF (re-attach fullscreen)
     */
    fun toggleFloating() {
        val current = _state.value
        if (current.isFloating) {
            // Turn floating OFF
            _state.update { it.copy(isFloating = false) }
            // Surface routing is handled by FloatingPlayerService stop + PlayerActivity re-attach
        } else {
            // Turn floating ON, also turn off audio-only if enabled
            _state.update { it.copy(isFloating = true, isAudioOnly = false) }
            surfaceRouter.detachAllForAudioOnly() // Detach fullscreen surface before floating attaches
        }
    }

    /**
     * Toggle audio-only mode ON/OFF.
     * Tap #1: audio-only ON (detach all video surfaces)
     * Tap #2: audio-only OFF (re-attach fullscreen)
     */
    fun toggleAudioOnly() {
        val current = _state.value
        if (current.isAudioOnly) {
            // Turn audio-only OFF
            _state.update { it.copy(isAudioOnly = false) }
            // Surface routing is handled by AudioOnlyService stop + PlayerActivity re-attach
        } else {
            // Turn audio-only ON, also turn off floating if enabled
            _state.update { it.copy(isAudioOnly = true, isFloating = false) }
            surfaceRouter.detachAllForAudioOnly()
        }
    }

    /**
     * Ensure state reflects that we're back in fullscreen (no floating, no audio-only).
     */
    fun returnToFullscreen() {
        _state.update { it.copy(isFloating = false, isAudioOnly = false) }
        surfaceRouter.attachToFullscreen()
    }

    // ---- Error handling ----

    fun retry() {
        val current = _state.value
        val uri = current.currentVideoUri ?: return
        val pos = current.positionMs
        play(uri, current.currentTitle, pos)
    }

    fun skipNext() {
        _state.update { it.copy(error = null) }
        next()
    }

    // ---- Lifecycle ----

    fun saveProgress() {
        val p = player ?: return
        val uri = _state.value.currentVideoUri ?: return
        val position = p.currentPosition.coerceAtLeast(0L)
        val duration = if (p.duration > 0) p.duration else _state.value.durationMs
        progressCallback?.invoke(uri, position, duration)
    }

    fun pauseAndSave() {
        pause()
        saveProgress()
    }

    fun destroyPlayer() {
        saveProgress()
        player?.stop()
        player?.release()
        player = null
        customRepeatMode = null
        onFolderNextRequested = null
        _state.update { PlayerEngineState() }
    }

    fun attachFullscreenPlayerView(playerView: androidx.media3.ui.PlayerView) {
        surfaceRouter.registerFullscreenView(playerView)
        // Attach player if we're in fullscreen mode
        if (!_state.value.isFloating && !_state.value.isAudioOnly) {
            playerView.player = player
        }
    }

    fun detachFullscreenPlayerView() {
        surfaceRouter.unregisterFullscreenView()
    }

    fun attachFloatingPlayerView(playerView: androidx.media3.ui.PlayerView, container: android.view.ViewGroup) {
        surfaceRouter.registerFloatingView(playerView, container)
        if (_state.value.isFloating) {
            playerView.player = player
        }
    }

    fun detachFloatingPlayerView() {
        surfaceRouter.unregisterFloatingView()
    }
}
