package com.natkibe.playerpro.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    private var player: ExoPlayer? = null

    fun get(context: Context): ExoPlayer = player ?: ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also {
            it.repeatMode = Player.REPEAT_MODE_OFF
            player = it
        }

    fun release() {
        player?.release()
        player = null
    }
}
