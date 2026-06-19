package com.natkibe.videoplayerpro.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.MainActivity
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.audioonly.AudioOnlyController
import com.natkibe.videoplayerpro.controls.PlayerControlsController
import com.natkibe.videoplayerpro.controls.PlaybackMenuController
import com.natkibe.videoplayerpro.controls.PlaybackSpeedSheet
import com.natkibe.videoplayerpro.controls.RepeatMode
import com.natkibe.videoplayerpro.controls.SettingsSheet
import com.natkibe.videoplayerpro.floating.FloatingWindowController
import com.natkibe.videoplayerpro.floating.OverlayPermissionHelper
import com.natkibe.videoplayerpro.playlist.PlaylistDrawerController
import com.natkibe.videoplayerpro.playlist.PlaylistInteractionListener
import com.natkibe.videoplayerpro.playlist.PlaylistItemAdapter
import com.natkibe.videoplayerpro.playlist.PlaylistItemUiModel
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private val resumeRepo get() = appContainer.resumeRepository
    private val thumbnailLoader get() = appContainer.thumbnailLoader

    private lateinit var playerView: PlayerView
    private val playerEngine: PlayerEngine get() = PlayerEngine.get()

    private var uri: String = ""
    private var videoTitle: String = ""
    private var folderName: String = ""
    private var collectJob: Job? = null

    // UI elements
    private lateinit var controlsOverlay: View
    private lateinit var tapCatcher: View
    private lateinit var topBar: View
    private lateinit var centerControls: View
    private lateinit var bottomBar: View
    private lateinit var videoTitleView: TextView
    private lateinit var centerPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeView: TextView
    private lateinit var totalTimeView: TextView
    private lateinit var audioArtworkView: ImageView
    private lateinit var audioArtworkDeck: FrameLayout
    private lateinit var audioArtworkThumb: ImageView
    private lateinit var audioEqualizerRing: ImageView
    private lateinit var audioEqualizerBars: AudioEqualizerView
    private lateinit var playerErrorText: TextView
    private lateinit var errorActionBar: LinearLayout
    private lateinit var errorRetryButton: Button
    private lateinit var errorSkipButton: Button
    private lateinit var errorPlayAsMusicButton: Button

    // Seek preview
    private lateinit var seekPreviewFrame: FrameLayout
    private lateinit var seekPreviewImage: ImageView
    private lateinit var seekPreviewTime: TextView
    private var seekPreviewJob: Job? = null

    // Bottom action ImageButtons
    private lateinit var playlistButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var floatingButton: ImageButton

    // Playlist components (new package)
    private lateinit var playlistDrawerController: PlaylistDrawerController
    private lateinit var playlistAdapter: PlaylistItemAdapter
    private var playlistItems: List<PlaylistItemUiModel> = emptyList()
    private var audioArtworkJob: Job? = null

    // Audio mode animations
    private var discRotateAnimator: ObjectAnimator? = null
    private var ringPulseAnimators: MutableList<ObjectAnimator> = mutableListOf()
    private var equalizerAnimators: MutableList<ObjectAnimator> = mutableListOf()

    // Controls controller (auto-hide logic)
    private lateinit var controlsController: PlayerControlsController

    // Playback menu and sheet controllers
    private lateinit var playbackMenu: PlaybackMenuController
    private lateinit var speedSheet: PlaybackSpeedSheet

    // Audio-only and floating controllers (Milestone 4 rewrite)
    private val audioOnlyController by lazy { AudioOnlyController(this) }
    private val floatingController by lazy { FloatingWindowController(this) }
    private val overlayPermissionHelper by lazy { OverlayPermissionHelper(this) }
    private var settingsSheet: SettingsSheet? = null

    // New view refs for top bar
    private lateinit var speedLabel: TextView
    private lateinit var musicStatusLabel: TextView
    private lateinit var folderNameLabel: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var topAudioOnlyButton: ImageButton

    // Notification
    private var notificationManager: NotificationManager? = null
    private var notificationReceiver: BroadcastReceiver? = null

    // State
    private var currentMode: PlayerMode = PlayerMode.FULLSCREEN
    private var isSeeking = false
    private var progressUpdateJob: Job? = null
    private var lastProgressSaveMs = 0L
    private val progressSaveThrottleMs = 5_000L
    private var ignoreNextPlayerClick = false
    private var floatingLaunchArmed = false
    private var keepFloatingArmedOnServiceStop = false
    // Track when a view was last shown to avoid races where a hide animation
    // completes after a near-immediate show (causing a visible flicker).
    private val viewLastShownMs: MutableMap<View, Long> = mutableMapOf()

    // Broadcast receiver for floating service closed (Milestone 4: uses FloatingWindowService)
    private val floatingClosedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_FLOATING_CLOSED -> {
                    val keepArmed = keepFloatingArmedOnServiceStop
                    keepFloatingArmedOnServiceStop = false
                    onFloatingServiceStopped(disarm = !keepArmed)
                }
                com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_RETURN_FULLSCREEN -> {
                    keepFloatingArmedOnServiceStop = false
                    onFloatingServiceStopped(disarm = true)
                }
                com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_OPEN_FOLDERS -> {
                    onFloatingServiceStopped(disarm = true)
                    startActivity(Intent(this@PlayerActivity, MainActivity::class.java))
                }
                com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_FLOATING_AUDIO_ONLY -> {
                    floatingLaunchArmed = false
                    toggleAudioOnly()
                }
                com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_FLOATING_SPEED -> {
                    speedSheet.show(playerEngine.state.value.speed)
                }
            }
        }
    }

    // Overlay permission launcher (Milestone 4: uses FloatingWindowController)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (overlayPermissionHelper.canDrawOverApps()) {
            armFloatingLaunch()
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
            playlistButton.setColorFilter(Color.parseColor("#FF2F80ED"))
        }

        override fun onDrawerClose() {
            playlistButton.setImageResource(R.drawable.ic_playlist)
            playlistButton.clearColorFilter()
        }

        override fun onToggleDrawer() {
            playlistDrawerController.toggleDrawer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configurePlayerSystemBars()
        setContentView(R.layout.activity_player)

        uri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return finish()
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty()
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME).orEmpty()

        // Initialize views
        playerView = findViewById(R.id.playerView)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        tapCatcher = findViewById(R.id.tapCatcher)
        topBar = findViewById(R.id.topBar)
        centerControls = findViewById(R.id.centerControls)
        bottomBar = findViewById(R.id.bottomBar)
        videoTitleView = findViewById(R.id.videoTitle)
        centerPlayPause = findViewById(R.id.centerPlayPause)
        seekBar = findViewById(R.id.seekBar)
        currentTimeView = findViewById(R.id.currentTime)
        totalTimeView = findViewById(R.id.totalTime)
        audioArtworkView = findViewById(R.id.audioArtworkView)
        audioArtworkDeck = findViewById(R.id.audioArtworkDeck)
        audioArtworkThumb = findViewById(R.id.audioArtworkThumb)
        audioEqualizerRing = findViewById(R.id.audioEqualizerRing)
        audioEqualizerBars = findViewById(R.id.audioEqualizerBars)
        playerErrorText = findViewById(R.id.playerErrorText)

        // Seek preview views
        seekPreviewFrame = findViewById(R.id.seekPreviewFrame)
        seekPreviewImage = findViewById(R.id.seekPreviewImage)
        seekPreviewTime = findViewById(R.id.seekPreviewTime)

        // Bottom action buttons
        playlistButton = findViewById(R.id.playlistButton)
        repeatButton = findViewById(R.id.repeatButton)
        floatingButton = findViewById(R.id.floatingButton)

        // New top bar views
        speedLabel = findViewById(R.id.speedLabel)
        musicStatusLabel = findViewById(R.id.musicStatusLabel)
        musicStatusLabel.contentDescription = "Play as Music active"
        folderNameLabel = findViewById(R.id.folderNameLabel)
        closeButton = findViewById(R.id.closeButton)
        topAudioOnlyButton = findViewById(R.id.topAudioOnlyButton)

        // Set title
        videoTitleView.text = videoTitle.ifBlank { "Now Playing" }
        folderNameLabel.text = folderName.ifBlank { "Current folder" }

        // Long-press on title opens diagnostics
        videoTitleView.setOnLongClickListener {
            startActivity(Intent(this@PlayerActivity, DiagnosticsActivity::class.java))
            true
        }

        // Create error action bar programmatically (retry/skip buttons)
        initErrorActionBar()

        // Apply system bar insets so controls sit inside the safe area,
        // while the video surface remains edge-to-edge behind status/nav bars.
        val playerScreen = findViewById<View>(R.id.playerScreen)
        ViewCompat.setOnApplyWindowInsetsListener(playerScreen) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBarTop = statusBars.top
            val navBarBottom = navBars.bottom
            // Shift topBar below status bar
            topBar.also { v ->
                val lp = v.layoutParams as? android.widget.FrameLayout.LayoutParams
                lp?.topMargin = statusBarTop
                v.layoutParams = lp
            }
            // Shift error banner below status bar
            playerErrorText.also { v ->
                val lp = v.layoutParams as? android.widget.FrameLayout.LayoutParams
                lp?.topMargin = statusBarTop
                v.layoutParams = lp
            }
            // Shift error action bar below status bar + error text
            if (::errorActionBar.isInitialized && errorActionBar.parent != null) {
                val elp = errorActionBar.layoutParams as? android.widget.FrameLayout.LayoutParams
                elp?.topMargin = statusBarTop + (48 * resources.displayMetrics.density).toInt()
                errorActionBar.layoutParams = elp
            }
            // Shift bottom bar above navigation bar
            bottomBar.also { v ->
                val lp = v.layoutParams as? android.widget.FrameLayout.LayoutParams
                lp?.bottomMargin = navBarBottom + (18 * resources.displayMetrics.density).toInt()
                v.layoutParams = lp
            }
            WindowInsetsCompat.CONSUMED
        }

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

        // Tap catcher captures taps reliably (unlike PlayerView which can eat events).
        // When controls are visible, tapCatcher is GONE so taps reach control buttons.
        // When controls are hidden, tapCatcher is VISIBLE and brings them back.
        tapCatcher.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                controlsController.showControls()
            }
            true
        }

        // Tap video to toggle controls (fallback — tapCatcher is primary).
        playerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (ignoreNextPlayerClick) {
                    ignoreNextPlayerClick = false
                } else {
                    controlsController.showControls()
                }
            }
            true
        }

        setupControls()
        setupPlaylist()
        setupPlayer()
        startProgressUpdates()

        // Register broadcast receiver for floating service closed
        val floatingFilter = IntentFilter(com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_FLOATING_CLOSED).apply {
            addAction(com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_RETURN_FULLSCREEN)
            addAction(com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_OPEN_FOLDERS)
            addAction(com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_FLOATING_AUDIO_ONLY)
            addAction(com.natkibe.videoplayerpro.floating.FloatingWindowService.ACTION_FLOATING_SPEED)
        }
        registerReceiver(floatingClosedReceiver, floatingFilter,
            if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0
        )

        // Set up media notification for system media controls visibility
        notificationManager = getSystemService(NotificationManager::class.java)
        ensureMainChannel()
        registerNotificationReceiver()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::playlistDrawerController.isInitialized && playlistDrawerController.onTouchEvent(event)) {
            ignoreNextPlayerClick = true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        try { unregisterReceiver(floatingClosedReceiver) } catch (_: Exception) {}
        removeMediaNotification()
        try { notificationReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        progressUpdateJob?.cancel()
        audioArtworkJob?.cancel()
        cancelAudioAnimations()
        collectJob?.cancel()
        controlsController.destroy()
        playbackMenu.dismiss()
        speedSheet.dismiss()
        settingsSheet?.dismiss()
        // Detach view and destroy player through engine
        playerEngine.detachFullscreenPlayerView()
        if (!playerEngine.state.value.isFloating && !playerEngine.state.value.isAudioOnly) {
            playerEngine.destroyPlayer()
        }
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
                    cycleRepeatMode()
                }
                override fun onAudioOnlyClicked() { toggleAudioOnly() }
                override fun onFloatingClicked() { toggleFloating() }
                override fun onSettingsClicked() {
                    val settings = appContainer.settingsStore.settings
                    lifecycleScope.launch {
                        val current = settings.first()
                        settingsSheet = SettingsSheet(
                            context = this@PlayerActivity,
                            autoHideEnabled = current.autoHideControls,
                            headunitSafeMode = current.headunitSafeMode,
                            onAutoHideChanged = { enabled ->
                                lifecycleScope.launch {
                                    appContainer.settingsStore.setAutoHideControls(enabled)
                                }
                                controlsController.updateSettings(
                                    autoHideEnabled = enabled,
                                    headunitSafeMode = controlsController.state.headunitSafeMode
                                )
                            },
                            onHeadunitChanged = { enabled ->
                                lifecycleScope.launch {
                                    appContainer.settingsStore.setHeadunitSafeMode(enabled)
                                }
                                controlsController.updateSettings(
                                    autoHideEnabled = controlsController.state.autoHideEnabled,
                                    headunitSafeMode = enabled
                                )
                                // Also apply to playlist drawer
                                if (::playlistDrawerController.isInitialized) {
                                    playlistDrawerController.headunitSafeMode = enabled
                                }
                            },
                            onDiagnosticsClicked = {
                                startActivity(Intent(this@PlayerActivity, DiagnosticsActivity::class.java))
                            }
                        )
                        settingsSheet?.show()
                    }
                }
                override fun onRefreshClicked() {
                    lifecycleScope.launch {
                        try {
                            val count = appContainer.videoLibraryRepository.refreshNow()
                            Toast.makeText(
                                this@PlayerActivity,
                                "Library refreshed ($count videos)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@PlayerActivity,
                                "Refresh failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )

        speedSheet = PlaybackSpeedSheet(this@PlayerActivity) { speed ->
            playerEngine.dispatch(PlaybackCommand.SetSpeed(speed))
            speedLabel.text = "${speed}x"
            musicStatusLabel.text = "${speed}x"
            Toast.makeText(this@PlayerActivity, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
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
            // Also configure playlist drawer for safe mode
            if (::playlistDrawerController.isInitialized) {
                playlistDrawerController.headunitSafeMode = settings.headunitSafeMode
            }
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

        // Close button (back arrow)
        closeButton.setOnClickListener { goBack() }

        // System back button: close drawer first if open, otherwise close player
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })

        // Seek bar with preview
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dur = playerEngine.state.value.durationMs
                if (fromUser && dur > 0L) {
                    val pos = ((progress.toFloat() / 1000f) * dur).toLong()
                    currentTimeView.text = TimeFormat.duration(pos)
                    // Update seek preview
                    seekPreviewTime.text = TimeFormat.duration(pos)
                    updateSeekPreview(pos)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                controlsController.onSeekStart()
                // Show seek preview
                seekPreviewFrame.visibility = View.VISIBLE
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                // Hide seek preview
                seekPreviewFrame.visibility = View.GONE
                seekPreviewJob?.cancel()
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
        repeatButton.setOnClickListener { cycleRepeatMode() }
        speedLabel.setOnClickListener {
            speedSheet.show(playerEngine.state.value.speed)
            controlsController.onUserInteraction()
        }
        speedLabel.setOnLongClickListener {
            speedSheet.show(playerEngine.state.value.speed)
            controlsController.onUserInteraction()
            true
        }
        topAudioOnlyButton.setOnClickListener { toggleAudioOnly() }
        floatingButton.setOnClickListener { toggleFloating() }
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
                playerEngine.setRepeatMode(settings.defaultRepeatMode)
            }

            // Wire folder-aware next-video callback
            playerEngine.setOnFolderNextRequested {
                lifecycleScope.launch {
                    skipNext()
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

                    // Update speed label with active state
                    speedLabel.text = "${engineState.speed}x"
                    musicStatusLabel.text = "${engineState.speed}x"
                    if (Math.abs(engineState.speed - 1.0f) > 0.01f) {
                        speedLabel.setTextColor(android.graphics.Color.parseColor("#FF2F80ED"))
                    } else {
                        speedLabel.setTextColor(android.graphics.Color.parseColor("#FFFFFFFF"))
                    }

                    // Update music status label visibility and active state
                    if (engineState.isAudioOnly) {
                        musicStatusLabel.visibility = View.VISIBLE
                        musicStatusLabel.setTextColor(android.graphics.Color.parseColor("#FFFFFFFF"))
                        speedLabel.visibility = View.GONE
                    } else {
                        musicStatusLabel.visibility = View.GONE
                        speedLabel.visibility = View.VISIBLE
                    }
                    updateAudioArtwork(engineState.isAudioOnly, engineState.currentVideoUri?.toString())

                    // Audio mode animations: tie to play/pause state
                    if (engineState.isAudioOnly) {
                        if (engineState.isPlaying) {
                            resumeAudioAnimations()
                        } else {
                            pauseAudioAnimations()
                        }
                    }

                    // Update play/pause and duration display
                    updatePlayPauseIcon()
                    updateRepeatButton(engineState.repeatMode)
                    updateModeButtons(engineState.isAudioOnly, engineState.isFloating)
                    val dur = engineState.durationMs
                    if (dur > 0L) {
                        totalTimeView.text = TimeFormat.duration(dur)
                    }

                    // Keep media notification in sync with playback state:
                    // only show when playing in fullscreen (non-floating, non-audio-only)
                    if (currentMode == PlayerMode.FULLSCREEN && !engineState.isAudioOnly && !engineState.isFloating && engineState.isPlaying) {
                        postMediaNotification()
                    } else {
                        removeMediaNotification()
                    }
                } catch (e: Exception) {
                    // Never crash on state updates
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configurePlayerSystemBars() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.BLACK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = window.decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    // ── Error UI ──────────────────────────────────────────────────────────

    private fun initErrorActionBar() {
        // Programmatically create error action bar with Retry, Skip, and Play as Music buttons,
        // because the existing layout does not include them.
        errorActionBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            // LayoutParams will be set via FrameLayout below
            visibility = View.GONE
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xCC8B0000.toInt())
        }

        // Row 1: Retry + Skip
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        errorRetryButton = Button(this).apply {
            text = "Retry"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2F80ED.toInt())
            minHeight = (64 * resources.displayMetrics.density).toInt()
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
            text = "Skip to Next"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF555555.toInt())
            minHeight = (64 * resources.displayMetrics.density).toInt()
            setOnClickListener {
                try {
                    // Try to skip to next in playlist
                    skipNext()
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
            setMargins(8, 4, 8, 4)
        }
        errorRetryButton.layoutParams = buttonParams
        errorSkipButton.layoutParams = buttonParams

        buttonRow.addView(errorRetryButton)
        buttonRow.addView(errorSkipButton)
        errorActionBar.addView(buttonRow)

        // Row 2: Play as Music
        errorPlayAsMusicButton = Button(this).apply {
            text = "Play as Music (Audio Only)"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2F80ED.toInt())
            minHeight = (64 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 4, 8, 8)
            }
            setOnClickListener {
                try {
                    // Clear error first, then switch to audio-only mode
                    PlayerEngine.get().dispatch(PlaybackCommand.Retry)
                    // Then enter audio-only mode immediately
                    toggleAudioOnly()
                    hideErrorUI()
                } catch (e: Exception) {
                    // Dispatch failed, keep UI visible
                }
            }
        }
        errorActionBar.addView(errorPlayAsMusicButton)

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

        // Hide tap catcher so it doesn't steal clicks from control buttons
        tapCatcher.visibility = View.GONE

        if (state.headunitSafeMode) {
            // Instant without animation
            controlsOverlay.visibility = View.VISIBLE
            topBar.visibility = View.VISIBLE
            centerControls.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
            orderPlayerChrome()
            return
        }

        // Only animate in if currently hidden — otherwise just ensure visible.
        // Setting alpha=0 unconditionally causes a visible flicker on every
        // onUserInteraction() call when controls are already shown.
        val dur = 250L
        fun showView(v: View) {
            val wasVisible = v.visibility == View.VISIBLE
            val currentAlpha = v.alpha.takeIf { it > 0f } ?: 0f
            v.animate().cancel()
            // Record show timestamp
            viewLastShownMs[v] = System.currentTimeMillis()
            v.visibility = View.VISIBLE
            if (!wasVisible) {
                v.alpha = 0f
                v.animate().alpha(1f).setDuration(dur).start()
                return
            }
            if (currentAlpha < 0.95f) {
                v.alpha = currentAlpha
                v.animate().alpha(1f).setDuration(dur).start()
                return
            }
            v.alpha = 1f
        }
        showView(controlsOverlay)
        showView(topBar)
        showView(centerControls)
        showView(bottomBar)
        orderPlayerChrome()
    }

    private fun orderPlayerChrome() {
        if (::audioEqualizerBars.isInitialized && audioEqualizerBars.visibility == View.VISIBLE) {
            audioEqualizerBars.bringToFront()
            audioArtworkDeck.bringToFront()
        }
        centerControls.bringToFront()
        topBar.bringToFront()
        bottomBar.bringToFront()
        if (::errorActionBar.isInitialized && errorActionBar.visibility == View.VISIBLE) {
            playerErrorText.bringToFront()
            errorActionBar.bringToFront()
        }
    }

    private fun hideControls() {
        val state = controlsController.state

        // Show tap catcher so taps are caught to re-show controls
        tapCatcher.visibility = View.VISIBLE
        tapCatcher.bringToFront()

        if (state.headunitSafeMode) {
            // Instant without animation
            controlsOverlay.visibility = View.GONE
            topBar.visibility = View.GONE
            centerControls.visibility = View.GONE
            bottomBar.visibility = View.GONE
            return
        }
        // Use per-view property animations and cancel any running animations
        val dur = 250L
        fun hideView(v: View) {
            v.animate().cancel()
            v.animate().alpha(0f).setDuration(dur).withEndAction {
                // Avoid hiding if the view was shown very recently (race condition)
                val lastShown = viewLastShownMs[v] ?: 0L
                val now = System.currentTimeMillis()
                if (now - lastShown > 150L) {
                    v.visibility = View.GONE
                    v.alpha = 1f // reset for next show
                } else {
                    // A show happened very recently; keep visible and reset alpha.
                    v.visibility = View.VISIBLE
                    v.alpha = 1f
                }
            }.start()
        }
        hideView(controlsOverlay)
        hideView(topBar)
        hideView(centerControls)
        hideView(bottomBar)
    }

    private fun showWithFade(view: View, animation: AlphaAnimation) {
        // Deprecated — kept for compatibility but prefer property animations.
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

    private fun updateRepeatButton(mode: Int = playerEngine.state.value.repeatMode) {
        val selected = mode != Player.REPEAT_MODE_OFF
        val icon = when (mode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            RepeatMode.FOLDER -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat
        }
        val description = when (mode) {
            Player.REPEAT_MODE_ONE -> "Repeat one selected"
            RepeatMode.FOLDER -> "Repeat folder selected"
            else -> "Repeat off"
        }
        repeatButton.setImageResource(icon)
        repeatButton.contentDescription = description
        if (selected) {
            repeatButton.setColorFilter(Color.parseColor("#FF2F80ED"))
        } else {
            repeatButton.clearColorFilter()
        }
    }

    // ── Audio mode animations ──────────────────────────────────────────────

    private fun startAudioAnimations() {
        if (discRotateAnimator != null) return // already running

        // 1. Disc rotation: continuous 360° vinyl spin, ~20s per full rotation
        discRotateAnimator = ObjectAnimator.ofFloat(audioArtworkThumb, "rotation", 0f, 360f).apply {
            duration = 20000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // 2. Subtle orbit breathing so the visualizer feels alive without fading the artwork.
        val deckScaleX = ObjectAnimator.ofFloat(audioEqualizerBars, "scaleX", 0.995f, 1.012f, 0.995f).apply {
            duration = 2200L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        }
        val deckScaleY = ObjectAnimator.ofFloat(audioEqualizerBars, "scaleY", 0.995f, 1.012f, 0.995f).apply {
            duration = 2200L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        }
        ringPulseAnimators.clear()
        ringPulseAnimators.addAll(listOf(deckScaleX, deckScaleY))
        ringPulseAnimators.forEach { it.start() }

        // 3. Keep the invisible compatibility ring in sync for pause/resume bookkeeping.
        val eqRotate = ObjectAnimator.ofFloat(audioEqualizerRing, "rotation", 360f, 0f).apply {
            duration = 30000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        }
        equalizerAnimators.clear()
        equalizerAnimators.add(eqRotate)
        equalizerAnimators.forEach { it.start() }

        // 4. Equalizer bars: vertical bar visualizer
        audioEqualizerBars.startAnimation()
    }

    private fun cancelAudioAnimations() {
        discRotateAnimator?.cancel()
        discRotateAnimator = null
        ringPulseAnimators.forEach { it.cancel() }
        ringPulseAnimators.clear()
        audioEqualizerBars.scaleX = 1f
        audioEqualizerBars.scaleY = 1f
        audioArtworkDeck.alpha = 1f
        equalizerAnimators.forEach { it.cancel() }
        equalizerAnimators.clear()
        audioEqualizerBars.cancelAnimation()
    }

    private fun pauseAudioAnimations() {
        discRotateAnimator?.pause()
        ringPulseAnimators.forEach { it.pause() }
        equalizerAnimators.forEach { it.pause() }
        audioEqualizerBars.pauseAnimation()
    }

    private fun resumeAudioAnimations() {
        val disc = discRotateAnimator
        if (disc == null && ringPulseAnimators.isEmpty() && equalizerAnimators.isEmpty()) {
            startAudioAnimations()
        } else {
            disc?.resume()
            ringPulseAnimators.forEach { it.resume() }
            equalizerAnimators.forEach { it.resume() }
            audioEqualizerBars.resumeAnimation()
        }
    }

    private fun updateAudioArtwork(isAudioOnly: Boolean, videoUri: String?) {
        audioArtworkJob?.cancel()
        if (!isAudioOnly) {
            cancelAudioAnimations()
            audioArtworkView.visibility = View.GONE
            audioArtworkDeck.visibility = View.GONE
            audioEqualizerRing.visibility = View.GONE
            audioEqualizerBars.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            return
        }

        playerView.visibility = View.GONE
        // Fullscreen dim backdrop with blurred video thumbnail
        audioArtworkView.visibility = View.VISIBLE
        // Equalizer ring (outermost, behind deck)
        audioEqualizerRing.visibility = View.VISIBLE
        // Center deck: ring + disc with small thumbnail inside
        audioArtworkDeck.visibility = View.VISIBLE
        // Equalizer bar visualizer between deck and bottom controls
        audioEqualizerBars.visibility = View.VISIBLE
        audioArtworkThumb.setImageResource(R.drawable.ic_music_note)
        orderPlayerChrome()

        val resolvedUri = videoUri ?: uri
        audioArtworkJob = lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = thumbnailLoader.loadThumbnail(resolvedUri, 720, 720)
            withContext(Dispatchers.Main) {
                if (audioArtworkView.visibility != View.VISIBLE) return@withContext
                if (bitmap != null) {
                    // Fullscreen backdrop gets the large blurred thumbnail
                    audioArtworkView.scaleType = ImageView.ScaleType.CENTER_CROP
                    audioArtworkView.setImageBitmap(bitmap)
                    // Small thumbnail inside the disc
                    audioArtworkThumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    audioArtworkThumb.setImageBitmap(bitmap)
                } else {
                    audioArtworkView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    audioArtworkView.setImageResource(R.drawable.ic_music_note)
                    audioArtworkThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    audioArtworkThumb.setImageResource(R.drawable.ic_music_note)
                }
            }
        }
    }

    private fun updateModeButtons(isAudioOnly: Boolean, isFloating: Boolean) {
        topAudioOnlyButton.contentDescription = if (isAudioOnly) "Play as Music selected" else "Play as Music"
        val floatingSelected = isFloating || floatingLaunchArmed
        floatingButton.contentDescription = when {
            isFloating -> "Floating player active"
            floatingLaunchArmed -> "Floating player armed"
            else -> "Floating player"
        }
        if (isAudioOnly) {
            topAudioOnlyButton.setColorFilter(Color.parseColor("#FF2F80ED"))
        } else {
            topAudioOnlyButton.clearColorFilter()
        }
        if (floatingSelected) {
            floatingButton.setColorFilter(Color.parseColor("#FF2F80ED"))
        } else {
            floatingButton.clearColorFilter()
        }
    }

    private fun cycleRepeatMode() {
        playerEngine.dispatch(PlaybackCommand.CycleRepeatMode)
        updateRepeatButton()
        Toast.makeText(this, repeatModeToast(playerEngine.state.value.repeatMode), Toast.LENGTH_SHORT).show()
        controlsController.onUserInteraction()
    }

    private fun repeatModeToast(mode: Int): String = when (mode) {
        Player.REPEAT_MODE_ONE -> "Repeat current video"
        RepeatMode.FOLDER -> "Repeat folder"
        else -> "Repeat off"
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
        } else if (playerEngine.state.value.repeatMode == RepeatMode.FOLDER &&
                   playlistAdapter.itemCount > 0) {
            // Repeat Folder: wrap to the first item in the folder
            val firstUri = playlistAdapter.getItemAt(0)?.uri ?: return
            lifecycleScope.launch {
                val entity = withContext(Dispatchers.IO) {
                    appContainer.database.videoDao().videoByUri(firstUri)
                }
                if (entity != null) playVideoItem(entity) else playByUri(firstUri)
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

    // ── Seek preview thumbnail ──────────────────────────────────────────

    private fun updateSeekPreview(positionMs: Long) {
        seekPreviewJob?.cancel()
        if (uri.isBlank() || positionMs <= 0L) return

        // Try to reuse cached thumbnail first
        val cachedThumb = thumbnailLoader.memoryThumbnail(uri, 128, 80)
        if (cachedThumb != null) {
            seekPreviewImage.setImageBitmap(cachedThumb)
            return
        }

        // Generate a preview thumbnail at the seek position
        seekPreviewJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = thumbnailLoader.seekPreviewThumbnail(uri, positionMs, 128, 80)
                withContext(Dispatchers.Main) {
                    if (seekPreviewFrame.visibility == View.VISIBLE && bitmap != null) {
                        seekPreviewImage.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
                // Silently ignore preview failures
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

    // ── Navigation ───────────────────────────────────────────────────────

    /** Go back: close playlist drawer if open, otherwise close player. */
    private fun goBack() {
        if (::playlistDrawerController.isInitialized && playlistDrawerController.isOpen()) {
            playlistDrawerController.closeDrawer()
        } else {
            floatingLaunchArmed = false
            finish()
        }
    }

    // ── Toggle modes ─────────────────────────────────────────────────────

    private fun toggleFloating() {
        val engineState = playerEngine.state.value
        if (engineState.isFloating) {
            exitFloatingMode(disarm = true)
            return
        }

        if (floatingLaunchArmed) {
            floatingLaunchArmed = false
            updateModeButtons(engineState.isAudioOnly, engineState.isFloating)
            Toast.makeText(this, "Floating disabled", Toast.LENGTH_SHORT).show()
            controlsController.onUserInteraction()
            return
        }

        if (!overlayPermissionHelper.canDrawOverApps()) {
            Toast.makeText(this, "Overlay permission required to float video", Toast.LENGTH_LONG).show()
            val intent = overlayPermissionHelper.permissionIntent()
            if (intent != null) {
                overlayPermissionLauncher.launch(intent)
            }
            return
        }

        armFloatingLaunch()
    }

    private fun armFloatingLaunch() {
        if (playerEngine.state.value.isAudioOnly) {
            audioOnlyController.exit()
            musicStatusLabel.visibility = View.GONE
        }
        floatingLaunchArmed = true
        currentMode = PlayerMode.FULLSCREEN
        playerEngine.returnToFullscreen()
        updateModeButtons(isAudioOnly = false, isFloating = false)
        Toast.makeText(this, "Floating ready — press Home to pop out", Toast.LENGTH_SHORT).show()
        controlsController.onUserInteraction()
    }

    private fun enterFloatingMode() {
        if (!floatingLaunchArmed || playerEngine.state.value.isFloating) return
        currentMode = PlayerMode.FLOATING_VIDEO
        saveProgress()
        if (floatingController.enter()) {
            moveTaskToBack(true)
        }
    }

    private fun exitFloatingMode(disarm: Boolean = true) {
        if (disarm) {
            floatingLaunchArmed = false
        }
        currentMode = PlayerMode.FULLSCREEN
        // Stop both old and new floating services
        stopService(Intent(this, FloatingPlayerService::class.java))
        floatingController.exit()
        updateModeButtons(playerEngine.state.value.isAudioOnly, false)
    }

    /** Called when FloatingWindowService stops on its own (user tapped close button). */
    private fun onFloatingServiceStopped(disarm: Boolean) {
        if (disarm) {
            floatingLaunchArmed = false
        }
        currentMode = PlayerMode.FULLSCREEN
        floatingController.onServiceStopped()
        playerEngine.returnToFullscreen()
        updateModeButtons(playerEngine.state.value.isAudioOnly, false)
    }

    private fun toggleAudioOnly() {
        if (playerEngine.state.value.isAudioOnly) {
            currentMode = PlayerMode.FULLSCREEN
            audioOnlyController.exit()
            updateModeButtons(isAudioOnly = false, isFloating = playerEngine.state.value.isFloating)
            updateAudioArtwork(isAudioOnly = false, videoUri = uri)
            controlsController.showControls()
        } else {
            // If coming from floating mode, exit floating first
            if (playerEngine.state.value.isFloating) {
                exitFloatingMode(disarm = true)
            }
            floatingLaunchArmed = false
            currentMode = PlayerMode.AUDIO_ONLY
            saveProgress()
            audioOnlyController.enter()
            speedLabel.visibility = View.GONE
            musicStatusLabel.visibility = View.VISIBLE
            musicStatusLabel.text = "${playerEngine.state.value.speed}x"
            updateModeButtons(isAudioOnly = true, isFloating = false)
            updateAudioArtwork(isAudioOnly = true, videoUri = uri)
            controlsController.showControls()
        }
    }

    private fun showMenu() {
        val engineState = playerEngine.state.value
        val currentSpeed = "${engineState.speed}x"
        val currentRepeat = repeatMenuLabel(engineState.repeatMode)
        playbackMenu.show(
            currentSpeed = currentSpeed,
            currentRepeat = currentRepeat,
            isAudioOnly = engineState.isAudioOnly,
            isFloating = engineState.isFloating || floatingLaunchArmed
        )
    }

    private fun repeatMenuLabel(mode: Int): String = when (mode) {
        Player.REPEAT_MODE_ONE -> "Repeat One"
        RepeatMode.FOLDER -> "Repeat Folder"
        else -> "Repeat Off"
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
                    val showThumbs = appContainer.settingsStore.settings.first()
                        .let { it.showThumbnails && !it.headunitSafeMode }
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
                    playlistDrawerController.updateItems(uiModels, showThumbs)
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterFloatingMode()
    }

    override fun onPause() {
        saveProgress()
        removeMediaNotification()
        super.onPause()
    }

    override fun onStop() {
        saveProgress()
        removeMediaNotification()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val engineState = playerEngine.state.value
        if (engineState.isFloating) {
            keepFloatingArmedOnServiceStop = true
            exitFloatingMode(disarm = false)
        } else if (engineState.isAudioOnly) {
            floatingLaunchArmed = false
            currentMode = PlayerMode.AUDIO_ONLY
        } else if (!engineState.isAudioOnly) {
            currentMode = PlayerMode.FULLSCREEN
            playerEngine.returnToFullscreen()
        }
        updateModeButtons(
            isAudioOnly = playerEngine.state.value.isAudioOnly,
            isFloating = playerEngine.state.value.isFloating
        )
        // Show/update media notification when in fullscreen video mode and playing
        if (currentMode == PlayerMode.FULLSCREEN && engineState.isPlaying) {
            postMediaNotification()
        }
    }

    // ── Media notification ───────────────────────────────────────────────

    private fun ensureMainChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID_MAIN,
            "VideoPlayer Pro",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media controls for video playback"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun registerNotificationReceiver() {
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_MAIN_PLAY_PAUSE -> togglePlayPause()
                    ACTION_MAIN_SKIP_PREV -> skipPrevious()
                    ACTION_MAIN_SKIP_NEXT -> skipNext()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_MAIN_PLAY_PAUSE)
            addAction(ACTION_MAIN_SKIP_PREV)
            addAction(ACTION_MAIN_SKIP_NEXT)
        }
        registerReceiver(
            notificationReceiver!!,
            filter,
            if (Build.VERSION.SDK_INT >= 33) RECEIVER_NOT_EXPORTED else 0
        )
    }

    private fun postMediaNotification() {
        if (currentMode != PlayerMode.FULLSCREEN) return
        val engine = playerEngine
        val state = engine.state.value
        val isPlaying = state.isPlaying
        val title = state.currentTitle.ifBlank { videoTitle.ifBlank { "VideoPlayer Pro" } }
        val subtitle = if (isPlaying) "Playing" else "Paused"

        // Play/Pause toggle action
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"
        val playPauseAction = NotificationCompat.Action.Builder(
            playPauseIcon,
            playPauseLabel,
            PendingIntent.getBroadcast(
                this, REQUEST_MAIN_PLAY_PAUSE,
                Intent(ACTION_MAIN_PLAY_PAUSE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

        // Skip previous action
        val skipPrevAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_previous,
            "Previous",
            PendingIntent.getBroadcast(
                this, REQUEST_MAIN_SKIP_PREV,
                Intent(ACTION_MAIN_SKIP_PREV),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

        // Skip next action
        val skipNextAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_next,
            "Next",
            PendingIntent.getBroadcast(
                this, REQUEST_MAIN_SKIP_NEXT,
                Intent(ACTION_MAIN_SKIP_NEXT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        ).build()

        // Content intent: return to this activity
        val contentIntent = PendingIntent.getActivity(
            this, REQUEST_MAIN_CONTENT,
            Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_MAIN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(engine.getMediaSession()?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(skipPrevAction)
            .addAction(playPauseAction)
            .addAction(skipNextAction)

        notificationManager?.notify(NOTIFICATION_ID_MAIN, builder.build())
    }

    private fun removeMediaNotification() {
        notificationManager?.cancel(NOTIFICATION_ID_MAIN)
    }

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_FOLDER_NAME = "folder_name"

        private const val CHANNEL_ID_MAIN = "videoplayer_pro_main"
        private const val NOTIFICATION_ID_MAIN = 2001

        private const val ACTION_MAIN_PLAY_PAUSE = "com.natkibe.videoplayerpro.action.MAIN_PLAY_PAUSE"
        private const val ACTION_MAIN_SKIP_PREV = "com.natkibe.videoplayerpro.action.MAIN_SKIP_PREV"
        private const val ACTION_MAIN_SKIP_NEXT = "com.natkibe.videoplayerpro.action.MAIN_SKIP_NEXT"

        private const val REQUEST_MAIN_PLAY_PAUSE = 10
        private const val REQUEST_MAIN_SKIP_PREV = 11
        private const val REQUEST_MAIN_SKIP_NEXT = 12
        private const val REQUEST_MAIN_CONTENT = 13
    }
}
