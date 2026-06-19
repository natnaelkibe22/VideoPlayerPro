package com.natkibe.videoplayerpro.core

/**
 * Parses a resolution string like "1920x1080" and returns quality badges.
 * Order: resolution tier first, then HDR/60fps.
 */
object ResolutionUtil {

    data class BadgeInfo(
        val badges: List<String>,
        val resolutionTier: String? // "4K", "1080p", "720p", "SD"
    )

    fun parseBadges(resolution: String?, frameRate: Float?): BadgeInfo {
        val badges = mutableListOf<String>()
        var resolutionTier: String? = null

        if (resolution != null) {
            val parts = resolution.split("x")
            if (parts.size == 2) {
                val width = parts[0].toIntOrNull() ?: 0
                val height = parts[1].toIntOrNull() ?: 0
                val maxDim = maxOf(width, height)

                resolutionTier = when {
                    maxDim >= 3840 -> "4K"
                    maxDim >= 2560 -> "1440p"
                    maxDim >= 1920 -> "1080p"
                    maxDim >= 1280 -> "720p"
                    maxDim >= 720 -> "HD"
                    else -> "SD"
                }
            }
        }

        // Add resolution badge
        resolutionTier?.let { badges.add(it) }

        // 60fps badge
        if (frameRate != null && frameRate >= 55f) {
            badges.add("60fps")
        }

        return BadgeInfo(badges, resolutionTier)
    }

    /**
     * Returns a human-readable label like "1080p" or "4K".
     */
    fun shortLabel(width: Int, height: Int): String {
        val maxDim = maxOf(width, height)
        return when {
            maxDim >= 3840 -> "4K"
            maxDim >= 1920 -> "1080p"
            maxDim >= 1280 -> "720p"
            maxDim >= 720 -> "HD"
            else -> "SD"
        }
    }
}
