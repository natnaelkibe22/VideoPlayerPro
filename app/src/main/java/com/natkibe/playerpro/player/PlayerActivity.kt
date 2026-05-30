package com.natkibe.playerpro.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.natkibe.playerpro.R
import com.natkibe.playerpro.data.AppDatabase
import com.natkibe.playerpro.settings.SettingsStore
import com.natkibe.playerpro.ui.VideoAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {
    private val db by lazy { AppDatabase.get(this) }
    private val settingsStore by lazy { SettingsStore(this) }
    private val progress by lazy { ProgressService(db.videoDao()) }

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
                findViewById<TextView>(R.id.playerErrorText).apply {
                    text = "Cannot play this video on this headunit. Unsupported codec, 4K/HEVC limit, slow USB, or corrupt file."
                    visibility = View.VISIBLE
                }
            }
        })
        lifecycleScope.launch {
            val settings = settingsStore.settings.first()
            player.repeatMode = settings.defaultRepeatMode
            player.setPlaybackSpeed(settings.defaultSpeed)
            val resume = if (settings.resumePlayback) progress.resumePosition(uri) else 0L
            if (resume > 0L) player.seekTo(resume)
            if (settings.showPlaylistWhileWatching) findViewById<View>(R.id.sidePlaylist).visibility = View.VISIBLE
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
        findViewById<Button>(R.id.audioOnlyButton).setOnClickListener { playAsMusic() }
        findViewById<Button>(R.id.playlistButton).setOnClickListener { togglePlaylist() }
        findViewById<Button>(R.id.floatingButton).setOnClickListener { startFloatingIfEnabled() }
    }

    private fun setupPlaylist() {
        val recycler = findViewById<RecyclerView>(R.id.playlistRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        playlistAdapter = VideoAdapter(emptyList(), showThumbnails = false) { item ->
            saveProgress()
            uri = item.uri
            player.setMediaItem(MediaItem.fromUri(Uri.parse(item.uri)))
            player.prepare()
            player.play()
        }
        recycler.adapter = playlistAdapter

        if (folderName.isNotBlank()) {
            lifecycleScope.launch {
                db.videoDao().observeVideosInFolder(folderName).collect { playlistAdapter?.submit(it, false) }
            }
        }
    }

    private fun playAsMusic() {
        saveProgress()
        playerView.player = null
        player.clearVideoSurface()
        startForegroundServiceCompat(Intent(this, AudioOnlyService::class.java))
        moveTaskToBack(true)
    }

    private fun togglePlaylist() {
        val panel = findViewById<View>(R.id.sidePlaylist)
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun startFloatingIfEnabled() = lifecycleScope.launch {
        val settings = settingsStore.settings.first()
        if (settings.enableFloatingPlayer) {
            startForegroundServiceCompat(Intent(this@PlayerActivity, FloatingPlayerService::class.java))
        } else {
            enterPipIfSupported()
        }
    }

    private fun enterPipIfSupported() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun saveProgress() {
        if (!::player.isInitialized || uri.isBlank()) return
        lifecycleScope.launch { progress.save(uri, player.currentPosition, player.duration) }
    }

    override fun onPause() { saveProgress(); super.onPause() }
    override fun onStop() { saveProgress(); super.onStop() }
    override fun onDestroy() { saveProgress(); playerView.player = null; super.onDestroy() }

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }
}
