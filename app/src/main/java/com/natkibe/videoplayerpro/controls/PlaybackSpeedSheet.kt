package com.natkibe.videoplayerpro.controls

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A bottom-sheet–style dialog for selecting playback speed.
 *
 * Available speeds:
 * - 0.25x, 0.5x, 0.75x, 1.0x (Normal), 1.25x, 1.5x, 1.75x, 2.0x
 *
 * Each row is ≥56dp tall (accessible touch target). The currently-selected
 * speed is highlighted with a checked radio button.
 *
 * @param context         Android context for view creation.
 * @param onSpeedSelected Callback invoked with the chosen speed [Float].
 */
class PlaybackSpeedSheet(
    private val context: Context,
    private val onSpeedSelected: (Float) -> Unit
) {
    /** Predefined speeds (index matches [labels]). */
    private val speeds: FloatArray = floatArrayOf(
        0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f
    )

    /** Display labels matching [speeds]. */
    private val labels: Array<String> = arrayOf(
        "0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "1.75x", "2.0x"
    )

    private var dialog: AlertDialog? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Show the speed selector with [currentSpeed] pre-selected.
     * The closest matching entry from [speeds] is checked.
     */
    fun show(currentSpeed: Float) {
        dismiss()

        val density = context.resources.displayMetrics.density
        val selectedIndex = findClosestSpeedIndex(currentSpeed)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "Playback speed sheet"
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        val radioButtons = mutableListOf<RadioButton>()

        for (i in speeds.indices) {
            val row = buildRadioRow(labels[i], i == selectedIndex)
            radioButtons.add(row)
            container.addView(row)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Playback Speed")
            .setView(container)
            .setOnDismissListener(null)
            .create()
            .also { dlg ->
                dlg.setOnShowListener {
                    // Wire clicks so selecting a row dismisses and calls back
                    for (i in radioButtons.indices) {
                        val idx = i
                        radioButtons[i].setOnClickListener {
                            dlg.dismiss()
                            onSpeedSelected(speeds[idx])
                        }
                    }
                }
                dlg.show()
            }
    }

    /** Dismiss the dialog if visible. */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun findClosestSpeedIndex(current: Float): Int {
        var best = 0
        var bestDelta = Float.MAX_VALUE
        for (i in speeds.indices) {
            val delta = kotlin.math.abs(speeds[i] - current)
            if (delta < bestDelta) {
                bestDelta = delta
                best = i
            }
        }
        return best
    }

    private fun buildRadioRow(label: String, checked: Boolean): RadioButton {
        val density = context.resources.displayMetrics.density
        return RadioButton(context).apply {
            text = label
            contentDescription = "Speed option $label"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = checked
            minimumHeight = (56 * density).toInt()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            buttonTintList = null // keep default radio colors
        }
    }
}
