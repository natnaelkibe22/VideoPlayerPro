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
import com.natkibe.playerpro.data.VideoItemEntity
import com.natkibe.playerpro.media.VideoLibraryRepository
import com.natkibe.playerpro.model.StorageTab
import com.natkibe.playerpro.player.PlayerActivity
import com.natkibe.playerpro.settings.SettingsStore
import com.natkibe.playerpro.ui.FolderAdapter
import com.natkibe.playerpro.ui.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val repository by lazy { VideoLibraryRepository(this) }
    private val settings by lazy { SettingsStore(this) }
    private lateinit var recycler: RecyclerView
    private lateinit var status: TextView
    private var collectJob: Job? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showFolders()
            repository.refreshInBackground()
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
        findViewById<Button>(R.id.refreshButton).setOnClickListener { repository.refreshInBackground(); status.text = "Refreshing videos in background..." }

        if (PermissionService.hasVideoPermission(this)) {
            showFolders()
            repository.refreshInBackground()
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
            repository.folders().collect { adapter.submit(it) }
        }
    }

    private fun showVideos(folderName: String) {
        collectJob?.cancel()
        status.text = "Folder: $folderName"
        collectJob = lifecycleScope.launch {
            val prefs = settings.settings.first()
            val adapter = VideoAdapter(emptyList(), prefs.showThumbnails) { openVideo(it) }
            recycler.adapter = adapter
            repository.videosInFolder(folderName).collect { adapter.submit(it, prefs.showThumbnails) }
        }
    }

    private fun showRecent() {
        collectJob?.cancel()
        status.text = "Recently watched videos"
        collectJob = lifecycleScope.launch {
            val prefs = settings.settings.first()
            val adapter = VideoAdapter(emptyList(), prefs.showThumbnails) { openVideo(it) }
            recycler.adapter = adapter
            repository.recentVideos().collect { adapter.submit(it, prefs.showThumbnails) }
        }
    }

    private fun showStorage() {
        collectJob?.cancel()
        status.text = StorageTab.entries.joinToString("  •  ") { it.label } + "\nUSB/SD depends on what Android MediaStore exposes on the headunit."
        recycler.adapter = null
    }

    private fun showSettings() {
        collectJob?.cancel()
        recycler.adapter = null
        lifecycleScope.launch {
            val s = settings.settings.first()
            status.text = buildString {
                appendLine("Settings are DataStore-backed and toggle-based to stay lightweight.")
                appendLine("Thumbnails: ${s.showThumbnails} (default false)")
                appendLine("Floating Player: ${s.enableFloatingPlayer} (default false)")
                appendLine("Playlist While Watching: ${s.showPlaylistWhileWatching}")
                appendLine("Resume Playback: ${s.resumePlayback}")
                appendLine("Autoplay Next: ${s.autoPlayNext}")
                appendLine("Accent: ${s.accentColorName}")
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
