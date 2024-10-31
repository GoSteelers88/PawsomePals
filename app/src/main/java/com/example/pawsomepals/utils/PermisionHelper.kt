// PermissionHelper.kt
package com.example.pawsomepals.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity

object PermissionHelper {
    fun shouldShowRequestPermissionRationale(context: Context, permission: String): Boolean {
        return if (context is FragmentActivity) {
            ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
        } else {
            false
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}