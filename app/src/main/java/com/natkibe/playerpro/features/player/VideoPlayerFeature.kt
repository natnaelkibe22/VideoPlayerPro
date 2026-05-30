package com.natkibe.playerpro.features.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.natkibe.playerpro.core.contracts.FeatureModule
import com.natkibe.playerpro.player.PlayerHolder

class VideoPlayerFeature(
    private val context: Context
) : FeatureModule {
    override val name = "Video Player"
    override val milestone = "v0.3-player-controls"

    fun sharedPlayer(): ExoPlayer = PlayerHolder.get(context)
}
