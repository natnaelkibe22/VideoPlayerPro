package com.natkibe.videoplayerpro.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import com.natkibe.videoplayerpro.ui.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gemini-style video-first player with:
 * - Fullscreen video background
 * - Tap-to-show overlay controls
 * - Auto-hide controls after 3 seconds
 * - Center play/pause, prev/next, 5s/15s seek
 * - Seek bar with current/total time
 * - Top title bar with menu
 * - Right playlist drawer (25-30% width)
 * - Toggle modes: Float, Play as Music, Playlist
 * - Single shared ExoPlayer
 * - Icon-based buttons with blue accent
 */
class PlayerActivity : AppCompatActivity() {
    private val appContainer by lazy { VideoPlayerProAppContainer(this) }
    private val progress get() = appContainer.progressService

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var controls: PlayerControlService

    private var uri: String = ""
    private var videoTitle: String = ""
    private var folderName: String = ""
    private var playlistAdapter: VideoAdapter? = null
    private var collectJob: Job? = null

    // UI elements
    private lateinit var controlsOverlay: View
    private lateinit var topBar: View
    private lateinit var centerControls: View
    private lateinit var bottomBar: View
    private lateinit var videoTitleView: TextView
    private lateinit var centerPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeView: TextView
    private lateinit var totalTimeView: TextView
    private lateinit var playerErrorText: TextView
    private lateinit var playlistPanel: View
    private lateinit var playlistCurrentLabel: TextView

    // Bottom action ImageButtons
    private lateinit var repeatButton: ImageButton
    private lateinit var speedButton: ImageButton
    private lateinit var playlistButton: ImageButton
    private lateinit var audioOnlyButton: ImageButton
    private lateinit var floatingButton: ImageButton

    // State
    private var isPlaying = false
    private var controlsVisible = false
    private var currentMode: PlayerMode = PlayerMode.FULLSCREEN
    private var isFloatingMode = false
    private var isAudioOnlyMode = false
    private var isPlaylistOpen = false
    private var isSeeking = false
    private var progressUpdateJob: Job? = null
    private var autoHideJob: Job? = null
    private var lastProgressSaveMs = 0L
    private val progressSaveThrottleMs = 5_000L

