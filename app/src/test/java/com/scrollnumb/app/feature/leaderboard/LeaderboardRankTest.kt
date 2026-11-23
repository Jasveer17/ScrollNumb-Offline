package com.scrollnumb.app.feature.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardRankTest {
    @Test
    fun `your rank uses the same eligible rows displayed in the list`() {
        val cloudRows = listOf(
            entry("first"),
            entry("ineligible", eligible = false),
            entry("second"),
            entry("rajwati", isMe = true)
        )

        val displayedRows = cloudRows.filter { it.competitionEligible }

        assertEquals(3, displayedLeaderboardRank(displayedRows))
    }

    @Test
    fun `your rank is zero when current user has no displayed row`() {
        assertEquals(0, displayedLeaderboardRank(listOf(entry("another"))))
    }

    private fun entry(
        id: String,
        eligible: Boolean = true,
        isMe: Boolean = false
    ) = LeaderboardEntry(
        id = id,
        name = id,
        username = "@$id",
        avatar = "",
        xp = 0,
        competitionEligible = eligible,
        isMe = isMe
    )
}
