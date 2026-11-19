package cn.mafei.cmp.session

import cn.mafei.cmp.ShortDramaEntrance
import cn.mafei.cmp.ShortDramaScrub
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.shortDramaEpisodeGroups
import cn.mafei.cmp.shortDramaSeekPosition
import cn.mafei.cmp.shortDramaSynopsis
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortDramaModeTest {
    private val server = ServerId("fixture")
    private val provider = ProviderKind.Feiniu
    private val drama = MediaCollection(CollectionId("drama"), server, provider, "精选短剧（竖屏）")
    private val regular = MediaCollection(CollectionId("tv"), server, provider, "电视剧")
    private val series = MediaItem(MediaId("series"), server, provider, "Fixture Series", MediaType.Series)

    @Test
    fun synopsisUsesTheWorkAndFallsBackToNonblankEpisodeMetadata() {
        assertEquals("Series synopsis", shortDramaSynopsis(" Series synopsis ", "Episode synopsis"))
        assertEquals("Episode synopsis", shortDramaSynopsis("  ", "Episode synopsis"))
        assertNull(shortDramaSynopsis(null, " "))
    }

    @Test
    fun currentLibraryNameDeterminesModeIncludingAfterRename() = runBlocking {
        var collections = listOf(drama, regular)
        val entrance = ShortDramaEntrance(
            collections = { collections },
            loadItem = { error("Known libraries need no lookup") },
            loadTarget = { _, _ -> null },
        )
        assertTrue(entrance.matches(series, drama.id.value))
        assertTrue(entrance.matches(series.copy(collectionId = drama.id), null))
        assertFalse(entrance.matches(series, regular.id.value))
        assertFalse(entrance.matches(series, "unknown"))
        assertFalse(entrance.matches(series.copy(mediaType = MediaType.Audio), drama.id.value))
        collections = listOf(drama.copy(name = "剧集合集"), regular)
        assertFalse(entrance.matches(series, drama.id.value))
    }

    @Test
    fun episodeOutsideLibraryViewInheritsSeriesLibrary() = runBlocking {
        var seriesDetail = series.copy(parentId = MediaId(drama.id.value))
        val entrance = ShortDramaEntrance(
            collections = { listOf(drama, regular) },
            loadItem = { id -> seriesDetail.also { assertEquals(it.id, id) } },
            loadTarget = { _, _ -> null },
        )
        val episode = series.copy(id = MediaId("episode"), mediaType = MediaType.Episode, seriesId = series.id)
        assertTrue(entrance.matches(episode, null))
        seriesDetail = series.copy(collectionId = regular.id)
        assertFalse(entrance.matches(episode, null))
        seriesDetail = series
        assertFalse(entrance.matches(episode, null))
    }

    @Test
    fun lastGroupContainsExactlyEpisodes151Through170() {
        val group = shortDramaEpisodeGroups(170).last()
        assertEquals(151, group.first + 1)
        assertEquals(170, group.last + 1)
        assertEquals(20, group.count())
    }

    @Test
    fun scrubCancelsWithoutSeekingAndCommitsOnlyTheFinalTarget() {
        val scrub = ShortDramaScrub()
        scrub.moveTo(shortDramaSeekPosition(.5f, 100_000))
        assertNull(scrub.finish(commit = false))
        scrub.moveTo(shortDramaSeekPosition(.2f, 100_000))
        scrub.moveTo(shortDramaSeekPosition(.75f, 100_000))
        assertEquals(75_000L, scrub.finish(commit = true))
        assertNull(scrub.finish(commit = true))
    }
}
