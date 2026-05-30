package com.natkibe.playerpro.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.natkibe.playerpro.R

class AudioOnlyService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(1001, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // PlayerActivity detaches PlayerView before starting this service.
        // The shared PlayerHolder keeps audio alive while video rendering is off.
        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("player_pro_audio", "Player Pro Audio", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, "player_pro_audio")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Player Pro")
        .setContentText("Play as Music is active")
        .setOngoing(true)
        .build()
}
