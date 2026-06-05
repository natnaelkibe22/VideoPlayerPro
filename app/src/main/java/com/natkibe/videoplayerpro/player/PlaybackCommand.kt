package com.natkibe.videoplayerpro.player

import android.net.Uri

sealed interface PlaybackCommand {
    data class Play(val uri: Uri, val title: String = "", val startPositionMs: Long = 0L) : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Resume : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand
    data object Next : PlaybackCommand
    data object Previous : PlaybackCommand
    data class SetSpeed(val speed: Float) : PlaybackCommand
    data object CycleRepeatMode : PlaybackCommand
    data object ToggleFloating : PlaybackCommand
    data object ToggleAudioOnly : PlaybackCommand
    data object Retry : PlaybackCommand
    data object SkipNext : PlaybackCommand
}
