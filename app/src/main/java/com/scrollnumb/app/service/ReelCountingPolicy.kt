package com.scrollnumb.app.service

import com.scrollnumb.app.core.util.Constants

internal object ReelCountingPolicy {
    fun isLikelyTikTokPanelContainer(
        viewId: String,
        className: String,
        isScrollable: Boolean,
        width: Int,
        height: Int,
        rootWidth: Int,
        rootHeight: Int
    ): Boolean {
        if (rootWidth <= 0 || rootHeight <= 0 || width <= 0 || height <= 0) return false
        if (viewId.contains("view_rootview", ignoreCase = true)) return false
        val listLike = isScrollable || className.contains("recyclerview", ignoreCase = true) ||
            className.contains("listview", ignoreCase = true)
        if (!listLike) return false
        return width >= rootWidth * 0.70f &&
            height >= rootHeight * 0.35f && height < rootHeight * 0.92f
    }

    fun shouldRememberUncountedEntry(platform: String): Boolean =
        platform == Constants.PLATFORM_TIKTOK

    fun canCountPageOnlyTransition(platform: String): Boolean =
        platform != Constants.PLATFORM_TIKTOK

    fun canBypassSeenVideoForPageAdvance(platform: String): Boolean =
        platform != Constants.PLATFORM_TIKTOK

    fun shouldSynthesizeMissingPage(platform: String): Boolean =
        platform != Constants.PLATFORM_YOUTUBE && platform != Constants.PLATFORM_TIKTOK

    fun shouldAllowUnknownScrollSource(platform: String): Boolean =
        platform != Constants.PLATFORM_TIKTOK
}
