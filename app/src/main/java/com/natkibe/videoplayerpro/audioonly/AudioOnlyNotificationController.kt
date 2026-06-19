package com.natkibe.videoplayerpro.audioonly

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.player.PlayerActivity
import com.natkibe.videoplayerpro.player.PlayerEngine

/**
 * Foreground service that keeps audio playback alive in "Play as Music" mode
 * and presents a MediaStyle notification with transport controls.
 *
 * Does NOT create its own player — all playback is handled by the shared [PlayerEngine].
 * Action intents are received via [onStartCommand] and forwarded to [PlayerEngine].
 */
class AudioOnlyNotificationController : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "videoplayer_pro_audio"

        private const val ACTION_PLAY_PAUSE = "com.natkibe.videoplayerpro.action.PLAY_PAUSE"
        private const val ACTION_SKIP_PREV = "com.natkibe.videoplayerpro.action.SKIP_PREV"
        private const val ACTION_SKIP_NEXT = "com.natkibe.videoplayerpro.action.SKIP_NEXT"

        private const val REQUEST_PLAY_PAUSE = 0
        private const val REQUEST_SKIP_PREV = 1
        private const val REQUEST_SKIP_NEXT = 2
        private const val REQUEST_CONTENT = 3
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                val engine = PlayerEngine.get()
                if (engine.state.value.isPlaying) engine.pause() else engine.resume()
                updateNotification()
            }
            ACTION_SKIP_PREV -> {
                PlayerEngine.get().previous()
                updateNotification()
            }
            ACTION_SKIP_NEXT -> {
                PlayerEngine.get().next()
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VideoPlayer Pro Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows media controls for audio-only background playback"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val engine = PlayerEngine.get()
        val state = engine.state.value
        val isPlaying = state.isPlaying
        val title = state.currentTitle.ifBlank { "VideoPlayer Pro" }
        val artist = if (isPlaying) "Playing" else "Paused"

        // Content intent: opens PlayerActivity when notification body is tapped
        val contentIntent = PendingIntent.getActivity(
            this, REQUEST_CONTENT,
            Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Play/Pause toggle action
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"
        val playPauseAction = NotificationCompat.Action.Builder(
            playPauseIcon,
            playPauseLabel,
            buildActionPendingIntent(ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE)
        ).build()

        // Skip previous action
        val skipPrevAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_previous,
            "Previous",
            buildActionPendingIntent(ACTION_SKIP_PREV, REQUEST_SKIP_PREV)
        ).build()

        // Skip next action
        val skipNextAction = NotificationCompat.Action.Builder(
            R.drawable.ic_skip_next,
            "Next",
            buildActionPendingIntent(ACTION_SKIP_NEXT, REQUEST_SKIP_NEXT)
        ).build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(engine.getMediaSession()?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(skipPrevAction)
            .addAction(playPauseAction)
            .addAction(skipNextAction)

        return builder.build()
    }

    /**
     * Builds a [PendingIntent] targeting this service with the given action string.
     * Tapping a notification action re-delivers to [onStartCommand] where we dispatch
     * the corresponding command to [PlayerEngine].
     */
    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AudioOnlyNotificationController::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Rebuilds and posts an updated notification reflecting the current
     * [PlayerEngine] state (title, play/pause icon, etc.).
     */
    private fun updateNotification() {
        val notification = buildNotification()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}
