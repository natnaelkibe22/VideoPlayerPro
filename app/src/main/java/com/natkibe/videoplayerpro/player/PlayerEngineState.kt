package com.natkibe.videoplayerpro.player

import android.net.Uri

data class PlayerEngineState(
    val currentVideoUri: Uri? = null,
    val currentTitle: String = "",
    val currentMimeType: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isFloating: Boolean = false,
    val isAudioOnly: Boolean = false,
    val speed: Float = 1.0f,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val error: PlayerErrorInfo? = null,
    val isBuffering: Boolean = false,
    val playlistIndex: Int = 0,
    val playlistSize: Int = 0
)

data class PlayerErrorInfo(
    val errorCode: Int,
    val userMessage: String,
    val isRecoverable: Boolean = true
)
