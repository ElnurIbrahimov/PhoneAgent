package com.phoneagent.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object OverlayPermissionManager {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(activity: Activity, requestCode: Int = REQUEST_CODE) {
        if (!canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, requestCode)
        } else {
            Toast.makeText(activity, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleOverlayPermissionResult(context: Context) {
        if (canDrawOverlays(context)) {
            Toast.makeText(context, "Overlay permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Overlay permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    const val REQUEST_CODE = 1001
}
