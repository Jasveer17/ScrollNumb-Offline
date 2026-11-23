package com.scrollnumb.app.core.util

import java.util.Calendar

/**
 * One gentle, non-judgmental tip per day — a mix of actionable focus advice
 * and warm health facts. Selected by day-of-year so it's stable within a day
 * and rotates automatically (same pattern as the shield quotes).
 *
 * Tone rule: never shame the user for scrolling. Every line frames a calmer
 * choice as a gift to their future self.
 */
object DailyTips {

    private val tips = listOf(
        "Trade 10 scroll-minutes for a 10-minute walk — your focus span widens for the next two hours.",
        "Every reel you skip keeps your dopamine baseline higher, so the next good thing feels good again.",
        "Heavy short-video use trains attention into ~8-second spans. Single-tasking rebuilds it within weeks.",
        "Scrolling within an hour of bed can delay melatonin by ~30 minutes. An earlier phone-down is deeper sleep.",
        "Boredom isn't the enemy — it's the quiet your best ideas need to surface.",
        "Cutting your daily scroll by 15 minutes gives you back ~91 hours a year. That's two full work weeks.",
        "Your morning attention is your sharpest. Spend the first 20 minutes off the feed and feel the day change.",
        "The feed is engineered to be endless. Choosing when to stop is a quiet kind of power.",
        "A 3-minute breathing break lowers your heart rate more than 30 minutes of scrolling ever will.",
        "Notifications fragment focus for up to 23 minutes each. Fewer pings, more presence.",
        "You remember almost none of what you scroll. You remember nearly everything you create.",
        "Looking 20 feet away for 20 seconds every 20 minutes rests the eyes screens strain.",
        "One reel becomes fifty because the next is always 'almost' better. It rarely is. You can stop now.",
        "Replacing one scroll session with a stretch resets your posture and your mood at once.",
        "Your brain consolidates memory during idle moments. Daydreaming is productive.",
        "The calm after you put the phone down is the feeling the feed keeps promising but never gives.",
        "Sunlight in the first hour awake sets your whole day's energy. The feed can wait until after.",
        "Reading two pages of a book rebuilds the deep-focus muscle short videos quietly erode.",
        "Each mindful pause you take today makes tomorrow's pause a little easier. Streaks are built this way.",
        "Drinking a glass of water during a scroll urge gives the craving 60 seconds to pass — it usually does.",
        "Your attention is the most valuable thing you own. Spend it on purpose, not by reflex.",
        "Less scrolling tonight means waking with a clearer, lighter mind tomorrow. Future-you says thanks.",
        "A short walk outdoors boosts creative thinking by ~60%. The best ideas rarely come from a screen.",
        "You don't need to quit the feed — just to choose it, instead of it choosing you.",
        "Phone face-down during meals brings the taste, the people, and the moment back.",
        "Every minute under your limit is a small vote for the person you're becoming.",
        "Deep breaths through the nose calm the nervous system faster than any video can.",
        "The urge to check passes in about 90 seconds if you let it. You're stronger than a notification.",
        "Protecting your focus today protects your sleep, your mood, and your memory all at once.",
        "Peace of mind isn't found at the bottom of the feed. It's found the moment you look up."
    )

    /** Today's tip — stable for the whole day, rotates daily. */
    fun today(): String {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return tips[day % tips.size]
    }
}
