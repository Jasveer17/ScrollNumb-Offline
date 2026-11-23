package com.scrollnumb.app.core.util

import com.scrollnumb.app.core.datastore.UserPreferences
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single entry point for (re)computing today's XP from the real counters and
 * banking it via [UserPreferences.recordDailyXp]. Every screen / lifecycle hook
 * that can change an input (reels scrolled, streak, math puzzles, breathing)
 * calls this, so the displayed total, the banked total, and the per-day
 * breakdown all come from one formula — no double counting, no drift.
 */
object XpLedger {
    suspend fun recompute(
        userPreferences: UserPreferences,
        streakDays: Int,
        reelsToday: Int,
        dailyLimit: Int
    ) {
        val mathPuzzles = userPreferences.mathFocusMinutesToday.first()
        val breatheSessions = userPreferences.breatheMinutesToday.first()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val weekId = LeagueSystem.weekId()
        // The install day is a PARTIAL day (user may have signed up at 23:00 with
        // 0 counted scrolls) — restraint XP for it would be unearned "free 80".
        // It stays 0 so nothing banks at the first midnight rollover.
        val isInstallDay = today == userPreferences.installDate.first()

        val bd = XpFormula.breakdown(
            streakDays = streakDays,
            mathFocusMinutes = mathPuzzles,
            breatheMinutes = breatheSessions,
            reelsToday = reelsToday,
            dailyLimit = dailyLimit
        )
        // Streak bonus is WRITE-ONCE for the day: granted while under the limit,
        // and KEPT even if the user later exceeds it. Breaking the streak only
        // stops FUTURE daily bonuses (streakDays resets to 0 at the next daily
        // evaluation) — it never claws back XP already awarded today.
        val storedStreakXp = userPreferences.weeklyXpHistory.first()
            .firstOrNull { it.date == today }?.streakXp ?: 0
        val streakXp = maxOf(bd.streakXp, storedStreakXp)

        val restraintXp = if (isInstallDay) 0 else bd.scrollLessXp
        userPreferences.recordDailyXp(
            activityXp = streakXp + bd.mathXp + bd.breatheXp,
            restraintXp = restraintXp,
            today = today,
            currentWeekId = weekId
        )
        userPreferences.recordDailyXpBreakdown(
            date = today,
            streakXp = streakXp,
            mathXp = bd.mathXp,
            breatheXp = bd.breatheXp,
            // Today's restraint is provisional until rollover; only the worker's
            // finalized value belongs in visible history.
            scrollLessXp = 0
        )
    }
}
