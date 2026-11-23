package com.scrollnumb.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the daily-XP invariants documented in AGENTS.md. These are pure
 * functions, so a break here is a real economy bug — not flakiness.
 */
class XpFormulaTest {

    // ── Restraint (scroll-less) XP ───────────────────────────────────────
    @Test
    fun `zero reels earns the full restraint bonus`() {
        assertEquals(XpFormula.SCROLL_LESS_MAX, XpFormula.restraint(reelsToday = 0, dailyLimit = 50))
    }

    @Test
    fun `at the limit restraint is zero`() {
        assertEquals(0, XpFormula.restraint(reelsToday = 50, dailyLimit = 50))
    }

    @Test
    fun `over the limit restraint is zero`() {
        assertEquals(0, XpFormula.restraint(reelsToday = 51, dailyLimit = 50))
        assertEquals(0, XpFormula.restraint(reelsToday = 999, dailyLimit = 50))
    }

    @Test
    fun `half the limit earns half the restraint bonus`() {
        // restraint = (1 - 25/50) * 80 = 40
        assertEquals(40, XpFormula.restraint(reelsToday = 25, dailyLimit = 50))
    }

    @Test
    fun `non-positive limit earns no restraint`() {
        assertEquals(0, XpFormula.restraint(reelsToday = 0, dailyLimit = 0))
    }

    // ── withinLimit is `<=` (the off-by-one AGENTS.md warns about) ────────
    @Test
    fun `ending exactly on the limit still earns the streak bonus`() {
        val b = XpFormula.breakdown(
            streakDays = 3, mathFocusMinutes = 0, breatheMinutes = 0,
            reelsToday = 50, dailyLimit = 50
        )
        assertEquals("day on the limit must keep its streak bonus", 20, b.streakXp)
    }

    @Test
    fun `going one over the limit forfeits the streak bonus`() {
        val b = XpFormula.breakdown(
            streakDays = 3, mathFocusMinutes = 0, breatheMinutes = 0,
            reelsToday = 51, dailyLimit = 50
        )
        assertEquals(0, b.streakXp)
    }

    // ── Streak bonus shape ───────────────────────────────────────────────
    @Test
    fun `no streak means no streak bonus`() {
        val b = XpFormula.breakdown(0, 0, 0, reelsToday = 0, dailyLimit = 50)
        assertEquals(0, b.streakXp)
    }

    @Test
    fun `seventh day adds the milestone bonus`() {
        val b = XpFormula.breakdown(7, 0, 0, reelsToday = 0, dailyLimit = 50)
        assertEquals(20 + 30, b.streakXp)
    }

    // ── Decompress caps ──────────────────────────────────────────────────
    @Test
    fun `math xp is capped per day`() {
        val capped = XpFormula.breakdown(0, mathFocusMinutes = 100, breatheMinutes = 0, reelsToday = 0, dailyLimit = 50)
        assertEquals(10 * 5, capped.mathXp) // 10 puzzles * 5 XP
        val partial = XpFormula.breakdown(0, mathFocusMinutes = 3, breatheMinutes = 0, reelsToday = 0, dailyLimit = 50)
        assertEquals(3 * 5, partial.mathXp)
    }

    @Test
    fun `breathe xp is capped per day`() {
        val capped = XpFormula.breakdown(0, 0, breatheMinutes = 100, reelsToday = 0, dailyLimit = 50)
        assertEquals(3 * XpFormula.BREATHE_XP_PER_SESSION, capped.breatheXp)
    }

    @Test
    fun `breakdown total is the sum of its parts`() {
        val b = XpFormula.breakdown(7, mathFocusMinutes = 2, breatheMinutes = 1, reelsToday = 0, dailyLimit = 50)
        assertEquals(b.streakXp + b.mathXp + b.breatheXp + b.scrollLessXp, b.total)
        assertTrue(b.total > 0)
    }
}
