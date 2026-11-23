package com.scrollnumb.app.service

import com.scrollnumb.app.core.util.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelCountingPolicyTest {
    @Test
    fun `TikTok partial-height scrollable sheet is a panel container`() {
        assertTrue(
            ReelCountingPolicy.isLikelyTikTokPanelContainer(
                viewId = "com.zhiliaoapp.musically:id/obfuscated",
                className = "androidx.recyclerview.widget.RecyclerView",
                isScrollable = true,
                width = 1000,
                height = 1500,
                rootWidth = 1080,
                rootHeight = 2400
            )
        )
    }

    @Test
    fun `TikTok full-screen feed and small comment button are not panels`() {
        assertFalse(
            ReelCountingPolicy.isLikelyTikTokPanelContainer(
                viewId = "com.zhiliaoapp.musically:id/view_rootview",
                className = "androidx.recyclerview.widget.RecyclerView",
                isScrollable = true,
                width = 1080,
                height = 2400,
                rootWidth = 1080,
                rootHeight = 2400
            )
        )
        assertFalse(
            ReelCountingPolicy.isLikelyTikTokPanelContainer(
                viewId = "com.zhiliaoapp.musically:id/comment",
                className = "android.widget.ImageButton",
                isScrollable = false,
                width = 120,
                height = 120,
                rootWidth = 1080,
                rootHeight = 2400
            )
        )
    }

    @Test
    fun `TikTok remembers its uncounted entry and rejects page-only transitions`() {
        assertTrue(ReelCountingPolicy.shouldRememberUncountedEntry(Constants.PLATFORM_TIKTOK))
        assertFalse(ReelCountingPolicy.canCountPageOnlyTransition(Constants.PLATFORM_TIKTOK))
        assertFalse(ReelCountingPolicy.shouldRememberUncountedEntry(Constants.PLATFORM_INSTAGRAM))
        assertTrue(ReelCountingPolicy.canCountPageOnlyTransition(Constants.PLATFORM_INSTAGRAM))
        assertFalse(ReelCountingPolicy.shouldRememberUncountedEntry(Constants.PLATFORM_YOUTUBE))
        assertTrue(ReelCountingPolicy.canCountPageOnlyTransition(Constants.PLATFORM_YOUTUBE))
    }

    @Test
    fun `TikTok never bypasses seen-video dedup for a pager change`() {
        assertFalse(ReelCountingPolicy.canBypassSeenVideoForPageAdvance(Constants.PLATFORM_TIKTOK))
        assertTrue(ReelCountingPolicy.canBypassSeenVideoForPageAdvance(Constants.PLATFORM_INSTAGRAM))
        assertTrue(ReelCountingPolicy.canBypassSeenVideoForPageAdvance(Constants.PLATFORM_YOUTUBE))
    }

    @Test
    fun `TikTok and YouTube do not synthesize missing pager indexes`() {
        assertFalse(ReelCountingPolicy.shouldSynthesizeMissingPage(Constants.PLATFORM_TIKTOK))
        assertFalse(ReelCountingPolicy.shouldSynthesizeMissingPage(Constants.PLATFORM_YOUTUBE))
        assertTrue(ReelCountingPolicy.shouldSynthesizeMissingPage(Constants.PLATFORM_INSTAGRAM))
    }

    @Test
    fun `only TikTok rejects unknown scroll sources`() {
        assertFalse(ReelCountingPolicy.shouldAllowUnknownScrollSource(Constants.PLATFORM_TIKTOK))
        assertTrue(ReelCountingPolicy.shouldAllowUnknownScrollSource(Constants.PLATFORM_INSTAGRAM))
        assertTrue(ReelCountingPolicy.shouldAllowUnknownScrollSource(Constants.PLATFORM_YOUTUBE))
    }
}
