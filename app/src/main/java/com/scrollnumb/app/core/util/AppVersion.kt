package com.scrollnumb.app.core.util

import android.content.Context

/**
 * The app's real versionName read from the installed package — single source of
 * truth so version strings never drift from build.gradle. Returns "" if the
 * package info can't be read (should never happen for the running app).
 */
fun appVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""
