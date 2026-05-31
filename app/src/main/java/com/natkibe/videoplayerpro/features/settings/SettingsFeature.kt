package com.natkibe.videoplayerpro.features.settings

import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.settings.VideoPlayerProSettings
import com.natkibe.videoplayerpro.settings.SettingsStore
import kotlinx.coroutines.flow.Flow

class SettingsFeature(private val settingsStore: SettingsStore) : FeatureModule {
    override val name = "Settings"
    override val milestone = "v0.6-storage-usb-settings"

    fun observe(): Flow<VideoPlayerProSettings> = settingsStore.settings
}
