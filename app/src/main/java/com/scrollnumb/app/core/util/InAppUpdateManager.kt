package com.scrollnumb.app.core.util

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play flexible in-app update: prompts a background download when a newer
 * version is live on Play, then installs it once downloaded. A no-op for builds
 * not installed from Play (sideload/debug), so it's always safe to call.
 */
class InAppUpdateManager(
    activity: Activity,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) manager.completeUpdate()
    }

    fun checkForUpdate() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            if (available && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                runCatching {
                    manager.startUpdateFlowForResult(
                        info,
                        launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                }
            }
        }
    }

    /** Finish an update that downloaded while the app was in the background. */
    fun completeIfDownloaded() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) manager.completeUpdate()
        }
    }

    fun unregister() {
        runCatching { manager.unregisterListener(listener) }
    }
}
