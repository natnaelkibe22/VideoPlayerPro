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
 * Repeat mode constants mirroring [androidx.media3.common.Player] repeat modes.
 */
object RepeatMode {
    const val OFF = androidx.media3.common.Player.REPEAT_MODE_OFF   // 0
    const val ONE = androidx.media3.common.Player.REPEAT_MODE_ONE   // 1
    const val ALL = androidx.media3.common.Player.REPEAT_MODE_ALL   // 2
}

/**
 * A bottom-sheet–style dialog for selecting the repeat mode.
 *
 * Options:
 * - "Repeat Off"    (REPEAT_MODE_OFF)
 * - "Repeat One"    (REPEAT_MODE_ONE)
 * - "Repeat All"    (REPEAT_MODE_ALL)
 *
 * Each row is ≥56dp tall for accessible touch targets.
 * The currently-active mode is highlighted with a checked radio button.
 *
 * @param context          Android context for view creation.
 * @param onRepeatSelected Callback invoked with the chosen repeat mode constant
 *                         from [RepeatMode].
 */
class RepeatModeSheet(
    private val context: Context,
    private val onRepeatSelected: (Int) -> Unit
) {
    private val modes: IntArray = intArrayOf(RepeatMode.OFF, RepeatMode.ONE, RepeatMode.ALL)
    private val labels: Array<String> = arrayOf("Repeat Off", "Repeat One", "Repeat All")

    private var dialog: AlertDialog? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Show the repeat-mode selector with [currentMode] pre-selected.
     *
     * @param currentMode One of [RepeatMode.OFF], [RepeatMode.ONE], [RepeatMode.ALL].
     */
    fun show(currentMode: Int) {
        dismiss()

        val density = context.resources.displayMetrics.density
        val selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        val radioButtons = mutableListOf<RadioButton>()

        for (i in modes.indices) {
            val row = buildRadioRow(labels[i], i == selectedIndex)
            radioButtons.add(row)
            container.addView(row)
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Repeat Mode")
            .setView(container)
            .create()
            .also { dlg ->
                dlg.setOnShowListener {
                    for (i in radioButtons.indices) {
                        val idx = i
                        radioButtons[i].setOnClickListener {
                            dlg.dismiss()
                            onRepeatSelected(modes[idx])
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

    private fun buildRadioRow(label: String, checked: Boolean): RadioButton {
        val density = context.resources.displayMetrics.density
        return RadioButton(context).apply {
            text = label
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
            buttonTintList = null
        }
    }
}
