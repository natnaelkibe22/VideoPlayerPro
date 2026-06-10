package com.natkibe.videoplayerpro.controls

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import com.natkibe.videoplayerpro.R
import kotlin.math.roundToInt

/**
 * A bottom-sheet–style dialog for selecting playback speed with a slider.
 *
 * Range:
 * - 0.0x to 2.0x with 0.01x increments
 *
 * @param context         Android context for view creation.
 * @param onSpeedSelected Callback invoked with the chosen speed [Float].
 */
class PlaybackSpeedSheet(
    private val context: Context,
    private val onSpeedSelected: (Float) -> Unit
) {
    private var popupWindow: PopupWindow? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Show the speed selector with [currentSpeed] pre-selected.
     * The closest matching entry from [speeds] is checked.
     */
    fun show(currentSpeed: Float) {
        dismiss()

        val density = context.resources.displayMetrics.density
        val display = context.resources.displayMetrics
        val panelWidth = (320 * density).toInt().coerceAtMost(display.widthPixels - (32 * density).toInt())
            .coerceAtLeast((260 * density).toInt())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "Playback speed sheet"
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61B1D22"))
                cornerRadius = 18 * density
                setStroke((1 * density).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }

        val title = TextView(context).apply {
            text = "Playback Speed"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (8 * density).toInt())
            contentDescription = "Playback Speed"
        }
        container.addView(title)

        val selectedValueLabel = TextView(context).apply {
            id = R.id.playbackSpeedValueLabel
            text = formatSpeed(currentSpeed)
            setTextColor(Color.parseColor("#FF8AB4FF"))
            textSize = 15f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (6 * density).toInt())
            contentDescription = "Current speed ${formatSpeed(currentSpeed)}"
        }
        container.addView(selectedValueLabel)

        val seekBar = SeekBar(context).apply {
            id = R.id.playbackSpeedSlider
            max = 200
            progress = (currentSpeed.coerceIn(0f, 2f) * 100f).roundToInt()
            contentDescription = "Playback speed slider"
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress / 100f
                selectedValueLabel.text = formatSpeed(speed)
                selectedValueLabel.contentDescription = "Current speed ${formatSpeed(speed)}"
                if (fromUser) {
                    onSpeedSelected(speed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        container.addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((8 * density).toInt(), 0, (8 * density).toInt(), (4 * density).toInt())
            }
        )

        val rangeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((18 * density).toInt(), 0, (18 * density).toInt(), (4 * density).toInt())
        }
        rangeRow.addView(
            TextView(context).apply {
                text = "0x"
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 10f
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        rangeRow.addView(
            TextView(context).apply {
                text = "2x"
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 10f
                gravity = Gravity.END
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        container.addView(rangeRow)

        val anchor = (context as? Activity)?.window?.decorView ?: return

        popupWindow = PopupWindow(
            container,
            panelWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = (8 * density)
            animationStyle = android.R.style.Animation_Dialog
            showAtLocation(anchor, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (72 * density).toInt())
        }
    }

    /** Dismiss the dialog if visible. */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun formatSpeed(speed: Float): String = String.format("%.2fx", speed.coerceIn(0f, 2f))
}