    // Broadcast receiver for floating service closed
    private val floatingClosedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingPlayerService.ACTION_FLOATING_CLOSED) {
                onFloatingServiceStopped()
            }
        }
    }

    // Overlay permission launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // After returning from settings, check if permission was granted
        val floating = appContainer.createFloatingPlayerFeature()
        if (floating.canDrawOverApps()) {
            enterFloatingMode()
        } else {
            Toast.makeText(this, "Overlay permission is required for floating player", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        uri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return finish()
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME).orEmpty()

        // Initialize views
        playerView = findViewById(R.id.playerView)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        topBar = findViewById(R.id.topBar)
        centerControls = findViewById(R.id.centerControls)
        bottomBar = findViewById(R.id.bottomBar)
        videoTitleView = findViewById(R.id.videoTitle)
        centerPlayPause = findViewById(R.id.centerPlayPause)
        seekBar = findViewById(R.id.seekBar)
        currentTimeView = findViewById(R.id.currentTime)
        totalTimeView = findViewById(R.id.totalTime)
        playerErrorText = findViewById(R.id.playerErrorText)
        playlistPanel = findViewById(R.id.sidePlaylist)
        playlistCurrentLabel = findViewById(R.id.playlistCurrentLabel)

        // Bottom action buttons
        repeatButton = findViewById(R.id.repeatButton)
        speedButton = findViewById(R.id.speedButton)
        playlistButton = findViewById(R.id.playlistButton)
        audioOnlyButton = findViewById(R.id.audioOnlyButton)
        floatingButton = findViewById(R.id.floatingButton)

        // Set title
        videoTitleView.text = videoTitle.ifBlank { "Now Playing" }

        // Initialize the shared player instance (thread-safe singleton)
        player = PlayerHolder.get(this)
        controls = PlayerControlService(player)
        playerView.player = player
        playerView.useController = false // We use custom controls

        // Tap video to toggle controls
        playerView.setOnClickListener { toggleControls() }

        setupControls()
        setupPlaylist()
        setupPlayer()
        startProgressUpdates()

        // Register broadcast receiver for floating service closed
        registerReceiver(floatingClosedReceiver, IntentFilter(FloatingPlayerService.ACTION_FLOATING_CLOSED),
            if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0
        )
    }

    override fun onDestroy() {
        try { unregisterReceiver(floatingClosedReceiver) } catch (_: Exception) {}
        progressUpdateJob?.cancel()
        autoHideJob?.cancel()
        collectJob?.cancel()
        // Save progress off the main thread
        if (::player.isInitialized && uri.isNotBlank()) {
            val pos = player.currentPosition
            val dur = player.duration
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    progress.save(uri, pos, dur)
                }
            }
        }
        playerView.player = null
        super.onDestroy()
    }

    private fun setupControls() {
        // Center controls
        centerPlayPause.setOnClickListener { togglePlayPause() }
        findViewById<ImageButton>(R.id.prevButton).setOnClickListener { skipPrevious() }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener { skipNext() }
        findViewById<ImageButton>(R.id.seekBack5).setOnClickListener { seekRelative(-5_000) }
        findViewById<ImageButton>(R.id.seekForward15).setOnClickListener { seekRelative(15_000) }

        // Menu button
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener { showMenu() }

        // Seek bar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player.duration > 0) {
                    val pos = (progress / 1000f * player.duration).toLong()
                    currentTimeView.text = TimeFormat.duration(pos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isSeeking = true; cancelAutoHide() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                if (player.duration > 0) {
                    val pos = (seekBar?.progress?.toFloat()?.div(1000f)?.times(player.duration)?.toLong() ?: 0)
                    player.seekTo(pos)
                }
                scheduleAutoHide()
            }
        })

        // Bottom action buttons
        repeatButton.setOnClickListener {
            val mode = controls.cycleRepeatMode()
            updateRepeatIcon(mode)
        }
        speedButton.setOnClickListener {
            val speed = controls.cycleSpeed()
            showSpeedToast(speed)
        }
        playlistButton.setOnClickListener { togglePlaylist() }
        audioOnlyButton.setOnClickListener { toggleAudioOnly() }
        floatingButton.setOnClickListener { toggleFloating() }
    }

    private fun setupPlayer() {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        player.prepare()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updatePlayPauseIcon()
                when (state) {
                    Player.STATE_READY -> {
                        playerErrorText.visibility = View.GONE
                        totalTimeView.text = TimeFormat.duration(player.duration)
                    }
                    Player.STATE_ENDED -> {
                        centerPlayPause.setImageResource(R.drawable.ic_replay_5)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerActivity.isPlaying = isPlaying
                updatePlayPauseIcon()
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorMsg = buildString {
                    append("Cannot play this video on this headunit.")
                    when {
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                            append(" USB/SD card may have been removed or is unreadable.")
                        }
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> {
                            append(" Unsupported codec, 4K/HEVC limit, or corrupt file.")
                        }
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                            append(" Codec initialization failed — format not supported on this device.")
                        }
                        else -> {
                            append(" Unsupported codec, 4K/HEVC limit, slow USB, or corrupt file.")
                        }
                    }
                }
                playerErrorText.text = errorMsg
                playerErrorText.visibility = View.VISIBLE
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (error == null) {
                    playerErrorText.visibility = View.GONE
                }
            }
        })

        lifecycleScope.launch {
            val settings = appContainer.settingsStore.settings.first()
            player.repeatMode = settings.defaultRepeatMode
            controls.initSpeed(settings.defaultSpeed)
            val resume = if (settings.resumePlayback) progress.resumePosition(uri) else 0L
            if (resume > 0L) player.seekTo(resume)

            // Initialize button icons from saved settings
            updateRepeatIcon(settings.defaultRepeatMode)

            // Show controls briefly on start, then auto-hide
            showControls()
            player.play()
        }
    }

    // ── Controls visibility ──────────────────────────────────────────────

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        controlsOverlay.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE
        centerControls.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        updatePlayPauseIcon()
        scheduleAutoHide()
    }

    private fun hideControls() {
        controlsVisible = false
        controlsOverlay.visibility = View.GONE
        topBar.visibility = View.GONE
        centerControls.visibility = View.GONE
        bottomBar.visibility = View.GONE
        cancelAutoHide()
    }

    private fun scheduleAutoHide() {
        cancelAutoHide()
        autoHideJob = lifecycleScope.launch {
            delay(3_000) // Auto-hide after 3 seconds
            if (!isSeeking && isPlaying) {
                hideControls()
            }
        }
    }

    private fun cancelAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = null
    }

    // ── Playback controls ────────────────────────────────────────────────

    private fun togglePlayPause() {
        if (player.playWhenReady) {
            player.pause()
        } else {
            player.play()
        }
        updatePlayPauseIcon()
        scheduleAutoHide()
    }

    private fun updatePlayPauseIcon() {
        centerPlayPause.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun skipPrevious() {
        player.seekTo(0)
    }

    private fun skipNext() {
        // If we have a playlist, play next item
        val currentIndex = playlistAdapter?.let { adapter ->
            (0 until adapter.itemCount).indexOfFirst { i ->
                adapter.getItemAt(i)?.uri == uri
            }
        } ?: -1
        if (currentIndex >= 0 && currentIndex + 1 < (playlistAdapter?.itemCount ?: 0)) {
            playlistAdapter?.getItemAt(currentIndex + 1)?.let { playVideoItem(it) }
        } else {
            // No next item, restart current
            player.seekTo(0)
        }
    }

    private fun seekRelative(ms: Long) {
        val newPos = (player.currentPosition + ms).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(newPos)
        // Show controls briefly after seek
        if (!controlsVisible) showControls() else scheduleAutoHide()
    }

    private fun showSpeedToast(speed: Float) {
        Toast.makeText(this, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
        scheduleAutoHide()
    }

    private fun updateRepeatIcon(mode: Int) {
        repeatButton.setImageResource(when (mode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat
        })
    }

    // ── Progress updates ─────────────────────────────────────────────────

    private fun startProgressUpdates() {
        progressUpdateJob = lifecycleScope.launch {
            while (isActive) {
                if (::player.isInitialized && player.isPlaying && !isSeeking) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0) {
                        seekBar.progress = ((pos.toFloat() / dur) * 1000f).toInt().coerceIn(0, 1000)
                    }
                    currentTimeView.text = TimeFormat.duration(pos)
                    totalTimeView.text = TimeFormat.duration(dur)
                }
                delay(250) // Update 4 times per second
            }
        }
    }

    // ── Progress save with throttling ────────────────────────────────────

    private fun saveProgress() {
        if (!::player.isInitialized || uri.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastProgressSaveMs < progressSaveThrottleMs) return
        lastProgressSaveMs = now
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                progress.save(uri, player.currentPosition, player.duration)
            }
        }
    }

    // ── Toggle modes ─────────────────────────────────────────────────────

    private fun toggleFloating() {
        if (isFloatingMode) {
            exitFloatingMode()
        } else {
            val floating = appContainer.createFloatingPlayerFeature()
            if (!floating.canDrawOverApps()) {
                // Request overlay permission
                Toast.makeText(this, "Overlay permission required to float video", Toast.LENGTH_LONG).show()
                if (Build.VERSION.SDK_INT >= 23) {
                    val intent = floating.overlayPermissionIntent()
                    if (intent != null) {
                        overlayPermissionLauncher.launch(intent)
                    }
                }
                return
            }
            enterFloatingMode()
        }
    }

    private fun enterFloatingMode() {
        isFloatingMode = true
        currentMode = PlayerMode.FLOATING_VIDEO
        floatingButton.setImageResource(R.drawable.ic_close)
        saveProgress()
        playerView.player = null
        player.clearVideoSurface()
        appContainer.createFloatingPlayerFeature().startIfAllowed()
    }

    private fun exitFloatingMode() {
        isFloatingMode = false
        currentMode = PlayerMode.FULLSCREEN
        floatingButton.setImageResource(R.drawable.ic_float)
        // Stop the floating service
        stopService(Intent(this, FloatingPlayerService::class.java))
        // Re-attach player to this view
        playerView.player = player
        player.play()
    }

    /** Called when FloatingPlayerService stops on its own (user tapped close button). */
    private fun onFloatingServiceStopped() {
        if (isFloatingMode) {
            isFloatingMode = false
            currentMode = PlayerMode.FULLSCREEN
            floatingButton.setImageResource(R.drawable.ic_float)
            // Re-attach player to this view
            playerView.player = player
            player.play()
        }
    }

    private fun toggleAudioOnly() {
        if (isAudioOnlyMode) {
            // Exit audio-only mode - restore video surface
            isAudioOnlyMode = false
            currentMode = PlayerMode.FULLSCREEN
            audioOnlyButton.setImageResource(R.drawable.ic_music_note)
            playerView.player = player
            player.play()
            // Stop AudioOnlyService if running
            stopService(Intent(this, AudioOnlyService::class.java))
        } else {
            isAudioOnlyMode = true
            currentMode = PlayerMode.AUDIO_ONLY
            audioOnlyButton.setImageResource(R.drawable.ic_close)
            saveProgress()
            playerView.player = null
            player.clearVideoSurface()
            appContainer.createPlayAsMusicFeature(player).detachVideoAndContinueAudio()
        }
    }

    private fun togglePlaylist() {
        isPlaylistOpen = !isPlaylistOpen
        currentMode = if (isPlaylistOpen) PlayerMode.PLAYLIST_DRAWER else PlayerMode.FULLSCREEN
        playlistPanel.visibility = if (isPlaylistOpen) View.VISIBLE else View.GONE
        playlistButton.setImageResource(if (isPlaylistOpen) R.drawable.ic_close else R.drawable.ic_playlist)
        if (isPlaylistOpen) scheduleAutoHide() else cancelAutoHide()
    }

    private fun showMenu() {
        // Show a popup listing all folders for quick folder switching
        lifecycleScope.launch {
            try {
                val folders = withContext(Dispatchers.IO) {
                    appContainer.database.videoDao().observeFolders().first()
                }
                val folderNames = folders.map { "${it.folderName} (${it.videoCount})" }

                if (folderNames.isEmpty()) {
                    Toast.makeText(this@PlayerActivity, "No folders found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val anchor = findViewById<ImageButton>(R.id.menuButton)
                val listView = ListView(this@PlayerActivity)
                listView.adapter = ArrayAdapter(
                    this@PlayerActivity,
                    android.R.layout.simple_list_item_1,
                    folderNames
                )
                listView.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
                    val selected = folderNames[position].substringBeforeLast(" (")
                    playFirstVideoInFolder(selected)
                }

                val popup = PopupWindow(
                    listView,
                    500,
                    600,
                    true
                )
                popup.showAsDropDown(anchor, -200, 0)
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Error loading folders: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playFirstVideoInFolder(targetFolder: String) {
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                appContainer.database.videoDao().videosInFolder(targetFolder)
            }
            if (videos.isNotEmpty()) {
                playVideoItem(videos.first())
                if (isPlaylistOpen) togglePlaylist()
                // Refresh the playlist with the new folder
                collectJob?.cancel()
                collectJob = lifecycleScope.launch {
                    appContainer.database.videoDao().observeVideosInFolder(targetFolder).collect { list ->
                        playlistAdapter?.submit(list, false)
                    }
                }
            } else {
                Toast.makeText(this@PlayerActivity, "No videos in $targetFolder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Playlist ─────────────────────────────────────────────────────────

    private fun setupPlaylist() {
        val recycler = findViewById<RecyclerView>(R.id.playlistRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        playlistAdapter = VideoAdapter(emptyList(), showThumbnails = false) { item ->
            playVideoItem(item)
        }
        recycler.adapter = playlistAdapter

        if (folderName.isNotBlank()) {
            collectJob = lifecycleScope.launch {
                appContainer.database.videoDao().observeVideosInFolder(folderName).collect { videos ->
                    playlistAdapter?.submit(videos, false)
                }
            }
        }
    }

    private fun playVideoItem(item: com.natkibe.videoplayerpro.data.VideoItemEntity) {
        saveProgress()
        uri = item.uri
        videoTitle = item.displayName
        videoTitleView.text = videoTitle
        player.stop()
        player.setMediaItem(MediaItem.fromUri(Uri.parse(item.uri)))
        player.prepare()
        player.play()
        // Highlight current in playlist
        playlistCurrentLabel.visibility = View.VISIBLE
        playlistCurrentLabel.text = "Now: ${item.displayName}"
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onPause() {
        saveProgress()
        super.onPause()
    }

    override fun onStop() {
        saveProgress()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // If we were in floating mode and came back, re-attach
        if (isFloatingMode && ::player.isInitialized) {
            isFloatingMode = false
            currentMode = PlayerMode.FULLSCREEN
            floatingButton.setImageResource(R.drawable.ic_float)
            playerView.player = player
        }
        // If we were in audio-only mode and came back, re-attach
        if (isAudioOnlyMode && ::player.isInitialized) {
            isAudioOnlyMode = false
            currentMode = PlayerMode.FULLSCREEN
            audioOnlyButton.setImageResource(R.drawable.ic_music_note)
            playerView.player = player
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }
}
