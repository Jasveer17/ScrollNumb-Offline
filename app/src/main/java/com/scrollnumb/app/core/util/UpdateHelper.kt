package com.scrollnumb.app.core.util

import android.app.Activity
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Manual "Check for updates" from Settings. The automatic flexible check already
 * runs at every launch (InAppUpdateManager in MainActivity); this one gives the
 * user explicit feedback: starts the Play update flow when a newer version is
 * live, toasts when already up to date, and falls back to the Play Store page
 * when the build isn't Play-installed (sideload/debug).
 */
object UpdateHelper {

    fun checkNow(activity: Activity) {
        Toast.makeText(activity, "Checking for updates…", Toast.LENGTH_SHORT).show()
        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val resumable =
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE || resumable) {
                    val type = if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE))
                        AppUpdateType.FLEXIBLE else AppUpdateType.IMMEDIATE
                    if (type == AppUpdateType.FLEXIBLE) {
                        // Install as soon as the background download finishes.
                        lateinit var listener: InstallStateUpdatedListener
                        listener = InstallStateUpdatedListener { state ->
                            when (state.installStatus()) {
                                InstallStatus.DOWNLOADED -> {
                                    manager.unregisterListener(listener)
                                    manager.completeUpdate()
                                }
                                InstallStatus.FAILED, InstallStatus.CANCELED ->
                                    manager.unregisterListener(listener)
                                else -> Unit
                            }
                        }
                        manager.registerListener(listener)
                    }
                    runCatching {
                        manager.startUpdateFlow(info, activity, AppUpdateOptions.newBuilder(type).build())
                    }.onFailure { ReviewHelper.openPlayStore(activity) }
                } else {
                    Toast.makeText(activity, "You're on the latest version 🌿", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                // Not installed from Play — manual update via the store page.
                ReviewHelper.openPlayStore(activity)
            }
    }
}
