package com.natkibe.playerpro.features.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.natkibe.playerpro.core.contracts.FeatureModule
import com.natkibe.playerpro.player.FloatingPlayerService

class FloatingPlayerFeature(private val context: Context) : FeatureModule {
    override val name = "Floating Player"
    override val milestone = "v0.5-floating-resizable"

    fun canDrawOverApps(): Boolean = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun overlayPermissionIntent(): Intent? = if (Build.VERSION.SDK_INT >= 23) {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    } else null

    fun startIfAllowed(): Boolean {
        if (!canDrawOverApps()) return false
        val intent = Intent(context, FloatingPlayerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        return true
    }
}
