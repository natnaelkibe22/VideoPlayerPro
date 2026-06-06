package com.natkibe.videoplayerpro.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helper that checks and requests overlay permission.
 * Works on API 23+ (Marshmallow), silently passes on older versions.
 */
class OverlayPermissionHelper(private val context: Context) {

    /** Whether the app can draw overlays right now. */
    fun canDrawOverApps(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    /** Build an intent that opens the system overlay-permission settings for this app,
     *  or null on API < 23. */
    fun permissionIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= 23) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else null

    /** Check and return a summary for logging/debugging. */
    fun statusText(): String =
        "${context.packageName} overlay=${canDrawOverApps()}"
}
