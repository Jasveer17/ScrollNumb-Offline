package com.scrollnumb.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scroll_numb_prefs")

/** One day's earned XP, split by source — powers the weekly XP explainer. */
data class DailyXpEntry(
    val date: String,
    val streakXp: Int,
    val mathXp: Int,
    val breatheXp: Int,
    val scrollLessXp: Int
) {
    val total: Int get() = streakXp + mathXp + breatheXp + scrollLessXp
}

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DAILY_LIMIT = intPreferencesKey("daily_limit")
        val BLOCK_DURATION_MINS = intPreferencesKey("block_duration_mins")
        val SHIELD_ENABLED = booleanPreferencesKey("shield_enabled")
        val TRACKED_INSTAGRAM = booleanPreferencesKey("tracked_instagram")
        val TRACKED_YOUTUBE = booleanPreferencesKey("tracked_youtube")
        val TRACKED_FACEBOOK = booleanPreferencesKey("tracked_facebook")
        val TRACKED_SNAPCHAT = booleanPreferencesKey("tracked_snapchat")
        val TRACKED_LINKEDIN = booleanPreferencesKey("tracked_linkedin")
        val TRACKED_TIKTOK = booleanPreferencesKey("tracked_tiktok")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val MOOD_TRACKING_ENABLED = booleanPreferencesKey("mood_tracking_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val AWARENESS_CHECKS_ENABLED = booleanPreferencesKey("awareness_checks_enabled")
        val APP_THEME = stringPreferencesKey("app_theme")
        val FOCUS_XP = intPreferencesKey("focus_xp") // legacy lifetime XP; migrated into XP_BASE_LIFETIME
        val WEEKLY_XP = intPreferencesKey("weekly_xp") // legacy, unused (kept for migration safety)
        // Two-layer XP ledger (see recordDailyXp):
        val XP_BASE_LIFETIME = intPreferencesKey("xp_base_lifetime")   // finalized lifetime XP
        val XP_WEEKLY_BASE = intPreferencesKey("xp_weekly_base")       // finalized XP this week
        val XP_DAY_KEY = stringPreferencesKey("xp_day_key")            // day xp_*_today belongs to
        val XP_ACTIVITY_TODAY = intPreferencesKey("xp_activity_today") // streak+math+breathe (monotonic today)
        val XP_RESTRAINT_TODAY = intPreferencesKey("xp_restraint_today") // current restraint (overwritten)
        val WEEK_ID = stringPreferencesKey("week_id")
        // Per-day XP breakdown history (one line per day, last ~14 days kept):
        // "date|streak|math|breathe|scrollless". Feeds the weekly XP explainer.
        val XP_HISTORY = stringPreferencesKey("xp_history")
        val LEAGUE_TIER = stringPreferencesKey("league_tier")
        val LEADERBOARD_OPT_IN = booleanPreferencesKey("leaderboard_opt_in")
        val INSTALL_DATE = stringPreferencesKey("install_date")   // first launch; streak never credits days before it
        val UNDO_DATE = stringPreferencesKey("undo_date")         // day the undo counter belongs to
        val UNDO_COUNT = intPreferencesKey("undo_count")          // undos used today (anti-cheat cap)
        val DAILY_TIME_LIMIT_MINS = intPreferencesKey("daily_time_limit_mins")

        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_USERNAME = stringPreferencesKey("profile_username")
        val PROFILE_BIO = stringPreferencesKey("profile_bio")
        val PROFILE_AVATAR = stringPreferencesKey("profile_avatar")
        val PROFILE_GENDER = stringPreferencesKey("profile_gender")

        val LOGGED_IN_USERNAME = stringPreferencesKey("logged_in_username")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val PROFILE_JOINED = stringPreferencesKey("profile_joined")
        val PROFILE_MOBILE = stringPreferencesKey("profile_mobile")
        val PROFILE_DOB = stringPreferencesKey("profile_dob") // ISO yyyy-MM-dd

        val MATH_FOCUS_MINUTES = intPreferencesKey("math_focus_minutes")
        val BREATHE_MINUTES = intPreferencesKey("breathe_minutes")
        val MATH_MIN_DATE = stringPreferencesKey("math_min_date")     // day the math minutes belong to
        val BREATHE_MIN_DATE = stringPreferencesKey("breathe_min_date")
        val DECOMPRESSION_DATE = stringPreferencesKey("decompression_date")
        val MATH_DONE_TODAY = booleanPreferencesKey("math_done_today")
        val BREATHE_DONE_TODAY = booleanPreferencesKey("breathe_done_today")
        val DARK_ROOM_DONE_TODAY = booleanPreferencesKey("dark_room_done_today")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        // Persisted manual "block reels until X" so a reboot / service kill can
        // restore the lock (the in-memory ScrollAccessibilityService.manualBlock*
        // statics are otherwise lost). 0 = no active manual block.
        val MANUAL_BLOCK_EXPIRY = longPreferencesKey("manual_block_expiry")
        val MANUAL_BLOCK_MESSAGE = stringPreferencesKey("manual_block_message")
    }

    // Locale.US → stable ASCII date keys regardless of the user's app language
    // (other languages can render Arabic-Indic digits, breaking key comparisons).
    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val dailyLimit: Flow<Int> = context.dataStore.data.map { it[Keys.DAILY_LIMIT] ?: 50 }
    val blockDurationMins: Flow<Int> = context.dataStore.data.map { it[Keys.BLOCK_DURATION_MINS] ?: 5 }
    val shieldEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHIELD_ENABLED] ?: true }
    val trackedInstagram: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKED_INSTAGRAM] ?: true }
    val trackedYoutube: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKED_YOUTUBE] ?: true }
    val trackedFacebook: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKED_FACEBOOK] ?: true }
    val trackedSnapchat: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKED_SNAPCHAT] ?: true }
    val trackedLinkedin: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKED_LINKEDIN] ?: true }
    val trackedTiktok: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKED_TIKTOK] ?: true }
    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTIC_ENABLED] ?: true }
    val moodTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MOOD_TRACKING_ENABLED] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val trackingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACKING_ENABLED] ?: true }
    val awarenessChecksEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AWARENESS_CHECKS_ENABLED] ?: true }
    val appTheme: Flow<String> = context.dataStore.data.map { it[Keys.APP_THEME] ?: "SYSTEM" }

    /** Opt-in (default true) to anonymous product analytics + crash/perf reporting. */
    val analyticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANALYTICS_ENABLED] ?: true }
    // Lifetime XP = finalized base (migrated from legacy focus_xp) + today's live
    // ACTIVITY XP only. Today's scroll-less (restraint) XP is deliberately NOT
    // shown live: it used to grant the full +80 at the start of the day before
    // anything was earned ("free XP"), then visibly drain as the user scrolled.
    // It is still tracked in XP_RESTRAINT_TODAY and banks into the bases when
    // the day rolls over — you earn exactly what your FINAL count deserves, and
    // an over-limit day finalizes at 0.
    val focusXp: Flow<Int> = context.dataStore.data.map { p ->
        val base = p[Keys.XP_BASE_LIFETIME] ?: (p[Keys.FOCUS_XP] ?: 0)
        base + (p[Keys.XP_ACTIVITY_TODAY] ?: 0)
    }
    // Weekly XP = finalized weekly base + today's live activity XP (restraint
    // lands at day rollover, same as focusXp above).
    val weeklyXp: Flow<Int> = context.dataStore.data.map { p ->
        (p[Keys.XP_WEEKLY_BASE] ?: 0) + (p[Keys.XP_ACTIVITY_TODAY] ?: 0)
    }
    // This week's per-day XP breakdown (Monday → today), oldest first.
    val weeklyXpHistory: Flow<List<DailyXpEntry>> = context.dataStore.data.map { p ->
        val monday = com.scrollnumb.app.core.util.LeagueSystem.weekId()
        parseXpHistory(p[Keys.XP_HISTORY])
            .filter { it.date >= monday }
            .sortedBy { it.date }
    }
    val leagueTier: Flow<String> = context.dataStore.data.map { it[Keys.LEAGUE_TIER] ?: "Bronze" }
    val leaderboardOptIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.LEADERBOARD_OPT_IN] ?: true }
    val installDate: Flow<String> = context.dataStore.data.map { it[Keys.INSTALL_DATE] ?: "" }
    val undosUsedToday: Flow<Int> = context.dataStore.data.map {
        if (it[Keys.UNDO_DATE] != todayKey()) 0 else (it[Keys.UNDO_COUNT] ?: 0)
    }
    val dailyTimeLimitMins: Flow<Int> = context.dataStore.data.map { it[Keys.DAILY_TIME_LIMIT_MINS] ?: 30 }
    
    val profileName: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_NAME] ?: "Player 1" }
    val profileUsername: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_USERNAME] ?: "@player1" }
    val profileBio: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_BIO] ?: "Ready to conquer my digital habits." }
    val profileAvatar: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_AVATAR] ?: "😎" }
    val profileGender: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_GENDER] ?: "Male" }

    val loggedInUsername: Flow<String?> = context.dataStore.data.map { it[Keys.LOGGED_IN_USERNAME] }
    val profileEmail: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_EMAIL] ?: "" }
    val profileJoined: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_JOINED] ?: "" }
    val profileMobile: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_MOBILE] ?: "" }
    /** Date of birth as ISO yyyy-MM-dd; "" when not set. */
    val profileDob: Flow<String> = context.dataStore.data.map { it[Keys.PROFILE_DOB] ?: "" }

    val mathFocusMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.MATH_FOCUS_MINUTES] ?: 0 }
    val breatheMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.BREATHE_MINUTES] ?: 0 }
    // Today-only decompression minutes (0 once the day rolls over) — these feed
    // daily XP so decompression is earnable every day, not capped for life.
    val mathFocusMinutesToday: Flow<Int> = context.dataStore.data.map {
        if (it[Keys.MATH_MIN_DATE] != todayKey()) 0 else (it[Keys.MATH_FOCUS_MINUTES] ?: 0)
    }
    val breatheMinutesToday: Flow<Int> = context.dataStore.data.map {
        if (it[Keys.BREATHE_MIN_DATE] != todayKey()) 0 else (it[Keys.BREATHE_MINUTES] ?: 0)
    }
    val decompressionTasksToday: Flow<Int> = context.dataStore.data.map {
        if (it[Keys.DECOMPRESSION_DATE] != todayKey()) 0 else listOf(
            it[Keys.MATH_DONE_TODAY] ?: false,
            it[Keys.BREATHE_DONE_TODAY] ?: false,
            it[Keys.DARK_ROOM_DONE_TODAY] ?: false
        ).count { done -> done }
    }
    val languageCode: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE_CODE] ?: "en" }

    // ── Persisted manual block (survives reboot / service restart) ───────────
    val manualBlockExpiry: Flow<Long> = context.dataStore.data.map { it[Keys.MANUAL_BLOCK_EXPIRY] ?: 0L }
    val manualBlockMessageStored: Flow<String> = context.dataStore.data.map { it[Keys.MANUAL_BLOCK_MESSAGE] ?: "" }

    suspend fun setManualBlock(expiryMs: Long, message: String) {
        context.dataStore.edit {
            it[Keys.MANUAL_BLOCK_EXPIRY] = expiryMs
            it[Keys.MANUAL_BLOCK_MESSAGE] = message
        }
    }

    suspend fun clearManualBlock() {
        context.dataStore.edit {
            it[Keys.MANUAL_BLOCK_EXPIRY] = 0L
            it[Keys.MANUAL_BLOCK_MESSAGE] = ""
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDailyLimit(limit: Int) {
        context.dataStore.edit { it[Keys.DAILY_LIMIT] = limit }
    }

    suspend fun setBlockDurationMins(mins: Int) {
        context.dataStore.edit { it[Keys.BLOCK_DURATION_MINS] = mins }
    }

    suspend fun setShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHIELD_ENABLED] = enabled }
    }

    suspend fun setTrackedInstagram(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKED_INSTAGRAM] = enabled }
    }

    suspend fun setTrackedYoutube(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKED_YOUTUBE] = enabled }
    }

    suspend fun setTrackedFacebook(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKED_FACEBOOK] = enabled }
    }

    suspend fun setTrackedSnapchat(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKED_SNAPCHAT] = enabled }
    }

    suspend fun setTrackedLinkedin(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKED_LINKEDIN] = enabled }
    }

    suspend fun setTrackedTiktok(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKED_TIKTOK] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setMoodTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MOOD_TRACKING_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRACKING_ENABLED] = enabled }
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANALYTICS_ENABLED] = enabled }
    }

    suspend fun setAwarenessChecksEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AWARENESS_CHECKS_ENABLED] = enabled }
    }

    /** Seed the new ledger's lifetime base from the legacy focus_xp once. */
    private fun MutablePreferences.migrateXpBaseIfNeeded() {
        if (this[Keys.XP_BASE_LIFETIME] == null) {
            this[Keys.XP_BASE_LIFETIME] = this[Keys.FOCUS_XP] ?: 0
        }
    }

    // NOTE: the old addFocusXp(xp) direct-award was removed: every XP source
    // flows through XpLedger.recompute (games via the *MinutesToday counters),
    // and a direct bank on top of that would double-count.

    /**
     * Restores cloud lifetime XP on login WITHOUT touching the weekly total
     * (a lifetime catch-up must never inflate this week's competition score).
     */
    suspend fun restoreLifetimeXp(cloudLifetime: Int) {
        context.dataStore.edit {
            it.migrateXpBaseIfNeeded()
            if (cloudLifetime > (it[Keys.XP_BASE_LIFETIME] ?: 0)) {
                it[Keys.XP_BASE_LIFETIME] = cloudLifetime
            }
        }
    }

    /**
     * Restores this week's XP on reinstall/login WITHOUT losing it (the previous
     * bug: sign-in left weekly at local 0, then the next sync pushed that 0 over
     * the cloud value). Only applies when the cloud value belongs to the CURRENT
     * week — a stale week must correctly stay reset to 0. Sets the weekly BASE so
     * that base + today's live XP equals the restored cloud total, and never
     * lowers a higher local value.
     */
    suspend fun restoreWeeklyXp(cloudWeekly: Int, cloudWeekId: String, currentWeekId: String) {
        if (cloudWeekId.isEmpty() || cloudWeekId != currentWeekId || cloudWeekly <= 0) return
        context.dataStore.edit {
            it.migrateXpBaseIfNeeded()
            // Live XP mirrors the weeklyXp flow: activity only (restraint banks
            // at day rollover and is not part of the displayed weekly total).
            val live = it[Keys.XP_ACTIVITY_TODAY] ?: 0
            val localWeekly = (it[Keys.XP_WEEKLY_BASE] ?: 0) + live
            if (cloudWeekly > localWeekly) {
                it[Keys.XP_WEEKLY_BASE] = (cloudWeekly - live).coerceAtLeast(0)
                it[Keys.WEEK_ID] = currentWeekId
            }
        }
    }

    /**
     * Records the recompute-derived XP for the current day.
     *  - [activityXp]  monotonic (streak + today's math/breathe) → kept as the
     *    day's max so it never drops mid-day.
     *  - [restraintXp] depends on today's reel count → overwritten every sync so
     *    an early "under-limit" bonus can't be banked and then farmed by scrolling.
     * On a day rollover the previous day's live XP is finalized into the bases;
     * on a week rollover the weekly base resets to 0.
     */
    suspend fun recordDailyXp(activityXp: Int, restraintXp: Int, today: String, currentWeekId: String) {
        context.dataStore.edit {
            it.migrateXpBaseIfNeeded()
            val dayKey = it[Keys.XP_DAY_KEY]
            if (dayKey != today) {
                val prevDay = (it[Keys.XP_ACTIVITY_TODAY] ?: 0) + (it[Keys.XP_RESTRAINT_TODAY] ?: 0)
                it[Keys.XP_BASE_LIFETIME] = (it[Keys.XP_BASE_LIFETIME] ?: 0) + prevDay
                val storedWeek = it[Keys.WEEK_ID]
                it[Keys.XP_WEEKLY_BASE] =
                    if (storedWeek != currentWeekId) 0 else (it[Keys.XP_WEEKLY_BASE] ?: 0) + prevDay
                it[Keys.WEEK_ID] = currentWeekId
                it[Keys.XP_DAY_KEY] = today
                it[Keys.XP_ACTIVITY_TODAY] = 0
                it[Keys.XP_RESTRAINT_TODAY] = 0
            } else if (it[Keys.WEEK_ID] != currentWeekId) {
                // Same day but week changed (defensive) — start the week fresh.
                it[Keys.XP_WEEKLY_BASE] = 0
                it[Keys.WEEK_ID] = currentWeekId
            }
            // Direct set: the game counters behind activityXp only ever grow, and
            // the streak component arrives already monotonic (XpLedger keeps the
            // day's max so an awarded bonus is never clawed back mid-day).
            it[Keys.XP_ACTIVITY_TODAY] = activityXp.coerceAtLeast(0)
            it[Keys.XP_RESTRAINT_TODAY] = restraintXp.coerceAtLeast(0)
        }
    }

    /**
     * Upsert today's XP breakdown into the rolling history (last 14 days kept).
     * Called alongside [recordDailyXp]; today's row is overwritten each sync so
     * it stays live, while past days stay frozen at their last value.
     */
    suspend fun recordDailyXpBreakdown(
        date: String,
        streakXp: Int,
        mathXp: Int,
        breatheXp: Int,
        scrollLessXp: Int
    ) {
        context.dataStore.edit { p ->
            val map = parseXpHistory(p[Keys.XP_HISTORY]).associateBy { it.date }.toMutableMap()
            map[date] = DailyXpEntry(
                date,
                streakXp.coerceAtLeast(0),
                mathXp.coerceAtLeast(0),
                breatheXp.coerceAtLeast(0),
                scrollLessXp.coerceAtLeast(0)
            )
            val kept = map.values.sortedByDescending { it.date }.take(14)
            p[Keys.XP_HISTORY] = kept.joinToString("\n") {
                "${it.date}|${it.streakXp}|${it.mathXp}|${it.breatheXp}|${it.scrollLessXp}"
            }
        }
    }

    private fun parseXpHistory(raw: String?): List<DailyXpEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 5) return@mapNotNull null
            DailyXpEntry(
                parts[0],
                parts[1].toIntOrNull() ?: 0,
                parts[2].toIntOrNull() ?: 0,
                parts[3].toIntOrNull() ?: 0,
                parts[4].toIntOrNull() ?: 0
            )
        }.toList()
    }

    /** Mirror the league tier assigned by the server so the UI can show it. */
    suspend fun setLeagueTier(tier: String) {
        context.dataStore.edit { it[Keys.LEAGUE_TIER] = tier }
    }

    suspend fun setLeaderboardOptIn(optIn: Boolean) {
        context.dataStore.edit { it[Keys.LEADERBOARD_OPT_IN] = optIn }
    }

    /** Record the first launch date once, so the streak never credits pre-install days. */
    suspend fun ensureInstallDate() {
        context.dataStore.edit {
            if (it[Keys.INSTALL_DATE].isNullOrEmpty()) it[Keys.INSTALL_DATE] = todayKey()
        }
    }

    /**
     * Atomically consume one "undo last count" for today if under [maxPerDay].
     * @return true if an undo was granted, false if the daily cap is reached.
     */
    suspend fun tryConsumeUndo(maxPerDay: Int): Boolean {
        var granted = false
        context.dataStore.edit {
            val today = todayKey()
            val used = if (it[Keys.UNDO_DATE] != today) 0 else (it[Keys.UNDO_COUNT] ?: 0)
            if (used < maxPerDay) {
                it[Keys.UNDO_DATE] = today
                it[Keys.UNDO_COUNT] = used + 1
                granted = true
            }
        }
        return granted
    }

    suspend fun setDailyTimeLimitMins(limit: Int) {
        context.dataStore.edit { it[Keys.DAILY_TIME_LIMIT_MINS] = limit }
    }

    suspend fun setProfileName(name: String) {
        context.dataStore.edit { it[Keys.PROFILE_NAME] = name }
    }
    
    suspend fun setProfileUsername(username: String) {
        context.dataStore.edit { it[Keys.PROFILE_USERNAME] = username }
    }
    
    suspend fun setProfileBio(bio: String) {
        context.dataStore.edit { it[Keys.PROFILE_BIO] = bio }
    }

    suspend fun setProfileAvatar(avatar: String) {
        context.dataStore.edit { it[Keys.PROFILE_AVATAR] = avatar }
    }

    suspend fun setProfileGender(gender: String) {
        context.dataStore.edit { it[Keys.PROFILE_GENDER] = gender }
    }

    suspend fun setLoggedInUsername(username: String?) {
        context.dataStore.edit {
            if (username == null) it.remove(Keys.LOGGED_IN_USERNAME)
            else it[Keys.LOGGED_IN_USERNAME] = username
        }
    }

    suspend fun setProfileEmail(email: String) {
        context.dataStore.edit { it[Keys.PROFILE_EMAIL] = email }
    }

    suspend fun setProfileJoined(joined: String) {
        context.dataStore.edit { it[Keys.PROFILE_JOINED] = joined }
    }

    suspend fun setProfileMobile(mobile: String) {
        context.dataStore.edit { it[Keys.PROFILE_MOBILE] = mobile }
    }

    /** Store DOB as ISO yyyy-MM-dd ("" clears it). */
    suspend fun setProfileDob(dob: String) {
        context.dataStore.edit { it[Keys.PROFILE_DOB] = dob }
    }

    suspend fun addMathFocusMinutes(extra: Int) {
        if (extra <= 0) return
        val today = todayKey()
        context.dataStore.edit {
            val base = if (it[Keys.MATH_MIN_DATE] != today) 0 else (it[Keys.MATH_FOCUS_MINUTES] ?: 0)
            it[Keys.MATH_MIN_DATE] = today
            it[Keys.MATH_FOCUS_MINUTES] = base + extra
        }
    }

    suspend fun addBreatheMinutes(extra: Int) {
        if (extra <= 0) return
        val today = todayKey()
        context.dataStore.edit {
            val base = if (it[Keys.BREATHE_MIN_DATE] != today) 0 else (it[Keys.BREATHE_MINUTES] ?: 0)
            it[Keys.BREATHE_MIN_DATE] = today
            it[Keys.BREATHE_MINUTES] = base + extra
        }
    }

    suspend fun markMathDoneToday() = markDecompressionDone(Keys.MATH_DONE_TODAY)

    suspend fun markBreatheDoneToday() = markDecompressionDone(Keys.BREATHE_DONE_TODAY)

    suspend fun markDarkRoomDoneToday() = markDecompressionDone(Keys.DARK_ROOM_DONE_TODAY)

    private suspend fun markDecompressionDone(key: Preferences.Key<Boolean>) {
        val today = todayKey()
        context.dataStore.edit {
            if (it[Keys.DECOMPRESSION_DATE] != today) {
                it[Keys.DECOMPRESSION_DATE] = today
                it[Keys.MATH_DONE_TODAY] = false
                it[Keys.BREATHE_DONE_TODAY] = false
                it[Keys.DARK_ROOM_DONE_TODAY] = false
            }
            it[key] = true
        }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    /**
     * If the XP ledger still holds [day] live (the rollover in [recordDailyXp]
     * hasn't happened yet), overwrite the restraint component with the day's
     * true final value. Called by DailyResetWorker so the bank at rollover
     * can't credit a stale, higher mid-day figure when no recompute ran after
     * the day's last scroll.
     */
    suspend fun finalizeRestraintIfPending(day: String, restraintXp: Int) {
        context.dataStore.edit {
            if (it[Keys.XP_DAY_KEY] == day) {
                it[Keys.XP_RESTRAINT_TODAY] = restraintXp.coerceAtLeast(0)
            }
        }
    }

    /** Wipes every stored preference. Used by account deletion. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        // The legacy SharedPreferences file shares the "scroll_numb_prefs" name
        // (different file on disk: .xml vs .preferences_pb) and holds
        // USER_GENDER for AvatarHelper — wipe it too so a full clear is a
        // full clear regardless of the caller.
        context.getSharedPreferences("scroll_numb_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    suspend fun lastDurableCloudSync(userId: String): Long {
        val key = longPreferencesKey("last_durable_sync_" + userId)
        return context.dataStore.data.first()[key] ?: 0L
    }

    suspend fun setLastDurableCloudSync(userId: String, timestamp: Long) {
        val key = longPreferencesKey("last_durable_sync_" + userId)
        context.dataStore.edit { it[key] = timestamp }
    }
}
