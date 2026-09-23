package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType

/**
 * One playback session's ordered items — the CMP equivalent of the legacy
 * ArkUI `QueueManager` play list that `PlayerChrome` renders in its 「选集」
 * panel, advances with 「下一集」 and follows automatically when an episode
 * ends.
 *
 * The queue is a value, not a service: the player screen advances it, the
 * browse layer builds it from the context it already has.
 */
data class PlaybackQueue(
    val items: List<MediaItem>,
    val index: Int = 0,
) {
    init {
        require(items.isNotEmpty()) { "PlaybackQueue needs at least one item" }
        require(index in items.indices) { "PlaybackQueue index $index is outside 0..${items.lastIndex}" }
    }

    val current: MediaItem get() = items[index]
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < items.lastIndex
    val size: Int get() = items.size

    fun previous(): PlaybackQueue? = if (hasPrevious) copy(index = index - 1) else null

    fun next(): PlaybackQueue? = if (hasNext) copy(index = index + 1) else null

    fun select(target: Int): PlaybackQueue? =
        if (target in items.indices && target != index) copy(index = target) else null

    /** Re-locates the queue after playback started from a different entry. */
    fun movedTo(itemId: MediaId): PlaybackQueue? {
        val target = items.indexOfFirst { it.id == itemId }
        return if (target >= 0 && target != index) copy(index = target) else null
    }

    companion object {
        /**
         * Builds the queue for [item] from the context the browse layer already
         * holds: when an episode is launched from a season page, every episode
         * of that season is playable in order; anything else is a single-entry
         * queue (no 「下一集」, no 「选集」). Cross-season continuation needs a
         * season walk the browse layer does not do yet.
         */
        fun forItem(item: MediaItem, episodes: List<MediaItem>): PlaybackQueue {
            if (item.mediaType != MediaType.Episode || episodes.isEmpty()) {
                return PlaybackQueue(listOf(item))
            }
            val ordered = episodes
                .filter { it.canPlay }
                .sortedWith(compareBy<MediaItem> { it.episodeNumber ?: Int.MAX_VALUE }.thenBy { it.title })
            if (ordered.isEmpty()) return PlaybackQueue(listOf(item))
            val target = ordered.indexOfFirst { it.id == item.id }
            return if (target >= 0) {
                PlaybackQueue(ordered, target)
            } else {
                // The tapped episode is not part of the loaded season page
                // (cross-season jump): keep it playable on its own.
                PlaybackQueue(listOf(item))
            }
        }
    }
}
