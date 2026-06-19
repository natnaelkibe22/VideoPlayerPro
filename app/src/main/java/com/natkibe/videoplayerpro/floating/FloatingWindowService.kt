package com.natkibe.videoplayerpro.floating

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.core.TimeFormat
import com.natkibe.videoplayerpro.audioonly.AudioOnlyController
import com.natkibe.videoplayerpro.data.AppDatabase
import com.natkibe.videoplayerpro.data.VideoItemEntity
import com.natkibe.videoplayerpro.thumbnail.ThumbnailLoader
import com.natkibe.videoplayerpro.player.PlaybackCommand
import com.natkibe.videoplayerpro.player.PlayerActivity
import com.natkibe.videoplayerpro.player.PlayerEngine
import com.natkibe.videoplayerpro.ui.FloatingVideoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that shows a floating video overlay.
 *
 * Lifecycle:
 * - [onCreate] creates the notification, then inflates and shows the overlay.
 * - [onStartCommand] handles ACTION_CLOSE to stop itself.
 * - [onDestroy] removes the overlay, stops foreground, sends close broadcast.
 *
 * Surface routing: attaches PlayerView to the shared [PlayerEngine] via
 * [FloatingResizeController] for drag/resize/corner snap.
 */
class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var resizeController: FloatingResizeController? = null
    private val thumbnailLoader by lazy { ThumbnailLoader(applicationContext) }
    private var floatingFolderAdapter: FloatingVideoAdapter? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eqAnimators = mutableListOf<ValueAnimator>()
    private var autoHideJob: Job? = null
    private var floatingControlsVisible = true
    private var panelOpen = false
    private val accentColor = Color.parseColor("#FF2F80ED")

    companion object {
        const val ACTION_CLOSE = "com.natkibe.videoplayerpro.action.CLOSE_FLOATING"
        const val ACTION_FLOATING_CLOSED = "com.natkibe.videoplayerpro.action.FLOATING_CLOSED"
        const val ACTION_RETURN_FULLSCREEN = "com.natkibe.videoplayerpro.action.RETURN_FULLSCREEN"
        const val ACTION_OPEN_FOLDERS = "com.natkibe.videoplayerpro.action.OPEN_FOLDERS"
        const val ACTION_FLOATING_AUDIO_ONLY = "com.natkibe.videoplayerpro.action.FLOATING_AUDIO_ONLY"
        const val ACTION_FLOATING_SPEED = "com.natkibe.videoplayerpro.action.FLOATING_SPEED"
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(1002, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Detach player from floating view before removing overlay
        overlay?.findViewById<PlayerView>(R.id.floatingPlayerView)?.player = null

        // Unregister floating view from engine
        if (PlayerEngine.isInitialized()) {
            val engine = PlayerEngine.get()
            engine.detachFloatingPlayerView()
            if (!engine.state.value.isAudioOnly) {
                engine.returnToFullscreen()
            }
        }

        stopEqualizerAnimation()
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        params = null
        resizeController = null
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Broadcast normal floating close only when audio-only did not intentionally take over.
        if (!PlayerEngine.isInitialized() || !PlayerEngine.get().state.value.isAudioOnly) {
            sendBroadcast(Intent(ACTION_FLOATING_CLOSED))
        }

        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlay != null) return

        // Guard: never show overlay without permission
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = LayoutInflater.from(this).inflate(R.layout.floating_player, null)

        // Attach PlayerView to shared PlayerEngine
        val pv = overlay?.findViewById<PlayerView>(R.id.floatingPlayerView)
        if (PlayerEngine.isInitialized() && pv != null) {
            val engine = PlayerEngine.get()
            engine.attachFloatingPlayerView(pv, pv.parent as android.view.ViewGroup)
        }

        params = WindowManager.LayoutParams(
            640,  // default width
            360,  // default height
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 80
        }

        // Close button
        overlay?.findViewById<ImageButton>(R.id.floatCloseButton)?.setOnClickListener {
            stopSelf()
        }
        overlay?.findViewById<ImageButton>(R.id.floatFullscreenButton)?.setOnClickListener {
            if (PlayerEngine.isInitialized()) {
                PlayerEngine.get().returnToFullscreen()
            }
            sendBroadcast(Intent(ACTION_RETURN_FULLSCREEN))
            bringPlayerToForeground()
            stopSelf()
        }
        setupTransportControls(overlay!!)
        setupFolderPanel(overlay!!)
        overlay?.findViewById<ImageButton>(R.id.floatMusicButton)?.setOnClickListener {
            enterAudioOnlyFromFloating()
        }
        setupSpeedPanel(overlay!!)
        setupProgressBar(overlay!!)
        observePlayerState(overlay!!)


        // Setup audio mode overlay (hidden by default)
        setupFloatingAudioMode(overlay!!)

        // Setup auto-hide: tap the root (outside controls) to toggle visibility
        setupFloatingAutoHide(overlay!!)

        // Attach drag & resize via the controller
        val root = overlay!!
        resizeController = FloatingResizeController(
            windowManager = windowManager!!,
            getParams = { params },
            updateLayout = { v, p ->
                windowManager?.updateViewLayout(v, p)
                updateResponsiveControls(v, p)
            }
        )
        resizeController?.attachDrag(root.findViewById(R.id.floatDragHandle), root)
        resizeController?.attachResize(root.findViewById(R.id.floatResizeHandle), root)

        windowManager?.addView(overlay, params)
        updateResponsiveControls(root, params)
    }

    private fun setupTransportControls(root: View) {
        root.findViewById<ImageButton>(R.id.floatPlayPauseButton).setOnClickListener {
            if (!PlayerEngine.isInitialized()) return@setOnClickListener
            val engine = PlayerEngine.get()
            if (engine.state.value.isPlaying) {
                engine.dispatch(PlaybackCommand.Pause)
            } else {
                engine.dispatch(PlaybackCommand.Resume)
            }
            resetFloatingAutoHide(root)
        }
        root.findViewById<ImageButton>(R.id.floatPreviousButton).setOnClickListener {
            playAdjacentVideo(step = -1)
            resetFloatingAutoHide(root)
        }
        root.findViewById<ImageButton>(R.id.floatNextButton).setOnClickListener {
            playAdjacentVideo(step = 1)
            resetFloatingAutoHide(root)
        }
    }

    private fun setupProgressBar(root: View) {
        val currentTimeView = root.findViewById<TextView>(R.id.floatingCurrentTime)
        val totalTimeView = root.findViewById<TextView>(R.id.floatingTotalTime)
        val seekBar = root.findViewById<SeekBar>(R.id.floatingSeekBar)

        fun refreshProgress() {
            if (!PlayerEngine.isInitialized()) return
            val state = PlayerEngine.get().state.value
            val duration = state.durationMs.coerceAtLeast(0L)
            val position = state.positionMs.coerceAtLeast(0L)
            currentTimeView.text = TimeFormat.duration(position)
            totalTimeView.text = TimeFormat.duration(duration)
            if (!seekBar.isPressed) {
                seekBar.progress = if (duration > 0L) {
                    ((position.toFloat() / duration.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
                } else {
                    0
                }
            }
        }

        seekBar.max = 1000
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || !PlayerEngine.isInitialized()) return
                val state = PlayerEngine.get().state.value
                val duration = state.durationMs.coerceAtLeast(0L)
                if (duration <= 0L) return
                val position = ((progress.toFloat() / 1000f) * duration).toLong()
                currentTimeView.text = TimeFormat.duration(position)
                PlayerEngine.get().dispatch(PlaybackCommand.SeekTo(position))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                autoHideJob?.cancel()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (floatingControlsVisible) scheduleFloatingAutoHide(overlay!!)
            }
        })

        refreshProgress()
        serviceScope.launch {
            PlayerEngine.get().state.collect {
                refreshProgress()
            }
        }
    }

    private fun setupFolderPanel(root: View) {
        val panel = root.findViewById<RecyclerView>(R.id.floatingFolderPanel).apply {
            layoutManager = LinearLayoutManager(this@FloatingWindowService)
        }
        root.findViewById<ImageButton>(R.id.floatFoldersButton).setOnClickListener {
            if (panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
                panelOpen = false
                if (floatingControlsVisible) scheduleFloatingAutoHide(root)
            } else {
                panelOpen = true
                autoHideJob?.cancel()
                serviceScope.launch {
                    populateFolderPanel(root)
                    panel.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupSpeedPanel(root: View) {
        val panel = root.findViewById<LinearLayout>(R.id.floatingSpeedPanel)
        val speedValue = root.findViewById<TextView>(R.id.floatingSpeedValue)
        val speedSlider = root.findViewById<SeekBar>(R.id.floatingSpeedSlider)
        val speedButton = root.findViewById<ImageButton>(R.id.floatSpeedButton)

        fun formatSpeed(speed: Float): String = String.format("%.1fx", speed.coerceIn(0f, 2f))

        fun refreshSelection() {
            val currentSpeed = if (PlayerEngine.isInitialized()) {
                PlayerEngine.get().state.value.speed
            } else {
                1.0f
            }
            speedValue.text = formatSpeed(currentSpeed)
            if (!speedSlider.isPressed) {
                speedSlider.progress = (currentSpeed.coerceIn(0f, 2f) * 100f).toInt()
            }
            updateSpeedActiveState(root, currentSpeed)
        }

        refreshSelection()

        // Toggle speed panel when user taps the speed button
        speedButton.setOnClickListener {
            if (panel.visibility == View.VISIBLE) {
                panel.visibility = View.GONE
                panelOpen = false
                if (floatingControlsVisible) scheduleFloatingAutoHide(root)
            } else {
                panelOpen = true
                autoHideJob?.cancel()
                // Close folder panel if open to avoid overlap
                root.findViewById<RecyclerView>(R.id.floatingFolderPanel)?.visibility = View.GONE
                panel.visibility = View.VISIBLE
            }
        }

        speedSlider.max = 200
        speedSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress / 100f
                speedValue.text = formatSpeed(speed)
                if (fromUser && PlayerEngine.isInitialized()) {
                    PlayerEngine.get().dispatch(PlaybackCommand.SetSpeed(speed))
                }
                updateSpeedActiveState(root, speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                autoHideJob?.cancel()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (floatingControlsVisible) scheduleFloatingAutoHide(root)
            }
        })
        refreshSelection()
    }

    private fun refreshSpeedTextDisplay() {
        // Speed button icon tint is handled by updateSpeedActiveState
    }

    private fun updateSpeedActiveState(root: View, speed: Float) {
        val speedButton = root.findViewById<ImageButton>(R.id.floatSpeedButton) ?: return
        val isActive = (speed - 1.0f).let { it > 0.01f || it < -0.01f }
        if (isActive) {
            speedButton.setColorFilter(PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN))
        } else {
            speedButton.clearColorFilter()
        }
    }

    private fun setupFloatingAudioMode(root: View) {
        // Audio ring and eq bars are initially hidden, shown/hidden by updateFloatingAudioMode
    }

    // ── Floating auto-hide controls ──────────────────────────────────────

    private fun setupFloatingAutoHide(root: View) {
        // Tap on the PlayerView (video surface) toggles controls.
        // This avoids conflicts with control buttons and folder panel items.
        val playerView = root.findViewById<PlayerView>(R.id.floatingPlayerView)
        playerView?.setOnClickListener {
            toggleFloatingControls(root)
        }
    }

    private fun toggleFloatingControls(root: View) {
        floatingControlsVisible = !floatingControlsVisible
        if (floatingControlsVisible) {
            showFloatingControls(root)
            scheduleFloatingAutoHide(root)
        } else {
            hideFloatingControls(root)
        }
    }

    private fun showFloatingControls(root: View) {
        root.findViewById<LinearLayout>(R.id.floatingControlBar)?.visibility = View.VISIBLE
        root.findViewById<LinearLayout>(R.id.floatTransportBar)?.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.floatingTitle)?.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.floatingSubtitle)?.visibility = View.VISIBLE
    }

    private fun hideFloatingControls(root: View) {
        panelOpen = false
        root.findViewById<LinearLayout>(R.id.floatingControlBar)?.visibility = View.GONE
        root.findViewById<LinearLayout>(R.id.floatTransportBar)?.visibility = View.GONE
        root.findViewById<TextView>(R.id.floatingTitle)?.visibility = View.GONE
        root.findViewById<TextView>(R.id.floatingSubtitle)?.visibility = View.GONE
        root.findViewById<LinearLayout>(R.id.floatingSpeedPanel)?.visibility = View.GONE
        root.findViewById<RecyclerView>(R.id.floatingFolderPanel)?.visibility = View.GONE
    }

    private fun scheduleFloatingAutoHide(root: View) {
        if (panelOpen) return // don't auto-hide while a panel is open
        autoHideJob?.cancel()
        autoHideJob = serviceScope.launch {
            delay(4000L)
            if (floatingControlsVisible && !panelOpen) {
                floatingControlsVisible = false
                hideFloatingControls(root)
            }
        }
    }

    private fun resetFloatingAutoHide(root: View) {
        if (floatingControlsVisible) {
            scheduleFloatingAutoHide(root)
        }
    }

    private fun startEqualizerAnimation(root: View) {
        stopEqualizerAnimation()

        val eqBars = root.findViewById<LinearLayout>(R.id.floatingAudioEqBars)
        val ring = root.findViewById<View>(R.id.floatingAudioRing)
        if (eqBars == null && ring == null) return

        // Ring pulse: alpha oscillates between 0.35 and 0.75
        val ringAnim = ValueAnimator.ofFloat(0.35f, 0.75f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                ring?.alpha = a.animatedValue as Float
            }
            start()
        }
        eqAnimators.add(ringAnim)

        // Equalizer bars: 4 bars, each with different duration for organic feel
        val barIds = arrayOf(R.id.eqBar1, R.id.eqBar2, R.id.eqBar3, R.id.eqBar4)
        val barDurations = longArrayOf(420L, 580L, 510L, 460L)
        val density = resources.displayMetrics.density
        val barMinPx = (4f * density).toInt()  // 4dp minimum
        val barMaxPx = (18f * density).toInt() // 18dp maximum

        for (i in barIds.indices) {
            val bar = root.findViewById<View>(barIds[i]) ?: continue
            val anim = ValueAnimator.ofInt(barMinPx, barMaxPx).apply {
                duration = barDurations[i]
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { a ->
                    val h = a.animatedValue as Int
                    bar.layoutParams = bar.layoutParams.also { it.height = h }
                    bar.requestLayout()
                }
                start()
            }
            eqAnimators.add(anim)
        }
    }

    private fun stopEqualizerAnimation() {
        eqAnimators.forEach { it.cancel() }
        eqAnimators.clear()
    }

    private fun updateFloatingAudioMode(root: View, isAudioOnly: Boolean, isPlaying: Boolean, videoUri: String?) {
        val ring = root.findViewById<View>(R.id.floatingAudioRing)
        val eqBars = root.findViewById<LinearLayout>(R.id.floatingAudioEqBars)
        val musicButton = root.findViewById<ImageButton>(R.id.floatMusicButton)
        val playerView = root.findViewById<PlayerView>(R.id.floatingPlayerView)

        if (isAudioOnly) {
            musicButton.setColorFilter(PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN))
            // Dim video surface to make audio mode visually distinct
            playerView?.alpha = 0.25f
            // Show ring and eq bars
            ring?.visibility = View.VISIBLE
            eqBars?.visibility = View.VISIBLE
            // Start/stop animation based on play state
            if (isPlaying) {
                startEqualizerAnimation(root)
            } else {
                stopEqualizerAnimation()
            }
        } else {
            musicButton.clearColorFilter()
            // Restore video surface
            playerView?.alpha = 1.0f
            // Hide ring and eq bars
            ring?.visibility = View.GONE
            eqBars?.visibility = View.GONE
            // Stop all animations
            stopEqualizerAnimation()
        }
    }

    private fun observePlayerState(root: View) {
        if (!PlayerEngine.isInitialized()) return
        val titleView = root.findViewById<TextView>(R.id.floatingTitle)
        val subtitleView = root.findViewById<TextView>(R.id.floatingSubtitle)
        val playPause = root.findViewById<ImageButton>(R.id.floatPlayPauseButton)
        serviceScope.launch {
            PlayerEngine.get().state.collect { state ->
                titleView.text = state.currentTitle.ifBlank { "Now Playing" }
                subtitleView.text = withContext(Dispatchers.IO) {
                    val currentUri = state.currentVideoUri?.toString() ?: return@withContext "Floating mode"
                    val current = AppDatabase.get(applicationContext).videoDao().videoByUri(currentUri)
                    current?.folderName?.takeIf { it.isNotBlank() } ?: "Floating mode"
                }
                playPause.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                playPause.contentDescription = if (state.isPlaying) {
                    "Floating pause"
                } else {
                    "Floating play"
                }
                updateSpeedActiveState(root, state.speed)
                // Update audio mode overlay (handles ring, eq bars, music button tint, PlayerView dim)
                updateFloatingAudioMode(root, state.isAudioOnly, state.isPlaying, state.currentVideoUri?.toString())
                // Schedule auto-hide when playback starts, cancel when paused
                if (state.isPlaying && floatingControlsVisible) {
                    scheduleFloatingAutoHide(root)
                } else if (!state.isPlaying) {
                    autoHideJob?.cancel()
                }
            }
        }
    }

    private fun playAdjacentVideo(step: Int) {
        if (!PlayerEngine.isInitialized()) return
        serviceScope.launch {
            val next = withContext(Dispatchers.IO) {
                val engineState = PlayerEngine.get().state.value
                val currentUri = engineState.currentVideoUri?.toString() ?: return@withContext null
                val dao = AppDatabase.get(applicationContext).videoDao()
                val current = dao.videoByUri(currentUri) ?: return@withContext null
                val videos = dao.videosInFolder(current.folderName)
                if (videos.isEmpty()) return@withContext null
                val currentIndex = videos.indexOfFirst { it.uri == currentUri }.takeIf { it >= 0 } ?: 0
                val nextIndex = (currentIndex + step).floorMod(videos.size)
                videos[nextIndex]
            } ?: return@launch
            PlayerEngine.get().dispatch(
                PlaybackCommand.Play(
                    uri = Uri.parse(next.uri),
                    title = next.displayName
                )
            )
            refreshFolderPanelThumbnails()
            overlay?.let { populateFolderPanel(it) }
        }
    }

    private suspend fun populateFolderPanel(root: View) {
        if (!PlayerEngine.isInitialized()) return
        val videos = withContext(Dispatchers.IO) {
            val state = PlayerEngine.get().state.value
            val currentUri = state.currentVideoUri?.toString() ?: return@withContext emptyList()
            val dao = AppDatabase.get(applicationContext).videoDao()
            val current = dao.videoByUri(currentUri) ?: return@withContext emptyList()
            dao.videosInFolder(current.folderName)
        }
        val panel = root.findViewById<RecyclerView>(R.id.floatingFolderPanel)
        if (floatingFolderAdapter == null) {
            floatingFolderAdapter = FloatingVideoAdapter(
                emptyList(),
                onClick = { item ->
                    PlayerEngine.get().dispatch(
                        PlaybackCommand.Play(
                            uri = Uri.parse(item.uri),
                            title = item.displayName
                        )
                    )
                    panel.visibility = View.GONE
                },
                thumbnailBitmapProvider = { uri -> thumbnailLoader.memoryThumbnail(uri) },
                onThumbnailMissing = { uri ->
                    serviceScope.launch {
                        val bmp = withContext(Dispatchers.IO) { thumbnailLoader.loadThumbnail(uri, 60, 60) }
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                floatingFolderAdapter?.notifyUriChanged(uri)
                            }
                        }
                    }
                }
            )
            panel.adapter = floatingFolderAdapter
        }
        floatingFolderAdapter?.submit(videos)
    }

    private fun refreshFolderPanelThumbnails() {
        val root = overlay ?: return
        val panel = root.findViewById<RecyclerView>(R.id.floatingFolderPanel)
        if (panel.visibility == View.VISIBLE) {
            serviceScope.launch {
                populateFolderPanel(root)
            }
        }
    }

    private fun enterAudioOnlyFromFloating() {
        if (!PlayerEngine.isInitialized()) return
        AudioOnlyController(this).enter()
        stopSelf()
    }

    private fun updateResponsiveControls(root: View, layoutParams: WindowManager.LayoutParams?) {
        val p = layoutParams ?: return
        val width = p.width
        val height = p.height
        // Compute scale factor based on the smaller dimension
        val minDim = minOf(width, height).coerceAtLeast(120)
        val scaleFactor = (minDim / 360f).coerceIn(0.35f, 1.0f)

        // Transport bar: show only when tall enough, with proportional button sizes
        val showTransport = height >= 160
        val transportBar = root.findViewById<LinearLayout>(R.id.floatTransportBar)
        transportBar.visibility = if (showTransport) View.VISIBLE else View.GONE
        if (showTransport) {
            val prevBtn = root.findViewById<ImageButton>(R.id.floatPreviousButton)
            val playBtn = root.findViewById<ImageButton>(R.id.floatPlayPauseButton)
            val nextBtn = root.findViewById<ImageButton>(R.id.floatNextButton)
            val btnSize = (108 * scaleFactor).toInt().coerceAtLeast(65)
            prevBtn.layoutParams = prevBtn.layoutParams.also { it.width = btnSize; it.height = btnSize }
            val playSize = (btnSize * 1.15f).toInt()
            playBtn.layoutParams = playBtn.layoutParams.also { it.width = playSize; it.height = playSize }
            nextBtn.layoutParams = nextBtn.layoutParams.also { it.width = btnSize; it.height = btnSize }
        }

        // Top control bar: proportional visibility and sizing
        val topBarVisible = width >= 200 && height >= 120
        root.findViewById<LinearLayout>(R.id.floatingControlBar).visibility = if (topBarVisible) View.VISIBLE else View.GONE
        if (topBarVisible) {
            val topBtnSize = (86 * scaleFactor).toInt().coerceAtLeast(54)
            listOf(R.id.floatFoldersButton, R.id.floatSpeedButton, R.id.floatMusicButton,
                R.id.floatFullscreenButton, R.id.floatCloseButton).forEach { id ->
                val btn = root.findViewById<ImageButton>(id)
                btn.layoutParams = btn.layoutParams.also { it.width = topBtnSize; it.height = topBtnSize }
            }
            // Speed text sizing
            // Progressive left-to-right button reveal as window expands
            root.findViewById<ImageButton>(R.id.floatCloseButton).visibility = if (width >= 180) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatFullscreenButton).visibility = if (width >= 220) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatMusicButton).visibility = if (width >= 260) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatSpeedButton).visibility = if (width >= 300) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatFoldersButton).visibility = if (width >= 340) View.VISIBLE else View.GONE
        }

        // Hide resize handle in audio mode
        val isAudioOnly = PlayerEngine.isInitialized() && PlayerEngine.get().state.value.isAudioOnly
        root.findViewById<ImageButton>(R.id.floatResizeHandle)?.let { handle ->
            handle.visibility = if (isAudioOnly) View.GONE else View.VISIBLE
        }

        // Title: show when there's enough room
        val showTitle = width >= 280 && height >= 140
        root.findViewById<TextView>(R.id.floatingTitle).visibility = if (showTitle) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.floatingSubtitle).visibility = if (showTitle) View.VISIBLE else View.GONE
        if (showTitle) {
            val titleSize = (13 * scaleFactor).toInt().coerceAtLeast(8)
            val subSize = (10 * scaleFactor).toInt().coerceAtLeast(7)
            (root.findViewById<TextView>(R.id.floatingTitle)).textSize = titleSize.toFloat()
            (root.findViewById<TextView>(R.id.floatingSubtitle)).textSize = subSize.toFloat()
        }

        // Panels: hide if too small
        if (width < 280) root.findViewById<LinearLayout>(R.id.floatingSpeedPanel).visibility = View.GONE
        if (width < 260) root.findViewById<RecyclerView>(R.id.floatingFolderPanel).visibility = View.GONE

        // Drag handle: scale with width
        root.findViewById<View>(R.id.floatDragHandle).layoutParams?.height = (97 * scaleFactor).toInt().coerceAtLeast(43)
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun bringPlayerToForeground() {
        if (!PlayerEngine.isInitialized()) return
        val state = PlayerEngine.get().state.value
        val videoUri = state.currentVideoUri?.toString() ?: return
        val intent = Intent(this, PlayerActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoUri)
            putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, state.currentTitle)
        }
        startActivity(intent)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    "videoplayer_pro_floating",
                    "VideoPlayer Pro Floating",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "videoplayer_pro_floating")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VideoPlayer Pro")
            .setContentText("Floating video is active — tap Exit Float to close")
            .setOngoing(true)
            .build()
}
