package com.natkibe.videoplayerpro.floating

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Handles drag-to-position and pinch/resize-corner logic for the floating window.
 * Attaches touch listeners to the drag handle and resize handle views.
 * Snaps to the nearest screen corner when the user lifts their finger.
 */
class FloatingResizeController(
    private val windowManager: WindowManager,
    private val getParams: () -> WindowManager.LayoutParams?,
    private val updateLayout: (View, WindowManager.LayoutParams) -> Unit
) {
    // Drag state
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    // Resize state
    private var resizeStartWidth = 0
    private var resizeStartHeight = 0
    private var resizeTouchX = 0f
    private var resizeTouchY = 0f

    /** Attach drag listener to [dragHandle]. Must be called after the window is added. */
    fun attachDrag(dragHandle: View, root: View) {
        dragHandle.setOnTouchListener { _, event ->
            val p = getParams() ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = p.x
                    dragStartY = p.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = dragStartX + (event.rawX - dragTouchX).toInt()
                    p.y = dragStartY + (event.rawY - dragTouchY).toInt()
                    updateLayout(root, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    snapToCorner(root)
                    true
                }
                else -> true
            }
        }
    }

    /** Attach resize listener to [resizeHandle]. Must be called after the window is added. */
    fun attachResize(resizeHandle: View, root: View) {
        resizeHandle.setOnTouchListener { _, event ->
            val p = getParams() ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeTouchX = event.rawX
                    resizeTouchY = event.rawY
                    resizeStartWidth = p.width
                    resizeStartHeight = p.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.width = (resizeStartWidth + (event.rawX - resizeTouchX).toInt())
                        .coerceIn(320, 1400)
                    p.height = (resizeStartHeight + (event.rawY - resizeTouchY).toInt())
                        .coerceIn(180, 900)
                    updateLayout(root, p)
                    true
                }
                else -> true
            }
        }
    }

    /** Snap the window to the nearest screen corner after drag ends. */
    private fun snapToCorner(root: View) {
        val p = getParams() ?: return
        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)
        val screenW = size.x
        val screenH = size.y

        val centerX = p.x + p.width / 2
        val centerY = p.y + p.height / 2
        val margin = 16

        p.x = if (centerX < screenW / 2) margin else screenW - p.width - margin
        p.y = if (centerY < screenH / 2) margin else screenH - p.height - margin

        updateLayout(root, p)
    }
}
