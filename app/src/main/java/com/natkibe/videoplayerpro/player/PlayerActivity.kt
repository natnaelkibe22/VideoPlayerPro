package com.natkibe.videoplayerpro.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
import com.natkibe.videoplayerpro.core.contracts.VideoPlayerProAppContainer
import com.natkibe.videoplayerpro.ui.VideoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : AppCompatActivity() {
    private val appContainer by lazy { VideoPlayerProAppContainer(this) }
    private val progress get() = appContainer.progressService

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var controls: PlayerControlService
    private var uri: String = ""
    private var folderName: String = ""
    private var playlistAdapter: VideoAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        uri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return finish()
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME).orEmpty()
        playerView = findViewById(R.id.playerView)

        // Initialize the shared player instance (thread-safe singleton)
        player = PlayerHolder.get(this)
        controls = PlayerControlService(player)
        playerView.player = player

        setupPlaylist()
        setupButtons()
        setupPlayer()
    }

    private fun setupPlayer() {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        player.prepare()
        player.addListener(object : Player.Listener {
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
                findViewById<TextView>(R.id.playerErrorText).apply {
                    text = errorMsg
                    visibility = View.VISIBLE
                }
            }

            override fun onPlayerErrorChanged(error: PlaybackException?) {
                if (error == null) {
                    findViewById<TextView>(R.id.playerErrorText).visibility = View.GONE
                }
            }
        })
        lifecycleScope.launch {
            val settings = appContainer.settingsStore.settings.first()
            player.repeatMode = settings.defaultRepeatMode
            controls.initSpeed(settings.defaultSpeed)
            val resume = if (settings.resumePlayback) progress.resumePosition(uri) else 0L
            if (resume > 0L) player.seekTo(resume)
            if (settings.showPlaylistWhileWatching) {
                findViewById<View>(R.id.sidePlaylist).visibility = View.VISIBLE
                findViewById<Button>(R.id.playlistButton).text = "Hide Playlist"
            }
            // Initialize button texts from saved settings
            findViewById<Button>(R.id.repeatButton).text = when (settings.defaultRepeatMode) {
                Player.REPEAT_MODE_ONE -> "Repeat 1"
                else -> "Repeat"
            }
            findViewById<Button>(R.id.repeatAllButton).text = when (settings.defaultRepeatMode) {
                Player.REPEAT_MODE_ALL -> "Repeat All"
                else -> "Repeat Folder"
            }
            findViewById<Button>(R.id.speedButton).text = "${settings.defaultSpeed}x"
            player.play()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.repeatButton).setOnClickListener {
            val mode = controls.toggleRepeatOne()
            it as Button
            it.text = if (mode == Player.REPEAT_MODE_ONE) "Repeat 1" else "Repeat"
        }
        findViewById<Button>(R.id.repeatAllButton).setOnClickListener {
            val mode = controls.toggleRepeatAll()
            it as Button
            it.text = if (mode == Player.REPEAT_MODE_ALL) "Repeat All" else "Repeat Folder"
        }
        findViewById<Button>(R.id.speedButton).setOnClickListener {
            val speed = controls.cycleSpeed()
            (it as Button).text = "${speed}x"
        }
        findViewById<Button>(R.id.playlistButton).setOnClickListener { togglePlaylist() }
        findViewById<Button>(R.id.audioOnlyButton).setOnClickListener { playAsMusic() }
        findViewById<Button>(R.id.floatingButton).setOnClickListener { startFloatingIfEnabled() }
    }

    private fun startFloatingIfEnabled() {
        val floating = appContainer.createFloatingPlayerFeature()
        if (!floating.canDrawOverApps()) {
            Toast.makeText(this, "Overlay permission required to float video", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(floating.overlayPermissionIntent()!!)
            }
            return
        }
        // Save progress and detach player from current view before going floating
        saveProgress()
        playerView.player = null
        player.clearVideoSurface()
        floating.startIfAllowed()
    }

    private fun playAsMusic() {
        // Save current progress before detaching video
        saveProgress()
        // Detach PlayerView from the player so video surface is released
        playerView.player = null
        // Clear video surface on the player
        player.clearVideoSurface()
        // Use PlayAsMusicFeature to start AudioOnlyService (keeps audio alive)
        appContainer.createPlayAsMusicFeature(player).detachVideoAndContinueAudio()
    }

    private fun setupPlaylist() {
        val recycler = findViewById<RecyclerView>(R.id.playlistRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        playlistAdapter = VideoAdapter(emptyList(), showThumbnails = false) { item ->
            saveProgress()
            uri = item.uri
            player.stop()
            player.setMediaItem(MediaItem.fromUri(Uri.parse(item.uri)))
            player.prepare()
            player.play()
        }
        recycler.adapter = playlistAdapter

        if (folderName.isNotBlank()) {
            lifecycleScope.launch {
                appContainer.database.videoDao().observeVideosInFolder(folderName).collect { playlistAdapter?.submit(it, false) }
            }
        }
    }

    private fun togglePlaylist() {
        val panel = findViewById<View>(R.id.sidePlaylist)
        val button = findViewById<Button>(R.id.playlistButton)
        val showing = panel.visibility == View.VISIBLE
        panel.visibility = if (showing) View.GONE else View.VISIBLE
        button.text = if (showing) "Playlist" else "Hide Playlist"
    }

    private fun saveProgress() {
        if (!::player.isInitialized || uri.isBlank()) return
        lifecycleScope.launch { progress.save(uri, player.currentPosition, player.duration) }
    }

    override fun onPause() { saveProgress(); super.onPause() }
    override fun onStop() { saveProgress(); super.onStop() }

    override fun onDestroy() {
        // Save progress off the main thread — never use runBlocking on the UI thread.
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

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }
}
