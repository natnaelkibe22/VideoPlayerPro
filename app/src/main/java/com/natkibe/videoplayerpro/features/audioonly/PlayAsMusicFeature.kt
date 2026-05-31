package com.natkibe.videoplayerpro.features.audioonly

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.exoplayer.ExoPlayer
import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.player.AudioOnlyService

class PlayAsMusicFeature(
    private val context: Context,
    private val player: ExoPlayer
) : FeatureModule {
    override val name = "Play as Music"
    override val milestone = "v0.4-play-as-music-only-audio-feature"

    fun detachVideoAndContinueAudio() {
        player.clearVideoSurface()
        val intent = Intent(context, AudioOnlyService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
}
