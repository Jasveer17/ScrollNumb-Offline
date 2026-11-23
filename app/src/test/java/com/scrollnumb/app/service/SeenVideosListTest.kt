package com.scrollnumb.app.service

import com.scrollnumb.app.core.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class SeenVideosListTest {
    @Test
    fun `returning to the uncounted TikTok entry is skipped`() {
        val seen = SeenVideosList()
        val entry = "tt_101_201"
        val next = "tt_102_202"

        seen.add(Constants.PLATFORM_TIKTOK, entry)
        assertEquals(SeenResult.COUNT, seen.checkSeenState(Constants.PLATFORM_TIKTOK, next))
        seen.add(Constants.PLATFORM_TIKTOK, next)

        assertEquals(
            SeenResult.SKIP_RECENTLY_COUNTED,
            seen.checkSeenState(Constants.PLATFORM_TIKTOK, entry)
        )
    }
}
