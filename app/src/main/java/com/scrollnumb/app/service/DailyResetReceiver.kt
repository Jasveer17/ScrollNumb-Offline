package com.scrollnumb.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when the midnight alarm goes off, or when the device finishes booting
 * (alarms don't survive a reboot, so re-arm). In both cases it runs the reset
 * DIRECTLY (via goAsync) — not through WorkManager — so the work completes inside
 * the Doze maintenance window instead of being deferred until the phone wakes.
 */
class DailyResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != ACTION_DAILY_RESET) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyResetWorker.runReset(appContext)
            } catch (_: Exception) {
                // Best-effort; the periodic WorkManager backup and on-open catch-up
                // will re-attempt. Never crash the receiver.
            } finally {
                // Re-arm tomorrow's alarm (and re-arm after a reboot).
                DailyResetAlarm.schedule(appContext)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAILY_RESET = "com.scrollnumb.app.action.DAILY_RESET"
    }
}
