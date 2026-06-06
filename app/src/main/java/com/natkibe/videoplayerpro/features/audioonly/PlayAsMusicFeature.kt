package com.natkibe.videoplayerpro.features.audioonly

import android.content.Context
import com.natkibe.videoplayerpro.audioonly.AudioOnlyController
import com.natkibe.videoplayerpro.core.contracts.FeatureModule

/**
 * Lightweight bridge that wraps [AudioOnlyController] for the feature-module contract.
 */
class PlayAsMusicFeature(private val context: Context) : FeatureModule {
    override val name: String = "feature.audio_only"
    override val milestone = "v0.9-audio-only"

    private val controller by lazy { AudioOnlyController(context) }

    fun detachVideoAndContinueAudio() {
        controller.enter()
    }

    fun returnToVideo() {
        controller.exit()
    }
}
