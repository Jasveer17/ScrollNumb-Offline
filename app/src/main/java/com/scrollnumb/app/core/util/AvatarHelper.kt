package com.scrollnumb.app.core.util

import android.content.Context
import android.net.Uri
import com.scrollnumb.app.R
import java.util.Locale

object AvatarHelper {

    /**
     * A photo URL we trust to render for OTHER users (not just the signed-in
     * owner): our own Firebase Storage bucket, or a Google account photo. Only
     * these are ever fetched for a non-self leaderboard row — an arbitrary URL in
     * a forged doc is never loaded (would leak the viewer's IP / allow a DoS).
     */
    fun isTrustedAvatarUrl(a: String): Boolean {
        val uri = runCatching { Uri.parse(a) }.getOrNull() ?: return false
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        if (host == "googleusercontent.com" || host.endsWith(".googleusercontent.com")) return true
        if (host != "firebasestorage.googleapis.com") return false

        val segments = uri.pathSegments
        return segments.size >= 5 &&
            segments[0] == "v0" && segments[1] == "b" &&
            segments[2] == "scroll-numb.firebasestorage.app" &&
            segments[3] == "o" && segments[4].startsWith("avatars/")
    }

    /**
     * What to store in the PUBLIC leaderboard doc: a short emoji, or a trusted
     * photo URL (Storage/Google). Anything else (blank, a device-local
     * content://file:// URI, or an untrusted URL) becomes "" so it falls back to
     * the gender illustration for viewers.
     */
    fun publicAvatar(a: String): String = when {
        a.isBlank() -> ""
        isTrustedAvatarUrl(a) -> a
        !a.startsWith("http") && !a.startsWith("content://") &&
            !a.startsWith("file://") && a.length <= 8 -> a // emoji
        else -> ""
    }

    fun getGender(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("USER_GENDER", "Male") ?: "Male"
    }

    fun getAwarenessAvatar(context: Context): Int {
        val isMale = getGender(context).equals("Male", ignoreCase = true)
        return if (isMale) R.drawable.male_avatar_more_tired else R.drawable.female_avatar_more_tired
    }

    fun getAvatarForPage(context: Context, pageName: String): Int {
        val isMale = getGender(context).equals("Male", ignoreCase = true)

        return when (pageName) {
            "your_brain_starts_everyday" -> {
                if (isMale) R.drawable.male_avatar_1 else R.drawable.female_avatar_1
            }
            "then_you_start_scrolling" -> {
                if (isMale) R.drawable.male_avatar_2 else R.drawable.female_avatar_2
            }
            "every_reel_drains_your_brain_more" -> {
                if (isMale) R.drawable.male_avatar_3 else R.drawable.female_avatar_3
            }
            "your_ability_to_focus_drops" -> {
                if (isMale) R.drawable.male_avatar_4 else R.drawable.female_avatar_4
            }
            "within_a_week_youll_scroll_less" -> {
                if (isMale) R.drawable.male_avatar_5 else R.drawable.female_avatar_5
            }
            "permissions" -> {
                if (isMale) R.drawable.male_avatar_7 else R.drawable.female_avatar_7
            }
            "welcome_to_scrollnumb" -> {
                if (isMale) R.drawable.male_avatar_fresh else R.drawable.female_avatar_fresh
            }
            else -> {
                if (isMale) R.drawable.male_avatar_fresh else R.drawable.female_avatar_fresh
            }
        }
    }

    fun getSmartLimitsAvatars(context: Context): Pair<Int, Int> {
        val isMale = getGender(context).equals("Male", ignoreCase = true)
        return if (isMale) {
            Pair(R.drawable.male_avatar_6, R.drawable.male_avatar_6_1)
        } else {
            Pair(R.drawable.female_avatar_6, R.drawable.female_avatar_6_1)
        }
    }

    fun getScrollStatusAvatar(context: Context, ratio: Float): Int {
        val isMale = getGender(context).equals("Male", ignoreCase = true)
        return if (isMale) {
            when {
                ratio < 0.25f -> R.drawable.male_avatar_fresh
                ratio < 0.55f -> R.drawable.male_avatar_little_tired
                ratio < 0.85f -> R.drawable.male_avatar_more_tired
                else -> R.drawable.male_avatar_very_tired
            }
        } else {
            when {
                ratio < 0.25f -> R.drawable.female_avatar_fresh
                ratio < 0.55f -> R.drawable.female_avatar_little_tired
                ratio < 0.85f -> R.drawable.female_avatar_more_tired
                else -> R.drawable.female_avatar_very_tired
            }
        }
    }

    fun getShieldAvatar(context: Context): Int {
        val isMale = getGender(context).equals("Male", ignoreCase = true)
        return if (isMale) R.drawable.male_avatar_shield else R.drawable.female_avatar_shield
    }

    fun getDefaultAvatar(gender: String): Int {
        return if (gender.equals("Female", ignoreCase = true))
            R.drawable.female_avatar_fresh
        else
            R.drawable.male_avatar_fresh
    }
}
