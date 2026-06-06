package com.natkibe.videoplayerpro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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

        findViewById<ImageButton>(R.id.videoTab).setOnClickListener { showFolders() }
        findViewById<ImageButton>(R.id.recentTab).setOnClickListener { showRecent() }
        findViewById<ImageButton>(R.id.storageTab).setOnClickListener { showStorage() }
        findViewById<ImageButton>(R.id.settingsTab).setOnClickListener { showSettings() }
        findViewById<ImageButton>(R.id.refreshButton).setOnClickListener {
            lifecycleScope.launch {
                if (settingsStore.settings.first().headunitSafeMode) {
                    status.text = "Refresh disabled in Headunit Safe Mode"
                    return@launch
                }
                libraryFeature.refreshInBackground()
                status.text = "Refreshing videos in background..."
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
        settingsPanel.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        status.text = "Video folders. Cached Room data appears first; MediaStore refresh runs in background."
        val adapter = FolderAdapter(emptyList()) { folder -> showVideos(folder.folderName) }
        recycler.adapter = adapter
        collectJob = lifecycleScope.launch {
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
        settingsPanel.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        status.text = if (isFavorites) "Favorite videos" else "Folder: $folderName"
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val showThumbs = prefs.showThumbnails && !prefs.headunitSafeMode
            val adapter = VideoAdapter(
                items = emptyList(),
                showThumbnails = showThumbs,
                onClick = { openVideo(it) },
                onLongPress = { toggleFavorite(it) },
                thumbnailBitmapProvider = if (showThumbs) { uri ->
                    // Non-blocking check of memory cache only; full async load in background
                    val key = appContainer.thumbnailDiskCache.keyFor(uri)
                    @Suppress("UNUSED_EXPRESSION")
                    appContainer.thumbnailMemoryPolicy.get(key)
                    null // Return null for now; real load happens async via ThumbnailLoader
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
        settingsPanel.visibility = View.GONE
        settingsTextInfo.visibility = View.GONE
        recycler.visibility = View.VISIBLE
        status.text = "Recently watched videos"
        collectJob = lifecycleScope.launch {
            val prefs = settingsFeature.observe().first()
            val showThumbs = prefs.showThumbnails && !prefs.headunitSafeMode
            val adapter = VideoAdapter(
                items = emptyList(),
                showThumbnails = showThumbs,
                onClick = { openVideo(it) },
                onLongPress = { toggleFavorite(it) },
                thumbnailBitmapProvider = if (showThumbs) { uri ->
                    val key = appContainer.thumbnailDiskCache.keyFor(uri)
                    appContainer.thumbnailMemoryPolicy.get(key)
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
            findViewById<Button>(R.id.btnAccentColor).text = s.accentColorName
            findViewById<Button>(R.id.btnDefaultSpeed).text = "${s.defaultSpeed}x"
            findViewById<Button>(R.id.btnRepeatMode).text = repeatModeLabel(s.defaultRepeatMode)
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
            lifecycleScope.launch { settingsStore.setShowThumbnails(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchFloating).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setFloating(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchFloatingControls).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setFloatingControlsOnly(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchPlaylist).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setPlaylist(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchDarkTheme).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setDarkTheme(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchResume).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setResume(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchAutoplayNext).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setAutoplayNext(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchSafeMode).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch {
                settingsStore.setHeadunitSafeMode(checked)
                if (checked) {
                    settingsStore.setShowThumbnails(false)
                    settingsStore.setFloating(false)
                    settingsStore.setUseFancyBlur(false)
                    settingsStore.setAutoHideControls(false)
                }
                showSettings()
            }
        }
        findViewById<SwitchCompat>(R.id.switchAutoHideControls).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setAutoHideControls(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchFancyBlur).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setUseFancyBlur(checked) }
        }
        findViewById<SwitchCompat>(R.id.switchCompactPlaylist).setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { settingsStore.setCompactPlaylist(checked) }
        }

        // Accent color cycle
        val accents = listOf("Blue", "Teal", "Orange", "Purple", "Red", "Green")
        findViewById<Button>(R.id.btnAccentColor).setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.settings.first().accentColorName
                val next = accents[(accents.indexOf(current) + 1) % accents.size]
                settingsStore.setAccent(next)
                findViewById<Button>(R.id.btnAccentColor).text = next
            }
        }

        // Default speed cycle
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        findViewById<Button>(R.id.btnDefaultSpeed).setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.settings.first().defaultSpeed
                val next = speeds[(speeds.indexOf(current) + 1) % speeds.size]
                settingsStore.setDefaultSpeed(next)
                findViewById<Button>(R.id.btnDefaultSpeed).text = "${next}x"
            }
        }

        // Default repeat mode cycle
        val repeatModes = listOf(0, 1, 2, 3) // off, one, all, folder
        findViewById<Button>(R.id.btnRepeatMode).setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.settings.first().defaultRepeatMode
                val next = repeatModes[(repeatModes.indexOf(current) + 1) % repeatModes.size]
                settingsStore.setRepeatMode(next)
                findViewById<Button>(R.id.btnRepeatMode).text = repeatModeLabel(next)
            }
        }

        // Diagnostics
        findViewById<Button>(R.id.openDiagnostics).setOnClickListener {
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
