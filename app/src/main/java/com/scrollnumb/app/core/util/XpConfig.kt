package com.scrollnumb.app.core.util

/**
 * Runtime-tunable XP amounts. Defaults match the design constants in
 * [XpFormula]; [RemoteConfigManager] overwrites them after a successful fetch so
 * the economy can be tuned without a Play release. Kept as a separate mutable
 * holder (rather than reading Remote Config inside [XpFormula]) so the formula
 * stays a pure, unit-testable function that never touches Firebase.
 */
object XpConfig {
    /** Max restraint XP for a zero-scroll day. Default mirrors XpFormula.SCROLL_LESS_MAX. */
    @Volatile var scrollLessMax: Int = 80

    /** Flat daily streak-kept bonus. Default mirrors XpFormula's STREAK_DAILY_BONUS. */
    @Volatile var streakDailyBonus: Int = 20
}
