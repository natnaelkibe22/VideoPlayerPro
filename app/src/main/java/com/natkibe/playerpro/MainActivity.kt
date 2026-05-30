package com.natkibe.playerpro

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.playerpro.core.PermissionService
import com.natkibe.playerpro.core.StorageClassifier
import com.natkibe.playerpro.core.contracts.PlayerProAppContainer
import com.natkibe.playerpro.data.VideoItemEntity
import com.natkibe.playerpro.model.StorageTab
import com.natkibe.playerpro.player.PlayerActivity
import com.natkibe.playerpro.ui.FolderAdapter
import com.natkibe.playerpro.ui.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val appContainer by lazy { PlayerProAppContainer(this) }

    // Use feature contracts instead of directly accessing repositories/settings.
    // This keeps activities decoupled from implementation details.
    private val libraryFeature get() = appContainer.libraryFeature
    private val settingsFeature get() = appContainer.settingsFeature

    private lateinit var recycler: RecyclerView
    private lateinit var status: TextView
    private var collectJob: Job? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showFolders()
            libraryFeature.refreshInBackground()
        } else {
            status.text = "Video permission is required to show folders."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        recycler = findViewById(R.id.recycler)
        status = findViewById(R.id.statusText)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.videoTab).setOnClickListener { showFolders() }
        findViewById<Button>(R.id.recentTab).setOnClickListener { showRecent() }
        findViewById<Button>(R.id.storageTab).setOnClickListener { showStorage() }
        findViewById<Button>(R.id.settingsTab).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { libraryFeature.refreshInBackground(); status.text = "Refreshing videos in background..." }

        if (PermissionService.hasVideoPermission(this)) {
            showFolders()
            libraryFeature.refreshInBackground()
        } else {
            permissionLauncher.launch(PermissionService.videoPermission())
        }
    }

    private fun showFolders() {
        collectJob?.cancel()
        status.text = "Video folders. Cached Room data appears first; MediaStore refresh runs in background."
        val adapter = FolderAdapter(emptyList()) { folder -> showVideos(folder.folderName) }
        recycler.adapter = adapter
        collectJob = lifecycleScope.launch {
            libraryFeature.folders().collect { adapter.submit(it) }
        }
    }

    private fun showVideos(folderName: String) {
        collectJob?.cancel()
        status.text = "Folder: $folderName"
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val adapter = VideoAdapter(emptyList(), prefs.showThumbnails) { openVideo(it) }
            recycler.adapter = adapter
            libraryFeature.videosInFolder(folderName).collect { adapter.submit(it, prefs.showThumbnails) }
        }
    }

    private fun showRecent() {
        collectJob?.cancel()
        status.text = "Recently watched videos"
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val adapter = VideoAdapter(emptyList(), prefs.showThumbnails) { openVideo(it) }
            recycler.adapter = adapter
            libraryFeature.recentVideos().collect { adapter.submit(it, prefs.showThumbnails) }
        }
    }

    private fun showStorage() {
        collectJob?.cancel()
        status.text = "Storage categories — tap a category to browse"
        recycler.adapter = null
        collectJob = lifecycleScope.launch {
            val allVideos = libraryFeature.allVideos().first()
            val categories = StorageTab.entries.map { tab ->
                val count = when (tab) {
                    StorageTab.ALL -> allVideos.size
                    else -> allVideos.count { it.storageRoot == tab.label }
                }
                "${tab.label} ($count)"
            }
            status.text = buildString {
                appendLine(categories.joinToString("  •  "))
                appendLine("USB/SD depends on what Android MediaStore exposes on the headunit.")
            }
        }
    }

    private fun showSettings() {
        collectJob?.cancel()
        recycler.adapter = null
        lifecycleScope.launch {
            val s = settingsFeature.observe().first()
            status.text = buildString {
                appendLine("Settings are DataStore-backed and toggle-based to stay lightweight.")
                appendLine("────────────────────────────")
                appendLine("Thumbnails:     ${s.showThumbnails} (default false)")
                appendLine("Floating Player: ${s.enableFloatingPlayer} (default false)")
                appendLine("Playlist:       ${s.showPlaylistWhileWatching} (default true)")
                appendLine("Resume:         ${s.resumePlayback} (default true)")
                appendLine("Autoplay Next:  ${s.autoPlayNext} (default false)")
                appendLine("Dark Theme:     ${s.darkTheme} (default true)")
                appendLine("Accent Color:   ${s.accentColorName} (default Blue)")
                appendLine("Repeat Mode:    ${s.defaultRepeatMode} (0=off)")
                appendLine("Default Speed:  ${s.defaultSpeed}x")
            }
        }
    }

    private fun openVideo(video: VideoItemEntity) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_VIDEO_URI, video.uri)
                .putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.displayName)
                .putExtra(PlayerActivity.EXTRA_FOLDER_NAME, video.folderName)
        )
    }
}
