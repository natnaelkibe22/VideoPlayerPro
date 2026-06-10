package com.natkibe.videoplayerpro.controls

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Creates and manages a playback options popup menu anchored to a View.
 *
 * Menu items (in order):
 * 1. "Playback Speed"   → [onSpeedClicked]
 * 2. "Repeat Mode"      → [onRepeatClicked] cycles Off → One → Folder → Off
 * 3. "Audio Only Mode"  → [onAudioOnlyClicked] (with checkbox indicator)
 * 4. "Floating Window"  → [onFloatingClicked]  (with checkbox indicator)
 * 5. "Settings"         → [onSettingsClicked]
 * 6. "Refresh Library"  → [onRefreshClicked]
 *
 * @param context    Android context for view creation.
 * @param anchorView The [View] (typically a menu button) that this popup anchors to.
 * @param callbacks  Callback object with one lambda per menu item.
 */
class PlaybackMenuController(
    private val context: Context,
    private val anchorView: View,
    private val callbacks: MenuCallbacks
) {
    /** Callbacks for each menu action. */
    interface MenuCallbacks {
        fun onSpeedClicked()
        fun onRepeatClicked()
        fun onAudioOnlyClicked()
        fun onFloatingClicked()
        fun onSettingsClicked()
        fun onRefreshClicked()
    }

    private var popupWindow: PopupWindow? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Show the menu popup anchored below [anchorView].
     *
     * @param currentSpeed  Display string for current speed (e.g. "1.0x").
     * @param currentRepeat Display string for current repeat (e.g. "Off").
     * @param isAudioOnly   Whether audio-only is active (shows ✓).
     * @param isFloating    Whether floating window is active (shows ✓).
     */
    fun show(currentSpeed: String, currentRepeat: String, isAudioOnly: Boolean, isFloating: Boolean) {
        dismiss()

        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "Playback menu"
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
            background = ColorDrawable(Color.parseColor("#FF2A2A2A"))
        }

        container.addView(
            buildRow(
                label = "Playback Speed",
                detail = currentSpeed,
                checked = false,
                contentDescription = "Speed button",
                onClick = { callbacks.onSpeedClicked() }
            )
        )
        container.addView(buildDivider())
        container.addView(
            buildRow(
                label = "Repeat Mode",
                detail = currentRepeat,
                checked = currentRepeat != "Repeat Off",
                contentDescription = "Repeat toggle",
                onClick = { callbacks.onRepeatClicked() }
            )
        )
        container.addView(buildDivider())
        container.addView(
            buildRow(
                label = "Audio Only Mode",
                detail = "",
                checked = isAudioOnly,
                contentDescription = "Play as Music toggle",
                onClick = { callbacks.onAudioOnlyClicked() }
            )
        )
        container.addView(buildDivider())
        container.addView(
            buildRow(
                label = "Floating Window",
                detail = "",
                checked = isFloating,
                contentDescription = "Float toggle",
                onClick = { callbacks.onFloatingClicked() }
            )
        )
        container.addView(buildDivider())
        container.addView(
            buildRow(
                label = "Settings",
                detail = "",
                checked = false,
                onClick = { callbacks.onSettingsClicked() }
            )
        )
        container.addView(buildDivider())
        container.addView(
            buildRow(
                label = "Refresh Library",
                detail = "",
                checked = false,
                onClick = { callbacks.onRefreshClicked() }
            )
        )

        val width = (280 * density).toInt()
        val height = ViewGroup.LayoutParams.WRAP_CONTENT

        popupWindow = PopupWindow(container, width, height, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            animationStyle = android.R.style.Animation_InputMethod
        }

        // Measure and show below the anchor
        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val yOffset = anchorView.height
        popupWindow?.showAsDropDown(anchorView, 0, yOffset, Gravity.START)
    }

    /** Dismiss the menu if currently shown. */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    // ── Internal builders ─────────────────────────────────────────────────

    private fun buildRow(
        label: String,
        detail: String,
        checked: Boolean,
        contentDescription: String = label,
        onClick: () -> Unit
    ): View {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (4 * density).toInt(),
                (16 * density).toInt(),
                (4 * density).toInt()
            )
            minimumHeight = (56 * density).toInt()
            setBackgroundColor(Color.TRANSPARENT)
            this.contentDescription = contentDescription
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss()
                onClick()
            }
        }

        val labelText = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        row.addView(labelText)

        if (detail.isNotEmpty()) {
            val detailText = TextView(context).apply {
                text = detail
                setTextColor(Color.parseColor("#FFBBBBBB"))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
            }
            row.addView(detailText)
        }

        if (checked) {
            val checkView = TextView(context).apply {
                text = "✓"
                setTextColor(Color.parseColor("#FF4CAF50"))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(checkView)
        }

        return row
    }

    private fun buildDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }
    }
}
