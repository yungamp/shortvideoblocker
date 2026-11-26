package io.github.yungamp.shortvideoblocker.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.yungamp.shortvideoblocker.service.BlockerAccessibilityService

object A11yStatus {
    /** True if the user switched the service on in the accessibility settings. */
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** App info page – contains "Allow restricted settings" in the ⋮ menu on Android 13+. */
    fun openAppInfo(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
