package com.natkibe.videoplayerpro.player

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developer diagnostics screen for Player Pro.
 * Shows decoder info, video resolution, storage type, library count, cache count.
 */
class DiagnosticsActivity : AppCompatActivity() {
    private val appContainer by lazy { VideoPlayerProAppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        setTitle("Player Diagnostics")

        findViewById<TextView>(R.id.diagnosticsContent).text = "Loading diagnostics..."
        loadDiagnostics()
    }

    private fun loadDiagnostics() {
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                buildDiagnostics()
            }
            findViewById<TextView>(R.id.diagnosticsContent).text = content
        }
    }

    private suspend fun buildDiagnostics(): String = buildString {
        appendLine("═══ Player Diagnostics ═══")
        appendLine()

        // Device info
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()

        // Decoder info
        appendLine("── Decoder ──")
        appendLine("Media3 ExoPlayer (system decoder)")
        appendLine("Max supported: varies by device")
        appendLine()

        // Storage info
        appendLine("── Storage ──")
        val allVideos = appContainer.libraryFeature.allVideos().first()
        val folders = appContainer.libraryFeature.folders().first()
        appendLine("Library count: ${allVideos.size} videos")
        appendLine("Folder count: ${folders.size} folders")
        appendLine()

        // Cache info
        appendLine("── Cache ──")
        appendLine("Room DB: videoplayer_pro.db")
        appendLine("Cached videos: ${allVideos.size}")
        appendLine("Cache strategy: Room first, WorkManager refresh")
        appendLine()

        // Settings
        appendLine("── Settings ──")
        val settings = appContainer.settingsStore.settings.first()
        appendLine("Thumbnails: ${settings.showThumbnails}")
        appendLine("Floating player: ${settings.enableFloatingPlayer}")
        appendLine("Playlist drawer: ${settings.showPlaylistWhileWatching}")
        appendLine("Resume playback: ${settings.resumePlayback}")
        appendLine("Auto-play next: ${settings.autoPlayNext}")
        appendLine("Headunit Safe Mode: ${settings.headunitSafeMode}")
        appendLine("Repeat mode: ${settings.defaultRepeatMode}")
        appendLine("Default speed: ${settings.defaultSpeed}x")
        appendLine()

        // Player state
        appendLine("── Player ──")
        appendLine("Shared ExoPlayer: ${PlayerEngine.isInitialized()}")
        appendLine("Player architecture: single instance, all modes share")
        appendLine()

        appendLine("═══ End Diagnostics ═══")
    }
}
