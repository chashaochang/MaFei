package cn.mafei.cmp.providers.jellyfin.core

/**
 * Jellyfin reports all media times in 100-ns ticks. Keep every conversion in
 * one place so playback code never hardcodes the factor.
 */
data object JellyfinTicks {
    const val PER_MILLISECOND: Long = 10_000L

    fun fromMilliseconds(ms: Long): Long = ms * PER_MILLISECOND

    fun toMilliseconds(ticks: Long): Long = ticks / PER_MILLISECOND
}
