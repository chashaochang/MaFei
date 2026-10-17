package cn.mafei.cmp.data.adapter_feiniu

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackOptions
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.facade.FeiniuFacade
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Feiniu implementation of the provider-neutral [MediaProviderAdapter]
 * (GLM-04C), consuming the frozen [FeiniuFacade] bound to one server +
 * account + host resolution. Failures surface as `DomainError` per the
 * contract; [CancellationException] passes through untouched.
 *
 * Frozen decisions (docs/superpowers/plans/2026-09-08-glm04c-feiniu-adapter.md):
 * - the dedicated favorites endpoint answers favorites-only listings. The
 *   fixed legacy Movie/Series/Episode/Video kinds and sort are forwarded, but
 *   arbitrary filter composition remains unavailable (`favoritesFilter=false`);
 * - watched filtering uses the legacy `"1"` / `"0"` tag literals from
 *   `FeiniuVideoCatalogProvider.itemListPayload`; genres (Int tag) and decade
 *   (string tag) still refuse because their request values remain unproven;
 * - `/episode/list/{guid}` takes a SEASON guid; the adapter recreates the
 *   legacy provider's whole-series walk when `seasonId == null`;
 * - progress reports convert position ms to seconds and skip ts<=0 entirely
 *   (legacy FeiniuVideoPlaybackProvider.ets:739-752).
 */
class FeiniuMediaProviderAdapter(
    facade: FeiniuFacade,
    override val serverId: ServerId,
    override val accountId: AccountId,
    resolution: FeiniuHostResolution,
    nowEpochMs: () -> Long = ::systemEpochMilliseconds,
) : MediaProviderAdapter {
    private val session = FeiniuAdapterSession(facade, serverId, accountId, resolution)
    private val details = FeiniuMediaDetails(session)
    private val catalog = FeiniuCatalog(session, details)
    private val homeFeed = FeiniuHomeFeed(session, details, nowEpochMs)
    private val playback = FeiniuPlayback(session)

    override val provider: ProviderKind get() = session.provider
    override val capabilities: AdapterCapabilities get() = session.capabilities

    override suspend fun isAdministrator(): Boolean {
        val operation = session.currentOperation()
        return session.translate {
            session.facade.auth.userInfo(session.resolution, pinnedOperation = operation)
        }.isAdmin == 1
    }

    override suspend fun collections(request: PageRequest): Page<MediaCollection> = catalog.collections(request)
    override suspend fun home(request: HomeFeedRequest): HomeFeed = homeFeed.home(request)
    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> =
        catalog.items(collectionId, request)
    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> = catalog.search(term, request)
    override suspend fun item(id: MediaId): MediaItem = details.item(id)
    override suspend fun person(id: MediaId): MediaPerson = catalog.person(id)
    override suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> =
        catalog.itemsByPerson(personId, request)
    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> = catalog.seasons(seriesId)
    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> =
        catalog.episodes(seriesId, seasonId, request)
    override suspend fun nextUp(seriesId: MediaId): MediaItem? = catalog.nextUp(seriesId)
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) = catalog.setFavorite(id, favorite)
    override suspend fun setWatched(id: MediaId, watched: Boolean) = catalog.setWatched(id, watched)
    override suspend fun resumeItems(request: PageRequest): Page<MediaItem> = catalog.resumeItems(request)
    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation =
        playback.preparePlayback(item, options)
    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) =
        playback.reportPlayback(stream, event)
}

@OptIn(ExperimentalTime::class)
private fun systemEpochMilliseconds(): Long = Clock.System.now().toEpochMilliseconds()
