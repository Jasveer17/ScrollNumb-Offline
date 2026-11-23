package com.scrollnumb.app.service

import android.content.res.Resources
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.scrollnumb.app.core.util.Constants
import java.util.LinkedList
import java.util.Locale
import java.util.regex.Pattern

enum class DetectionType {
    NOT_REELS,
    PANEL_OPEN,
    REELS_ACTIVE
}

data class DetectionResult(
    val type: DetectionType,
    val signature: String? = null
)

object ScrollDetector {

    // TEMP diagnostic: log IG detection outcome only when it CHANGES (avoids
    // per-poll spam) so we can see why the pill is late on reel entry.
    @Volatile private var lastIgReason = ""
    private fun igLog(reason: String): Unit {
        if (reason != lastIgReason) {
            lastIgReason = reason
            Log.d("ScrollNumb", "IGDETECT: $reason")
        }
    }

    @Volatile private var lastFbReason = ""
    private fun fbLog(reason: String): Unit {
        if (reason != lastFbReason) {
            lastFbReason = reason
            Log.d("ScrollNumb", "FBDETECT: $reason")
        }
    }

    private val channelHandlePattern = Pattern.compile("@([A-Za-z0-9_.\\-]{3,30})")
    // Matches text that is PURELY a number / engagement metric — "1.2K", "12M",
    // "345", "5%", "+99". Such nodes (like/comment/share/view counts) tick up
    // while the user sits on one reel, so they must never feed the signature or
    // the same video phantom-counts. Mirrors BrainPal's "^[\\d,.KkMmBb%+\\-\\s]+$".
    private val numericMetricPattern = Pattern.compile("^[\\d,.KkMmBb%+\\-\\s]+$")
    private val tiktokPackages = listOf(
        Constants.PKG_TIKTOK_AWEME,
        Constants.PKG_TIKTOK,
        Constants.PKG_TIKTOK_TRILL
    )

    private val blacklistKeywords = setOf(
        "video progress", "seconds of", "second of", "minutes of", "minute of",
        "home", "shorts", "library", "you", "create", "search", "more options",
        "trends", "live", "subscriptions", "content available", "new content is available",
        "subscribe", "unsubscribe", "like this video", "dislike", "share this",
        "view", "remix this", "see more", "go to", "donate", "navigate"
    )

    private val adKeywords = setOf(
        "sponsored", "shop now", "install now", "visit site", "paid partnership", "learn more"
    )

    private val actionKeywords = setOf(
        "like", "comment", "share", "send", "save", "more", "follow", "profile"
    )

    private val instagramSignatureIgnoreKeywords = setOf(
        "caption", "comment", "profile", "username", "play", "pause", "mute", "unmute",
        "music", "audio", "more options", "snackbar", "sponsored"
    )

    // Snapchat nav/chrome content-descs — constant across every Spotlight
    // video, so they must never feed the signature (they'd collapse all
    // videos into one identity).
    private val snapchatChromeDescs = setOf(
        "map", "chat", "camera", "stories", "story sent", "search",
        "add friends", "create", "memories", "flash", "flip camera"
    )

    private val shortVideoKeywords = setOf(
        "reel", "reels", "short", "shorts", "spotlight", "tiktok", "swipe up", "swipe down",
        "full screen video", "fullscreen video", "video feed"
    )

    /**
     * Entry point to determine if Reels/Shorts are active in the target package,
     * and returns the unique signature/ID of the current video.
     * Returns null if not active or skipped (e.g. comments open).
     */
    fun detectVideoSignature(rootNode: AccessibilityNodeInfo?, platform: String): DetectionResult {
        if (rootNode == null) return DetectionResult(DetectionType.NOT_REELS)

        return when (platform) {
            Constants.PLATFORM_YOUTUBE -> detectYouTubeShort(rootNode)
            Constants.PLATFORM_INSTAGRAM -> detectInstagramReel(rootNode)
            Constants.PLATFORM_FACEBOOK -> detectFacebookReel(rootNode)
            Constants.PLATFORM_SNAPCHAT -> detectSnapchatSpotlight(rootNode)
            Constants.PLATFORM_LINKEDIN -> detectLinkedInReel(rootNode)
            Constants.PLATFORM_TIKTOK -> detectTikTok(rootNode)
            else -> detectGenericFallback(rootNode, platform)
        }
    }

    fun isReelNodeActive(rootNode: AccessibilityNodeInfo?, platform: String): Boolean {
        if (rootNode == null) return false
        return detectVideoSignature(rootNode, platform).type == DetectionType.REELS_ACTIVE
    }

    fun generateScreenSignature(rootNode: AccessibilityNodeInfo?, platform: String): String {
        if (rootNode == null) return ""
        return detectVideoSignature(rootNode, platform).signature ?: ""
    }

