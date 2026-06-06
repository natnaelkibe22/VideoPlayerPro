package com.natkibe.videoplayerpro.audioonly

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.natkibe.videoplayerpro.R

/**
 * Lightweight foreground service that keeps the app alive while "Play as Music" is active.
 * Does NOT create its own player — playback is handled by the shared [PlayerEngine].
 */
class AudioOnlyNotificationController : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(1001, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "videoplayer_pro_audio",
                "VideoPlayer Pro Audio",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "videoplayer_pro_audio")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VideoPlayer Pro")
            .setContentText("Play as Music is active")
            .setOngoing(true)
            .build()
}
