package com.natkibe.videoplayerpro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.core.PermissionService
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.model.StorageTab
import com.natkibe.videoplayerpro.player.DiagnosticsActivity
import com.natkibe.videoplayerpro.player.PlayerActivity
import com.natkibe.videoplayerpro.ui.FolderAdapter
import com.natkibe.videoplayerpro.ui.VideoAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections

class MainActivity : AppCompatActivity() {
    private val appContainer by lazy { VideoPlayerProAppContainer(this) }

    private val libraryFeature get() = appContainer.libraryFeature
    private val settingsFeature get() = appContainer.settingsFeature
    private val settingsStore get() = appContainer.settingsStore
    private val favoriteRepository get() = appContainer.favoriteRepository
    private val resumeRepository get() = appContainer.resumeRepository

    private lateinit var recycler: RecyclerView
    private lateinit var status: TextView
    private lateinit var settingsPanel: ScrollView
    private lateinit var settingsTextInfo: TextView
    private var collectJob: Job? = null
    private var thumbnailGeneration = 0
    private var bindingSettings = false
    private val inFlightThumbnails = Collections.synchronizedSet(mutableSetOf<String>())

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
        settingsPanel = findViewById(R.id.settingsPanel)
        settingsTextInfo = findViewById(R.id.settingsTextInfo)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.videoTab).setOnClickListener { showFolders() }
        findViewById<View>(R.id.recentTab).setOnClickListener { showRecent() }
        findViewById<View>(R.id.storageTab).setOnClickListener { showStorage() }
        findViewById<View>(R.id.settingsTab).setOnClickListener { showSettings() }
        findViewById<View>(R.id.refreshButton).setOnClickListener {
            lifecycleScope.launch {
                status.text = "Refreshing videos in background..."
                try {
                    val count = libraryFeature.refreshNow()
                    status.text = if (count == 0) {
                        "No video folders found. Tap Refresh to scan USB/SD cards."
                    } else {
                        "Found $count videos. Folders updated."
                    }
                } catch (e: Exception) {
                    status.text = "Refresh failed: ${e.message ?: "MediaStore scan error"}"
                }
            }
        }

        // Settings: 13 interactive toggles
        setupSettingsToggles()

        if (PermissionService.hasVideoPermission(this)) {
            showFolders()
            lifecycleScope.launch {
                if (!settingsStore.settings.first().headunitSafeMode) {
                    libraryFeature.refreshInBackground()
                }
            }
        } else {
            permissionLauncher.launch(PermissionService.videoPermission())
        }
    }

    private fun showFolders() {
        collectJob?.cancel()
        val generation = nextThumbnailGeneration()
        settingsPanel.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        status.text = "Video folders. Cached Room data appears first; MediaStore refresh runs in background."
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val showThumbs = !prefs.headunitSafeMode
            lateinit var adapter: FolderAdapter
            adapter = FolderAdapter(
                items = emptyList(),
                onClick = { folder -> showVideos(folder.folderName) },
                showThumbnails = showThumbs,
                thumbnailBitmapProvider = if (showThumbs) { uri ->
                    appContainer.thumbnailLoader.memoryThumbnail(uri)
                } else null,
                onThumbnailMissing = if (showThumbs) { uri ->
                    loadThumbnailForVisibleFolder(uri, adapter, generation)
                } else null
            )
            recycler.adapter = adapter
            libraryFeature.folders().collect { folders ->
                adapter.submit(folders)
                if (folders.isEmpty()) {
                    status.text = "No video folders found. Tap Refresh to scan USB/SD cards."
                }
            }
        }
    }

    private fun showVideos(folderName: String, isFavorites: Boolean = false) {
        collectJob?.cancel()
        val generation = nextThumbnailGeneration()
        settingsPanel.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        status.text = if (isFavorites) "Favorite videos" else "Folder: $folderName"
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val showThumbs = prefs.showThumbnails && !prefs.headunitSafeMode
            lateinit var adapter: VideoAdapter
            adapter = VideoAdapter(
                items = emptyList(),
                showThumbnails = showThumbs,
                onClick = { openVideo(it) },
                onLongPress = { toggleFavorite(it) },
                thumbnailBitmapProvider = if (showThumbs) { uri ->
                    appContainer.thumbnailLoader.memoryThumbnail(uri)
                } else null,
                onThumbnailMissing = if (showThumbs) { uri ->
                    loadThumbnailForVisibleRow(uri, adapter, generation)
                } else null
            )
            recycler.adapter = adapter
            val flow = if (isFavorites) {
                favoriteRepository.observeFavorites()
            } else {
                libraryFeature.videosInFolder(folderName)
            }
            flow.collect { videos ->
                adapter.submit(videos, showThumbs)
                if (videos.isEmpty()) {
                    status.text = if (isFavorites) {
                        "No favorite videos. Long-press a video to add it to favorites."
                    } else {
                        "Folder \"$folderName\" is empty. Tap Refresh to scan for new videos."
                    }
                }
            }
        }
    }

    private fun showRecent() {
        collectJob?.cancel()
        val generation = nextThumbnailGeneration()
        settingsPanel.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        status.text = "Recently watched videos"
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val showThumbs = prefs.showThumbnails && !prefs.headunitSafeMode
            lateinit var adapter: VideoAdapter
            adapter = VideoAdapter(
                items = emptyList(),
                showThumbnails = showThumbs,
                onClick = { openVideo(it) },
                onLongPress = { toggleFavorite(it) },
                thumbnailBitmapProvider = if (showThumbs) { uri ->
                    appContainer.thumbnailLoader.memoryThumbnail(uri)
                } else null,
                onThumbnailMissing = if (showThumbs) { uri ->
                    loadThumbnailForVisibleRow(uri, adapter, generation)
                } else null
            )
            recycler.adapter = adapter
            libraryFeature.recentVideos().collect { videos ->
                adapter.submit(videos, showThumbs)
                if (videos.isEmpty()) {
                    status.text = "No recently watched videos. Play a video to see it here."
                }
            }
        }
    }

    private fun toggleFavorite(video: VideoItemEntity) {
        lifecycleScope.launch {
            val isNowFav = favoriteRepository.toggle(video.uri)
            status.text = if (isNowFav) "★ Added to favorites" else "☆ Removed from favorites"
        }
    }

    private fun nextThumbnailGeneration(): Int {
        inFlightThumbnails.clear()
        thumbnailGeneration += 1
        return thumbnailGeneration
    }

    private fun loadThumbnailForVisibleRow(uri: String, adapter: VideoAdapter, generation: Int) {
        if (generation != thumbnailGeneration || !inFlightThumbnails.add(uri)) return
        lifecycleScope.launch {
            try {
                val bitmap = appContainer.thumbnailLoader.loadThumbnail(uri)
                if (bitmap != null && generation == thumbnailGeneration && recycler.adapter === adapter) {
                    adapter.notifyUriChanged(uri)
                }
            } finally {
                inFlightThumbnails.remove(uri)
            }
        }
    }

    private fun loadThumbnailForVisibleFolder(uri: String, adapter: FolderAdapter, generation: Int) {
        if (generation != thumbnailGeneration || !inFlightThumbnails.add(uri)) return
        lifecycleScope.launch {
            try {
                val bitmap = appContainer.thumbnailLoader.loadThumbnail(uri)
                if (bitmap != null && generation == thumbnailGeneration && recycler.adapter === adapter) {
                    adapter.notifyDataSetChanged()
                }
            } finally {
                inFlightThumbnails.remove(uri)
            }
        }
    }

    private fun showStorage() {
        collectJob?.cancel()
        status.text = "Storage categories — tap a category to browse"
        recycler.adapter = null
        collectJob = lifecycleScope.launch {
            val allVideos = libraryFeature.allVideos().first()
            if (allVideos.isEmpty()) {
                status.text = "No videos found. Tap Refresh to scan USB/SD cards."
                return@launch
            }
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
        recycler.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        settingsPanel.visibility = View.VISIBLE
        status.text = "Settings — toggle each option below"

        lifecycleScope.launch {
            val s = settingsStore.settings.first()
            bindSettingsState(s)
        }
    }

    private fun bindSettingsState(s: com.natkibe.videoplayerpro.settings.VideoPlayerProSettings) {
        bindingSettings = true
        try {
            findViewById<SwitchCompat>(R.id.switchThumbnails).isChecked = s.showThumbnails
            findViewById<SwitchCompat>(R.id.switchFloating).isChecked = s.enableFloatingPlayer
            findViewById<SwitchCompat>(R.id.switchFloatingControls).isChecked = s.enableFloatingControlsOnly
            findViewById<SwitchCompat>(R.id.switchPlaylist).isChecked = s.showPlaylistWhileWatching
            findViewById<SwitchCompat>(R.id.switchDarkTheme).isChecked = s.darkTheme
            findViewById<SwitchCompat>(R.id.switchResume).isChecked = s.resumePlayback
            findViewById<SwitchCompat>(R.id.switchAutoplayNext).isChecked = s.autoPlayNext
            findViewById<SwitchCompat>(R.id.switchSafeMode).isChecked = s.headunitSafeMode
            findViewById<SwitchCompat>(R.id.switchAutoHideControls).isChecked = s.autoHideControls
            findViewById<SwitchCompat>(R.id.switchFancyBlur).isChecked = s.useFancyBlur
            findViewById<SwitchCompat>(R.id.switchCompactPlaylist).isChecked = s.compactPlaylistRows
            findViewById<TextView>(R.id.btnAccentColor).text = s.accentColorName
            findViewById<TextView>(R.id.btnDefaultSpeed).text = "${s.defaultSpeed}x"
            findViewById<TextView>(R.id.btnRepeatMode).text = repeatModeLabel(s.defaultRepeatMode)
        } finally {
            bindingSettings = false
        }
    }

    private fun repeatModeLabel(mode: Int): String = when (mode) {
        1 -> "One"
        2 -> "All"
        3 -> "Folder"
        else -> "Off"
    }

    private fun setupSettingsToggles() {
        // Boolean switches (10 total)
        findViewById<SwitchCompat>(R.id.switchThumbnails).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setShowThumbnails(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchFloating).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setFloating(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchFloatingControls).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setFloatingControlsOnly(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchPlaylist).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setPlaylist(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchDarkTheme).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setDarkTheme(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchResume).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setResume(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchAutoplayNext).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setAutoplayNext(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchSafeMode).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                settingsStore.setHeadunitSafeMode(checked)
                if (checked) {
                    settingsStore.setShowThumbnails(false)
                    settingsStore.setFloating(false)
                    settingsStore.setUseFancyBlur(false)
                    settingsStore.setAutoHideControls(false)
                    val updated = settingsStore.settings.first()
                    bindSettingsState(updated)
                }
            }
        }
        findViewById<SwitchCompat>(R.id.switchAutoHideControls).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setAutoHideControls(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchFancyBlur).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setUseFancyBlur(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchCompactPlaylist).setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            lifecycleScope.launch { settingsStore.setCompactPlaylist(checked) }
        }

        // Accent color cycle
        val accents = listOf("Blue", "Teal", "Orange", "Purple", "Red", "Green")
        findViewById<TextView>(R.id.btnAccentColor).setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.settings.first().accentColorName
                val next = accents[(accents.indexOf(current) + 1) % accents.size]
                settingsStore.setAccent(next)
                findViewById<TextView>(R.id.btnAccentColor).text = next
            }
        }

        // Default speed cycle
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        findViewById<TextView>(R.id.btnDefaultSpeed).setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.settings.first().defaultSpeed
                val next = speeds[(speeds.indexOf(current) + 1) % speeds.size]
                settingsStore.setDefaultSpeed(next)
                findViewById<TextView>(R.id.btnDefaultSpeed).text = "${next}x"
            }
        }

        // Default repeat mode cycle
        val repeatModes = listOf(0, 1, 3) // off, one, folder
        findViewById<TextView>(R.id.btnRepeatMode).setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.settings.first().defaultRepeatMode
                val next = repeatModes[(repeatModes.indexOf(current) + 1) % repeatModes.size]
                settingsStore.setRepeatMode(next)
                findViewById<TextView>(R.id.btnRepeatMode).text = repeatModeLabel(next)
            }
        }

        // Diagnostics
        findViewById<TextView>(R.id.openDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
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
