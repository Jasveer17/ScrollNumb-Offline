package com.scrollnumb.app.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Two-layer progression model.
 *
 *  - Lifetime (total) XP  -> never resets. Level 1 starts at 0 XP, then each
 *    XP_PER_LEVEL advances one level.
 *  - Weekly XP            -> resets every Sunday night. Drives Leagues/Leaderboard.
 *
 * The weekly reset + league promotion/demotion is authoritatively done by the
 * `weeklyReset` Cloud Function. The client only mirrors a local week id so the
 * UI shows 0 the instant a new week starts, before the next server sweep.
 */
object LeagueSystem {

    const val XP_PER_LEVEL = 1000
    const val MIN_PROMOTION_WEEKLY_XP = 20
    const val COMPETITION_DAILY_REEL_TARGET = 50
    private const val EXCESS_REELS_PER_SCORE_POINT = 5

    fun levelFor(totalXp: Int): Int = (totalXp.coerceAtLeast(0)) / XP_PER_LEVEL + 1

    /** XP accumulated inside the current level (0..XP_PER_LEVEL). */
    fun xpIntoLevel(totalXp: Int): Int = (totalXp.coerceAtLeast(0)) % XP_PER_LEVEL

    fun levelProgress(totalXp: Int): Float = xpIntoLevel(totalXp).toFloat() / XP_PER_LEVEL

    fun competitionDaysElapsed(now: Date = Date()): Int {
        val c = Calendar.getInstance(WEEK_TZ).apply { time = now }
        return when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    fun mindfulScore(
        weeklyXp: Int,
        weeklyScrolls: Int,
        daysElapsed: Int = competitionDaysElapsed()
    ): Int {
        val target = COMPETITION_DAILY_REEL_TARGET * daysElapsed.coerceIn(1, 7)
        val excess = (weeklyScrolls.coerceAtLeast(0) - target).coerceAtLeast(0)
        val penalty = (excess + EXCESS_REELS_PER_SCORE_POINT - 1) / EXCESS_REELS_PER_SCORE_POINT
        return weeklyXp.coerceAtLeast(0) - penalty
    }

    /** Public leaderboard XP follows the mindful score, but XP is never negative. */
    fun leaderboardXp(mindfulScore: Int): Int = mindfulScore.coerceAtLeast(0)

    /** App competition timezone. Week boundaries are computed here so the weekly
     *  reset lands on local (India) Monday 00:00, matching the server schedule. */
    private val WEEK_TZ = java.util.TimeZone.getTimeZone("Asia/Kolkata")

    /**
     * Week identifier = the Monday (ISO week start) of the week `now` falls in,
     * as yyyy-MM-dd, computed in IST. The competition week runs Mon 00:00 ->
     * Sun 23:59 IST; a fresh weekId means weekly XP should be 0.
     */
    fun weekId(now: Date = Date()): String {
        val c = Calendar.getInstance(WEEK_TZ)
        c.time = now
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).also { it.timeZone = WEEK_TZ }.format(c.time)
    }

    /** Competition day (IST), yyyy-MM-dd. Stamped on leaderboard rows so the
     *  daily tie-break only compares scroll counts from the same day. */
    fun dayId(now: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).also { it.timeZone = WEEK_TZ }.format(now)

    fun isWeekend(now: Date = Date()): Boolean {
        // Same competition timezone as weekId() — the 1.5x window must sit
        // inside the same Mon-Sun IST week it multiplies, for every user.
        val c = Calendar.getInstance(WEEK_TZ)
        c.time = now
        return when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY -> true
            else -> false
        }
    }

    /** Ordered low -> high. Server promotes/demotes between adjacent tiers. */
    val TIERS = listOf("Bronze", "Silver", "Gold", "Platinum", "Diamond")
    const val DEFAULT_TIER = "Bronze"

    fun tierEmoji(tier: String): String = when (tier) {
        "Diamond" -> "💎"
        "Platinum" -> "🔷"
        "Gold" -> "🥇"
        "Silver" -> "🥈"
        else -> "🥉"
    }

    fun normalizeTier(tier: String?): String =
        if (tier != null && TIERS.contains(tier)) tier else DEFAULT_TIER

    /** The tier a user promotes INTO, or null if already at the top (Diamond). */
    fun nextTier(tier: String): String? {
        val i = TIERS.indexOf(normalizeTier(tier))
        return if (i in 0 until TIERS.size - 1) TIERS[i + 1] else null
    }
}
