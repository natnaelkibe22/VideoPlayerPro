package com.natkibe.playerpro.core.contracts

import android.content.Context
import com.natkibe.playerpro.data.AppDatabase
import com.natkibe.playerpro.media.VideoLibraryRepository
import com.natkibe.playerpro.settings.SettingsStore

/**
 * Lightweight manual dependency container. Avoids heavy DI frameworks on weak headunits.
 */
class PlayerProAppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val videoLibraryRepository: VideoLibraryRepository by lazy { VideoLibraryRepository(appContext) }
}
