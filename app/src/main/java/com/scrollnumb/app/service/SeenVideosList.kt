package com.scrollnumb.app.service

import com.scrollnumb.app.core.util.Constants

enum class SeenResult {
    COUNT,
    SKIP_CURRENT_VIDEO,
    SKIP_RECENTLY_COUNTED
}

class SeenVideosList {
    private val maxCapacity = 50
    private val seenList = ArrayList<String>()
    private var lastSeenSignature: String? = null
    private var lastSeenIndex = -1

    fun checkSeenState(packageName: String, signature: String): SeenResult {
        val compositeKey = "$packageName|$signature"
        
        if (seenList.isNotEmpty() && lastSeenIndex >= 0 && lastSeenIndex < seenList.size) {
            if (compositeKey == seenList[lastSeenIndex]) {
                return SeenResult.SKIP_CURRENT_VIDEO
            }
        }
        
        if (seenList.contains(compositeKey)) {
            val indexOf = seenList.indexOf(compositeKey)
            if (indexOf != -1) {
                lastSeenIndex = indexOf
                lastSeenSignature = signature
            }
            return SeenResult.SKIP_RECENTLY_COUNTED
        }
        
        return SeenResult.COUNT
    }

    fun isLikelySameRecentVideo(packageName: String, signature: String): Boolean {
        if (packageName != Constants.PLATFORM_YOUTUBE) return false
        val newCore = coreSignature(signature) ?: return false
        for (entry in seenList.asReversed()) {
            val pipeIdx = entry.indexOf('|')
            if (pipeIdx < 0) continue
            if (entry.substring(0, pipeIdx) != packageName) continue
            val oldSig = entry.substring(pipeIdx + 1)
            val oldCore = coreSignature(oldSig) ?: continue
            if (oldCore == newCore) return true
        }
        return false
    }

    private fun coreSignature(sig: String): String? {
        val parts = sig.split("_", limit = 3)
        if (parts.size < 2) return null
        // Include the TITLE segment, not just platform+channel. Keying on channel
        // alone collapsed every short from one channel into a single "recent
        // video", so scrolling a single creator's Shorts never counted past the
        // first. Two different titles from the same channel must stay distinct;
        // only a genuine loop (identical title) collapses. Trailing digits are
        // stripped so view-count / timestamp drift on the SAME short still matches.
        val title = parts.getOrElse(2) { "" }.replace(Regex("\\d+"), "")
        return parts[0] + "_" + parts[1] + "_" + title
    }

    fun add(packageName: String, signature: String) {
        val compositeKey = "$packageName|$signature"
        val indexOf = seenList.indexOf(compositeKey)
        if (indexOf != -1) {
            lastSeenIndex = indexOf
        } else {
            seenList.add(compositeKey)
            lastSeenIndex = seenList.size - 1
            while (seenList.size > maxCapacity) {
                seenList.removeAt(0)
                lastSeenIndex = Math.max(0, lastSeenIndex - 1)
            }
        }
        lastSeenSignature = signature
    }

    fun clear() {
        seenList.clear()
        lastSeenIndex = -1
        lastSeenSignature = null
    }

    fun getLastSeenSignature(): String? {
        return lastSeenSignature
    }
}
