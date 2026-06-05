package com.natkibe.videoplayerpro.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.controls.PlayerControlsController
import com.natkibe.videoplayerpro.controls.PlaybackMenuController
import com.natkibe.videoplayerpro.controls.PlaybackSpeedSheet
import com.natkibe.videoplayerpro.controls.RepeatModeSheet
import com.natkibe.videoplayerpro.playlist.PlaylistDrawerController
import com.natkibe.videoplayerpro.playlist.PlaylistInteractionListener
import com.natkibe.videoplayerpro.playlist.PlaylistItemAdapter
import com.natkibe.videoplayerpro.playlist.PlaylistItemUiModel
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
    private val playerEngine: PlayerEngine get() = PlayerEngine.get()

    private var uri: String = ""
    private var videoTitle: String = ""
    private var folderName: String = ""
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
    private lateinit var errorActionBar: LinearLayout
    private lateinit var errorRetryButton: Button
    private lateinit var errorSkipButton: Button

    // Bottom action ImageButtons
    private lateinit var playlistButton: ImageButton

    // Playlist components (new package)
    private lateinit var playlistDrawerController: PlaylistDrawerController
    private lateinit var playlistAdapter: PlaylistItemAdapter
    private var playlistItems: List<PlaylistItemUiModel> = emptyList()

    // Controls controller (auto-hide logic)
    private lateinit var controlsController: PlayerControlsController

    // Playback menu and sheet controllers
    private lateinit var playbackMenu: PlaybackMenuController
    private lateinit var speedSheet: PlaybackSpeedSheet
    private lateinit var repeatSheet: RepeatModeSheet

    // New view refs for top bar
    private lateinit var speedLabel: TextView
    private lateinit var musicStatusLabel: TextView
    private lateinit var closeButton: ImageButton

    // State
    private var currentMode: PlayerMode = PlayerMode.FULLSCREEN
    private var isSeeking = false
    private var progressUpdateJob: Job? = null
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

    // Playlist interaction listener shared by adapter and controller
    private val playlistInteractionListener = object : PlaylistInteractionListener {
        override fun onVideoSelected(uri: String) {
            lifecycleScope.launch {
                val entity = withContext(Dispatchers.IO) {
                    appContainer.database.videoDao().videoByUri(uri)
                }
                if (entity != null) {
                    playVideoItem(entity)
                }
            }
        }

        override fun onDrawerOpen() {
            playlistButton.setImageResource(R.drawable.ic_close)
        }

        override fun onDrawerClose() {
            playlistButton.setImageResource(R.drawable.ic_playlist)
        }

        override fun onToggleDrawer() {
            playlistDrawerController.toggleDrawer()
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

        // Bottom action buttons
        playlistButton = findViewById(R.id.playlistButton)

        // New top bar views
        speedLabel = findViewById(R.id.speedLabel)
        musicStatusLabel = findViewById(R.id.musicStatusLabel)
        closeButton = findViewById(R.id.closeButton)

        // Set title
        videoTitleView.text = videoTitle.ifBlank { "Now Playing" }

        // Create error action bar programmatically (retry/skip buttons)
        initErrorActionBar()

        // Initialize PlayerEngine singleton with progress callback
        PlayerEngine.init(this) { videoUri, position, duration ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    progress.save(videoUri.toString(), position, duration)
                }
            }
        }
        playerView.useController = false // We use custom controls

        // Attach PlayerView to engine
        playerEngine.attachFullscreenPlayerView(playerView)

        initControlsController()

        // Tap video to toggle controls, swipe from right edge to open drawer.
        // Use setOnTouchListener so we can forward events to the drawer's edge
        // gesture detector — PlayerView's SurfaceView would consume clicks otherwise.
        playerView.setOnTouchListener { _, event ->
            // Let drawer controller check for edge swipe first
            if (::playlistDrawerController.isInitialized && playlistDrawerController.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            // Tap detection: toggle controls on ACTION_UP
            if (event.action == MotionEvent.ACTION_UP) {
                controlsController.toggleControls()
                if (controlsController.state.controlsVisible) showControls() else hideControls()
            }
            false // don't consume, let PlayerView handle internally
        }

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
        collectJob?.cancel()
        controlsController.destroy()
        playbackMenu.dismiss()
        speedSheet.dismiss()
        repeatSheet.dismiss()
        // Detach view and destroy player through engine
        playerEngine.detachFullscreenPlayerView()
        playerEngine.destroyPlayer()
        super.onDestroy()
    }

    private fun initControlsController() {
        // Initialize controlsController synchronously with defaults first
        // (so click listeners can reference it immediately).
        controlsController = PlayerControlsController(
            autoHideEnabled = true,
            headunitSafeMode = false,
            scope = lifecycleScope
        ) { state ->
            if (state.controlsVisible) showControls() else hideControls()
        }

        // Initialize menu and sheets synchronously
        playbackMenu = PlaybackMenuController(
            context = this@PlayerActivity,
            anchorView = findViewById(R.id.menuButton),
            callbacks = object : PlaybackMenuController.MenuCallbacks {
                override fun onSpeedClicked() {
                    val currentSpeed = playerEngine.state.value.speed
                    speedSheet.show(currentSpeed)
                }
                override fun onRepeatClicked() {
                    val currentRepeat = playerEngine.state.value.repeatMode
                    repeatSheet.show(currentRepeat)
                }
                override fun onAudioOnlyClicked() { toggleAudioOnly() }
                override fun onFloatingClicked() { toggleFloating() }
                override fun onSettingsClicked() {
                    Toast.makeText(this@PlayerActivity, "Settings", Toast.LENGTH_SHORT).show()
                }
                override fun onRefreshClicked() {
                    Toast.makeText(this@PlayerActivity, "Library refreshed", Toast.LENGTH_SHORT).show()
                }
            }
        )

        speedSheet = PlaybackSpeedSheet(this@PlayerActivity) { speed ->
            playerEngine.dispatch(PlaybackCommand.SetSpeed(speed))
            speedLabel.text = "${speed}x"
            Toast.makeText(this@PlayerActivity, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
        }

        repeatSheet = RepeatModeSheet(this@PlayerActivity) { mode ->
            val engineState = playerEngine.state.value
            var current = engineState.repeatMode
            while (current != mode) {
                playerEngine.dispatch(PlaybackCommand.CycleRepeatMode)
                current = (current + 1) % 3
            }
        }

        // Touch listeners on control layers to reset auto-hide timer
        topBar.setOnTouchListener { _, _ ->
            controlsController.onUserInteraction()
            false
        }
        centerControls.setOnTouchListener { _, _ ->
            controlsController.onUserInteraction()
            false
        }
        bottomBar.setOnTouchListener { _, _ ->
            controlsController.onUserInteraction()
            false
        }

        // Now read actual settings and update the controller
        lifecycleScope.launch {
            val settings = appContainer.settingsStore.settings.first()
            controlsController.updateSettings(
                autoHideEnabled = settings.autoHideControls,
                headunitSafeMode = settings.headunitSafeMode
            )
        }
    }

    private fun setupControls() {
        // Center controls
        centerPlayPause.setOnClickListener { togglePlayPause() }
        findViewById<ImageButton>(R.id.prevButton).setOnClickListener { skipPrevious() }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener { skipNext() }
        findViewById<ImageButton>(R.id.seekBack5).setOnClickListener { seekRelative(-5_000) }
        findViewById<ImageButton>(R.id.seekForward15).setOnClickListener { seekRelative(15_000) }

        // Menu button opens playback menu
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener { showMenu() }

        // Close button
        closeButton.setOnClickListener { finish() }

        // Seek bar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dur = playerEngine.state.value.durationMs
                if (fromUser && dur > 0L) {
                    val pos = ((progress.toFloat() / 1000f) * dur).toLong()
                    currentTimeView.text = TimeFormat.duration(pos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                controlsController.onSeekStart()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                val dur = playerEngine.state.value.durationMs
                if (dur > 0L) {
                    val pos = ((seekBar?.progress?.toFloat() ?: 0f) / 1000f * dur).toLong()
                    playerEngine.dispatch(PlaybackCommand.SeekTo(pos))
                }
                controlsController.onSeekEnd()
            }
        })

        // Playlist button
        playlistButton.setOnClickListener { playlistDrawerController.toggleDrawer() }
    }

    private fun setupPlayer() {
        lifecycleScope.launch {
            val settings = appContainer.settingsStore.settings.first()
            val resumePos = if (settings.resumePlayback) progress.resumePosition(uri) else 0L

            // Dispatch Play command through engine — this handles prepare, seek, and play
            playerEngine.dispatch(PlaybackCommand.Play(
                uri = Uri.parse(uri),
                title = videoTitle,
                startPositionMs = resumePos
            ))

            // Apply saved speed and repeat mode
            if (settings.defaultSpeed != 1.0f) {
                playerEngine.dispatch(PlaybackCommand.SetSpeed(settings.defaultSpeed))
            }
            if (settings.defaultRepeatMode != Player.REPEAT_MODE_OFF) {
                // Cycle to reach the desired mode (engine starts at REPEAT_MODE_OFF)
                while (playerEngine.state.value.repeatMode != settings.defaultRepeatMode) {
                    playerEngine.dispatch(PlaybackCommand.CycleRepeatMode)
                }
            }

            // Initialize speed label
            speedLabel.text = "${settings.defaultSpeed}x"

            // Show controls briefly on start via controller
            controlsController.showControls()
        }

        // Observe engine state for errors and playback state changes
        lifecycleScope.launch {
            playerEngine.state.collect { engineState ->
                try {
                    // Handle errors from PlayerEngine
                    if (engineState.error != null) {
                        showErrorUI(engineState.error)
                        // Ensure controls are visible for error display
                        controlsController.showControls()
                    } else {
                        hideErrorUI()
                    }

                    // Update controls controller with playback state
                    controlsController.onPlaybackStateChanged(engineState.isPlaying)

                    // Update speed label
                    speedLabel.text = "${engineState.speed}x"

                    // Update music status label visibility
                    musicStatusLabel.visibility = if (engineState.isAudioOnly) View.VISIBLE else View.GONE
                    if (engineState.isAudioOnly) {
                        speedLabel.visibility = View.GONE
                    } else {
                        speedLabel.visibility = View.VISIBLE
                    }

                    // Update play/pause and duration display
                    updatePlayPauseIcon()
                    val dur = engineState.durationMs
                    if (dur > 0L) {
                        totalTimeView.text = TimeFormat.duration(dur)
                    }
                } catch (e: Exception) {
                    // Never crash on state updates
                }
            }
        }
    }

    // ── Error UI ──────────────────────────────────────────────────────────

    private fun initErrorActionBar() {
        // Programmatically create error action bar with Retry and Skip buttons,
        // because the existing layout does not include them.
        errorActionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            // LayoutParams will be set via FrameLayout below
            visibility = View.GONE
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xCC8B0000.toInt())
        }

        errorRetryButton = Button(this).apply {
            text = "Retry"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2F80ED.toInt())
            setOnClickListener {
                try {
                    PlayerEngine.get().dispatch(PlaybackCommand.Retry)
                    hideErrorUI()
                } catch (e: Exception) {
                    // Dispatch failed, keep UI visible
                }
            }
        }

        errorSkipButton = Button(this).apply {
            text = "Skip"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF555555.toInt())
            setOnClickListener {
                try {
                    PlayerEngine.get().dispatch(PlaybackCommand.SkipNext)
                    hideErrorUI()
                } catch (e: Exception) {
                    // Dispatch failed, keep UI visible
                }
            }
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        ).apply {
            setMargins(8, 0, 8, 0)
        }
        errorRetryButton.layoutParams = buttonParams
        errorSkipButton.layoutParams = buttonParams

        errorActionBar.addView(errorRetryButton)
        errorActionBar.addView(errorSkipButton)

        // Add to the root FrameLayout, positioned below the error text (top-area)
        val rootLayout = (playerView.parent as? android.widget.FrameLayout)
        rootLayout?.let { frame ->
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                // Position below playerErrorText — roughly 48dp margin to sit below the error banner
                topMargin = (48 * resources.displayMetrics.density).toInt()
            }
            frame.addView(errorActionBar, params)
        }
    }

    private fun showErrorUI(errorInfo: PlayerErrorInfo) {
        try {
            playerErrorText.text = errorInfo.userMessage
            playerErrorText.visibility = View.VISIBLE
            errorActionBar.visibility = View.VISIBLE

            // If error is not recoverable, disable/disable retry
            if (!errorInfo.isRecoverable) {
                errorRetryButton.isEnabled = false
                errorRetryButton.alpha = 0.4f
            } else {
                errorRetryButton.isEnabled = true
                errorRetryButton.alpha = 1.0f
            }
        } catch (e: Exception) {
            // Never crash on UI updates
        }
    }

    private fun hideErrorUI() {
        try {
            playerErrorText.visibility = View.GONE
            errorActionBar.visibility = View.GONE
        } catch (e: Exception) {
            // Never crash on UI updates
        }
    }

    // ── Controls visibility ──────────────────────────────────────────────

    private fun showControls() {
        val state = controlsController.state
        updatePlayPauseIcon()

        if (state.headunitSafeMode) {
            // Instant without animation
            controlsOverlay.visibility = View.VISIBLE
            topBar.visibility = View.VISIBLE
            centerControls.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
            return
        }

        // Fade in animation
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 250
            fillAfter = true
        }
        showWithFade(controlsOverlay, fadeIn)
        showWithFade(topBar, fadeIn)
        showWithFade(centerControls, fadeIn)
        showWithFade(bottomBar, fadeIn)
    }

    private fun hideControls() {
        val state = controlsController.state

        if (state.headunitSafeMode) {
            // Instant without animation
            controlsOverlay.visibility = View.GONE
            topBar.visibility = View.GONE
            centerControls.visibility = View.GONE
            bottomBar.visibility = View.GONE
            return
        }

        // Fade out animation
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 250
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    controlsOverlay.visibility = View.GONE
                    topBar.visibility = View.GONE
                    centerControls.visibility = View.GONE
                    bottomBar.visibility = View.GONE
                }
            })
        }
        controlsOverlay.startAnimation(fadeOut)
        topBar.startAnimation(fadeOut)
        centerControls.startAnimation(fadeOut)
        bottomBar.startAnimation(fadeOut)
    }

    private fun showWithFade(view: View, animation: AlphaAnimation) {
        view.visibility = View.VISIBLE
        view.startAnimation(animation)
    }

    // ── Playback controls ────────────────────────────────────────────────

    private fun togglePlayPause() {
        val engineState = playerEngine.state.value
        if (engineState.isPlaying) {
            playerEngine.dispatch(PlaybackCommand.Pause)
        } else {
            playerEngine.dispatch(PlaybackCommand.Resume)
        }
        updatePlayPauseIcon()
        controlsController.onUserInteraction()
    }

    private fun updatePlayPauseIcon() {
        centerPlayPause.setImageResource(
            if (playerEngine.state.value.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun skipPrevious() {
        playerEngine.dispatch(PlaybackCommand.SeekTo(0L))
    }

    private fun skipNext() {
        // Find the current index in the playlist and advance to next item
        val currentIndex = (0 until playlistAdapter.itemCount).indexOfFirst { i ->
            playlistAdapter.getItemAt(i)?.uri == uri
        }
        if (currentIndex >= 0 && currentIndex + 1 < playlistAdapter.itemCount) {
            val nextUri = playlistAdapter.getItemAt(currentIndex + 1)?.uri ?: return
            lifecycleScope.launch {
                val entity = withContext(Dispatchers.IO) {
                    appContainer.database.videoDao().videoByUri(nextUri)
                }
                if (entity != null) playVideoItem(entity) else playByUri(nextUri)
            }
        } else {
            // No next item, restart current
            playerEngine.dispatch(PlaybackCommand.SeekTo(0L))
        }
    }

    private fun seekRelative(ms: Long) {
        val engineState = playerEngine.state.value
        val dur = engineState.durationMs.coerceAtLeast(0L)
        val pos = engineState.positionMs
        val newPos = (pos + ms).coerceIn(0, dur)
        playerEngine.dispatch(PlaybackCommand.SeekTo(newPos))
        // Show controls briefly after seek
        controlsController.onUserInteraction()
    }

    // ── Progress updates ─────────────────────────────────────────────────

    private fun startProgressUpdates() {
        progressUpdateJob = lifecycleScope.launch {
            while (isActive) {
                val engineState = playerEngine.state.value
                if (engineState.isPlaying && !isSeeking) {
                    val pos = engineState.positionMs
                    val dur = engineState.durationMs
                    if (dur > 0L) {
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
        if (uri.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastProgressSaveMs < progressSaveThrottleMs) return
        lastProgressSaveMs = now
        playerEngine.saveProgress()
    }

    // ── Toggle modes ─────────────────────────────────────────────────────

    private fun toggleFloating() {
        if (playerEngine.state.value.isFloating) {
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
        currentMode = PlayerMode.FLOATING_VIDEO
        // If coming from audio-only, reset its status and stop its service
        if (playerEngine.state.value.isAudioOnly) {
            musicStatusLabel.visibility = View.GONE
            stopService(Intent(this, AudioOnlyService::class.java))
        }
        saveProgress()
        playerEngine.dispatch(PlaybackCommand.ToggleFloating)
        appContainer.createFloatingPlayerFeature().startIfAllowed()
    }

    private fun exitFloatingMode() {
        currentMode = PlayerMode.FULLSCREEN
        // Stop the floating service
        stopService(Intent(this, FloatingPlayerService::class.java))
        // Re-attach player to this view via engine
        playerEngine.returnToFullscreen()
    }

    /** Called when FloatingPlayerService stops on its own (user tapped close button). */
    private fun onFloatingServiceStopped() {
        if (playerEngine.state.value.isFloating) {
            currentMode = PlayerMode.FULLSCREEN
            // Re-attach player to this view via engine
            playerEngine.returnToFullscreen()
        }
    }

    private fun toggleAudioOnly() {
        if (playerEngine.state.value.isAudioOnly) {
            // Exit audio-only mode - restore video surface
            currentMode = PlayerMode.FULLSCREEN
            playerEngine.dispatch(PlaybackCommand.ToggleAudioOnly)
            // Stop AudioOnlyService if running
            stopService(Intent(this, AudioOnlyService::class.java))
            // Re-attach video surface
            playerEngine.surfaceRouter.attachToFullscreen()
        } else {
            // If coming from floating mode, stop its service
            if (playerEngine.state.value.isFloating) {
                stopService(Intent(this, FloatingPlayerService::class.java))
            }
            currentMode = PlayerMode.AUDIO_ONLY
            saveProgress()
            playerEngine.dispatch(PlaybackCommand.ToggleAudioOnly)
            // Start AudioOnlyService (surface already detached by ToggleAudioOnly)
            val intent = Intent(this, AudioOnlyService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        }
    }

    private fun showMenu() {
        val engineState = playerEngine.state.value
        val currentSpeed = "${engineState.speed}x"
        val currentRepeat = when (engineState.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat One"
            Player.REPEAT_MODE_ALL -> "Repeat All"
            else -> "Repeat Off"
        }
        playbackMenu.show(
            currentSpeed = currentSpeed,
            currentRepeat = currentRepeat,
            isAudioOnly = engineState.isAudioOnly,
            isFloating = engineState.isFloating
        )
    }

    private fun playFirstVideoInFolder(targetFolder: String) {
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                appContainer.database.videoDao().videosInFolder(targetFolder)
            }
            if (videos.isNotEmpty()) {
                playVideoItem(videos.first())
                // Close drawer if open when switching folders
                if (playlistDrawerController.isOpen()) playlistDrawerController.closeDrawer()
                // Refresh the playlist with the new folder
                collectJob?.cancel()
                collectJob = lifecycleScope.launch {
                    appContainer.database.videoDao().observeVideosInFolder(targetFolder).collect { list ->
                        val uiModels = list.map { entity ->
                            PlaylistItemUiModel(
                                id = entity.uri,
                                uri = entity.uri,
                                title = entity.displayName,
                                durationMs = entity.durationMs,
                                folderName = entity.folderName,
                                isCurrentlyPlaying = entity.uri == uri
                            )
                        }
                        playlistItems = uiModels
                        playlistDrawerController.updateItems(uiModels, false)
                    }
                }
            } else {
                Toast.makeText(this@PlayerActivity, "No videos in $targetFolder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Playlist ─────────────────────────────────────────────────────────

    private fun setupPlaylist() {
        // Create playlist adapter with listener that routes to playVideoItem
        playlistAdapter = PlaylistItemAdapter(
            items = emptyList(),
            showThumbnails = false,
            listener = playlistInteractionListener
        )

        val recycler = findViewById<RecyclerView>(R.id.playlistRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = playlistAdapter

        // Find the dim overlay from the layout
        val dimOverlay: View = findViewById(R.id.playlistDimOverlay)

        // Create the drawer controller
        playlistDrawerController = PlaylistDrawerController(
            drawerView = findViewById(R.id.sidePlaylist),
            recyclerView = recycler,
            dimOverlay = dimOverlay,
            adapter = playlistAdapter,
            listener = playlistInteractionListener
        )

        // Observe the current folder's videos and populate the playlist
        if (folderName.isNotBlank()) {
            collectJob = lifecycleScope.launch {
                appContainer.database.videoDao().observeVideosInFolder(folderName).collect { list ->
                    val uiModels = list.map { entity ->
                        PlaylistItemUiModel(
                            id = entity.uri,
                            uri = entity.uri,
                            title = entity.displayName,
                            durationMs = entity.durationMs,
                            folderName = entity.folderName,
                            isCurrentlyPlaying = entity.uri == uri
                        )
                    }
                    playlistItems = uiModels
                    playlistDrawerController.updateItems(uiModels, false)
                }
            }
        }
    }

    private fun playVideoItem(item: VideoItemEntity) {
        saveProgress()
        uri = item.uri
        videoTitle = item.displayName
        videoTitleView.text = videoTitle
        playerEngine.dispatch(PlaybackCommand.Play(
            uri = Uri.parse(item.uri),
            title = item.displayName
        ))
        // Update playlist highlight to reflect the currently playing item
        playlistItems = playlistItems.map { it.copy(isCurrentlyPlaying = it.uri == item.uri) }
        playlistDrawerController.updateItems(playlistItems, false)
        playlistDrawerController.setSelectedUri(item.uri)
    }

    /**
     * Fallback playback by URI when no VideoItemEntity is available.
     * Used by skipNext when database lookup fails.
     */
    private fun playByUri(videoUri: String) {
        saveProgress()
        uri = videoUri
        videoTitle = "" // title unknown without entity lookup
        videoTitleView.text = "Now Playing"
        playerEngine.dispatch(PlaybackCommand.Play(
            uri = Uri.parse(videoUri),
            title = ""
        ))
        playlistItems = playlistItems.map { it.copy(isCurrentlyPlaying = it.uri == videoUri) }
        playlistDrawerController.updateItems(playlistItems, false)
        playlistDrawerController.setSelectedUri(videoUri)
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
        val engineState = playerEngine.state.value
        // If we were in floating mode and came back, re-attach and stop service
        if (engineState.isFloating) {
            currentMode = PlayerMode.FULLSCREEN
            stopService(Intent(this, FloatingPlayerService::class.java))
            playerEngine.returnToFullscreen()
        }
        // If we were in audio-only mode and came back, re-attach and stop service
        if (engineState.isAudioOnly) {
            currentMode = PlayerMode.FULLSCREEN
            stopService(Intent(this, AudioOnlyService::class.java))
            playerEngine.returnToFullscreen()
        }
    }

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }
}
