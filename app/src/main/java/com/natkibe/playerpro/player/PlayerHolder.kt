package com.natkibe.playerpro.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    @Volatile
    private var player: ExoPlayer? = null

    /** Returns the shared [ExoPlayer] instance, creating it once in a thread-safe manner. */
    fun get(context: Context): ExoPlayer {
        // Fast-path: already initialized
        player?.let { return it }

        // Slow-path: create under lock to guarantee a single instance
        return synchronized(this) {
            player ?: ExoPlayer.Builder(context.applicationContext)
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
        }
    }

    fun release() {
        player?.release()
        player = null
    }
}
