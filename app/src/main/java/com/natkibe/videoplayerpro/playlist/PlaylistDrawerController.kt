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

    /** Gesture detector for edge-swipe-to-open (fed by host dispatchTouchEvent). */
    private val edgeGestureDetector: GestureDetector

    /** Gesture detector for swipe-right-to-close (on the drawer itself). */
    private val drawerGestureDetector: GestureDetector

    /** Density-scaled edge margin in pixels (50 dp). */
    private val edgeMarginPx: Int

    /** Calculated drawer width in pixels. */
    private var drawerWidthPx: Int = 0

    private var edgeSwipeStartedInMargin = false

    init {
        val context = drawerView.context
        val density = context.resources.displayMetrics.density
        edgeMarginPx = (50 * density).toInt()

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
     * Call this from the host Activity's dispatchTouchEvent so swipes over the
     * video surface are observed even when PlayerView children consume touches.
     *
     * @return true if the drawer opened and the event stream should be consumed.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawerState.isOpen || isAnimating) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val screenWidth = drawerView.resources.displayMetrics.widthPixels
                edgeSwipeStartedInMargin = event.rawX >= screenWidth - edgeMarginPx
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                val consumed = edgeGestureDetector.onTouchEvent(event)
                edgeSwipeStartedInMargin = false
                return consumed
            }
        }

        return if (edgeSwipeStartedInMargin) {
            edgeGestureDetector.onTouchEvent(event)
        } else {
            false
        }
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

        // RecyclerView scroll should work normally when drawer is open
        recyclerView.setNestedScrollingEnabled(true)
        recyclerView.isFocusable = true
        recyclerView.isFocusableInTouchMode = true
    }

    /** Gesture listener that detects a horizontal left-fling from the right edge. */
    private inner class EdgeSwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = edgeSwipeStartedInMargin

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val deltaX = e2.rawX - start.rawX
            val deltaY = e2.rawY - start.rawY
            val isHorizontalLeftSwipe = deltaX < -MIN_SWIPE_DISTANCE_PX &&
                kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * HORIZONTAL_SWIPE_RATIO &&
                velocityX < -MIN_FLING_VELOCITY &&
                kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY)

            if (!drawerState.isOpen && edgeSwipeStartedInMargin && isHorizontalLeftSwipe) {
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

        private const val MIN_FLING_VELOCITY = 300f
        private const val MIN_SWIPE_DISTANCE_PX = 48f
        private const val HORIZONTAL_SWIPE_RATIO = 1.5f
    }
}
