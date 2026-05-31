package com.natkibe.videoplayerpro.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.media3.ui.PlayerView
import com.natkibe.videoplayerpro.R

class FloatingPlayerService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(1002, buildNotification())
        showOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlay != null) return
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = LayoutInflater.from(this).inflate(R.layout.floating_player, null)
        overlay?.findViewById<PlayerView>(R.id.floatingPlayerView)?.player = PlayerHolder.get(this)

        params = WindowManager.LayoutParams(
            640,
            360,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 80 }

        attachDragAndResize(overlay!!)
        windowManager?.addView(overlay, params)
    }

    private fun attachDragAndResize(root: View) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        root.findViewById<View>(R.id.floatDragHandle).setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startX = p.x; startY = p.y; touchX = event.rawX; touchY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> { p.x = startX + (event.rawX - touchX).toInt(); p.y = startY + (event.rawY - touchY).toInt(); windowManager?.updateViewLayout(root, p); true }
                else -> true
            }
        }
        root.findViewById<View>(R.id.floatResizeHandle).setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { touchX = event.rawX; touchY = event.rawY; startX = p.width; startY = p.height; true }
                MotionEvent.ACTION_MOVE -> {
                    p.width = (startX + (event.rawX - touchX).toInt()).coerceIn(320, 1400)
                    p.height = (startY + (event.rawY - touchY).toInt()).coerceIn(180, 900)
                    windowManager?.updateViewLayout(root, p)
                    true
                }
                else -> true
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("videoplayer_pro_floating", "VideoPlayer Pro Floating", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, "videoplayer_pro_floating")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("VideoPlayer Pro")
        .setContentText("Floating video is active")
        .setOngoing(true)
        .build()
}
