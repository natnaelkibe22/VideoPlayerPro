package com.natkibe.videoplayerpro.floating

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
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.media3.ui.PlayerView
import com.natkibe.videoplayerpro.R
import com.natkibe.videoplayerpro.player.PlayerEngine

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

        // Unregister floating view from engine
        if (PlayerEngine.isInitialized()) {
            PlayerEngine.get().detachFloatingPlayerView()
        }

        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        params = null
        resizeController = null
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Broadcast that floating mode has closed so PlayerActivity can re-attach
        sendBroadcast(Intent(ACTION_FLOATING_CLOSED))

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

        // Attach drag & resize via the controller
        val root = overlay!!
        resizeController = FloatingResizeController(
            windowManager = windowManager!!,
            getParams = { params },
            updateLayout = { v, p -> windowManager?.updateViewLayout(v, p) }
        )
        resizeController?.attachDrag(root.findViewById(R.id.floatDragHandle), root)
        resizeController?.attachResize(root.findViewById(R.id.floatResizeHandle), root)

        windowManager?.addView(overlay, params)
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
