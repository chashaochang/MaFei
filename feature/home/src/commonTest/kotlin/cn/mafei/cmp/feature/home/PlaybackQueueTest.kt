package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 播放队列（旧版 QueueManager 的等价物）：顺序、边界与上下文构造规则。 */
class PlaybackQueueTest {

    private val serverId = ServerId("s")
    private val provider = ProviderKind.Jellyfin

    private fun episode(id: String, number: Int, canPlay: Boolean = true) = MediaItem(
        id = MediaId(id),
        serverId = serverId,
        provider = provider,
        title = "E$number",
        mediaType = MediaType.Episode,
        episodeNumber = number,
        canPlay = canPlay,
    )

    private fun movie(id: String) = MediaItem(
        id = MediaId(id),
        serverId = serverId,
        provider = provider,
        title = id,
        mediaType = MediaType.Movie,
        canPlay = true,
    )

    @Test
    fun singleEntryQueueHasNoNeighbours() {
        val queue = PlaybackQueue(listOf(movie("m1")))
        assertFalse(queue.hasPrevious)
        assertFalse(queue.hasNext)
        assertNull(queue.next())
        assertNull(queue.previous())
        assertEquals("m1", queue.current.id.value)
    }

    @Test
    fun navigationMovesAndStopsAtBothEnds() {
        val queue = PlaybackQueue(listOf(movie("m1"), movie("m2"), movie("m3")), index = 1)
        assertTrue(queue.hasPrevious)
        assertTrue(queue.hasNext)
        assertEquals(0, queue.previous()?.index)
        assertEquals(2, queue.next()?.index)
        val first = PlaybackQueue(queue.items, 0)
        assertNull(first.previous())
        val last = PlaybackQueue(queue.items, 2)
        assertNull(last.next())
    }

    @Test
    fun queueForEpisodeOrdersTheSeasonAndLocatesTheEntry() {
        val episodes = listOf(episode("e3", 3), episode("e1", 1), episode("e2", 2))
        val queue = PlaybackQueue.forItem(episodes[0], episodes)
        assertEquals(listOf("e1", "e2", "e3"), queue.items.map { it.id.value })
        assertEquals(2, queue.index)
        assertFalse(queue.hasNext)
        assertTrue(queue.hasPrevious)
    }

    @Test
    fun unplayableEpisodesAreSkipped() {
        val episodes = listOf(episode("e1", 1, canPlay = false), episode("e2", 2))
        val queue = PlaybackQueue.forItem(episodes[1], episodes)
        assertEquals(listOf("e2"), queue.items.map { it.id.value })
    }

    @Test
    fun episodeOutsideTheLoadedSeasonStaysPlayableAlone() {
        val episodes = listOf(episode("e1", 1), episode("e2", 2))
        val stranger = episode("e9", 9)
        val queue = PlaybackQueue.forItem(stranger, episodes)
        assertEquals(listOf("e9"), queue.items.map { it.id.value })
        assertFalse(queue.hasNext)
    }

    @Test
    fun moviesAlwaysPlayAlone() {
        val queue = PlaybackQueue.forItem(movie("m1"), listOf(episode("e1", 1)))
        assertEquals(listOf("m1"), queue.items.map { it.id.value })
    }

    @Test
    fun movedToRelocatesWithoutChangingOrder() {
        val queue = PlaybackQueue(listOf(movie("m1"), movie("m2"), movie("m3")), index = 0)
        assertEquals(2, queue.movedTo(MediaId("m3"))?.index)
        assertNull(queue.movedTo(MediaId("missing")))
        assertTrue(queue.movedTo(MediaId("m1")) === queue)
    }

    @Test
    fun selectIgnoresOutOfRangeTargets() {
        val queue = PlaybackQueue(listOf(movie("m1"), movie("m2")), index = 0)
        assertEquals(1, queue.select(1)?.index)
        assertNull(queue.select(5))
        assertNull(queue.select(0))
    }
}
