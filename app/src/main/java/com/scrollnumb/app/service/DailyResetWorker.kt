package com.scrollnumb.app.service

import android.content.Context
import androidx.work.*
import com.scrollnumb.app.core.database.dao.DailyStatsDao
import com.scrollnumb.app.core.database.dao.StreakDao
import com.scrollnumb.app.core.database.entity.StreakEntity
import com.scrollnumb.app.core.util.Constants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.scrollnumb.app.core.datastore.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        runReset(applicationContext)
        Result.success()
    } catch (e: Exception) {
        // Transient failure (DB busy, etc.) must not silently skip a whole day of
        // streak evaluation — let WorkManager retry with backoff.
        Result.retry()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DailyResetEntryPoint {
        fun dailyStatsDao(): DailyStatsDao
        fun streakDao(): StreakDao
        fun userPreferences(): UserPreferences
    }

    companion object {
        private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Serializes the three overlapping triggers (periodic worker, midnight
        // alarm, on-launch catch-up) so the streak read-modify-write can't run
        // concurrently and double-advance.
        private val resetMutex = Mutex()

        /**
         * Finalize yesterday's restraint XP and catch up every missed streak day.
         * Runs from the WorkManager job, the midnight [DailyResetReceiver] alarm
         * (which fires inside Doze, unlike WorkManager, so the reset lands even
         * without a battery-optimization exemption), and on app launch. Idempotent
         * and mutually excluded, so safe to call from any of them at any time.
         */
        suspend fun runReset(context: Context) = resetMutex.withLock {
            runResetLocked(context)
        }

        private suspend fun runResetLocked(context: Context) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DailyResetEntryPoint::class.java
            )
            val dailyStatsDao = entryPoint.dailyStatsDao()
            val streakDao = entryPoint.streakDao()
            val userPreferences = entryPoint.userPreferences()
            val fmt = dateFormat

            val yesterdayStr = fmt.format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
            )
            val installDate = userPreferences.installDate.first().ifEmpty { yesterdayStr }

            // Finalize yesterday's restraint XP before recordDailyXp banks it: the
            // ledger holds whatever the LAST recompute wrote, which is stale (too
            // high) if the user scrolled after the app was last opened. No-op when
            // the rollover already happened (day key moved on). The INSTALL day
            // always finalizes at 0 — it's a partial day (signup at 23:00 = 0
            // scrolls), and banking the full restraint bonus for it handed every
            // new account "free 80 XP" at the first midnight.
            val yesterdayStats = dailyStatsDao.getStatsForDate(yesterdayStr)
            val yesterdayLimit = userPreferences.dailyLimit.first()
            val restraint = if (yesterdayStr == installDate || yesterdayStats == null) 0
                else com.scrollnumb.app.core.util.XpFormula.restraint(yesterdayStats.totalScrolls, yesterdayLimit)
            userPreferences.finalizeRestraintIfPending(yesterdayStr, restraint)

            val streak = streakDao.getStreakSync() ?: StreakEntity()
            // Idempotency: already evaluated through yesterday → nothing to do.
            if (streak.lastUpdatedDate == yesterdayStr) return

            // First day to evaluate = day after the last evaluated day, but never
            // before the install date (so a fresh install gets no free pre-install
            // days). Empty lastUpdatedDate → start at install date.
            var cursor = if (streak.lastUpdatedDate.isNotEmpty())
                nextDay(streak.lastUpdatedDate) else installDate
            if (cursor < installDate) cursor = installDate

            var current = streak.currentStreak
            var longest = streak.longestStreak
            var tokens = streak.freezeTokens
            var strikes = streak.strikesThisWeek
            var lastEval = streak.lastUpdatedDate
            // Week the current strike count belongs to. Crossing into a new week
            // (Mon-start) zeroes the count before that day is evaluated.
            var weekKey = if (streak.lastUpdatedDate.isNotEmpty())
                weekKeyFor(streak.lastUpdatedDate) else weekKeyFor(cursor)

            // Catch up EVERY missed day (phone off / Doze can skip runs), not just
            // yesterday. Guard caps the loop.
            var guard = 0
            while (cursor <= yesterdayStr && guard < 120) {
                guard++
                // Reset the weekly strike tally on the first day of a new week,
                // regardless of whether the day is trackable.
                val cursorWeek = weekKeyFor(cursor)
                if (cursorWeek != weekKey) {
                    strikes = 0
                    weekKey = cursorWeek
                }
                // A stats row is the historical proof that tracking ran that day;
                // today's tracking switch must not rewrite yesterday's verdict.
                // The install day remains neutral because it is only a partial day.
                val trackable = cursor > installDate
                if (trackable) {
                    val stats = dailyStatsDao.getStatsForDate(cursor)
                    when {
                        // No row = the service never ran that day (phone off, etc.).
                        // We couldn't measure it, so it's NEUTRAL: don't extend the
                        // streak, don't break it, don't grant a freeze.
                        stats == null -> Unit
                        !stats.limitWasExceeded -> {
                            current += 1
                            longest = maxOf(longest, current)
                            // Perfect week (7 clean days in a row) earns a freeze,
                            // capped so they can't be hoarded into immunity.
                            if (current % Constants.STREAK_FREEZE_EARN_INTERVAL == 0 &&
                                tokens < Constants.STREAK_FREEZE_MAX) {
                                tokens += 1
                            }
                        }
                        // Burn a freeze only when there IS a streak to protect.
                        current > 0 && tokens > 0 -> tokens -= 1
                        else -> {                            // over limit, no token → strike (break)
                            current = 0
                            strikes += 1
                        }
                    }
                }
                lastEval = cursor
                cursor = nextDay(cursor)
            }

            // Milestone analytics when the streak crosses a new multiple of 7.
            if (current > streak.currentStreak && current > 0 && current % 7 == 0) {
            }
            streakDao.insertOrUpdate(
                streak.copy(
                    currentStreak = current,
                    longestStreak = longest,
                    freezeTokens = tokens,
                    strikesThisWeek = strikes,
                    lastUpdatedDate = lastEval
                )
            )

            // BANK it now: recompute rolls the ledger's day key over, moving
            // yesterday's finalized (activity + restraint) XP into the lifetime /
            // weekly totals — so the credit lands at midnight without the user
            // opening the app. Idempotent (recordDailyXp banks a day only once).
            val todayStr = fmt.format(Date())
            val todayReels = dailyStatsDao.getStatsForDate(todayStr)?.totalScrolls ?: 0
            val limit = userPreferences.dailyLimit.first()
            com.scrollnumb.app.core.util.XpLedger.recompute(
                userPreferences = userPreferences,
                streakDays = current,
                reelsToday = todayReels,
                dailyLimit = limit
            )
        }

        /** yyyy-MM-dd of the day after [date]. */
        private fun nextDay(date: String): String {
            val cal = Calendar.getInstance()
            cal.time = dateFormat.parse(date) ?: return date
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return dateFormat.format(cal.time)
        }

        /** Monday (yyyy-MM-dd) of the week [date] falls in — the weekly strike bucket. */
        private fun weekKeyFor(date: String): String {
            val cal = Calendar.getInstance()
            cal.time = dateFormat.parse(date) ?: return date
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            return dateFormat.format(cal.time)
        }

        /** Periodic WorkManager backup (KEEP). The alarm is the primary trigger. */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = midnight.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_reset",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** One-shot catch-up (used on app open — WorkManager is fine when awake). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyResetWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "daily_reset_now",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
