package com.natkibe.videoplayerpro.controls

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A bottom-sheet–style dialog for quick settings toggles.
 *
 * Options:
 * - Auto-hide controls (on/off)
 * - Headunit safe mode (on/off)
 *
 * Each row is ≥56dp tall for accessible touch targets.
 * Toggle state is read from and written to [SettingsStore] via callbacks.
 *
 * @param context          Android context for view creation.
 * @param autoHideEnabled  Initial state for auto-hide controls toggle.
 * @param headunitSafeMode Initial state for headunit safe mode toggle.
 * @param onAutoHideChanged Called when auto-hide toggle changes ([Boolean]).
 * @param onHeadunitChanged Called when headunit-safe-mode toggle changes ([Boolean]).
 */
class SettingsSheet(
    private val context: Context,
    private val autoHideEnabled: Boolean,
    private val headunitSafeMode: Boolean,
    private val onAutoHideChanged: (Boolean) -> Unit,
    private val onHeadunitChanged: (Boolean) -> Unit,
    private val onDiagnosticsClicked: () -> Unit = {}
) {
    private var dialog: AlertDialog? = null

    // ── Public API ────────────────────────────────────────────────────────

    /** Show the settings dialog with current toggle states. */
    fun show() {
        dismiss()

        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = "Settings screen"
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        // Auto-hide controls row
        val autoHideSwitch = buildSwitchRow(
            label = "Auto-hide controls",
            subtitle = "Fade out overlay after inactivity",
            checked = autoHideEnabled
        ) { checked -> onAutoHideChanged(checked) }
        container.addView(autoHideSwitch)

        // Divider
        container.addView(buildDivider())

        // Headunit safe mode row
        val headunitSwitch = buildSwitchRow(
            label = "Headunit safe mode",
            subtitle = "Disable thumbnails, animations & background refresh",
            checked = headunitSafeMode
        ) { checked -> onHeadunitChanged(checked) }
        container.addView(headunitSwitch)

        // Divider
        container.addView(buildDivider())

        // Diagnostics link
        val diagnosticsRow = buildSimpleRow(
            label = "Developer Diagnostics",
            subtitle = "View library stats, cache info, decoder details",
            onClick = onDiagnosticsClicked
        )
        container.addView(diagnosticsRow)

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Settings")
            .setView(container)
            .create()
            .also { dlg ->
                dlg.show()
            }
    }

    /** Dismiss the dialog if visible. */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun buildSwitchRow(
        label: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val density = context.resources.displayMetrics.density

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt()
            )
            minimumHeight = (64 * density).toInt()
        }

        // Label + subtitle in a vertical layout
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val labelView = android.widget.TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        textColumn.addView(labelView)

        val subtitleView = android.widget.TextView(context).apply {
            text = subtitle
            setTextColor(Color.parseColor("#FFBBBBBB"))
            textSize = 12f
        }
        textColumn.addView(subtitleView)

        row.addView(textColumn)

        val switch = SwitchCompat(context).apply {
            contentDescription = if (label.equals("Headunit safe mode", ignoreCase = true)) "Safe Mode toggle" else "$label toggle"
            isChecked = checked
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                onChanged(isChecked)
            }
        }
        row.addView(switch)

        return row
    }

    private fun buildDivider(): android.view.View {
        return android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
        }
    }

    private fun buildSimpleRow(
        label: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        val density = context.resources.displayMetrics.density

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt()
            )
            minimumHeight = (64 * density).toInt()
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val labelView = android.widget.TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        textColumn.addView(labelView)

        val subtitleView = android.widget.TextView(context).apply {
            text = subtitle
            setTextColor(Color.parseColor("#FFBBBBBB"))
            textSize = 12f
        }
        textColumn.addView(subtitleView)

        row.addView(textColumn)

        // Arrow indicator
        val arrowView = android.widget.TextView(context).apply {
            text = "→"
            setTextColor(Color.parseColor("#FFBBBBBB"))
            textSize = 18f
        }
        row.addView(arrowView)

        return row
    }
}
