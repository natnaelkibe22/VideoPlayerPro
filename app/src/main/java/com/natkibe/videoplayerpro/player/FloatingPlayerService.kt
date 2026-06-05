package com.natkibe.videoplayerpro.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.media3.ui.PlayerView
import com.natkibe.videoplayerpro.R

/**
 * Floating video overlay service.
 * Uses the shared PlayerHolder ExoPlayer instance.
 * Draggable, resizable, snaps to corners, can be closed.
 * Sends broadcast ACTION_FLOATING_CLOSED when stopped so PlayerActivity can re-attach player.
 */
class FloatingPlayerService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        const val ACTION_CLOSE = "com.natkibe.videoplayerpro.action.CLOSE_FLOATING"
        const val ACTION_FLOATING_CLOSED = "com.natkibe.videoplayerpro.action.FLOATING_CLOSED"
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
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Broadcast that floating mode has closed so PlayerActivity can re-attach
        sendBroadcast(Intent(ACTION_FLOATING_CLOSED))

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

        // Guard: only attach if PlayerHolder exists and no other view holds it
        val pv = overlay?.findViewById<PlayerView>(R.id.floatingPlayerView)
        val shared = PlayerHolder.get(this)
        // Detach from any previous PlayerView to avoid double-attach crash
        if (pv?.player !== shared) {
            pv?.player = shared
        }

        params = WindowManager.LayoutParams(
            640,
            360,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 80; y = 80 }

        // Close button
        overlay?.findViewById<ImageButton>(R.id.floatCloseButton)?.setOnClickListener {
            stopSelf()
        }

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
                MotionEvent.ACTION_UP -> { snapToCorner(root); true }
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

    /** Snap floating window to nearest corner when drag ends. */
    private fun snapToCorner(root: View) {
        val p = params ?: return
        val wm = windowManager ?: return
        val display = wm.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)
        val screenW = size.x
        val screenH = size.y

        // Determine nearest corner
        val centerX = p.x + p.width / 2
        val centerY = p.y + p.height / 2
        val snapMargin = 16

        p.x = when {
            centerX < screenW / 2 -> snapMargin
            else -> screenW - p.width - snapMargin
        }
        p.y = when {
            centerY < screenH / 2 -> snapMargin
            else -> screenH - p.height - snapMargin
        }
        wm.updateViewLayout(root, p)
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
        .setContentText("Floating video is active — tap Exit Float to close")
        .setOngoing(true)
        .build()
}
