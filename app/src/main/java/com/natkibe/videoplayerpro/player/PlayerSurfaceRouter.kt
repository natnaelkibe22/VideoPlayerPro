package com.natkibe.videoplayerpro.player

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

class PlayerSurfaceRouter(
    private val context: Context,
    private val playerProvider: () -> Player?
) {

    private var fullscreenPlayerView: PlayerView? = null
    private var floatingPlayerView: PlayerView? = null
    private var floatingContainer: ViewGroup? = null

    fun registerFullscreenView(playerView: PlayerView) {
        fullscreenPlayerView = playerView
    }

    fun unregisterFullscreenView() {
        fullscreenPlayerView?.player = null
        fullscreenPlayerView = null
    }

    fun registerFloatingView(playerView: PlayerView, container: ViewGroup) {
        floatingPlayerView = playerView
        floatingContainer = container
    }

    fun unregisterFloatingView() {
        floatingPlayerView?.player = null
        floatingPlayerView = null
        floatingContainer = null
    }

    /**
     * Attach player to fullscreen surface, detaching from any other surface.
     */
    fun attachToFullscreen() {
        val player = playerProvider() ?: return
        detachAll()
        fullscreenPlayerView?.player = player
    }

    /**
     * Attach player to floating overlay surface, detaching from fullscreen.
     */
    fun attachToFloating() {
        val player = playerProvider() ?: return
        detachAll()
        floatingPlayerView?.player = player
    }

    /**
     * Detach player from all video surfaces (audio-only mode).
     * Player continues playing audio without a video surface.
     */
    fun detachAllForAudioOnly() {
        detachAll()
    }

    /**
     * Detach player from all registered surfaces.
     */
    private fun detachAll() {
        fullscreenPlayerView?.player = null
        floatingPlayerView?.player = null
    }

    /**
     * Creates a new PlayerView for the floating window.
     */
    fun createFloatingPlayerView(parent: ViewGroup): PlayerView {
        val playerView = PlayerView(context)
        playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        playerView.useController = false
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        parent.addView(playerView)
        return playerView
    }

    fun release() {
        detachAll()
        fullscreenPlayerView = null
        floatingPlayerView?.let { pv ->
            (pv.parent as? ViewGroup)?.removeView(pv)
        }
        floatingPlayerView = null
        floatingContainer = null
    }
}
