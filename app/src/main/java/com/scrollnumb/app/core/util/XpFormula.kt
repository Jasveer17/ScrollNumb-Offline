package com.scrollnumb.app.core.util

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Daily XP formula. Each component is what you can earn IN A SINGLE DAY — the
 * ledger (UserPreferences.recordDailyXp) banks one day's worth at a time, so
 * nothing here may be cumulative-over-time or it would re-mint every day.
 *
 * Design goals:
 *  - Streak pays a flat daily consistency bonus (with a small milestone boost),
 *    NOT the whole streak length, so it can be banked every day without inflation.
 *  - Staying under the limit pays a flat success bonus plus a restraint bonus
 *    scaled by the *ratio* of the limit used — not the absolute limit — so a user
 *    can't farm XP by setting a huge limit and ignoring the app.
 *  - Decompress activities pay modest, linear XP with per-day ceilings.
 */
object XpFormula {

    private const val STREAK_DAILY_BONUS = 20      // consistency bonus for keeping the streak today
    private const val STREAK_MILESTONE_BONUS = 30  // extra every 7th day
    private const val STREAK_MILESTONE_EVERY = 7

    // Less-scrolling reward is purely proportional to how far UNDER the limit you
    // stayed — no flat bonus, so scrolling right up to the cap earns ~0 and going
    // over earns nothing. A zero-scroll day earns the full amount.
    const val SCROLL_LESS_MAX = 80

    private const val MATH_XP_PER_PUZZLE = 5
    private const val MATH_DAILY_CAP_PUZZLES = 10   // up to 50 XP/day from puzzles
    const val BREATHE_XP_PER_SESSION = 20
    private const val BREATHE_DAILY_CAP_SESSIONS = 3 // up to 60 XP/day from breathing

    /** Flat bonus for maintaining the streak today, plus a weekly milestone kick. */
    private fun dailyStreakXp(streakDays: Int): Int {
        if (streakDays <= 0) return 0
        val milestone = if (streakDays % STREAK_MILESTONE_EVERY == 0) STREAK_MILESTONE_BONUS else 0
        // Base bonus is Remote-Config tunable; defaults to STREAK_DAILY_BONUS (20).
        return XpConfig.streakDailyBonus + milestone
    }

    private fun scrollLessXp(reelsToday: Int, dailyLimit: Int): Int {
        if (dailyLimit <= 0) return 0
        val reels = reelsToday.coerceAtLeast(0)
        if (reels >= dailyLimit) return 0          // at the limit the ratio is 0 anyway; over earns nothing
        // restraint: 1.0 = didn't scroll at all, →0 as you approach the limit.
        val restraint = 1f - (reels.toFloat() / dailyLimit)
        // Ceiling is Remote-Config tunable; defaults to SCROLL_LESS_MAX (80).
        return (restraint * XpConfig.scrollLessMax).roundToInt()
    }

    // Note: mathPuzzles / breatheSessions are COUNTS (one unit per solved puzzle /
    // completed breathing session), not literal minutes.
    private fun mathXp(mathPuzzles: Int): Int =
        min(mathPuzzles.coerceAtLeast(0), MATH_DAILY_CAP_PUZZLES) * MATH_XP_PER_PUZZLE

    private fun breatheXp(breatheSessions: Int): Int =
        min(breatheSessions.coerceAtLeast(0), BREATHE_DAILY_CAP_SESSIONS) * BREATHE_XP_PER_SESSION

    // "Kept the limit" = at or under it. Must match ScrollRepository's exceeded
    // latch (count > dailyLimit) and the streak worker's !limitWasExceeded, or
    // a day ending exactly ON the limit advances the streak yet forfeits the
    // streak bonus — the off-by-one this comparator previously had (`<`).
    private fun withinLimit(reelsToday: Int, dailyLimit: Int): Boolean =
        dailyLimit > 0 && reelsToday.coerceAtLeast(0) <= dailyLimit

    /** Restraint part of today's XP: depends on today's reel count (can drop). */
    fun restraint(reelsToday: Int, dailyLimit: Int): Int = scrollLessXp(reelsToday, dailyLimit)

    data class Breakdown(
        val streakXp: Int,
        val mathXp: Int,
        val breatheXp: Int,
        val scrollLessXp: Int
    ) {
        val total: Int get() = streakXp + mathXp + breatheXp + scrollLessXp
    }

    /** Today's earned-XP breakdown, for the profile explainer. */
    fun breakdown(
        streakDays: Int,
        mathFocusMinutes: Int,
        breatheMinutes: Int,
        reelsToday: Int,
        dailyLimit: Int
    ): Breakdown = Breakdown(
        streakXp = if (withinLimit(reelsToday, dailyLimit)) dailyStreakXp(streakDays) else 0,
        mathXp = mathXp(mathFocusMinutes),
        breatheXp = breatheXp(breatheMinutes),
        scrollLessXp = scrollLessXp(reelsToday, dailyLimit)
    )
}
