package com.natkibe.videoplayerpro.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class PlayerControlService(private val player: ExoPlayer) {
    private val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 1

    fun cycleRepeatMode(): Int {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        return player.repeatMode
    }

    fun cycleSpeed(): Float {
        speedIndex = (speedIndex + 1) % speeds.size
        val speed = speeds[speedIndex]
        player.setPlaybackSpeed(speed)
        return speed
    }

    fun initSpeed(speed: Float) {
        speedIndex = speeds.indexOf(speed).coerceAtLeast(0)
        player.setPlaybackSpeed(speeds[speedIndex])
    }

    fun currentSpeed(): Float = speeds[speedIndex]
}
