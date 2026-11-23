package com.scrollnumb.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Test

/** Guards the level/league/week invariants documented in AGENTS.md. */
class LeagueSystemTest {

    // ── Levels ───────────────────────────────────────────────────────────
    @Test
    fun `level starts at one and advances every xp interval`() {
        assertEquals(1, LeagueSystem.levelFor(0))
        assertEquals(1, LeagueSystem.levelFor(999))
        assertEquals(2, LeagueSystem.levelFor(1000))
        assertEquals(3, LeagueSystem.levelFor(2500))
    }

    @Test
    fun `negative xp clamps to level one`() {
        assertEquals(1, LeagueSystem.levelFor(-500))
        assertEquals(0, LeagueSystem.xpIntoLevel(-500))
    }

    @Test
    fun `xp into level and progress are consistent`() {
        assertEquals(500, LeagueSystem.xpIntoLevel(2500))
        assertEquals(0.5f, LeagueSystem.levelProgress(2500), 0.0001f)
    }

    @Test
    fun `mindful score balances weekly xp against excess reels`() {
        assertEquals(20, LeagueSystem.mindfulScore(20, 50, daysElapsed = 1))
        assertEquals(-2, LeagueSystem.mindfulScore(0, 60, daysElapsed = 1))
        assertEquals(-40, LeagueSystem.mindfulScore(50, 500, daysElapsed = 1))
    }

    @Test
    fun `mindful score penalty uses exact five reel buckets`() {
        assertEquals(100, LeagueSystem.mindfulScore(100, 50, daysElapsed = 1))
        assertEquals(99, LeagueSystem.mindfulScore(100, 51, daysElapsed = 1))
        assertEquals(99, LeagueSystem.mindfulScore(100, 55, daysElapsed = 1))
        assertEquals(98, LeagueSystem.mindfulScore(100, 56, daysElapsed = 1))
        assertEquals(100, LeagueSystem.mindfulScore(100, 150, daysElapsed = 3))
        assertEquals(99, LeagueSystem.mindfulScore(100, 151, daysElapsed = 3))
    }

    @Test
    fun `leaderboard xp follows score but never becomes negative`() {
        assertEquals(37, LeagueSystem.leaderboardXp(37))
        assertEquals(0, LeagueSystem.leaderboardXp(0))
        assertEquals(0, LeagueSystem.leaderboardXp(-40))
    }

    // ── Tiers ────────────────────────────────────────────────────────────
    @Test
    fun `unknown or null tier normalizes to Bronze`() {
        assertEquals("Bronze", LeagueSystem.normalizeTier(null))
        assertEquals("Bronze", LeagueSystem.normalizeTier("NotATier"))
        assertEquals("Gold", LeagueSystem.normalizeTier("Gold"))
    }

    @Test
    fun `next tier walks up and stops at Diamond`() {
        assertEquals("Silver", LeagueSystem.nextTier("Bronze"))
        assertEquals("Platinum", LeagueSystem.nextTier("Gold"))
        assertNull(LeagueSystem.nextTier("Diamond"))
    }

    // ── Week id (Monday start, IST, ASCII digits) ────────────────────────
    @Test
    fun `week id is a Monday within the week and formatted yyyy-MM-dd`() {
        // A fixed instant: 2026-07-08 12:00 UTC (a Wednesday in IST).
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(2026, Calendar.JULY, 8, 12, 0, 0)
        val input = cal.time

        val id = LeagueSystem.weekId(input)
        assertTrue("id must be yyyy-MM-dd with ASCII digits", Regex("\\d{4}-\\d{2}-\\d{2}").matches(id))

        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ist }
        val monday = fmt.parse(id)!!
        val c = Calendar.getInstance(ist).apply { time = monday }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))

        // The Monday must be at/before the input and no more than 7 days earlier.
        val deltaDays = (input.time - monday.time).toDouble() / (24 * 60 * 60 * 1000)
        assertTrue("Monday must be on/before the input", deltaDays >= 0)
        assertTrue("within the same week", deltaDays < 7)
    }

    @Test
    fun `same week resolves to the same id`() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        fun instant(y: Int, mo: Int, d: Int, h: Int) = Calendar.getInstance(ist).apply {
            clear(); set(y, mo, d, h, 0, 0)
        }.time
        // Two days in the same Mon-Sun IST week must share a week id.
        val a = LeagueSystem.weekId(instant(2026, Calendar.JULY, 8, 10))  // Wed
        val b = LeagueSystem.weekId(instant(2026, Calendar.JULY, 10, 22)) // Fri same week
        assertEquals(a, b)
    }
}
