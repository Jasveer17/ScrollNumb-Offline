package com.scrollnumb.app.core.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play In-App Review. Shows the native rating sheet inside the app (far
 * more reviews than deep-linking to the store). Play may throttle/skip the sheet
 * silently — that's expected — so on any failure we fall back to the store page.
 */
object ReviewHelper {

    fun request(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result)
            } else {
                openPlayStore(activity)
            }
        }
    }

    fun openPlayStore(activity: Activity) {
        val pkg = activity.packageName
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        }.onFailure {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            )
        }
    }
}
