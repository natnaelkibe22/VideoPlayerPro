package com.natkibe.videoplayerpro.playlist

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

/**
 * Controls the playlist drawer UI: sliding animation, swipe gestures,
 * dim overlay, and touch interception.
 *
 * This controller is purely UI-layer — all playback actions are communicated
 * outward via [listener] (PlaylistInteractionListener). No direct references
 * to PlayerEngine, PlayerActivity, or ExoPlayer exist in this package.
 *
 * @param drawerView   The side panel ViewGroup (the drawer itself, e.g. R.id.sidePlaylist).
 * @param recyclerView The RecyclerView inside the drawer that holds playlist items.
 * @param dimOverlay   A semi-transparent dark overlay shown behind the drawer when open.
 *                     The host must place this behind the drawer in the view hierarchy.
 * @param adapter      The [PlaylistItemAdapter] already attached to [recyclerView].
 * @param listener     Callback for interaction events.
 */
class PlaylistDrawerController(
    private val drawerView: ViewGroup,
    private val recyclerView: RecyclerView,
    private val dimOverlay: View,
    private val adapter: PlaylistItemAdapter,
    private val listener: PlaylistInteractionListener
) {

    /** Current drawer state. */
    private var drawerState: PlaylistDrawerState = PlaylistDrawerState.DEFAULT

    /** Flag to prevent overlapping animations. */
    private var isAnimating = false

    /** Gesture detector for edge-swipe-to-open (on the root container). */
    private val edgeGestureDetector: GestureDetector

    /** Gesture detector for swipe-right-to-close (on the drawer itself). */
    private val drawerGestureDetector: GestureDetector

    /** Density-scaled edge margin in pixels (50 dp). */
    private val edgeMarginPx: Int

    /** Calculated drawer width in pixels. */
    private var drawerWidthPx: Int = 0

    /** The root container (parent of drawerView) used for edge-swipe detection. */
    private val rootContainer: View

    init {
        val context = drawerView.context
        val density = context.resources.displayMetrics.density
        edgeMarginPx = (50 * density).toInt()

        // Root container is the parent FrameLayout that contains both the content and the drawer
        rootContainer = drawerView.parent as? View
            ?: error("drawerView must have a parent ViewGroup")

        // Gesture detector for edge swipe (left-fling from right edge)
        edgeGestureDetector = GestureDetector(context, EdgeSwipeGestureListener())

        // Gesture detector for drawer close swipe (right-fling on drawer)
        drawerGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                // Swipe right (positive velocityX) closes the drawer
                if (drawerState.isOpen && velocityX > 300) {
                    closeDrawer()
                    return true
                }
                return false
            }
        })

        // Initialize positions after layout pass
        drawerView.post {
            setupInitialPositions()
            setupTouchHandling()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Forward touch events to the edge-swipe gesture detector.
     * Call this from the PlayerView touch listener so that swipes
     * over the video surface are detected alongside rootContainer touches.
     *
     * @return true if the gesture detector consumed the event.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawerState.isOpen && !isAnimating) {
            return edgeGestureDetector.onTouchEvent(event)
        }
        return false
    }

    /** Animate the drawer open. */
    fun openDrawer() {
        if (drawerState.isOpen || isAnimating) return
        performOpenAnimation()
    }

    /** Animate the drawer closed. */
    fun closeDrawer() {
        if (!drawerState.isOpen || isAnimating) return
        performCloseAnimation()
    }

    /** Toggle the drawer open/closed. */
    fun toggleDrawer() {
        if (drawerState.isOpen) closeDrawer() else openDrawer()
    }

    /** Replace the playlist items and optionally update thumbnail visibility. */
    fun updateItems(items: List<PlaylistItemUiModel>, showThumbnails: Boolean = drawerState.showThumbnails) {
        adapter.submit(items, showThumbnails)
    }

    /** Update the internal state and re-apply any relevant values (e.g., width percent). */
    fun updateState(state: PlaylistDrawerState) {
        drawerState = state
        recalculateDrawerWidth()
    }

    /** Set the currently-selected URI to highlight the active row in the playlist. */
    fun setSelectedUri(uri: String) {
        drawerState = drawerState.copy(selectedVideoUri = uri)
        updateHighlightedItem(uri)
    }

    /** Returns whether the drawer is currently open. */
    fun isOpen(): Boolean = drawerState.isOpen

    // ──────────────────────────────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────────────────────────────

    private fun setupInitialPositions() {
        recalculateDrawerWidth()

        // Ensure the drawer is visible so it participates in layout, then push it off-screen.
        drawerView.isVisible = true
        drawerView.translationX = drawerWidthPx.toFloat()

        // Dim overlay starts invisible but clickable (tap to close).
        dimOverlay.alpha = 0f
        dimOverlay.isVisible = false
        dimOverlay.isClickable = true
    }

    private fun recalculateDrawerWidth() {
        val screenWidth = drawerView.resources.displayMetrics.widthPixels
        drawerWidthPx = (screenWidth * drawerState.clampedWidthPercent()).toInt()

        val lp = drawerView.layoutParams
        if (lp.width != drawerWidthPx) {
            lp.width = drawerWidthPx
            drawerView.layoutParams = lp
        }

        // If already off-screen, update translationX to match new width
        if (!drawerState.isOpen) {
            drawerView.translationX = drawerWidthPx.toFloat()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Touch Handling
    // ──────────────────────────────────────────────────────────────────────

    private fun setupTouchHandling() {
        // Tap on dim overlay closes the drawer
        dimOverlay.setOnClickListener { closeDrawer() }

        // Gesture detection on the drawer itself (swipe right to close)
        drawerView.setOnTouchListener { _, event ->
            drawerGestureDetector.onTouchEvent(event)
            // Return false to let the RecyclerView handle scrolling
            false
        }

        // Edge-swipe detection on the root container (swipe from right edge to open).
        // We always return false so touch events pass through to player controls.
        // The gesture detector observes all events but only acts on left-flings when closed.
        rootContainer.setOnTouchListener { _, event ->
            if (!drawerState.isOpen && !isAnimating) {
                edgeGestureDetector.onTouchEvent(event)
            }
            false // Never consume — player controls must still work when drawer is closed
        }

        // RecyclerView scroll should work normally when drawer is open
        recyclerView.setNestedScrollingEnabled(true)
        recyclerView.isFocusable = true
        recyclerView.isFocusableInTouchMode = true
    }

    /** Gesture listener that detects a left-fling (velocityX < -300) to open the drawer. */
    private inner class EdgeSwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!drawerState.isOpen && velocityX < -300) {
                openDrawer()
                return true
            }
            return false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Animations
    // ──────────────────────────────────────────────────────────────────────

    /** Whether headunit safe mode is active — when true, animations are instant. */
    var headunitSafeMode: Boolean = false

    private fun performOpenAnimation() {
        isAnimating = true
        drawerState = drawerState.copy(isOpen = true)

        // Show overlay and drawer before animating
        dimOverlay.isVisible = true
        drawerView.isVisible = true

        if (headunitSafeMode) {
            // Instant — no animation
            drawerView.translationX = 0f
            dimOverlay.alpha = DIM_ALPHA_MAX
            isAnimating = false
            listener.onDrawerOpen()
            return
        }

        // Animate drawer sliding in from the right (translationX: width → 0)
        drawerView.animate()
            .translationX(0f)
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withStartAction {
                drawerView.isVisible = true
            }
            .withEndAction {
                isAnimating = false
                listener.onDrawerOpen()
            }
            .start()

        // Fade in the dim overlay
        dimOverlay.animate()
            .alpha(DIM_ALPHA_MAX)
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun performCloseAnimation() {
        isAnimating = true

        if (headunitSafeMode) {
            // Instant — no animation
            drawerView.translationX = drawerWidthPx.toFloat()
            drawerView.isVisible = false
            drawerState = drawerState.copy(isOpen = false)
            dimOverlay.alpha = 0f
            dimOverlay.isVisible = false
            isAnimating = false
            listener.onDrawerClose()
            return
        }

        // Animate drawer sliding out to the right (translationX: 0 → width)
        drawerView.animate()
            .translationX(drawerWidthPx.toFloat())
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                drawerView.isVisible = false
                drawerState = drawerState.copy(isOpen = false)
                isAnimating = false
                listener.onDrawerClose()
            }
            .start()

        // Fade out the dim overlay
        dimOverlay.animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                dimOverlay.isVisible = false
            }
            .start()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Re-binds visible items to update the highlight on the currently-playing row. */
    private fun updateHighlightedItem(selectedUri: String) {
        for (i in 0 until adapter.itemCount) {
            val item = adapter.getItemAt(i) ?: continue
            if (item.uri == selectedUri) {
                recyclerView.post {
                    adapter.notifyItemChanged(i)
                }
                return
            }
        }
    }

    companion object {
        /** Animation duration in milliseconds for open/close. */
        private const val ANIM_DURATION_MS = 280L

        /** Maximum alpha value for the dim overlay behind the drawer. */
        private const val DIM_ALPHA_MAX = 0.5f
    }
}
