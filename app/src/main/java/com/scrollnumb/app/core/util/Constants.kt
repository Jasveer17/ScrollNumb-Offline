package com.scrollnumb.app.core.util

object Constants {
    const val DEFAULT_DAILY_LIMIT = 50
    const val MAX_STRIKES_PER_WEEK = 3
    const val STREAK_FREEZE_EARN_INTERVAL = 7
    const val STREAK_FREEZE_MAX = 5          // hard cap on stored freezes

    // Platform package names
    const val PKG_INSTAGRAM = "com.instagram.android"
    const val PKG_YOUTUBE = "com.google.android.youtube"
    const val PKG_FACEBOOK = "com.facebook.katana"
    const val PKG_SNAPCHAT = "com.snapchat.android"
    const val PKG_LINKEDIN = "com.linkedin.android"
    const val PKG_TIKTOK = "com.zhiliaoapp.musically"
    const val PKG_TIKTOK_AWEME = "com.ss.android.ugc.aweme"
    const val PKG_TIKTOK_TRILL = "com.ss.android.ugc.trill"

    const val PLATFORM_INSTAGRAM = "instagram"
    const val PLATFORM_YOUTUBE = "youtube"
    const val PLATFORM_FACEBOOK = "facebook"
    const val PLATFORM_SNAPCHAT = "snapchat"
    const val PLATFORM_LINKEDIN = "linkedin"
    const val PLATFORM_TIKTOK = "tiktok"

    // Brain state thresholds
    const val THRESHOLD_HEALTHY = 1
    const val THRESHOLD_MILD = 21
    const val THRESHOLD_WARNING = 51
    const val THRESHOLD_CRITICAL = 101
    const val THRESHOLD_NUMB = 201

    // XP values
    const val XP_DAILY_CLEAN = 100
    const val XP_SWAP_COMPLETE = 25
    const val XP_STREAK_MILESTONE_7 = 500
    const val XP_STREAK_MILESTONE_14 = 1000
    const val XP_STREAK_MILESTONE_30 = 2000
    const val XP_CLAN_WIN = 50
    const val XP_STRIKE_CLEAR = 30

    // AI thresholds
    const val FRENZY_SCROLLS_PER_MIN = 15
    const val FAST_SCROLLS_PER_MIN = 8
    const val NORMAL_SCROLLS_PER_MIN = 3
    const val LIMIT_WARNING_PERCENT = 0.8f

    // Scroll debounce
    const val SCROLL_DEBOUNCE_MS = 300L
    const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
}
