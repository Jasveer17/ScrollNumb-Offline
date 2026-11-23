package com.scrollnumb.app.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.database.dao.DailyStatsDao
import com.scrollnumb.app.core.database.dao.MoodEntryDao
import com.scrollnumb.app.core.database.entity.DailyStatsEntity
import com.scrollnumb.app.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class StatsPeriod(val label: String, val days: Int) {
    DAY("Today", 1),
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
    LIFETIME("All time", 100_000)
}

data class ChartBar(val label: String, val value: Int)

data class AppBreakdown(
    val name: String,
    val count: Int,
    val fraction: Float
)

data class AnalyticsUiState(
    // True until the first Room load lands. The screen must NOT render its
    // "no data" empty state off the initial emptyList — that flashed "no
    // data" for a split second on every Stats open (loadAnalytics always
    // pads to 7 rows, so the empty state can only ever appear pre-load).
    val isLoading: Boolean = true,
    val weeklyStats: List<DailyStatsEntity> = emptyList(),
    val totalWeeklyMinutes: Int = 0,
    val avgPreMood: Float = 0f,
    val avgPostMood: Float = 0f,
    val moodDropPercentage: Int = 0,
    val totalScrollsThisWeek: Int = 0,
    val isRefreshing: Boolean = false,
    // Period chart state
    val period: StatsPeriod = StatsPeriod.DAY,
    val chartBars: List<ChartBar> = emptyList(),
    val periodTotal: Int = 0,
    val periodDailyCounts: List<Int> = emptyList(),
    val periodMinutes: Int = 0,
    val appBreakdown: List<AppBreakdown> = emptyList(),
    val dailyLimit: Int = 50
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val dailyStatsDao: DailyStatsDao,
    private val moodEntryDao: MoodEntryDao,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAnalytics()
        loadPeriod(StatsPeriod.DAY)
    }

    fun selectPeriod(period: StatsPeriod) {
        _uiState.value = _uiState.value.copy(period = period)
        loadPeriod(period)
    }

    private fun loadPeriod(period: StatsPeriod) {
        viewModelScope.launch {
            // Build chart bars: bucket by day (week), week-of-month (month),
            // month (year/lifetime). Today shows per-app bars instead.
            val dayLabel = SimpleDateFormat("EEE", Locale.getDefault())
            val monthLabel = SimpleDateFormat("MMM", Locale.getDefault())
            // Date KEY must match the Locale.US keys written by the repositories.
            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val stats = if (period == StatsPeriod.DAY) {
                listOfNotNull(dailyStatsDao.getStatsForDate(parse.format(Date())))
            } else {
                // getLatestStats returns the last N ROWS, not the last N days —
                // with skipped days a "week" could include rows far older than
                // 7 days and inflate the period total. Cut to the real window.
                val cutoffKey = if (period == StatsPeriod.LIFETIME) "" else {
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_YEAR, -(period.days - 1))
                    parse.format(c.time)
                }
                dailyStatsDao.getLatestStats(period.days)
                    .filter { it.date >= cutoffKey }
                    .reversed()
            }

            val bars: List<ChartBar> = when (period) {
                StatsPeriod.DAY -> {
                    val today = stats.lastOrNull()
                    listOf(
                        ChartBar("IG", today?.instagramScrolls ?: 0),
                        ChartBar("YT", today?.youtubeScrolls ?: 0),
                        ChartBar("FB", today?.facebookScrolls ?: 0),
                        ChartBar("SC", today?.snapchatScrolls ?: 0),
                        ChartBar("TT", today?.tiktokScrolls ?: 0),
                        ChartBar("LI", today?.linkedinScrolls ?: 0)
                    )
                }
                StatsPeriod.WEEK -> {
                    // Pad to 7 consecutive days ending today
                    val cal = Calendar.getInstance()
                    (6 downTo 0).map { back ->
                        val c = cal.clone() as Calendar
                        c.add(Calendar.DAY_OF_YEAR, -back)
                        val key = parse.format(c.time)
                        val hit = stats.find { it.date == key }
                        ChartBar(dayLabel.format(c.time).take(2), hit?.totalScrolls ?: 0)
                    }
                }
                StatsPeriod.MONTH -> {
                    // 4 weekly buckets
                    val buckets = IntArray(4)
                    stats.forEach { s ->
                        runCatching {
                            val d = parse.parse(s.date) ?: return@forEach
                            val daysAgo = ((System.currentTimeMillis() - d.time) / 86_400_000L).toInt()
                            val bucket = (3 - (daysAgo / 7).coerceIn(0, 3))
                            buckets[bucket] += s.totalScrolls
                        }
                    }
                    buckets.mapIndexed { i, v -> ChartBar("W${i + 1}", v) }
                }
                StatsPeriod.YEAR, StatsPeriod.LIFETIME -> {
                    // Monthly buckets, oldest → newest, max 12. Lifetime keys
                    // include the year so "Jul 2025" and "Jul 2026" don't merge
                    // into one bar.
                    val lifetimeLabel = SimpleDateFormat("MMM yy", Locale.getDefault())
                    val byMonth = LinkedHashMap<String, Int>()
                    stats.forEach { s ->
                        runCatching {
                            val d = parse.parse(s.date) ?: return@forEach
                            val key = if (period == StatsPeriod.LIFETIME)
                                lifetimeLabel.format(d) else monthLabel.format(d)
                            byMonth[key] = (byMonth[key] ?: 0) + s.totalScrolls
                        }
                    }
                    byMonth.entries.toList().takeLast(12).map { ChartBar(it.key, it.value) }
                }
            }

            val total = stats.sumOf { it.totalScrolls }
            val minutes = stats.sumOf {
                if (it.totalScrollTimeSeconds > 0) it.totalScrollTimeSeconds / 60
                else it.totalScrollTimeMinutes
            }.let { if (it == 0) (total * 15) / 60 else it }

            // Per-app totals across the period
            val appTotals = listOf(
                "Instagram" to stats.sumOf { it.instagramScrolls },
                "YouTube" to stats.sumOf { it.youtubeScrolls },
                "TikTok" to stats.sumOf { it.tiktokScrolls },
                "Facebook" to stats.sumOf { it.facebookScrolls },
                "Snapchat" to stats.sumOf { it.snapchatScrolls },
                "LinkedIn" to stats.sumOf { it.linkedinScrolls }
            ).filter { it.second > 0 }.sortedByDescending { it.second }
            val appSum = appTotals.sumOf { it.second }.coerceAtLeast(1)
            val breakdown = appTotals.map {
                AppBreakdown(it.first, it.second, it.second.toFloat() / appSum)
            }

            _uiState.value = _uiState.value.copy(
                chartBars = bars,
                periodTotal = total,
                periodDailyCounts = stats.map { it.totalScrolls },
                periodMinutes = minutes,
                appBreakdown = breakdown
            )
        }
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            try {
                loadAnalyticsInner()
            } finally {
                // isLoading gates the whole screen (see AnalyticsScreen): if a
                // Room/prefs read throws, it must still clear or the screen
                // stays permanently blank instead of showing the empty state.
                if (_uiState.value.isLoading) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    private suspend fun loadAnalyticsInner() {
        run {
            val dbStats = dailyStatsDao.getLatestStats(7).reversed()

            val paddedStats = if (dbStats.size < 7) {
                val calendar = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dates = (0 until 7).map {
                    val cal = calendar.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, -it)
                    dateFormat.format(cal.time)
                }.reversed()

                dates.map { date ->
                    dbStats.find { it.date == date } ?: DailyStatsEntity(
                        date = date,
                        totalScrolls = 0
                    )
                }
            } else {
                dbStats
            }

            val totalScrolls = paddedStats.sumOf { it.totalScrolls }
            val totalWeeklyMinutes = (totalScrolls * 15) / 60

            val avgPre = moodEntryDao.getAverageMoodByType("pre_scroll") ?: 3.8f
            val avgPost = moodEntryDao.getAverageMoodByType("post_scroll") ?: 2.9f

            val dropPercentage = if (avgPre > 0f) {
                (((avgPre - avgPost) / avgPre) * 100).toInt().coerceAtLeast(0)
            } else {
                0
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                weeklyStats = paddedStats,
                totalWeeklyMinutes = totalWeeklyMinutes,
                avgPreMood = avgPre,
                avgPostMood = avgPost,
                moodDropPercentage = dropPercentage,
                totalScrollsThisWeek = totalScrolls,
                dailyLimit = userPreferences.dailyLimit.first()
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            kotlinx.coroutines.delay(500)
            loadAnalytics()
            loadPeriod(_uiState.value.period)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
