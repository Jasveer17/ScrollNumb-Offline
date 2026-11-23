package com.scrollnumb.app.data.repository

import android.util.Log
import com.scrollnumb.app.core.database.dao.DailyStatsDao
import com.scrollnumb.app.core.database.dao.ScrollEventDao
import com.scrollnumb.app.core.database.dao.AppSessionDao
import com.scrollnumb.app.core.database.entity.DailyStatsEntity
import com.scrollnumb.app.core.database.entity.ScrollEventEntity
import com.scrollnumb.app.core.database.entity.AppSessionEntity
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.util.Constants
import com.scrollnumb.app.core.util.LeagueSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrollRepository @Inject constructor(
    private val scrollEventDao: ScrollEventDao,
    private val dailyStatsDao: DailyStatsDao,
    private val appSessionDao: AppSessionDao,
    private val userPreferences: UserPreferences
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val _scrollUpdates = MutableSharedFlow<Int>(replay = 1)
    val scrollUpdates = _scrollUpdates.asSharedFlow()
    private val recordMutex = kotlinx.coroutines.sync.Mutex()

    // Room remains durable on every scroll. Only a compact live counter is
    // mirrored to Realtime Database; Firestore receives the durable snapshot
    // from CloudSyncManager at most once per day.
    private fun today(): String = dateFormat.format(Date())

    suspend fun recordScroll(platform: String, sessionId: String, dailyLimit: Int = 0): Int {
        val todayStr = today()
        val newTotal = recordMutex.withLock {
            val currentStats = dailyStatsDao.getStatsForDate(todayStr)
            val count = (currentStats?.totalScrolls ?: 0) + 1

            scrollEventDao.insertEvent(
                ScrollEventEntity(
                    timestamp = System.currentTimeMillis(),
                    platform = platform,
                    sessionId = sessionId,
                    globalCountAtEvent = count
                )
            )

            // Latch "exceeded today" against the limit ACTIVE RIGHT NOW. Once set
            // it stays set, so changing the limit later or undoing counts can't
            // rewrite the day's verdict — this is what the streak worker reads.
            val exceeded = (currentStats?.limitWasExceeded ?: false) ||
                (dailyLimit > 0 && count > dailyLimit)

            val updated = (currentStats ?: DailyStatsEntity(date = todayStr)).let { stats ->
                stats.copy(
                    totalScrolls = count,
                    limitWasExceeded = exceeded,
                    instagramScrolls = stats.instagramScrolls + if (platform == Constants.PLATFORM_INSTAGRAM) 1 else 0,
                    youtubeScrolls = stats.youtubeScrolls + if (platform == Constants.PLATFORM_YOUTUBE) 1 else 0,
                    facebookScrolls = stats.facebookScrolls + if (platform == Constants.PLATFORM_FACEBOOK) 1 else 0,
                    snapchatScrolls = stats.snapchatScrolls + if (platform == Constants.PLATFORM_SNAPCHAT) 1 else 0,
                    tiktokScrolls = stats.tiktokScrolls + if (platform == Constants.PLATFORM_TIKTOK) 1 else 0,
                    linkedinScrolls = stats.linkedinScrolls + if (platform == Constants.PLATFORM_LINKEDIN) 1 else 0
                )
            }
            dailyStatsDao.insertOrUpdate(updated)
            count
        }

        _scrollUpdates.emit(newTotal)
        
        return newTotal
    }

    /**
     * Mark today as "measured" by ensuring a stats row exists (0 scrolls if
     * none yet). The streak worker uses row-presence to tell a real under-limit
     * day from a day the service never ran (phone off) — see DailyResetWorker.
     */
    suspend fun ensureTodayStatsRow() {
        val todayStr = today()
        recordMutex.withLock {
            if (dailyStatsDao.getStatsForDate(todayStr) == null) {
                dailyStatsDao.insertOrUpdate(DailyStatsEntity(date = todayStr))
                true
            } else {
                false
            }
        }

    }

    fun getTodayStatsFlow(): Flow<DailyStatsEntity?> = dailyStatsDao.getStatsForDateFlow(today())

    /** @return the new total after removal, or null if nothing to remove. */
    suspend fun removeLastScroll(platform: String): Int? {
        val todayStr = today()
        val newTotal = recordMutex.withLock {
            val currentStats = dailyStatsDao.getStatsForDate(todayStr)
            if (currentStats == null || currentStats.totalScrolls <= 0) return@withLock null
            // When the caller doesn't know the platform (e.g. "recover focus"),
            // resolve the last scroll's platform so the per-platform counters
            // and event log stay consistent with the decremented total.
            val plat = platform.ifEmpty {
                scrollEventDao.getLatestEventPlatformForDate(todayStr).orEmpty()
            }
            val total = currentStats.totalScrolls - 1
            val updated = currentStats.copy(
                totalScrolls = total,
                instagramScrolls = if (plat == Constants.PLATFORM_INSTAGRAM) maxOf(0, currentStats.instagramScrolls - 1) else currentStats.instagramScrolls,
                youtubeScrolls = if (plat == Constants.PLATFORM_YOUTUBE) maxOf(0, currentStats.youtubeScrolls - 1) else currentStats.youtubeScrolls,
                facebookScrolls = if (plat == Constants.PLATFORM_FACEBOOK) maxOf(0, currentStats.facebookScrolls - 1) else currentStats.facebookScrolls,
                snapchatScrolls = if (plat == Constants.PLATFORM_SNAPCHAT) maxOf(0, currentStats.snapchatScrolls - 1) else currentStats.snapchatScrolls,
                tiktokScrolls = if (plat == Constants.PLATFORM_TIKTOK) maxOf(0, currentStats.tiktokScrolls - 1) else currentStats.tiktokScrolls,
                linkedinScrolls = if (plat == Constants.PLATFORM_LINKEDIN) maxOf(0, currentStats.linkedinScrolls - 1) else currentStats.linkedinScrolls
            )
            dailyStatsDao.insertOrUpdate(updated)
            if (plat.isNotEmpty()) {
                scrollEventDao.deleteLatestEvent(plat)
            }
            total
        }
        // Notify observers (Dashboard + the running accessibility service) so the
        // shield/cap/widget reflect the lowered count, mirroring recordScroll.
        if (newTotal != null) {
            _scrollUpdates.emit(newTotal)
            
        }
        return newTotal
    }

    suspend fun getTodayStats(): DailyStatsEntity? = dailyStatsDao.getStatsForDate(today())

    suspend fun getStatsForRange(startDate: String, endDate: String): List<DailyStatsEntity> =
        dailyStatsDao.getStatsForDateRange(startDate, endDate)

    suspend fun getRecentStats(limit: Int = 30): List<DailyStatsEntity> =
        dailyStatsDao.getLatestStats(limit)

    suspend fun getEventsForDate(date: String): List<ScrollEventEntity> =
        scrollEventDao.getEventsForDate(date)

    suspend fun recordSession(platform: String, startTime: Long, endTime: Long) {
        val todayStr = today()
        val totalMs = endTime - startTime
        if (totalMs <= 0) return

        appSessionDao.insertSession(
            AppSessionEntity(
                date = todayStr,
                platform = platform,
                startTime = startTime,
                endTime = endTime,
                totalTimeMs = totalMs
            )
        )

        // Also update total time in DailyStats. Store seconds for accuracy
        // and derive minutes (rounded) for legacy display columns.
        // Same read-modify-write as recordScroll, so it must hold the same
        // mutex — a session ending as a scroll lands (quit right after a
        // swipe) could otherwise clobber the fresher row with stale counts.
        recordMutex.withLock {
            val currentStats = dailyStatsDao.getStatsForDate(todayStr) ?: DailyStatsEntity(date = todayStr)
            val secondsToAdd = (totalMs / 1000).toInt()
            val newTotalSeconds = currentStats.totalScrollTimeSeconds + secondsToAdd
            val newTotalMinutes = (newTotalSeconds + 30) / 60

            val updated = currentStats.copy(
                totalScrollTimeSeconds = newTotalSeconds,
                totalScrollTimeMinutes = newTotalMinutes,
                sessionCount = currentStats.sessionCount + 1
            )
            dailyStatsDao.insertOrUpdate(updated)
        }
    }

    suspend fun removeLastSession(platform: String, startTime: Long, endTime: Long) {
        val todayStr = today()
        val totalMs = endTime - startTime
        if (totalMs <= 0) return
        recordMutex.withLock {
            val currentStats = dailyStatsDao.getStatsForDate(todayStr) ?: return@withLock
            val secondsToRemove = (totalMs / 1000).toInt()
            val newTotalSeconds = (currentStats.totalScrollTimeSeconds - secondsToRemove).coerceAtLeast(0)
            val newTotalMinutes = (newTotalSeconds + 30) / 60
            val updated = currentStats.copy(
                totalScrollTimeSeconds = newTotalSeconds,
                totalScrollTimeMinutes = newTotalMinutes,
                sessionCount = (currentStats.sessionCount - 1).coerceAtLeast(0)
            )
            dailyStatsDao.insertOrUpdate(updated)
        }
    }

    fun getTotalTimeForTodayFlow(): Flow<Long?> = appSessionDao.getTotalTimeForDateFlow(today())

    fun getTotalScrollsFlow(): Flow<Int> = dailyStatsDao.getTotalScrollsFlow()

    // ── Lifetime / weekly scroll aggregates ─────────────────────────────────
    /** All-time reels counted across every recorded day. */
    suspend fun lifetimeScrolls(): Int = dailyStatsDao.getTotalScrollsSync()

    /** Reels in the current Monday-start competition week. */
    suspend fun weeklyScrolls(): Int {
        return dailyStatsDao.getScrollsInRange(LeagueSystem.weekId(), today())
    }

}
