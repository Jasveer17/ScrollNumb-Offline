package com.scrollnumb.app.core.permissions

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.scrollnumb.app.MainActivity
import com.scrollnumb.app.service.ScrollAccessibilityService

/**
 * Centralised checks + launchers for every special permission ScrollNumb
 * needs in order to track app usage and keep running in the background.
 */
object PermissionsHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ScrollAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = splitter.next()
            val enabledComponent = ComponentName.unflattenFromString(component)
            if (enabledComponent == expected ||
                component.equals(expected.flattenToString(), ignoreCase = true) ||
                component.equals(expected.flattenToShortString(), ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(context)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // --- Launchers ---

    /** Armed when we hand the user off to system accessibility settings; the
     *  service's onServiceConnected reads it to bounce straight back into the
     *  app the moment the toggle is flipped ON (no double Back needed). */
    const val KEY_RETURN_AFTER_ACCESS_GRANT = "RETURN_TO_APP_AFTER_ACCESS_GRANT"
    const val KEY_RETURN_AFTER_OVERLAY_GRANT = "RETURN_TO_APP_AFTER_OVERLAY_GRANT"
    const val KEY_RETURN_AFTER_USAGE_GRANT = "RETURN_TO_APP_AFTER_USAGE_GRANT"

    private const val RETURN_GRANT_WINDOW_MS = 10 * 60_000L
    private const val RETURN_POLL_MS = 700L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val returnMonitorsRunning = mutableSetOf<String>()

    fun openAccessibilitySettings(context: Context) {
        armReturnAfterGrant(context, KEY_RETURN_AFTER_ACCESS_GRANT)
        val component = ComponentName(context, ScrollAccessibilityService::class.java)
        val flattened = component.flattenToString()

        // Android 12+: deep-link straight to ScrollNumb's own accessibility page.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra("android.intent.extra.COMPONENT_NAME", flattened)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(details)
                return
            } catch (e: Exception) {
                // fall through to the list + highlight approach
            }
        }

        // Older OS / fallback: open the accessibility list but ask Settings to
        // scroll to and highlight ScrollNumb so the user can find it quickly.
        val highlightArgs = android.os.Bundle().apply {
            putString(":settings:fragment_args_key", flattened)
        }
        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", flattened)
            putExtra(":settings:show_fragment_args", highlightArgs)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(list)
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        armReturnAfterGrant(context, KEY_RETURN_AFTER_OVERLAY_GRANT)
        startReturnMonitor(context.applicationContext, KEY_RETURN_AFTER_OVERLAY_GRANT) {
            canDrawOverlays(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: open the "Display over other apps" LIST itself —
            // the user scrolls to ScrollNumb and toggles it. More direct than
            // the old App-Info hop (which needed an extra row tap and read as
            // a dead end for some users). The package URI is ignored on 11+,
            // so the plain list intent is the honest UX.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                Toast.makeText(
                    context,
                    "Find ScrollNumb in the list → Allow",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (e: Exception) {
                // fall through to the classic intents below
            }
        }
        // Android 10 and below: the package-scoped intent genuinely opens
        // ScrollNumb's own overlay toggle.
        val perApp = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try {
            context.startActivity(perApp)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e2: Exception) {
                openAppDetails(context)
            }
        }
    }

    private fun armReturnAfterGrant(context: Context, markerKey: String) {
        context.getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong(markerKey, System.currentTimeMillis())
            .putString(returnActivityKey(markerKey), context.findActivityClassName() ?: MainActivity::class.java.name)
            .apply()
    }

    fun returnToAppAfterPermissionGrant(context: Context, markerKey: String): Boolean {
        val prefs = context.getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
        val armedAt = prefs.getLong(markerKey, 0L)
        if (armedAt <= 0L) return false

        val activityName = prefs.getString(returnActivityKey(markerKey), MainActivity::class.java.name)
            ?: MainActivity::class.java.name
        prefs.edit()
            .remove(markerKey)
            .remove(returnActivityKey(markerKey))
            .apply()

        if (System.currentTimeMillis() - armedAt >= RETURN_GRANT_WINDOW_MS) return false

        return try {
            val activityClass = Class.forName(activityName)
            context.startActivity(Intent(context, activityClass).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            })
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Polls [granted] while the user is off in system settings and bounces
     * straight back to the arming activity the moment the permission turns ON
     * (no Back-Back navigation needed). One monitor per marker key.
     */
    private fun startReturnMonitor(
        context: Context,
        markerKey: String,
        granted: (Context) -> Boolean
    ) {
        if (!returnMonitorsRunning.add(markerKey)) return
        val appContext = context.applicationContext

        fun poll() {
            val prefs = appContext.getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
            val armedAt = prefs.getLong(markerKey, 0L)
            when {
                armedAt <= 0L -> returnMonitorsRunning.remove(markerKey)
                System.currentTimeMillis() - armedAt >= RETURN_GRANT_WINDOW_MS -> {
                    prefs.edit()
                        .remove(markerKey)
                        .remove(returnActivityKey(markerKey))
                        .apply()
                    returnMonitorsRunning.remove(markerKey)
                }
                granted(appContext) -> {
                    returnMonitorsRunning.remove(markerKey)
                    val backDispatched = markerKey == KEY_RETURN_AFTER_USAGE_GRANT &&
                        ScrollAccessibilityService.returnFromPermissionSettings()
                    if (backDispatched) {
                        // performGlobalAction(BACK) only proves the Back was
                        // DISPATCHED — a deep Settings stack can swallow it and
                        // leave the user in Settings. Verify the app actually
                        // reached the foreground once the transition settles;
                        // fall back to the explicit relaunch if it didn't.
                        mainHandler.postDelayed({
                            if (isAppForeground(appContext)) {
                                prefs.edit()
                                    .remove(markerKey)
                                    .remove(returnActivityKey(markerKey))
                                    .apply()
                            } else {
                                returnToAppAfterPermissionGrant(appContext, markerKey)
                            }
                        }, 1_200L)
                    } else {
                        returnToAppAfterPermissionGrant(appContext, markerKey)
                    }
                }
                else -> mainHandler.postDelayed({ poll() }, RETURN_POLL_MS)
            }
        }

        mainHandler.postDelayed({ poll() }, RETURN_POLL_MS)
    }

    private fun returnActivityKey(markerKey: String) = "${markerKey}_ACTIVITY"

    private fun isAppForeground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager ?: return false
        val myPid = android.os.Process.myPid()
        return am.runningAppProcesses?.any {
            it.pid == myPid &&
                it.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } == true
    }

    private tailrec fun Context.findActivityClassName(): String? = when (this) {
        is Activity -> javaClass.name
        is ContextWrapper -> baseContext.findActivityClassName()
        else -> null
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = packageUri
            if (context.findActivityClassName() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't show the battery popup", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't show the battery popup", Toast.LENGTH_LONG).show()
        }
    }

    /** App's own details page (app highlighted) — generic fallback. */
    private fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // nothing else to try
        }
    }

    fun openUsageAccessSettings(context: Context) {
        // Bounce back to where the user left off the moment the toggle flips ON
        // (same pattern as the overlay grant).
        armReturnAfterGrant(context, KEY_RETURN_AFTER_USAGE_GRANT)
        startReturnMonitor(context.applicationContext, KEY_RETURN_AFTER_USAGE_GRANT) {
            hasUsageStatsPermission(it)
        }
        // Deep-link straight to ScrollNumb's own Usage-access page using just the
        // package URI — the same clean per-app pattern the accessibility deep-link
        // uses. (The older `:settings:show_fragment_args` highlight extras made
        // many builds open the full list instead, so they're intentionally gone.)
        // Falls back to the plain list, then app details, if the OEM rejects it.
        val perApp = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            if (context.findActivityClassName() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(perApp)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    if (context.findActivityClassName() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e2: Exception) {
                openAppDetails(context)
            }
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetails(context)
        }
    }
}
