package com.natkibe.playerpro.features.settings

import com.natkibe.playerpro.core.contracts.FeatureModule
import com.natkibe.playerpro.settings.PlayerProSettings
import com.natkibe.playerpro.settings.SettingsStore
import kotlinx.coroutines.flow.Flow

class SettingsFeature(private val settingsStore: SettingsStore) : FeatureModule {
    override val name = "Settings"
    override val milestone = "v0.6-storage-usb-settings"

    fun observe(): Flow<PlayerProSettings> = settingsStore.settings
}
