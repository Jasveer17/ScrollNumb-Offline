package com.scrollnumb.app.data.repository

import com.scrollnumb.app.core.database.dao.StreakDao
import com.scrollnumb.app.core.database.entity.StreakEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to the streak. The streak (continue/break, freeze-token
 * earn/spend) is owned entirely by [com.scrollnumb.app.service.DailyResetWorker],
 * which evaluates each finished day against the daily limit. The old manual
 * mutation API (incrementStreak/addStrike/clearStrike/useFreezeToken) was never
 * wired and was superseded by the worker, so it has been removed to avoid a
 * second writer fighting the worker over lastUpdatedDate.
 */
@Singleton
class StreakRepository @Inject constructor(
    private val streakDao: StreakDao
) {
    fun getStreakStatus(): Flow<StreakEntity?> = streakDao.getStreak()
}
