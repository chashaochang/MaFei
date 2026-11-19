package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageCursor
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortDramaQueueTest {
    private val second = PageCursor("page-2")
    private val third = PageCursor("page-3")

    @Test
    fun collectsAcrossPagesAndSeasonsAndRemovesDuplicates() = runBlocking {
        val result = collectShortDramaEpisodes { cursor ->
            when (cursor) {
                null -> Page.of((1..100).map { episode(it, 1) }, nextCursor = second)
                second -> Page.of(listOf(episode(100, 1), episode(2, 2), episode(1, 2), episode(101, 1)), nextCursor = third)
                else -> Page.of(listOf(episode(3, 2).copy(canPlay = false), episode(4, 2)), nextCursor = null)
            }
        }
        assertTrue(result.complete)
        assertEquals(104, result.items.size)
        assertEquals(listOf("1:101", "2:1", "2:2", "2:4"), result.items.takeLast(4).map { it.id.value })
    }

    @Test
    fun retainsPlayableEpisodesAfterLatePageFailure() = runBlocking {
        val result = collectShortDramaEpisodes { cursor ->
            if (cursor == null) Page.of(listOf(episode(1, 1)), nextCursor = second)
            else error("network")
        }
        assertFalse(result.complete)
        assertEquals(listOf(episode(1, 1)), result.items)
    }

    private fun episode(index: Int, season: Int) = MediaItem(
        MediaId("$season:$index"), ServerId("fixture"), ProviderKind.Jellyfin, "Episode $index", MediaType.Episode,
        seasonId = MediaId("season-$season"), seasonNumber = season, episodeNumber = index, canPlay = true,
    )
}
