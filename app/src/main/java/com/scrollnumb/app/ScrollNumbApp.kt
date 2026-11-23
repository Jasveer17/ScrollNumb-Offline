package com.scrollnumb.app

import android.app.Application
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.service.DailyResetAlarm
import com.scrollnumb.app.service.DailyResetWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ScrollNumbApp : Application() {

    @Inject lateinit var userPreferences: UserPreferences

    override fun onCreate() {
        super.onCreate()

        // Stamp the install date once so the streak never credits pre-install days.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            userPreferences.ensureInstallDate()
        }

        // Daily streak evaluation. Three overlapping triggers so yesterday's
        // restraint XP + streak get credited even with battery optimization ON:
        //  1. AlarmManager (Doze-friendly, no exemption needed) — primary.
        //  2. Periodic WorkManager — backup.
        //  3. A one-shot catch-up now, so opening the app also finalizes.
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("cloud_sync")
        DailyResetAlarm.schedule(this)
        DailyResetWorker.schedule(this)
        DailyResetWorker.runNow(this)

    }
}
