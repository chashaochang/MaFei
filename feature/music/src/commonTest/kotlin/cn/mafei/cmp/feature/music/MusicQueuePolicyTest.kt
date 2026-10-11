package cn.mafei.cmp.feature.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicQueuePolicyTest {
    private val tracks = listOf(
        MusicTrack("a", "A", artist = "Artist", durationMs = 1000),
        MusicTrack("b", "B", artist = "Artist", durationMs = 2000),
        MusicTrack("c", "C", artist = "Artist", durationMs = 3000),
    )

    @Test fun createsScopedQueueAndSupportsNavigation() {
        val queue = MusicQueuePolicy.create(tracks, 1, "account/server")
        assertEquals(listOf("a", "b", "c"), queue.items.map { it.id })
        assertEquals(1, queue.currentIndex)
        assertEquals(2, MusicQueuePolicy.nextIndex(queue))
        assertEquals(0, MusicQueuePolicy.previousIndex(queue))
        assertEquals(-1, MusicQueuePolicy.nextIndex(queue.copy(currentIndex = 2)))
        assertTrue(MusicQueuePolicy.matchesScope(queue, "account/server"))
        assertFalse(MusicQueuePolicy.matchesScope(queue, "other"))
    }

    @Test fun completionHonorsRepeatModeAndRemovalKeepsCurrentItem() {
        val queue = MusicQueuePolicy.create(tracks, 1, "scope")
        assertEquals(-1, MusicQueuePolicy.completionIndex(queue, MusicRepeatMode.Off))
        assertEquals(1, MusicQueuePolicy.completionIndex(queue, MusicRepeatMode.One))
        assertEquals(0, MusicQueuePolicy.completionIndex(queue, MusicRepeatMode.All))
        val removed = MusicQueuePolicy.removeAt(queue, 0)
        assertTrue(removed.changed)
        assertEquals(listOf("b", "c"), removed.queue.items.map { it.id })
        assertEquals(0, removed.queue.currentIndex)
        assertFalse(MusicQueuePolicy.removeAt(queue, 1).changed)
    }
}
