package com.scrollnumb.app.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.scrollnumb.app.core.permissions.PermissionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UsageStatsMonitor(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Periodically polls UsageStatsManager to determine the foreground application.
     * Yields the package name of the active app, or null if Usage Stats permission
     * has not been granted.
     */
    fun pollForegroundApp(intervalMs: Long = 2000L): Flow<String?> = flow {
        while (true) {
            val foregroundApp = if (PermissionsHelper.hasUsageStatsPermission(context)) {
                getForegroundAppReliable()
            } else {
                null
            }
            emit(foregroundApp)
            delay(intervalMs)
        }
    }

    private fun getForegroundAppReliable(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        var lastPackageName: String? = null
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            // ACTIVITY_RESUMED means the app came to the foreground
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackageName = event.packageName
            }
        }

        return lastPackageName
    }
}
