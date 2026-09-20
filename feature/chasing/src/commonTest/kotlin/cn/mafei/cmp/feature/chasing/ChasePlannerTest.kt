package cn.mafei.cmp.feature.chasing

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class ChasePlannerTest {
    @Test
    fun portsLegacyDeduplicationCompletionAndSummaryRules() {
        val continued = item("episode-1", MediaType.Episode, seriesId = "series-a")
        val duplicatedNext = continued.copy(subtitle = "next")
        val next = item("episode-2", MediaType.Episode, seriesId = "series-b")
        val activeSeries = item(
            "series-a",
            MediaType.Series,
            userData = MediaUserData(unplayedItemCount = 0),
        )
        val completed = item(
            "series-c",
            MediaType.Series,
            userData = MediaUserData(unplayedItemCount = 0),
        )
        val completedDuplicate = completed.copy(title = "duplicate")

        val plan = ChasePlanner().plan(
            continueItems = listOf(continued),
            nextUpItems = listOf(duplicatedNext, next),
            followedSeriesItems = listOf(activeSeries, completed, completedDuplicate),
        )

        assertEquals("继续观看 1 · 接下来 1 · 已看完 1", plan.summary)
        assertEquals(3, plan.totalCount)
        assertEquals(listOf("episode-1"), plan.sections[0].items.map { it.media.id.value })
        assertEquals(listOf("episode-2"), plan.sections[1].items.map { it.media.id.value })
        assertEquals(listOf("series-c"), plan.sections[2].items.map { it.media.id.value })
    }

    @Test
    fun playedSeriesCompletesOnlyWhenUnplayedCountIsUnknown() {
        val notComplete = item(
            "series-a",
            MediaType.Series,
            userData = MediaUserData(isPlayed = true, unplayedItemCount = 2),
        )
        val legacyFallback = item(
            "series-b",
            MediaType.Series,
            userData = MediaUserData(isPlayed = true),
        )

        val completed = ChasePlanner().plan(emptyList(), emptyList(), listOf(notComplete, legacyFallback))
            .sections[2].items

        assertEquals(listOf("series-b"), completed.map { it.media.id.value })
    }

    private fun item(
        id: String,
        type: MediaType,
        seriesId: String? = null,
        userData: MediaUserData = MediaUserData(),
    ): ChasingItem = ChasingItem(
        media = MediaItem(
            id = MediaId(id),
            serverId = ServerId("server"),
            provider = ProviderKind.Jellyfin,
            title = id,
            mediaType = type,
            seriesId = seriesId?.let(::MediaId),
            userData = userData,
        ),
        title = id,
        subtitle = "subtitle",
        progressPercent = 0,
    )
}
