package com.scrollnumb.app.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Single source of truth for the home-screen widget. Callable from the app
 * (DB-backed today count) as well as the accessibility service (live count),
 * so the widget mirrors exactly what the app shows instead of drifting / going
 * stale when the service is idle or after the midnight reset.
 */
object WidgetUpdater {
    suspend fun update(context: Context, totalScrolls: Int, dailyLimit: Int) {
        val manager = GlanceAppWidgetManager(context)
        val widget = ScrollNumbWidget()
        manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[intPreferencesKey("total_scrolls")] = totalScrolls
                prefs[intPreferencesKey("daily_limit")] = dailyLimit.coerceAtLeast(1)
            }
            widget.update(context, glanceId)
        }
    }
}
