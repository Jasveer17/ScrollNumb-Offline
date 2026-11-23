package com.scrollnumb.app.service

import com.scrollnumb.app.core.util.Constants

data class VideoMemoryEntry(
    val platform: String,
    val signature: String,
    val author: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayLabel: String
        get() {
            val cleanAuthor = author.trim().removePrefix("@")
            val cleanTitle = title.trim()
            return when {
                cleanAuthor.isNotEmpty() && cleanTitle.isNotEmpty() -> "@$cleanAuthor • \"$cleanTitle\""
                cleanAuthor.isNotEmpty() -> "@$cleanAuthor on ${platformName(platform)}"
                cleanTitle.isNotEmpty() -> "\"$cleanTitle\""
                else -> "A video on ${platformName(platform)}"
            }
        }

    private fun platformName(pkg: String): String = when (pkg) {
        Constants.PLATFORM_YOUTUBE -> "YouTube"
        Constants.PLATFORM_INSTAGRAM -> "Instagram"
        Constants.PLATFORM_FACEBOOK -> "Facebook"
        Constants.PLATFORM_SNAPCHAT -> "Snapchat"
        Constants.PLATFORM_TIKTOK -> "TikTok"
        Constants.PLATFORM_LINKEDIN -> "LinkedIn"
        else -> "Shorts"
    }
}

class RecentVideoMemory(private val capacity: Int = 10) {
    private val lock = Any()
    private val history = ArrayList<VideoMemoryEntry>()

    fun recordVideo(platform: String, signature: String) {
        if (signature.isBlank()) return
        synchronized(lock) {
            if (history.isNotEmpty() && history.last().signature == signature) {
                return
            }
            val (author, title) = parseSignature(signature)
            val entry = VideoMemoryEntry(
                platform = platform,
                signature = signature,
                author = author,
                title = title
            )
            history.add(entry)
            while (history.size > capacity) {
                history.removeAt(0)
            }
        }
    }

    /**
     * The video watched two videos ago.
     * With history [A, B, C] where C is the current reel:
     * - C (size - 1) = current video
     * - B (size - 2) = 1 video ago
     * - A (size - 3) = 2 videos ago
     */
    fun getVideoTwoAgo(): VideoMemoryEntry? {
        synchronized(lock) {
            return if (history.size >= 3) {
                history[history.size - 3]
            } else {
                null
            }
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return history.size
        }
    }

    fun clear() {
        synchronized(lock) {
            history.clear()
        }
    }

    private fun parseSignature(signature: String): Pair<String, String> {
        return when {
            signature.startsWith("ch_") -> {
                // YouTube: ch_<cleanedChannelHandle>_<titlePart>
                val parts = signature.removePrefix("ch_").split("_", limit = 2)
                val channel = parts.getOrNull(0).orEmpty()
                val title = parts.getOrNull(1)?.replace("_", " ").orEmpty()
                Pair(channel, title)
            }
            signature.startsWith("ig_") -> {
                // Instagram: ig_<author>_<captionKey> or ig_ad_<stableHash>_...
                var clean = signature.removePrefix("ig_")
                if (clean.startsWith("ad_")) {
                    clean = clean.removePrefix("ad_")
                }
                val parts = clean.split("_", limit = 2)
                val author = parts.getOrNull(0).orEmpty()
                val caption = parts.getOrNull(1)?.replace("_", " ").orEmpty()
                Pair(author, caption)
            }
            else -> {
                val parts = signature.split("_", limit = 2)
                val author = parts.getOrNull(0).orEmpty()
                val snippet = parts.getOrNull(1)?.replace("_", " ").orEmpty()
                Pair(author, snippet)
            }
        }
    }
}
