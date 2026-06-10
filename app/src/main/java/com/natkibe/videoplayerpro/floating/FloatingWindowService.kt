package com.natkibe.videoplayerpro.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.FrameLayout
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
import com.natkibe.videoplayerpro.ui.VideoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var floatingFolderAdapter: VideoAdapter? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        }
        root.findViewById<ImageButton>(R.id.floatPreviousButton).setOnClickListener {
            playAdjacentVideo(step = -1)
        }
        root.findViewById<ImageButton>(R.id.floatNextButton).setOnClickListener {
            playAdjacentVideo(step = 1)
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

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
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
            } else {
                serviceScope.launch {
                    populateFolderPanel(root)
                    panel.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupSpeedPanel(root: View) {
        val panel = root.findViewById<LinearLayout>(R.id.floatingSpeedPanel)
        val button = root.findViewById<ImageButton>(R.id.floatSpeedButton)
        val speedValue = root.findViewById<TextView>(R.id.floatingSpeedValue)
        val speedSlider = root.findViewById<SeekBar>(R.id.floatingSpeedSlider)

        fun formatSpeed(speed: Float): String = String.format("%.2fx", speed.coerceIn(0f, 2f))

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
        }

        button.setOnClickListener {
            refreshSelection()
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        speedSlider.max = 200
        speedSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress / 100f
                speedValue.text = formatSpeed(speed)
                if (fromUser && PlayerEngine.isInitialized()) {
                    PlayerEngine.get().dispatch(PlaybackCommand.SetSpeed(speed))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        refreshSelection()
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
            floatingFolderAdapter = VideoAdapter(
                emptyList(),
                true,
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
                        val bmp = withContext(Dispatchers.IO) { thumbnailLoader.loadThumbnail(uri, 96, 54) }
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
        floatingFolderAdapter?.submit(videos, true)
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
            val btnSize = (40 * scaleFactor).toInt().coerceAtLeast(24)
            prevBtn.layoutParams = prevBtn.layoutParams.also { it.width = btnSize; it.height = btnSize }
            val playSize = (btnSize * 1.15f).toInt()
            playBtn.layoutParams = playBtn.layoutParams.also { it.width = playSize; it.height = playSize }
            nextBtn.layoutParams = nextBtn.layoutParams.also { it.width = btnSize; it.height = btnSize }
        }

        // Top control bar: proportional visibility and sizing
        val topBarVisible = width >= 200 && height >= 120
        root.findViewById<LinearLayout>(R.id.floatingControlBar).visibility = if (topBarVisible) View.VISIBLE else View.GONE
        if (topBarVisible) {
            val topBtnSize = (32 * scaleFactor).toInt().coerceAtLeast(20)
            listOf(R.id.floatFoldersButton, R.id.floatSpeedButton, R.id.floatMusicButton,
                R.id.floatFullscreenButton, R.id.floatCloseButton).forEach { id ->
                val btn = root.findViewById<ImageButton>(id)
                btn.layoutParams = btn.layoutParams.also { it.width = topBtnSize; it.height = topBtnSize }
            }
            // Hide individual buttons at very small sizes
            root.findViewById<ImageButton>(R.id.floatSpeedButton).visibility = if (width >= 280) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatMusicButton).visibility = if (width >= 240) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatFoldersButton).visibility = if (width >= 260) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatFullscreenButton).visibility = if (width >= 220) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.floatCloseButton).visibility = if (width >= 200) View.VISIBLE else View.GONE
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
        root.findViewById<View>(R.id.floatDragHandle).layoutParams?.height = (36 * scaleFactor).toInt().coerceAtLeast(16)
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
