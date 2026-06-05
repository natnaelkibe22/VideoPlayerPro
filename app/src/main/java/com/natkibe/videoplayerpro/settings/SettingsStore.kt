package com.natkibe.videoplayerpro.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.videoPlayerProDataStore by preferencesDataStore("videoplayer_pro_settings")

data class VideoPlayerProSettings(
    val showThumbnails: Boolean = false,
    val enableFloatingPlayer: Boolean = false,
    val enableFloatingControlsOnly: Boolean = false,
    val showPlaylistWhileWatching: Boolean = true,
    val darkTheme: Boolean = true,
    val accentColorName: String = "Blue",
    val resumePlayback: Boolean = true,
    val autoPlayNext: Boolean = false,
    val defaultRepeatMode: Int = 0,
    val defaultSpeed: Float = 1.0f,
    val headunitSafeMode: Boolean = false,
    val autoHideControls: Boolean = true,
    val useFancyBlur: Boolean = false
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
        val ENABLE_FLOATING = booleanPreferencesKey("enable_floating")
        val ENABLE_FLOATING_CONTROLS = booleanPreferencesKey("enable_floating_controls")
        val SHOW_PLAYLIST = booleanPreferencesKey("show_playlist")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ACCENT = stringPreferencesKey("accent")
        val RESUME = booleanPreferencesKey("resume")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val HEADUNIT_SAFE_MODE = booleanPreferencesKey("headunit_safe_mode")
        val AUTO_HIDE_CONTROLS = booleanPreferencesKey("auto_hide_controls")
        val USE_FANCY_BLUR = booleanPreferencesKey("use_fancy_blur")
    }

    val settings: Flow<VideoPlayerProSettings> = context.videoPlayerProDataStore.data.map { p ->
        VideoPlayerProSettings(
            showThumbnails = p[Keys.SHOW_THUMBNAILS] ?: false,
            enableFloatingPlayer = p[Keys.ENABLE_FLOATING] ?: false,
            enableFloatingControlsOnly = p[Keys.ENABLE_FLOATING_CONTROLS] ?: false,
            showPlaylistWhileWatching = p[Keys.SHOW_PLAYLIST] ?: true,
            darkTheme = p[Keys.DARK_THEME] ?: true,
            accentColorName = p[Keys.ACCENT] ?: "Blue",
            resumePlayback = p[Keys.RESUME] ?: true,
            autoPlayNext = p[Keys.AUTOPLAY_NEXT] ?: false,
            defaultRepeatMode = p[Keys.REPEAT_MODE] ?: 0,
            defaultSpeed = p[Keys.DEFAULT_SPEED] ?: 1.0f,
            headunitSafeMode = p[Keys.HEADUNIT_SAFE_MODE] ?: true,
            autoHideControls = p[Keys.AUTO_HIDE_CONTROLS] ?: true,
            useFancyBlur = p[Keys.USE_FANCY_BLUR] ?: false
        )
    }

    suspend fun setShowThumbnails(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.SHOW_THUMBNAILS] = value }
    suspend fun setFloating(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.ENABLE_FLOATING] = value }
    suspend fun setFloatingControlsOnly(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.ENABLE_FLOATING_CONTROLS] = value }
    suspend fun setPlaylist(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.SHOW_PLAYLIST] = value }
    suspend fun setDarkTheme(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.DARK_THEME] = value }
    suspend fun setAccent(value: String) = context.videoPlayerProDataStore.edit { it[Keys.ACCENT] = value }
    suspend fun setResume(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.RESUME] = value }
    suspend fun setAutoplayNext(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.AUTOPLAY_NEXT] = value }
    suspend fun setRepeatMode(value: Int) = context.videoPlayerProDataStore.edit { it[Keys.REPEAT_MODE] = value }
    suspend fun setDefaultSpeed(value: Float) = context.videoPlayerProDataStore.edit { it[Keys.DEFAULT_SPEED] = value }
    suspend fun setHeadunitSafeMode(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.HEADUNIT_SAFE_MODE] = value }
    suspend fun setAutoHideControls(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.AUTO_HIDE_CONTROLS] = value }
    suspend fun setUseFancyBlur(value: Boolean) = context.videoPlayerProDataStore.edit { it[Keys.USE_FANCY_BLUR] = value }
}
