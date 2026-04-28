package com.phoneagent.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object OverlayPermissionManager {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun createOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun handleOverlayPermissionResult(context: Context) {
        if (canDrawOverlays(context)) {
            Toast.makeText(context, "Overlay permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Overlay permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