    /**
     * Shorts surface containers. The Shorts TAB uses reel_recycler; Shorts
     * opened from a CHANNEL page / search render directly in the reel
     * player page container with NO recycler (verified on-device, YT 19.x).
     */
    private val youtubeShortsContainerIds = listOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/reel_player_overlay_root",
        "com.google.android.youtube:id/reel_watch_pager"
    )

    // Content-description substrings that belong to YouTube Shorts CHROME
    // (action rail, nav bar, progress) — never the video title. Used to reject
    // them when picking the title content-desc.
    private val youtubeActionDescKeywords = setOf(
        "go to channel", "subscribe to", "like this video", "dislike this video",
        "view ", " comments", "comment", "share this", "remix this",
        "see more videos", "using this sound", "more options", "subscriptions",
        "new content", "minutes of", "minute of", "seconds of", "second of",
        "search", "create", "remixes",
        // Player nav chrome, exposed only since FLAG_INCLUDE_NOT_IMPORTANT_VIEWS:
        // mid-swipe it briefly won as the "longest" desc → a junk title
        // ("Previous Video") that double-counted the first swipe after entry.
        "previous video", "next video", "play video", "pause video",
        // A LOOPING Short flickers its title desc to the bare "Remix" chrome
        // label — that junk title read as a NEW reel and +1-counted the loop.
        "remix",
        // Press-and-hold 2x speed overlay ("2x", "Pull down to lock 2x speed",
        // "Playing at 2x speed") won as the title → +1 on speed-up. The bare
        // "2x" also rejects legit titles containing it — those safely fall
        // back to a channel-only signature.
        "2x", "pull down to", "playing at"
    )

    /**
     * Extract the channel handle and video title from their dedicated
     * content-descriptions. Returns (channelHandle, title). Both are stable
     * per-Short (verified on device), so a looping Short keeps the same
     * signature while a genuinely different Short — even from the same channel —
     * produces a different one.
     */
    private fun extractYouTubeChannelAndTitle(node: AccessibilityNodeInfo): Pair<String, String> {
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        var channelHandle = ""
        var channelTop = Int.MIN_VALUE
        val titleCandidates = ArrayList<Pair<Int, String>>() // (top, desc)
        val bounds = android.graphics.Rect()
        while (queue.isNotEmpty() && count < 300) {
            val current = queue.poll() ?: continue
            count++
            val viewId = current.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            // Skip captions/subtitles — their text changes every second.
            if (!viewId.contains("subtitle") && !viewId.contains("caption")) {
                val cd = current.contentDescription?.toString()?.trim().orEmpty()
                    .ifEmpty { current.text?.toString()?.trim().orEmpty() }
                if (cd.isNotEmpty()) {
                    val lower = cd.lowercase(Locale.ROOT)
                    if (lower.startsWith("go to channel") || lower.startsWith("subscribe to")) {
                        if (channelHandle.isEmpty()) {
                            val m = channelHandlePattern.matcher(cd)
                            if (m.find()) channelHandle = m.group(1) ?: ""
                        }
                        current.getBoundsInScreen(bounds)
                        if (bounds.height() > 0 && bounds.top > channelTop) {
                            channelTop = bounds.top
                        }
                    } else if (!cd.contains("@") &&
                        youtubeActionDescKeywords.none { lower.contains(it) } &&
                        blacklistKeywords.none { lower.contains(it) } &&
                        !numericMetricPattern.matcher(cd).matches() &&
                        // Must contain a letter/digit: the highlighted-comment
                        // feature adds an emoji REACTION row ("❤️😂😢") BELOW the
                        // title — as pure-emoji descs they won the bottom-most
                        // pick, giving every Short on a channel the same junk
                        // title and dedup-blocking all counts (verified on
                        // device: sig=ch_<channel>_❤️ for every short).
                        cd.any { it.isLetterOrDigit() }) {
                        current.getBoundsInScreen(bounds)
                        if (bounds.height() > 0) {
                            titleCandidates.add(bounds.top to cd)
                        }
                    }
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        // Title = the BOTTOM-MOST candidate BELOW the channel row. The real
        // title always sits under the "Go to channel"/"Subscribe" row at the
        // footer's bottom; YouTube's highlighted-comment popup renders ABOVE
        // the channel row with a long id-less content-desc — under the old
        // "longest desc wins" rule it hijacked the title, and the signature
        // flip phantom-counted +1 mid-watch (verified on device: sig flipped
        // to the popup's comment text). When the channel row hasn't loaded,
        // fall back to the bottom-most candidate overall — the popup zone is
        // higher up, so bottom-most still prefers the real title.
        val eligible = if (channelTop != Int.MIN_VALUE) {
            titleCandidates.filter { it.first > channelTop }
        } else {
            titleCandidates
        }
        val title = eligible.maxWithOrNull(
            compareBy({ it.first }, { it.second.length })
        )?.second ?: ""
        return channelHandle to title
    }

    private fun detectYouTubeShort(rootNode: AccessibilityNodeInfo): DetectionResult {
        val shieldShowing = BlockerOverlayService.instance?.isShieldContentVisible == true
        val surfaceNodes = findVisibleNodesByViewIds(rootNode, youtubeShortsContainerIds)
        if (surfaceNodes.isEmpty() || (!shieldShowing && surfaceNodes.none { it.isVisibleToUser })) {
            return DetectionResult(DetectionType.NOT_REELS)
        }

        val engagementPanel = rootNode.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/app_engagement_panel")
        val bottomSheet = rootNode.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/design_bottom_sheet")
        if ((!engagementPanel.isNullOrEmpty() && engagementPanel.any { it.isVisibleToUser }) ||
            (!bottomSheet.isNullOrEmpty() && bottomSheet.any { it.isVisibleToUser })) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        var targetNode: AccessibilityNodeInfo? = null
        val pageContent = rootNode.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page_content")
        if (!pageContent.isNullOrEmpty()) {
            val screenCenterY = Resources.getSystem().displayMetrics.heightPixels / 2
            val bounds = android.graphics.Rect()
            targetNode = pageContent
                .filter { it.isVisibleToUser }
                .minByOrNull { node ->
                    node.getBoundsInScreen(bounds)
                    kotlin.math.abs(bounds.centerY() - screenCenterY)
                }
                ?.takeIf { node ->
                    node.getBoundsInScreen(bounds)
                    val settleTolerance = maxOf(
                        (24 * Resources.getSystem().displayMetrics.density).toInt(),
                        bounds.height() / 5
                    )
                    kotlin.math.abs(bounds.centerY() - screenCenterY) <= settleTolerance
                }
            // During a held half-swipe two pages are visible but neither page is
            // centred. Do not expose either signature until the pager snaps.
            if (targetNode == null && pageContent.any { it.isVisibleToUser }) {
                return DetectionResult(DetectionType.REELS_ACTIVE, null)
            }
        }

        // NOTE: deliberately NO subtitle_window_identifier fallback. Captions
        // change every second and were polluting the title → unstable signature.
        if (targetNode == null) {
            targetNode = rootNode
        }

        // Read the channel + title from their DEDICATED content-descriptions
        // (verified via on-device uiautomator dump). The title node has
        // text="" but content-desc="<the title>", and the channel lives in a
        // "Go to channel @X" / "Subscribe to @X" desc. The old "first >20-char
        // text" scan never saw the title (empty text shadowed its content-desc)
        // and grabbed an @mention from the caption as the channel — both made
        // the signature flip on the SAME short and phantom-count every loop.
        val (channelHandle, videoTitle) = extractYouTubeChannelAndTitle(targetNode)

        val cleanedChannelHandle = channelHandle.lowercase(Locale.ROOT)
        val cleanedTitle = cleanYouTubeTitle(videoTitle)

        // ALWAYS use the "ch_<handle>_<title>" scheme, even when one part is
        // missing ("ch__<title>" / "ch_<handle>_"). The old bare-title form
        // ("<title>" with no prefix) broke isLikelyDifferentReel's segment
        // parsing the moment the channel desc loaded a beat later —
        // "<title>" → "ch_<handle>_<title>" read as a DIFFERENT reel and
        // phantom-counted the short you just opened a few seconds after entry.
        val rawId = if (cleanedChannelHandle.isEmpty() && cleanedTitle.isEmpty()) {
            ""
        } else {
            val titlePart = if (cleanedTitle.length > 30) cleanedTitle.substring(0, 30) else cleanedTitle
            "ch_${cleanedChannelHandle}_$titlePart"
        }

        if (rawId.isEmpty()) {
            return DetectionResult(DetectionType.REELS_ACTIVE, null)
        }

        val finalId = if (rawId.length > 40) rawId.substring(0, 40) else rawId
        return DetectionResult(DetectionType.REELS_ACTIVE, finalId)
    }

    private fun cleanYouTubeTitle(title: String): String {
        if (title.isEmpty()) return ""
        var cleaned = title.trim()
        val ignoredPhrases = listOf(
            "replay", "watch again", "play again", "again", "loop", "restart",
            "youtube shorts", "shorts", "view", "more", "subscribe"
        )
        for (phrase in ignoredPhrases) {
            cleaned = cleaned.replace(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE), "")
        }
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    private fun cleanInstagramCaption(caption: String): String {
        if (caption.isEmpty()) return ""
        val ignoredWords = setOf(
            "pause", "play", "tap", "to", "unmute", "mute", "more", "options",
            "open", "like", "comment", "share", "send", "save", "follow",
            "profile", "view", "audio", "original",
            // "See more" was scraped as the caption before the real one loaded,
            // so the signature flipped (…_see → …_realcaption) and read as a
            // DIFFERENT reel — a caption tap then +1-counted the same reel.
            "see"
        )
        val words = caption.split(Regex("\\s+"))
        val cleanWords = ArrayList<String>()
        for (word in words) {
            var w = word.trim()
            val suffixPatterns = listOf("...more", "... more", "more", "...")
            for (suffix in suffixPatterns) {
                if (w.endsWith(suffix, ignoreCase = true)) {
                    w = w.substring(0, w.length - suffix.length).trim()
                }
            }
            if (w.isNotEmpty() && w.lowercase(Locale.ROOT) !in ignoredWords) {
                cleanWords.add(w)
            }
            if (cleanWords.size >= 4) {
                break
            }
        }
        return cleanWords.joinToString(" ")
    }

    private fun cleanInstagramSignatureSegment(value: String, maxLength: Int): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9.]+"), "")
            .take(maxLength)
    }

    private fun detectInstagramReel(rootNode: AccessibilityNodeInfo): DetectionResult {
        if (isInstagramHomeFeed(rootNode)) {
            igLog("NOT_REELS: home feed gate")
            return DetectionResult(DetectionType.NOT_REELS)
        }

        // Sheets can resize or hide the viewer below the full-screen threshold,
        // so classify stories/comments before the viewer-geometry early return.
        if (hasInstagramStoryViewer(rootNode)) {
            igLog("NOT_REELS: story viewer")
            return DetectionResult(DetectionType.NOT_REELS)
        }
        val hasRawReelContext = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_author_username",
            "com.instagram.android:id/clips_ufi_component"
        ).any { id ->
            !rootNode.findAccessibilityNodeInfosByViewId(id).isNullOrEmpty()
        }
        if (hasRawReelContext && hasInstagramCommentPanel(rootNode)) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        // ONLY clips_* ids: on Instagram "reel_viewer_*" / "reel_main_container"
        // are the STORY viewer (IG's internal name for stories is "reel";
        // device-verified: the Reels tab tree contains clips_viewer_view_pager
        // and NO reel_viewer ids). Holding a story hides its chrome, which
        // defeated the story-heuristics — with the story ids matched here as a
        // "reel viewer", the pill appeared while holding and on story ads.
        val instagramViewerIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager"
        )
        var viewerNodes = findVisibleNodesByViewIds(rootNode, instagramViewerIds)
            .filter { isFullScreenInstagramViewer(it, rootNode) }
        val shieldShowing = BlockerOverlayService.instance?.isShieldContentVisible == true

        // Fallback: profile reels use a generic ViewPager2 without the clips_viewer
        // resource ID. Detect them by finding a visible ViewPager2 that contains
        // video content and action rail buttons (like/comment/share).
        if (viewerNodes.isEmpty() || (!shieldShowing && viewerNodes.none { it.isVisibleToUser })) {
            val fallbackViewer = findProfileReelViewer(rootNode)
            if (fallbackViewer != null) {
                viewerNodes = listOf(fallbackViewer)
            } else {
                igLog("NOT_REELS: no full-screen viewer (clips_viewer absent / not yet ≥70% screen)")
                return DetectionResult(DetectionType.NOT_REELS)
            }
        }

        // Share / "Send to" sheet — opening it slides a recipient list up, which
        // fires a scroll event and changed signature that was being miscounted
        // as a new reel. Treat it as a panel so the underlying reel isn't counted.
        val shareSheetIds = listOf(
            "com.instagram.android:id/direct_share_sheet_container",
            "com.instagram.android:id/share_sheet_container",
            "com.instagram.android:id/reshare_sheet",
            "com.instagram.android:id/share_sheet_recipient_list",
            "com.instagram.android:id/direct_recipients_list",
            "com.instagram.android:id/recipients_list"
        )
        if (findVisibleNodesByViewIds(rootNode, shareSheetIds).isNotEmpty() ||
            scanForSharePanelBfs(rootNode)) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        igLog("REELS_ACTIVE")

        val visibleViewer = viewerNodes.firstOrNull { it.isVisibleToUser } ?: viewerNodes.firstOrNull()
        var mediaNode: AccessibilityNodeInfo? = null
        val instagramMediaIds = listOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_media_component",
            "com.instagram.android:id/reel_viewer_media_container",
            "com.instagram.android:id/reel_viewer_media_layout",
            "com.instagram.android:id/reel_viewer_texture_view",
            "com.instagram.android:id/reel_media_view",
            "com.instagram.android:id/video_view"
        )
        val mediaNodes = findVisibleNodesByViewIds(rootNode, instagramMediaIds)
        if (mediaNodes.isNotEmpty()) {
            mediaNode = mediaNodes.firstOrNull { it.isVisibleToUser } ?: mediaNodes.firstOrNull()
        }

        val signatureNode = mediaNode ?: visibleViewer ?: return DetectionResult(DetectionType.REELS_ACTIVE, null)

        val mediaFingerprint = collectVisibleNodeFingerprint(visibleViewer)

        // Caption: clips_caption_component is a CONTAINER on modern Instagram —
        // the actual text lives in child nodes, so read the subtree, not the node.
        var captionText = ""
        val captionNodes = rootNode.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_caption_component")
        if (!captionNodes.isNullOrEmpty()) {
            val captionNode = captionNodes.firstOrNull { it.isVisibleToUser } ?: captionNodes.firstOrNull()
            captionText = extractFirstTextDeep(captionNode)
        }

        // Author username — unique per reel together with the caption. This is
        // the primary stable discriminator: the old fingerprint-only fallback
        // collected static UI labels (like/share buttons) that are identical
        // for every reel, so the signature never changed and nothing counted.
        var authorText = ""
        val authorIds = listOf(
            "com.instagram.android:id/clips_author_username",
            "com.instagram.android:id/clips_author_info_component"
        )
        for (id in authorIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val node = nodes.firstOrNull { it.isVisibleToUser } ?: nodes.firstOrNull()
                authorText = extractFirstTextDeep(node)
                if (authorText.isNotEmpty()) break
            }
        }

        val mediaCd = signatureNode.contentDescription?.toString()?.trim().orEmpty()
        var isAd = mediaCd.contains("Sponsored Reel by ", ignoreCase = true)
        if (!isAd) {
            val lowerFingerprint = mediaFingerprint.lowercase(Locale.ROOT)
            isAd = adKeywords.any { lowerFingerprint.contains(it) }
        }

        val cleanedAuthor = cleanInstagramSignatureSegment(authorText, 24)
        val cleanedMediaCd = cleanInstagramCaption(mediaCd)
        // Normalise the caption to its first few meaningful words so that
        // TAPPING the caption (which expands "… more" into the full text)
        // does NOT change the signature and phantom-count the same reel.
        val cleanedCaption = cleanInstagramCaption(captionText)
        val cleanedCaptionKey = cleanInstagramSignatureSegment(cleanedCaption, 32)
        val cleanedMediaKey = cleanInstagramSignatureSegment(cleanedMediaCd, 32)
        val authorCaption = when {
            cleanedAuthor.isNotEmpty() && cleanedCaptionKey.isNotEmpty() -> "${cleanedAuthor}_${cleanedCaptionKey}"
            cleanedAuthor.isNotEmpty() -> cleanedAuthor
            cleanedCaptionKey.isNotEmpty() -> "caption_${cleanedCaptionKey}"
            else -> ""
        }
        val stableBase = when {
            authorCaption.isNotEmpty() -> authorCaption
            cleanedMediaKey.isNotEmpty() -> "media_${cleanedMediaKey}"
            else -> mediaFingerprint
        }
        val stableHash = stableBase.take(48).replace(Regex("\\d+"), "")
        val formattedId = if (isAd) {
            val fingerprintPart = mediaFingerprint
                .take(24)
                .replace(Regex("[^A-Za-z0-9]+"), "_")
                .trim('_')
            "ig_ad_${stableHash}_$fingerprintPart"
        } else {
            "ig_$stableHash"
        }

        return DetectionResult(DetectionType.REELS_ACTIVE, formattedId)
    }

    private fun hasInstagramCommentPanel(rootNode: AccessibilityNodeInfo): Boolean {
        val commentPanelIds = listOf(
            "com.instagram.android:id/layout_comment_thread_edittext",
            "com.instagram.android:id/layout_comment_thread",
            "com.instagram.android:id/comment_thread_recycler_view",
            "com.instagram.android:id/comment_composer_edit_text",
            "com.instagram.android:id/comment_composer",
            "com.instagram.android:id/comments_list",
            "com.instagram.android:id/comment_list"
        )
        return findVisibleNodesByViewIds(rootNode, commentPanelIds).isNotEmpty() ||
            scanForCommentInputBfs(rootNode)
    }

    private fun isFullScreenInstagramViewer(node: AccessibilityNodeInfo, rootNode: AccessibilityNodeInfo): Boolean {
        val nodeBounds = Rect()
        val rootBounds = Rect()
        node.getBoundsInScreen(nodeBounds)
        rootNode.getBoundsInScreen(rootBounds)
        if (rootBounds.height() <= 0 || rootBounds.width() <= 0) {
            return nodeBounds.height() > 1200 && nodeBounds.width() > 600
        }
        return nodeBounds.height() >= rootBounds.height() * 0.70 &&
            nodeBounds.width() >= rootBounds.width() * 0.70
    }

    private fun isInstagramHomeFeed(rootNode: AccessibilityNodeInfo): Boolean {
        // Require at least TWO independent home-feed signals to avoid false-
        // positive on profile pages (which show a subset of feed UI elements).
        var signalCount = 0

        // Primary check: top action bar elements (visible when at the top of the home feed)
        val homeFeedTopBarIds = listOf(
            "com.instagram.android:id/action_bar_title_logo_image",
            "com.instagram.android:id/action_bar_title_logo",
            "com.instagram.android:id/action_bar_button_notification",
            "com.instagram.android:id/action_bar_button_direct_inbox",
            "com.instagram.android:id/notification_button",
            "com.instagram.android:id/direct_inbox_button"
        )
        for (id in homeFeedTopBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                signalCount++
                break
            }
        }

        // Secondary check: bottom navigation tab bar.
        // When the user scrolls down on the home feed, the top bar disappears but the
        // bottom tab bar remains visible. A reel viewer opened from a profile hides the
        // bottom tab bar entirely (full-screen). So: if the tab bar is visible AND there
        // is no dedicated clips viewer container, we are on a main navigation page
        // (home/explore/profile), not inside a profile reel viewer.
        val tabBarIds = listOf(
            "com.instagram.android:id/tab_bar",
            "com.instagram.android:id/navigation_bar_item_icon_view",
            "com.instagram.android:id/bottom_nav_bar"
        )
        var tabBarVisible = false
        for (id in tabBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                tabBarVisible = true
                break
            }
        }
        if (tabBarVisible) {
            signalCount++
            // Tab bar is visible. But the standalone Reels Tab shows both the tab bar
            // AND a clips_viewer_view_pager. Only treat as "home feed" when there is
            // NO dedicated reel-viewer container active.
            val clipsViewerIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/reel_viewer_root",
                "com.instagram.android:id/reel_main_container"
            )
            val hasClipsViewer = clipsViewerIds.any { viewId ->
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                !nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }
            }
            // Profile reels: when opened from a user's profile grid, Instagram uses a
            // generic ViewPager2 (no clips_viewer resource id) but the clips_* action
            // rail buttons ARE present. If they are visible, we are inside a reel
            // surface — NOT the home feed — even though the tab bar is showing.
            if (!hasClipsViewer && !hasActionRailInRoot(rootNode)) {
                signalCount++
            }
        }

        // Tertiary check: Home Feed specific action buttons!
        // If we see row_feed_button_like, we are definitely on the Home Feed (or explore grid post),
        // but definitely NOT in a full-screen Reel. This catches the home feed even if the top bar
        // and tab bar are hidden.
        val feedButtonIds = listOf(
            "com.instagram.android:id/row_feed_button_like",
            "com.instagram.android:id/row_feed_button_comment",
            "com.instagram.android:id/row_feed_button_share"
        )
        for (id in feedButtonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                signalCount++
                break
            }
        }

        return signalCount >= 2
    }

    private fun hasInstagramStoryViewer(rootNode: AccessibilityNodeInfo): Boolean {
        // Check known story-specific view IDs
        val storyIds = listOf(
            // Instagram LIVE streams reuse reel_viewer_root too, and their chat
            // composer matched the comment-panel scan → PANEL_OPEN → the pill
            // showed over lives (device-verified). Lives are not reels.
            "iglive_reel_layout",
            "iglive_header_layout",
            "iglive_media_layout",
            "story_container",
            "story_viewer_scrubber_root",
            "story_timestamp",
            "story_caption_container",
            "story_delayed_skip_ad_timer",
            "stories_viewer_progress_bar_container",
            "reel_viewer_timeline",
            "story_progress_segment",
            "stories_reply_composer",
            "stories_reply_message_bar",
            "stories_viewer_container",
            "story_viewer_root",
            "story_viewer_container"
        )
        val hasStoryId = storyIds.any { id ->
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id")
            !nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }
        }
        if (hasStoryId) return true

        // Heuristic: look for story progress bar segments at the top of the screen.
        // Stories have a horizontal row of thin progress segments at the very top;
        // reels never do. Also check for "Send message" / "Reply" input bars that
        // are story-only (reels use a comment sheet instead).
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var count = 0
        var progressSegments = 0
        var hasReplyBar = false
        var sawClipsUi = false
        while (queue.isNotEmpty() && count < 200) {
            val node = queue.poll() ?: continue
            count++
            if (node.isVisibleToUser) {
                val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
                val cd = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
                val className = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""

                // Any clips_* id = the REELS surface (or its comment sheet) is on
                // screen. Reel comment sheets contain "Reply" buttons and a
                // composer, so the reply-bar heuristic below must not fire there.
                if (viewId.contains("clips_")) {
                    sawClipsUi = true
                }
                // IG LIVE chrome anywhere = live stream, never a reel.
                if (viewId.contains("iglive")) {
                    return true
                }
                // Story progress bar segments
                if (viewId.contains("progress") && viewId.contains("segment")) {
                    progressSegments++
                }
                // Thin horizontal ProgressBar at the top (story indicator)
                if (className.contains("progressbar")) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.height() < 20 && bounds.top < 200) {
                        progressSegments++
                    }
                }
                // "Send message" reply bar — story-exclusive
                if (cd.contains("send message") || cd.contains("reply to") ||
                    viewId.contains("reply") || viewId.contains("send_message")) {
                    hasReplyBar = true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        // Heuristics only apply when no reels UI is present — reels' comment
        // sheets have reply rows, and their loading spinners could read as
        // progress bars. Stories never render clips_* ids.
        if (sawClipsUi) return false
        // Multiple progress segments = story (reels don't have them)
        if (progressSegments >= 2) return true
        if (hasReplyBar) return true

        return false
    }

    /**
     * Fallback for profile reels: find a visible ViewPager2 or RecyclerView
     * on Instagram that has video-like children and action-rail nodes,
     * indicating a reel surface opened from a profile grid.
     */
    private fun findProfileReelViewer(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isInstagramHomeFeed(rootNode)) return null

        // Check if the root has Instagram action-rail elements anywhere (like/comment/share)
        // visible on screen — this confirms we are inside a reel surface regardless of
        // which specific container holds the video.
        val rootHasActionRail = hasActionRailInRoot(rootNode)

        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var count = 0
        while (queue.isNotEmpty() && count < 150) {
            val node = queue.poll() ?: continue
            count++
            if (node.isVisibleToUser) {
                // Use a relaxed threshold (70%) for profile reel surfaces — they have a
                // back-button header that slightly reduces the usable height.
                val isLargeEnough = isProfileReelSizedViewer(node, rootNode)
                if (isLargeEnough) {
                    val className = node.className?.toString() ?: ""
                    val lowerClass = className.lowercase(Locale.ROOT)
                    val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
                    val isKnownViewerId = viewId.contains("clips_viewer_view_pager") ||
                        viewId.contains("reel_viewer_root") ||
                        viewId.contains("reel_main_container") ||
                        viewId.contains("reel_viewer_media_container")
                    val isKnownPagerClass = lowerClass.contains("viewpager") ||
                        lowerClass == "androidx.recyclerview.widget.recyclerview"
                    if (isKnownViewerId || isKnownPagerClass) {
                        val hasVideo = hasVideoContentChild(node)
                        // Accept if video content present AND the root screen has an action rail.
                        // The Home Feed is safely filtered out by isInstagramHomeFeed above.
                        if (hasVideo && rootHasActionRail) {
                            return node
                        }
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /** Relaxed size check for profile reel viewers (70% of screen — accounts for back-button header). */
    private fun isProfileReelSizedViewer(node: AccessibilityNodeInfo, rootNode: AccessibilityNodeInfo): Boolean {
        val nodeBounds = Rect()
        val rootBounds = Rect()
        node.getBoundsInScreen(nodeBounds)
        rootNode.getBoundsInScreen(rootBounds)
        if (rootBounds.height() <= 0 || rootBounds.width() <= 0) {
            return nodeBounds.height() > 1200 && nodeBounds.width() > 600
        }
        return nodeBounds.height() >= rootBounds.height() * 0.70 &&
            nodeBounds.width() >= rootBounds.width() * 0.70
    }

    /**
     * Check whether the root screen contains Instagram REEL-specific action-rail elements
     * visible on screen — used as a fallback signal that we are inside a reel surface on
     * a profile page.
     *
     * IMPORTANT: Only use clips_* prefixed IDs here. The home-feed row_feed_button_like /
     * row_feed_button_comment IDs also exist on the home feed and must NOT be included,
     * otherwise the home feed (when scrolled down so the top bar is hidden) gets falsely
     * detected as a profile reel surface.
     */
    private fun hasActionRailInRoot(rootNode: AccessibilityNodeInfo): Boolean {
        val reelOnlyActionIds = listOf(
            // Standard Reels tab / clips viewer action rail
            "com.instagram.android:id/clips_like_button",
            "com.instagram.android:id/clips_comment_button",
            "com.instagram.android:id/clips_follow_button",
            "com.instagram.android:id/clips_share_button",
            "com.instagram.android:id/clips_audio_button",
            // Verified on Instagram 433.x: the clips viewer UFI uses generic ids
            "com.instagram.android:id/like_button",
            "com.instagram.android:id/comment_button",
            "com.instagram.android:id/clips_ufi_component",
            "com.instagram.android:id/clips_author_username",
            "com.instagram.android:id/clips_video_container"
        )
        for (id in reelOnlyActionIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    /** Check if a node tree has a TextureView / SurfaceView / video_view child. */
    private fun hasVideoContentChild(node: AccessibilityNodeInfo, maxNodes: Int = 140): Boolean {
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < maxNodes) {
            val child = queue.poll() ?: continue
            count++
            val cls = child.className?.toString()?.lowercase(Locale.ROOT) ?: ""
            val viewId = child.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            if (cls.contains("textureview") || cls.contains("surfaceview") ||
                viewId.contains("video_view") || viewId.contains("player") ||
                viewId.contains("exo_")) {
                return true
            }
            for (i in 0 until child.childCount) {
                child.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /**
     * Markers that ONLY exist on the Facebook home feed / main surfaces —
     * never inside the dedicated full-screen Reels viewer. Any visible hit
     * means we are NOT in Reels, regardless of what the geometric layout
     * heuristic thinks (it false-positived on feed video posts).
     */
    // Phrases that ONLY appear on the Facebook home feed / feed surfaces and
    // NEVER inside the full-screen Reels viewer. The bottom navigation bar
    // ("Home, tab 1 of 6", "Marketplace, tab 4 of 6", "Notifications, tab…")
    // PERSISTS on the Reels tab, so those nav labels must NOT be markers — they
    // were false-positiving every real Reel as "home feed" and blocking
    // detection entirely. The composer + feed reel-preview phrases below are the
    // only reliable feed-exclusive signals (verified against an on-device dump:
    // the Reels viewer shows "Tap to show video controls", not "double tap to
    // play", and has no composer).
    private val facebookFeedMarkers = setOf(
        "what's on your mind", "create story", "create a story",
        "make a post", "reel video, double tap to play"
    )

    private fun isFacebookHomeFeed(rootNode: AccessibilityNodeInfo): Boolean {
        // VISIBLE nodes only. The tree always carries off-screen feed text (the
        // feed behind the viewer, adjacent pager pages) — that's what made the
        // old any-node scan false-block every real reel. The composer / story
        // tray are visible ONLY on the actual home feed (verified on-device:
        // absent from the reels page's visible tree), so this is a safe gate.
        val texts = ArrayList<String>()
        extractVisibleTextsBfs(rootNode, texts, limit = 100)
        for (text in texts) {
            val lower = text.lowercase(Locale.ROOT)
            if (facebookFeedMarkers.any { lower == it || lower.startsWith(it) }) {
                return true
            }
        }
        return false
    }

    private fun detectFacebookReel(rootNode: AccessibilityNodeInfo): DetectionResult {
        // Home-feed gate. After a Reels→Home tab switch FB keeps the reels-page
        // subtree in the tree (still visible-flagged) for a few seconds: the
        // action rail kept matching → pill over the home feed, and home-feed
        // text polluted the caption → the tab switch phantom-counted +1. The
        // visible composer/story-tray markers positively identify the home feed
        // and win over the lingering rail.
        if (isFacebookHomeFeed(rootNode)) {
            fbLog("home-feed markers visible → NOT_REELS")
            return DetectionResult(DetectionType.NOT_REELS)
        }
        // POSITIVE detection only. The old keyword home-feed gate was unreliable:
        // the accessibility tree carries off-screen nodes (the feed behind the
        // viewer, adjacent pager pages) whose text false-matched feed markers and
        // blocked every real Reel. Instead require the two signals the Reels
        // viewer always has and the home feed never does together:
        //   (1) a vertical action-rail column (Like/Comment/Share/Save on the
        //       right edge) — feed video posts use a HORIZONTAL button row, so
        //       they don't form this column, and
        //   (2) a near-full-screen video surface — feed videos sit in a smaller card.
        val result = parseGeometricLayout(rootNode, "com.facebook.katana", flags = 92)
        if (!result.isShortFormContentPresent || result.nodeInfo.isNullOrEmpty()) {
            fbLog("no vertical action-rail → NOT_REELS")
            return DetectionResult(DetectionType.NOT_REELS)
        }

        // Rail geometry alone false-matched button columns while fast-scrolling
        // the home feed (the composer/story-tray markers were scrolled off, so
        // the home gate above was silent) — a feed post got counted as a reel.
        // Require the viewer's full-screen surface too: a VISIBLE near-full-
        // screen node carrying the "Tap to show video controls" / "Reels tab
        // details" chrome desc, which only the dedicated Reels viewer has.
        if (!hasFacebookReelViewerSurface(rootNode)) {
            fbLog("rail matched but no full-screen viewer surface → NOT_REELS")
            return DetectionResult(DetectionType.NOT_REELS)
        }

        // NOTE: the old hasFullScreenVideo() gate was REMOVED. FB renders the reel
        // video in a plain hardware view (NOT a SurfaceView/TextureView) on current
        // builds, so the gate over-rejected real reels → no pill. BrainPal detects FB
        // reels from the action-rail column alone; the feed's HORIZONTAL button row
        // never clusters into an edge column, so the rail check already excludes it.

        // FB obfuscates every view-id to "(name removed)", so scanForCommentInputBfs
        // (which keys on viewId containing "comment") never fires for FB. The reel
        // viewer itself has NO text input, so ANY visible EditText means the comment /
        // reply composer is open → treat as a panel. Without this the comment TEXT was
        // scraped as the reel caption → a phantom +1 every time comments were opened.
        if (scanForCommentInputBfs(rootNode) || hasVisibleEditText(rootNode)) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        // Signature from the reel's AUTHOR (+ caption when exposed). The OLD code
        // read result.nodeInfo — the action-RAIL column — whose labels are constant
        // chrome ("Menu", "Messaging", "Like button…"), so every reel produced the
        // SAME id and only the first ever counted. The real per-reel author lives in
        // the tree as a "Follow <author>" / "View <author>'s reels" content-desc and a
        // clean author text node; the caption is exposed only on some reels.
        val (fbAuthor, fbCaption) = extractFacebookAuthorAndCaption(rootNode)
        val cleanAuthor = cleanInstagramSignatureSegment(fbAuthor, 24)
        val cleanCaption = cleanInstagramSignatureSegment(fbCaption, 24)
        val baseId = when {
            cleanAuthor.isNotEmpty() && cleanCaption.isNotEmpty() -> "fb_${cleanAuthor}_$cleanCaption"
            cleanAuthor.isNotEmpty() -> "fb_$cleanAuthor"
            cleanCaption.isNotEmpty() -> "fb_caption_$cleanCaption"
            else -> "fb_${System.currentTimeMillis() / 10000}"
        }
        // TEMP diagnostic: which nodes formed the matched rail (change-gated).
        val railDebug = result.nodeInfo.joinToString("|") { n ->
            (n.text?.toString().orEmpty().ifEmpty { n.contentDescription?.toString().orEmpty() }).take(18)
        }.take(120)
        fbLog("REELS_ACTIVE id=$baseId rail=[$railDebug]")
        return DetectionResult(DetectionType.REELS_ACTIVE, baseId)
    }

    private val facebookFollowPattern = Pattern.compile("^Follow\\s+(.+)$", Pattern.CASE_INSENSITIVE)
    private val facebookViewReelsPattern = Pattern.compile("^View\\s+(.+?)'s reels$", Pattern.CASE_INSENSITIVE)

    /** True if a VISIBLE, near-full-screen Reels-viewer surface node exists
     *  ("Tap to show video controls" / "Reels tab details" chrome). */
    private fun hasFacebookReelViewerSurface(rootNode: AccessibilityNodeInfo): Boolean {
        val shieldShowing = BlockerOverlayService.instance?.isShieldContentVisible == true
        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var count = 0
        while (queue.isNotEmpty() && count < 200) {
            val node = queue.poll() ?: continue
            count++
            val cd = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
            // English chrome descs are the precise signal; the structural
            // fallback (full-screen CLICKABLE node with no text of its own —
            // the viewer's tap-to-toggle surface) keeps FB detection working on
            // non-English devices, where the descs are localized.
            val descMatch = cd.startsWith("tap to show video controls") ||
                cd.startsWith("reels tab details")
            val structuralMatch = node.isClickable &&
                cd.isEmpty() && node.text.isNullOrEmpty()
            if ((descMatch || structuralMatch) && (node.isVisibleToUser || shieldShowing)) {
                val b = Rect()
                node.getBoundsInScreen(b)
                if (rootBounds.width() <= 0 || rootBounds.height() <= 0 ||
                    (b.width() >= rootBounds.width() * 0.85 && b.height() >= rootBounds.height() * 0.70)) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /**
     * Extract (author, caption) for a Facebook reel by BFS over the whole tree.
     * Author is taken from the reliable "Follow <author>" / "View <author>'s reels"
     * content-descriptions (present regardless of follow state via the avatar's
     * "View …'s reels" label); caption is the longest plain text that isn't chrome,
     * a numeric metric, or the author itself. FB exposes the caption only sometimes,
     * so author is the primary discriminator.
     */
    private fun extractFacebookAuthorAndCaption(rootNode: AccessibilityNodeInfo): Pair<String, String> {
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var count = 0
        var author = ""
        var caption = ""
        while (queue.isNotEmpty() && count < 250) {
            val node = queue.poll() ?: continue
            count++
            // Visible nodes only: invisible home-feed leftovers ("X updated his
            // profile picture") were read as the caption, so the signature
            // flipped with feed content instead of the reel — phantom counts.
            if (!node.isVisibleToUser) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                continue
            }
            val cd = node.contentDescription?.toString()?.trim().orEmpty()
            if (author.isEmpty() && cd.isNotEmpty()) {
                val viewM = facebookViewReelsPattern.matcher(cd)
                if (viewM.matches()) {
                    author = viewM.group(1)?.trim().orEmpty()
                } else {
                    val followM = facebookFollowPattern.matcher(cd)
                    if (followM.matches()) {
                        val a = followM.group(1)?.trim().orEmpty()
                        if (a.length in 1..40 && !a.contains("button", ignoreCase = true)) author = a
                    }
                }
            }
            val txt = node.text?.toString()?.trim().orEmpty()
            if (txt.length in 8..120 && txt.length > caption.length) {
                val lower = txt.lowercase(Locale.ROOT)
                // Caption lives in the BOTTOM part of the viewer. Social-context
                // lines ("A, B and 4 friends follow", reshare attributions) float
                // mid-screen and WON the longest-text pick, so two different
                // reels from one author produced the SAME signature — the second
                // swipe was deduped and never counted.
                val nodeBounds = Rect()
                node.getBoundsInScreen(nodeBounds)
                val rootBounds = Rect()
                rootNode.getBoundsInScreen(rootBounds)
                val inCaptionZone = rootBounds.height() <= 0 ||
                    nodeBounds.top >= rootBounds.top + rootBounds.height() * 0.55
                val isChrome = blacklistKeywords.any { lower.contains(it) } ||
                    actionKeywords.any { lower == it } ||
                    lower.contains("tab ") || lower.contains("•") ||
                    lower.contains(" follow") || lower.contains("friends") ||
                    numericMetricPattern.matcher(txt).matches() ||
                    txt.equals(author, ignoreCase = true)
                if (!isChrome && inCaptionZone) caption = txt
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return author to caption
    }

    private fun detectSnapchatSpotlight(rootNode: AccessibilityNodeInfo): DetectionResult {
        // The Spotlight page marker is the FULL-SCREEN spotlight_container
        // (verified on-device: bounds [0,0][w,h], x-center mid-screen). The old
        // flags=42 required that container INSIDE the action rail's edge
        // column — geometrically impossible — so Spotlight never detected and
        // the pill never showed. Check the container globally, then match the
        // rail column alone (flags 106 = 42 or 64 → containerId dropped).
        if (findVisibleNodesByViewIds(rootNode, listOf("com.snapchat.android:id/spotlight_container")).isEmpty()) {
            return DetectionResult(DetectionType.NOT_REELS)
        }
        val result = parseGeometricLayout(rootNode, "com.snapchat.android", flags = 106)
        if (!result.isShortFormContentPresent || result.nodeInfo.isNullOrEmpty()) {
            return DetectionResult(DetectionType.NOT_REELS)
        }

        val replyInput = rootNode.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/spotlight_reply_composer")
            ?: rootNode.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/spotlight_comment_composer")
        if (!replyInput.isNullOrEmpty() && replyInput.any { it.isVisibleToUser }) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }
        if (scanForCommentInputBfs(rootNode)) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        // Signature from the visible creator / music TEXT nodes only. The nav
        // tabs ("Chat", "Camera", "Story sent") are content-desc-only and won
        // the old scan — a chrome signature identical for every video, so only
        // the first swipe ever counted. The rail's own texts are engagement
        // COUNTS ("34K"), filtered as numeric metrics.
        // Cap 400: with FLAG_INCLUDE_NOT_IMPORTANT_VIEWS Snapchat's tree is far
        // bigger than the ~143 visible nodes — a 150-node BFS often ended
        // before reaching the username/music TEXT nodes, so the signature came
        // up null and swipes never counted (pill showed, count frozen).
        val texts = ArrayList<String>()
        val descs = ArrayList<String>()
        run {
            val queue = LinkedList<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var scanned = 0
            while (queue.isNotEmpty() && scanned < 400) {
                val current = queue.poll() ?: continue
                scanned++
                if (current.isVisibleToUser) {
                    current.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
                    current.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { descs.add(it) }
                }
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { queue.add(it) }
                }
            }
        }
        fun stableOf(candidates: List<String>): List<String> = candidates.filter { t ->
            t.length in 4..80 &&
                !numericMetricPattern.matcher(t).matches() &&
                !t.equals("spotlight", ignoreCase = true) &&
                t.lowercase(Locale.ROOT) !in snapchatChromeDescs &&
                blacklistKeywords.none { t.lowercase(Locale.ROOT).contains(it) }
        }.take(3)
        var stableParts = stableOf(texts)
        if (stableParts.isEmpty()) {
            // Some videos (ads, minimal posts) expose NO text nodes — a run of
            // them froze counting because every sig came up null. Fall back to
            // non-chrome content-descs before giving up.
            stableParts = stableOf(descs)
        }
        if (stableParts.isEmpty()) {
            fbLog("SNAP: REELS_ACTIVE sig=null (no stable texts, scanned tree)")
            // Pill can show; counting waits for a readable signature.
            return DetectionResult(DetectionType.REELS_ACTIVE, null)
        }
        val composite = stableParts.joinToString("_") { it.replace(Regex("\\s+"), "") }
        fbLog("SNAP: REELS_ACTIVE sig=snapchat_spotlight_${composite.take(40)}")
        return DetectionResult(DetectionType.REELS_ACTIVE, "snapchat_spotlight_${composite.take(40)}")
    }

    private fun detectLinkedInReel(rootNode: AccessibilityNodeInfo): DetectionResult {
        val texts = ArrayList<String>()
        extractTextsBfs(rootNode, texts, limit = 120)
        val lowerTexts = texts.map { it.lowercase(Locale.ROOT) }

        val hasShortVideoLanguage = lowerTexts.any { text ->
            shortVideoKeywords.any { keyword -> text.contains(keyword) }
        }
        val hasVideoSurface = findFirstVisibleByViewIds(
            rootNode,
            listOf(
                "com.linkedin.android:id/video_view",
                "com.linkedin.android:id/player_view",
                "com.linkedin.android:id/video_player",
                "com.linkedin.android:id/reels_player",
                "com.linkedin.android:id/reel_player",
                "com.linkedin.android:id/shorts_player"
            )
        ) != null

        val actionCount = lowerTexts.count { text ->
            actionKeywords.any { keyword -> text == keyword || text.startsWith("$keyword ") }
        }

        if ((!hasShortVideoLanguage && !hasVideoSurface) || actionCount < 3) {
            return DetectionResult(DetectionType.NOT_REELS)
        }

        if (scanForCommentInputBfs(rootNode)) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        val signatureText = texts.firstOrNull { text ->
            val lower = text.lowercase(Locale.ROOT)
            text.length > 8 &&
                !blacklistKeywords.any { lower.contains(it) } &&
                !actionKeywords.any { lower == it || lower.startsWith("$it ") } &&
                !shortVideoKeywords.any { lower == it }
        }

        val signature = if (signatureText != null) {
            "li_${signatureText.take(40).hashCode()}"
        } else {
            val composite = lowerTexts
                .filter { it.length > 3 }
                .take(8)
                .joinToString("_")
            if (composite.isNotEmpty()) "li_${composite.hashCode()}" else "li_${System.currentTimeMillis() / 10000}"
        }

        return DetectionResult(DetectionType.REELS_ACTIVE, signature)
    }

    private fun detectTikTok(rootNode: AccessibilityNodeInfo): DetectionResult {
        val rootViews = findVisibleNodesByViewIds(
            rootNode,
            tiktokPackages.map { "$it:id/view_rootview" }
        )

        if (rootViews.isEmpty()) {
            return DetectionResult(DetectionType.NOT_REELS)
        }

        if (isTikTokCommentPanelOpen(rootNode)) {
            return DetectionResult(DetectionType.PANEL_OPEN)
        }

        var authorText = ""
        var contentText = ""

        val visibleTitle = findFirstVisibleByViewIds(rootNode, tiktokPackages.map { "$it:id/title" })
        val visibleDesc = findFirstVisibleByViewIds(rootNode, tiktokPackages.map { "$it:id/desc" })

        if (visibleTitle != null) {
            authorText = visibleTitle.text?.toString() ?: ""
        }
        if (visibleDesc != null) {
            contentText = visibleDesc.text?.toString() ?: ""
        }

        val baseId: String? = when {
            authorText.isNotEmpty() && contentText.isNotEmpty() -> {
                "tt_${authorText.hashCode()}_${contentText.hashCode()}"
            }
            authorText.isNotEmpty() -> {
                "tt_${authorText.hashCode()}"
            }
            // Missing metadata is a transient UI state (comments, long-press
            // controls, or an unsettled swipe), not a new video identity.
            else -> null
        }
        return DetectionResult(DetectionType.REELS_ACTIVE, baseId)
    }

    fun isTikTokCommentPanelOpen(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        val knownPanelIds = tiktokPackages.flatMap { packageName ->
            listOf(
                "$packageName:id/comment_list",
                "$packageName:id/comments_list",
                "$packageName:id/comment_recycler_view",
                "$packageName:id/comment_recyclerview",
                "$packageName:id/comment_thread",
                "$packageName:id/comment_panel",
                "$packageName:id/comment_sheet"
            )
        }
        if (findVisibleNodesByViewIds(rootNode, knownPanelIds).isNotEmpty() ||
            scanForCommentInputBfs(rootNode) || hasVisibleEditText(rootNode)) {
            return true
        }

        // TikTok frequently renames panel resources. A large visible node whose
        // id is comment/reply-related and whose role is list/panel-like is the
        // sheet; the always-visible comment button is small and cannot pass.
        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)
        val rootWidth = rootBounds.width().takeIf { it > 0 }
            ?: Resources.getSystem().displayMetrics.widthPixels
        val rootHeight = rootBounds.height().takeIf { it > 0 }
            ?: Resources.getSystem().displayMetrics.heightPixels
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var count = 0
        var commentSemanticSignals = 0
        while (queue.isNotEmpty() && count < 200) {
            val current = queue.poll() ?: continue
            count++
            if (current.isVisibleToUser) {
                val viewId = current.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
                val className = current.className?.toString()?.lowercase(Locale.ROOT).orEmpty()
                val commentRelated = viewId.contains("comment") || viewId.contains("reply")
                val panelLike = className.contains("recyclerview") ||
                    className.contains("listview") ||
                    listOf("list", "recycler", "thread", "panel", "sheet", "container")
                        .any { viewId.contains(it) }
                if (commentRelated && panelLike) {
                    val bounds = Rect()
                    current.getBoundsInScreen(bounds)
                    if (bounds.height() >= rootHeight * 0.25f) return true
                }
                val bounds = Rect()
                current.getBoundsInScreen(bounds)
                if (ReelCountingPolicy.isLikelyTikTokPanelContainer(
                        viewId = viewId,
                        className = className,
                        isScrollable = current.isScrollable,
                        width = bounds.width(),
                        height = bounds.height(),
                        rootWidth = rootWidth,
                        rootHeight = rootHeight
                    )) {
                    return true
                }
                val label = current.text?.toString()?.trim().orEmpty()
                    .ifEmpty { current.contentDescription?.toString()?.trim().orEmpty() }
                    .lowercase(Locale.ROOT)
                if (label == "comments" || label.startsWith("add comment") ||
                    label.startsWith("view replies") || label == "reply") {
                    commentSemanticSignals++
                    if (commentSemanticSignals >= 2) return true
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun detectGenericFallback(rootNode: AccessibilityNodeInfo, platform: String): DetectionResult {
        val texts = ArrayList<String>()
        extractTextsBfs(rootNode, texts)
        val filtered = texts.filter { it.length > 5 && !blacklistKeywords.contains(it.lowercase()) }
        if (filtered.isEmpty()) return DetectionResult(DetectionType.NOT_REELS)
        val signature = "${platform}_${filtered.joinToString("_").hashCode()}"
        val finalSignature = if (signature.length > 40) signature.substring(0, 40) else signature
        return DetectionResult(DetectionType.REELS_ACTIVE, finalSignature)
    }

    /** Detect an open share/"Send to" sheet by its recipient-list / sheet view
     *  ids. Matches sheet-specific substrings only, never the always-present
     *  share BUTTON (row_feed_button_share / clips_share_button). */
    private fun scanForSharePanelBfs(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < 120) {
            val current = queue.poll() ?: continue
            count++
            val viewId = current.viewIdResourceName ?: ""
            if (viewId.contains("share_sheet") || viewId.contains("reshare") ||
                viewId.contains("recipients_list") || viewId.contains("send_to")
            ) {
                return true
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /** True if any visible EditText exists in the tree. Used for Facebook, whose
     *  obfuscated view-ids defeat id-based comment detection — a reel viewer has no
     *  text field, so a visible EditText reliably means a comment/reply composer. */
    private fun hasVisibleEditText(node: AccessibilityNodeInfo?, maxNodes: Int = 200): Boolean {
        if (node == null) return false
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < maxNodes) {
            val current = queue.poll() ?: continue
            count++
            val className = current.className?.toString()?.lowercase(Locale.ROOT) ?: ""
            if (className.contains("edittext") && current.isVisibleToUser) return true
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun scanForCommentInputBfs(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < 80) {
            val current = queue.poll() ?: continue
            count++
            val className = current.className?.toString() ?: ""
            if ((className == "android.widget.AutoCompleteTextView" || className == "android.widget.EditText") && current.isVisibleToUser) {
                val viewId = current.viewIdResourceName ?: ""
                if (viewId.contains("comment") || viewId.contains("reply") || viewId.contains("composer")) {
                    return true
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    // Geometric layout parser structures
    data class LayoutResult(
        val isShortFormContentPresent: Boolean,
        val nodeInfo: List<AccessibilityNodeInfo>?,
        val groupMap: Any? = null
    )

    private fun parseGeometricLayout(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        flags: Int
    ): LayoutResult {
        val minElements = if ((flags and 32) != 0) 3 else 2
        val obfuscatedId = if ((flags and 4) != 0) null else "$packageName:id/0_resource_name_obfuscated"
        val containerId = if ((flags and 64) != 0) null else "$packageName:id/spotlight_container"

        val xGroups = LinkedHashMap<Int, ArrayList<AccessibilityNodeInfo>>()
        val yGroups = LinkedHashMap<Int, ArrayList<AccessibilityNodeInfo>>()
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(rootNode)

        val shieldShowing = BlockerOverlayService.instance?.isShieldContentVisible == true
        var maxRight = 0

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue
            val shouldProcess = node.isVisibleToUser || (shieldShowing && node.packageName?.toString() == packageName)
            if (shouldProcess) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                maxRight = Math.max(maxRight, bounds.right)

                val xCenter = (bounds.left + bounds.right) / 2
                val yCenter = (bounds.top + bounds.bottom) / 2

                xGroups.getOrPut(xCenter) { ArrayList() }.add(node)
                yGroups.getOrPut(yCenter) { ArrayList() }.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        // Cluster columns and rows
        val clusteredColumns = clusterKeys(xGroups)

        var matchedColumnKey: Int? = null
        var matchCount = 0

        for ((colX, columnNodes) in clusteredColumns) {
            var matchingButtonCount = 0
            var containerMatchCount = 0
            val distinctActions = HashSet<String>()

            for (node in columnNodes) {
                if (obfuscatedId != null && node.viewIdResourceName == obfuscatedId) {
                    matchingButtonCount++
                } else if (obfuscatedId == null) {
                    val kw = actionRailKeywordOf(node)
                    if (kw != null) {
                        matchingButtonCount++
                        distinctActions.add(kw)
                    }
                }
                if (containerId != null && node.viewIdResourceName == containerId) {
                    containerMatchCount++
                }
            }

            // For the keyword-based (Facebook) path, require ≥2 DISTINCT action types
            // in the column. A real reel rail is Like+Comment+Share+Save stacked
            // vertically; the home FEED's columns are a single repeated action — all
            // "Like" buttons (one per stacked post) or all "More" menus — so only 1
            // distinct type. This is what tells reels apart from the feed now that the
            // full-screen-video gate is gone (it wrongly rejected real reels).
            val distinctOk = obfuscatedId != null || distinctActions.size >= 2
            if (matchingButtonCount >= minElements && distinctOk) {
                val colXDouble = colX.toDouble()
                val widthDouble = maxRight.toDouble()
                // Check if in right-side action button area (between 25% and 75% width or matches container)
                if (colXDouble >= widthDouble * 0.75 || colXDouble <= widthDouble * 0.25) {
                    if (containerId == null || containerMatchCount > 0) {
                        matchedColumnKey = colX
                        matchCount = matchingButtonCount
                        break
                    }
                }
            }
        }

        if (matchedColumnKey != null) {
            val matchedNodes = clusteredColumns[matchedColumnKey] ?: emptyList()
            return LayoutResult(true, matchedNodes)
        }

        return LayoutResult(false, null)
    }

    private fun clusterKeys(
        groups: Map<Int, List<AccessibilityNodeInfo>>
    ): Map<Int, List<AccessibilityNodeInfo>> {
        if (groups.size < 2) return groups
        val sortedKeys = groups.keys.sorted()
        val result = LinkedHashMap<Int, List<AccessibilityNodeInfo>>()

        var currentCluster = ArrayList<Int>()
        currentCluster.add(sortedKeys[0])

        for (i in 1 until sortedKeys.size) {
            if (sortedKeys[i] - sortedKeys[i - 1] <= 10) {
                currentCluster.add(sortedKeys[i])
            } else {
                val repKey = currentCluster.last()
                val clusterNodes = ArrayList<AccessibilityNodeInfo>()
                for (key in currentCluster) {
                    groups[key]?.let { clusterNodes.addAll(it) }
                }
                result[repKey] = clusterNodes
                currentCluster = ArrayList()
                currentCluster.add(sortedKeys[i])
            }
        }
        if (currentCluster.isNotEmpty()) {
            val repKey = currentCluster.last()
            val clusterNodes = ArrayList<AccessibilityNodeInfo>()
            for (key in currentCluster) {
                groups[key]?.let { clusterNodes.addAll(it) }
            }
            result[repKey] = clusterNodes
        }
        return result
    }

    private fun findFirstVisibleByViewIds(
        rootNode: AccessibilityNodeInfo,
        viewIds: List<String>
    ): AccessibilityNodeInfo? = findVisibleNodesByViewIds(rootNode, viewIds).firstOrNull()

    private fun findVisibleNodesByViewIds(
        rootNode: AccessibilityNodeInfo,
        viewIds: List<String>
    ): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val shieldShowing = BlockerOverlayService.instance?.isShieldContentVisible == true
        for (viewId in viewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId) ?: continue
            result.addAll(nodes.filter { it.isVisibleToUser || shieldShowing })
        }
        return result
    }

    private fun isActionRailNode(node: AccessibilityNodeInfo): Boolean =
        actionRailKeywordOf(node) != null

    /** Returns WHICH action keyword (like/comment/share/…) a node represents, or null.
     *  Used to count DISTINCT action types in a column so the reel rail (many distinct
     *  actions stacked vertically) is told apart from home-feed columns (a single
     *  repeated action — all "Like", or all "More" menus — across stacked posts).
     *  FB action-rail buttons carry their label only in contentDescription with
     *  text="" (empty, not null), so prefer the first NON-EMPTY of text / cd. */
    private fun actionRailKeywordOf(node: AccessibilityNodeInfo): String? {
        val shieldShowing = BlockerOverlayService.instance?.isShieldContentVisible == true
        if (!node.isVisibleToUser && !shieldShowing) return null
        val className = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""
        val buttonLike = className.contains("button") || node.isClickable
        if (!buttonLike) return null
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val label = node.text?.toString()?.trim().orEmpty()
            .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            .lowercase(Locale.ROOT)
        return actionKeywords.firstOrNull { keyword ->
            label == keyword || label.startsWith("$keyword ") || label.contains(" $keyword") ||
                viewId.contains(keyword)
        }
    }

    /**
     * BFS a node's subtree and return the first non-empty text or content
     * description (capped at 80 chars). Needed because modern Instagram
     * wraps caption/username text in container components whose own
     * text/contentDescription are null.
     */
    private fun extractFirstTextDeep(node: AccessibilityNodeInfo?, maxNodes: Int = 40): String {
        if (node == null) return ""
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < maxNodes) {
            val current = queue.poll() ?: continue
            count++
            val txt = current.text?.toString()?.trim().orEmpty()
                .ifEmpty { current.contentDescription?.toString()?.trim().orEmpty() }
            if (txt.isNotEmpty()) return txt.take(80)
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return ""
    }

    /** Like [extractTextsBfs] but reads text/content-desc from VISIBLE nodes only. */
    private fun extractVisibleTextsBfs(
        node: AccessibilityNodeInfo?,
        outList: ArrayList<String>,
        limit: Int = 250
    ) {
        if (node == null) return
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (queue.isNotEmpty() && count < limit) {
            val current = queue.poll() ?: continue
            count++
            if (current.isVisibleToUser) {
                val txt = current.text?.toString() ?: current.contentDescription?.toString() ?: ""
                if (txt.trim().isNotEmpty()) {
                    outList.add(txt)
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    private fun extractTextsBfs(
        node: AccessibilityNodeInfo?,
        outList: ArrayList<String>,
        limit: Int = 250
    ) {
        if (node == null) return
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)

        var count = 0
        while (queue.isNotEmpty() && count < limit) {
            val current = queue.poll() ?: continue
            count++
            val txt = current.text?.toString() ?: current.contentDescription?.toString() ?: ""
            if (txt.trim().isNotEmpty()) {
                outList.add(txt)
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    private fun collectVisibleNodeFingerprint(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(node)
        val parts = ArrayList<String>()
        var count = 0

        while (queue.isNotEmpty() && count < 120) {
            val current = queue.poll() ?: continue
            count++
            if (current.isVisibleToUser) {
                val viewId = current.viewIdResourceName?.substringAfterLast('/') ?: ""
                val label = (current.contentDescription?.toString() ?: current.text?.toString() ?: "")
                    .trim()
                    .take(40)
                val viewIdLower = viewId.lowercase(Locale.ROOT)
                val labelLower = label.lowercase(Locale.ROOT)
                if (instagramSignatureIgnoreKeywords.any { keyword ->
                        viewIdLower.contains(keyword) || labelLower.contains(keyword)
                    }) {
                    continue
                }
                if (viewId.isNotEmpty() || label.isNotEmpty()) {
                    parts.add("$viewId:$label")
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return parts.joinToString("|").ifEmpty { "visible_reel" }
    }

    fun isReelsContainerPresent(rootNode: AccessibilityNodeInfo?, platform: String): Boolean {
        if (rootNode == null) return false
        return when (platform) {
            Constants.PLATFORM_YOUTUBE -> {
                findVisibleNodesByViewIds(rootNode, youtubeShortsContainerIds).isNotEmpty()
            }
            Constants.PLATFORM_INSTAGRAM -> {
                if (isInstagramHomeFeed(rootNode)) return false
                // Instagram STORIES reuse the same reel_viewer_* container ids as
                // reels, so without this they read as a reel surface and the pill
                // shows over stories. detectInstagramReel already excludes stories
                // for the signature/count path; the pill path must do the same.
                if (hasInstagramStoryViewer(rootNode)) return false
                // clips_* only — the reel_viewer_* ids are the STORY viewer and
                // made the pill show over held stories / story ads (see
                // instagramViewerIds above).
                val knownIds = findVisibleNodesByViewIds(
                    rootNode,
                    listOf(
                        "com.instagram.android:id/clips_viewer_view_pager",
                        "com.instagram.android:id/clips_video_container",
                        "com.instagram.android:id/clips_media_component"
                    )
                ).isNotEmpty()
                knownIds || findProfileReelViewer(rootNode) != null
            }
            Constants.PLATFORM_FACEBOOK -> {
                !isFacebookHomeFeed(rootNode) &&
                    parseGeometricLayout(rootNode, "com.facebook.katana", flags = 92).isShortFormContentPresent
            }
            Constants.PLATFORM_SNAPCHAT -> {
                findVisibleNodesByViewIds(rootNode, listOf("com.snapchat.android:id/spotlight_container")).isNotEmpty() &&
                    parseGeometricLayout(rootNode, "com.snapchat.android", flags = 106).isShortFormContentPresent
            }
            Constants.PLATFORM_LINKEDIN -> {
                val texts = ArrayList<String>()
                extractTextsBfs(rootNode, texts, limit = 120)
                val lowerTexts = texts.map { it.lowercase(Locale.ROOT) }
                val hasShortVideoLanguage = lowerTexts.any { text ->
                    shortVideoKeywords.any { keyword -> text.contains(keyword) }
                }
                val hasVideoSurface = findFirstVisibleByViewIds(
                    rootNode,
                    listOf(
                        "com.linkedin.android:id/video_view",
                        "com.linkedin.android:id/player_view",
                        "com.linkedin.android:id/video_player",
                        "com.linkedin.android:id/reels_player",
                        "com.linkedin.android:id/reel_player",
                        "com.linkedin.android:id/shorts_player"
                    )
                ) != null
                hasShortVideoLanguage || hasVideoSurface
            }
            Constants.PLATFORM_TIKTOK -> {
                val ids = tiktokPackages.map { "$it:id/view_rootview" }
                findVisibleNodesByViewIds(rootNode, ids).isNotEmpty()
            }
            else -> false
        }
    }
}
