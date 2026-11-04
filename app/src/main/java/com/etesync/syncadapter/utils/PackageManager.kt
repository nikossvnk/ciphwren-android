package com.etesync.syncadapter.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build


fun packageInstalled(context: Context, packageName: String): Boolean {
    try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        return false
    }

    return true
}

/** Package name of the app (store) which installed this app, or null if unknown (e.g. installed from an APK) */
fun installerPackageName(context: Context): String? {
    val pm = context.packageManager
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        else
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
