package com.natkibe.videoplayerpro.player

import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Developer diagnostics screen for Player Pro.
 * Shows decoder info, video resolution, storage type, library count,
 * cache sizes, MIME type of current video, last playback error.
 * Accessible from Settings → Diagnostics or via long-press on app version.
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
        appendLine("── Device ──")
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Model: ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val memInfo = Runtime.getRuntime()
        appendLine("Max heap: ${memInfo.maxMemory() / 1024 / 1024} MB")
        appendLine("Free heap: ${memInfo.freeMemory() / 1024 / 1024} MB")
        appendLine("Total heap: ${memInfo.totalMemory() / 1024 / 1024} MB")
        appendLine()

        // Decoder info
        appendLine("── Decoder ──")
        appendLine("Engine: Media3 ExoPlayer")
        appendLine("Decoder: ${getDecoderInfo()}")
        appendLine("Renderer: software + hardware (system-selected)")
        appendLine()

        // Storage info
        appendLine("── Storage ──")
        val allVideos = appContainer.libraryFeature.allVideos().first()
        val folders = appContainer.libraryFeature.folders().first()
        appendLine("Library item count: ${allVideos.size} videos")
        appendLine("Folder count: ${folders.size} folders")
        appendLine()

        // Cache info
        appendLine("── Cache ──")
        appendLine("Room DB: videoplayer_pro.db")
        appendLine("Cached videos: ${allVideos.size}")
        val (diskCacheSize, diskCacheFiles) = getDiskCacheSize()
        appendLine("Thumbnail disk cache: $diskCacheSize ($diskCacheFiles files)")
        val memoryCacheInfo = appContainer.thumbnailMemoryPolicy.cacheInfo()
        appendLine("Thumbnail memory cache: $memoryCacheInfo")
        appendLine("Cache strategy: Room first, WorkManager refresh")
        appendLine()

        // Settings
        appendLine("── Settings ──")
        val settings = appContainer.settingsStore.settings.first()
        appendLine("Show Thumbnails: ${settings.showThumbnails}")
        appendLine("Headunit Safe Mode: ${settings.headunitSafeMode}")
        appendLine("Floating player: ${settings.enableFloatingPlayer}")
        appendLine("Playlist drawer: ${settings.showPlaylistWhileWatching}")
        appendLine("Resume playback: ${settings.resumePlayback}")
        appendLine("Auto-play next: ${settings.autoPlayNext}")
        appendLine("Auto-hide controls: ${settings.autoHideControls}")
        appendLine("Repeat mode: ${settings.defaultRepeatMode}")
        appendLine("Default speed: ${settings.defaultSpeed}x")
        appendLine("Fancy blur: ${settings.useFancyBlur}")
        appendLine()

        // Player state
        appendLine("── Player ──")
        appendLine("Engine initialized: ${PlayerEngine.isInitialized()}")
        if (PlayerEngine.isInitialized()) {
            val engineState = PlayerEngine.get().state.value
            appendLine("Playing: ${engineState.isPlaying}")
            appendLine("Audio-only: ${engineState.isAudioOnly}")
            appendLine("Floating: ${engineState.isFloating}")
            appendLine("Speed: ${engineState.speed}x")
            val currentUri = engineState.currentVideoUri
            if (currentUri != null) {
                appendLine("Current video URI: $currentUri")
                val mime = engineState.currentMimeType ?: getMimeType(currentUri.toString())
                appendLine("MIME type: $mime")
            } else {
                appendLine("Current video: none")
                appendLine("MIME type: N/A")
            }
            appendLine("Position: ${engineState.positionMs}ms / ${engineState.durationMs}ms")
            if (engineState.error != null) {
                appendLine("Playback error: ${engineState.error!!.userMessage}")
                appendLine("Error code: ${engineState.error!!.errorCode}")
                appendLine("Recoverable: ${engineState.error!!.isRecoverable}")
            } else {
                appendLine("Last playback error: none")
            }
        } else {
            appendLine("Player engine not initialized")
        }
        appendLine()

        // Storage space
        appendLine("── Storage Space ──")
        appendLine(getStorageSpaceInfo())

        appendLine()
        appendLine("═══ End Diagnostics ═══")
    }

    private fun getDecoderInfo(): String {
        return try {
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            val decoderCount = codecList.codecInfos.count { !it.isEncoder }
            "$decoderCount decoders available"
        } catch (_: Exception) {
            "system-default"
        }
    }

    private fun getDiskCacheSize(): Pair<String, Int> {
        return try {
            val cacheDir = File(cacheDir, "thumbnails")
            var totalSize = 0L
            var fileCount = 0
            cacheDir.listFiles()?.forEach {
                totalSize += it.length()
                fileCount++
            }
            val sizeStr = when {
                totalSize < 1024 -> "$totalSize B"
                totalSize < 1024 * 1024 -> "${totalSize / 1024} KB"
                else -> "${"%.1f".format(totalSize.toDouble() / 1024 / 1024)} MB"
            }
            Pair(sizeStr, fileCount)
        } catch (_: Exception) {
            Pair("0 B", 0)
        }
    }

    private fun getMimeType(uri: String): String {
        return try {
            contentResolver.getType(android.net.Uri.parse(uri)) ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun getStorageSpaceInfo(): String {
        return try {
            val stat = StatFs(cacheDir.absolutePath)
            val blockSize = stat.blockSizeLong
            val total = stat.totalBytes
            val available = stat.availableBytes
            "Total: ${"%.1f".format(total.toDouble() / 1024 / 1024 / 1024)} GB, " +
                "Available: ${"%.1f".format(available.toDouble() / 1024 / 1024 / 1024)} GB"
        } catch (_: Exception) {
            "unavailable"
        }
    }
}
