package cn.mafei.cmp.feature.chasing

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.repositories.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChasingBrowserTest {
    @Test
    fun loadsLegacyThreeSourcesAndIgnoresOneSeriesNextUpFailure() = runBlocking {
        val adapter = Adapter()
        val repository = MediaRepository(FixedEpochClock(), fetchDispatcher = Dispatchers.Unconfined)
        val session = repository.open(adapter)
        val store = Store(listOf(MediaId("series-a"), MediaId("series-b"), MediaId("series-c")))
        val now = 1_800_000_000_000L
        val browser = ChasingBrowser(
            repository = repository,
            session = session,
            capabilities = adapter.capabilities,
            store = store,
            nowEpochMs = { now },
        )

        browser.refresh()

        val state = browser.state.value
        assertEquals(3, state.followedCount)
        assertEquals("继续观看 1 · 接下来 1 · 已看完 1", state.summary)
        assertEquals(listOf("resume-a"), state.sections[0].items.map { it.media.id.value })
        assertEquals(listOf("next-c"), state.sections[1].items.map { it.media.id.value })
        assertEquals(listOf("series-b"), state.sections[2].items.map { it.media.id.value })
        assertEquals(100, adapter.resumeRequest?.pageSize)
        assertEquals(
            now - 365L * 24L * 60L * 60L * 1_000L,
            adapter.nextUpRequests.single { it.first == MediaId("series-c") }.second,
        )
        assertNull(state.error)
    }

    private class Store(private val ids: List<MediaId>) : ChasingSeriesStore {
        override suspend fun seriesIds(accountId: AccountId): List<MediaId> = ids
        override suspend fun replace(accountId: AccountId, seriesIds: List<MediaId>) = Unit
    }

    private class Adapter : MediaProviderAdapter {
        override val provider = ProviderKind.Jellyfin
        override val serverId = ServerId("server")
        override val accountId = AccountId("account")
        override val capabilities = AdapterCapabilities(chasing = true)
        var resumeRequest: PageRequest? = null
        val nextUpRequests = mutableListOf<Pair<MediaId, Long>>()

        private fun media(
            id: String,
            type: MediaType,
            seriesId: String? = null,
            userData: MediaUserData = MediaUserData(),
        ) = MediaItem(
            id = MediaId(id),
            serverId = serverId,
            provider = provider,
            title = id,
            mediaType = type,
            seriesId = seriesId?.let(::MediaId),
            userData = userData,
        )

        override suspend fun resumeItems(request: PageRequest): Page<MediaItem> {
            resumeRequest = request
            return Page.of(
                listOf(
                    media(
                        "resume-a",
                        MediaType.Episode,
                        seriesId = "series-a",
                        userData = MediaUserData(playedPercentage = 35.0),
                    ),
                    media("unrelated", MediaType.Episode, seriesId = "series-other"),
                ),
            )
        }

        override suspend fun chasingNextUp(seriesId: MediaId, nextUpDateCutoffEpochMs: Long): MediaItem? {
            nextUpRequests += seriesId to nextUpDateCutoffEpochMs
            return when (seriesId.value) {
                "series-a" -> media("resume-a", MediaType.Episode, seriesId = "series-a")
                "series-b" -> error("one series failed")
                "series-c" -> media("next-c", MediaType.Episode, seriesId = "series-c")
                else -> null
            }
        }

        override suspend fun item(id: MediaId): MediaItem = when (id.value) {
            "series-b" -> media(
                id.value,
                MediaType.Series,
                userData = MediaUserData(unplayedItemCount = 0),
            )
            else -> media(id.value, MediaType.Series, userData = MediaUserData(unplayedItemCount = 1))
        }

        override suspend fun collections(request: PageRequest): Page<MediaCollection> = Page.empty()
        override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = Page.empty()
        override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = Page.empty()
        override suspend fun episodes(
            seriesId: MediaId,
            seasonId: MediaId?,
            request: PageRequest,
        ): Page<MediaItem> = Page.empty()

        override suspend fun setFavorite(id: MediaId, favorite: Boolean) = error("unused")
        override suspend fun setWatched(id: MediaId, watched: Boolean) = error("unused")
        override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
        override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) = error("unused")
    }
}
