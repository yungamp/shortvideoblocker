package io.github.yungamp.shortvideoblocker.inspector

import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

data class AppVersion(val versionName: String?, val versionCode: Long)

object AppVersions {
    /** Returns the installed version of [pkg] or null if it is not installed. */
    fun of(pm: PackageManager, pkg: String): AppVersion? = try {
        val info = pm.getPackageInfo(pkg, 0)
        AppVersion(info.versionName, PackageInfoCompat.getLongVersionCode(info))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
