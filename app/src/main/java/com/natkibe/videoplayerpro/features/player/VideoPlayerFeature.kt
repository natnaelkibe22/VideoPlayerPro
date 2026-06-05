package com.natkibe.videoplayerpro.features.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.player.PlayerEngine

class VideoPlayerFeature(
    private val context: Context
) : FeatureModule {
    override val name = "Video Player"
    override val milestone = "v0.3-player-controls"

    fun sharedPlayer(): ExoPlayer = PlayerEngine.get().let {
        // Non-public API access — returns underlying ExoPlayer if available
        throw UnsupportedOperationException("Use PlayerEngine.get() instead of direct ExoPlayer access")
    }
}
