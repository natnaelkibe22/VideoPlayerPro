package com.natkibe.videoplayerpro.features.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.natkibe.videoplayerpro.core.contracts.FeatureModule
import com.natkibe.videoplayerpro.floating.FloatingWindowController

/**
 * Lightweight bridge that wraps [FloatingWindowController] for the feature-module contract.
 */
class FloatingPlayerFeature(private val context: Context) : FeatureModule {
    override val name = "Floating Player"
    override val milestone = "v0.5-floating-resizable"

    private val controller by lazy { FloatingWindowController(context) }

    fun canDrawOverApps(): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(): Intent? = if (Build.VERSION.SDK_INT >= 23) {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    } else null

    fun startIfAllowed(): Boolean {
        if (!canDrawOverApps()) return false
        return controller.enter()
    }
}
