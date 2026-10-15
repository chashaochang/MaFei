package cn.mafei.cmp.feature.music

data class MusicQueueSnapshot(
    val items: List<MusicQueueItem>,
    val currentIndex: Int,
    val scope: String,
)

data class MusicQueueMutation(val queue: MusicQueueSnapshot, val changed: Boolean)

/**
 * Queue rules copied from the legacy MusicQueuePolicy. A queue is always
 * scoped to the authenticated server/account that created it.
 */
object MusicQueuePolicy {
    fun create(tracks: List<MusicTrack>, startIndex: Int, scope: String): MusicQueueSnapshot {
        if (tracks.isEmpty() || startIndex !in tracks.indices || scope.isBlank()) {
            return clear(scope)
        }
        return MusicQueueSnapshot(
            items = tracks.map { track ->
                MusicQueueItem(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumId = track.albumId,
                    imageUrl = track.imageUrl,
                    durationMs = track.durationMs,
                    favorite = track.favorite,
                    playbackPositionMs = track.playbackPositionMs,
                    scope = scope,
                )
            },
            currentIndex = startIndex,
            scope = scope,
        )
    }

    fun nextIndex(queue: MusicQueueSnapshot): Int =
        (queue.currentIndex + 1).takeIf { it in queue.items.indices } ?: -1

    fun previousIndex(queue: MusicQueueSnapshot): Int =
        (queue.currentIndex - 1).takeIf { it in queue.items.indices } ?: -1

    fun jumpIndex(queue: MusicQueueSnapshot, index: Int): Int =
        index.takeIf { it in queue.items.indices } ?: -1

    fun completionIndex(queue: MusicQueueSnapshot, repeatMode: MusicRepeatMode): Int {
        if (queue.items.isEmpty() || queue.currentIndex !in queue.items.indices) return -1
        return when (repeatMode) {
            MusicRepeatMode.One -> queue.currentIndex
            MusicRepeatMode.All -> 0
            MusicRepeatMode.Off -> -1
        }
    }

    fun removeAt(queue: MusicQueueSnapshot, index: Int): MusicQueueMutation {
        if (index !in queue.items.indices || index == queue.currentIndex) {
            return MusicQueueMutation(queue.copy(items = queue.items.toList()), changed = false)
        }
        val items = queue.items.toMutableList().also { it.removeAt(index) }
        val current = if (index < queue.currentIndex) queue.currentIndex - 1 else queue.currentIndex
        return MusicQueueMutation(queue.copy(items = items, currentIndex = current), changed = true)
    }

    fun clear(scope: String = ""): MusicQueueSnapshot = MusicQueueSnapshot(emptyList(), -1, scope)

    fun matchesScope(queue: MusicQueueSnapshot, scope: String): Boolean =
        queue.scope.isNotBlank() && queue.scope == scope
}
