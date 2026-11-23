package com.scrollnumb.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules the daily streak/XP finalization via AlarmManager. Uses
 * `setAndAllowWhileIdle`, which fires during a Doze maintenance window **without
 * needing an exact-alarm permission or a battery-optimization exemption** — so
 * yesterday's restraint XP + streak get credited around midnight even if the
 * user never opens the app and never whitelisted us from battery optimization.
 * (Inexact by design; the reset logic catches up any drift, so approximate is fine.)
 */
object DailyResetAlarm {

    private const val REQUEST_CODE = 4801

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyResetReceiver::class.java).apply {
            action = DailyResetReceiver.ACTION_DAILY_RESET
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }
}
