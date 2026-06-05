package com.natkibe.videoplayerpro.features.audioonly

import android.content.Context
import android.content.Intent
import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.player.AudioOnlyService
import com.natkibe.videoplayerpro.player.PlaybackCommand
import com.natkibe.videoplayerpro.player.PlayerEngine

class PlayAsMusicFeature(private val context: Context) : FeatureModule {
    override val name: String = "feature.audio_only"
    override val milestone = "v0.9-audio-only"

    fun detachVideoAndContinueAudio() {
        PlayerEngine.get().dispatch(PlaybackCommand.ToggleAudioOnly)
        val intent = Intent(context, AudioOnlyService::class.java)
        intent.action = "START_AUDIO_ONLY"
        context.startForegroundService(intent)
    }

    fun returnToVideo() {
        PlayerEngine.get().returnToFullscreen()
        val intent = Intent(context, AudioOnlyService::class.java)
        context.stopService(intent)
    }
}
