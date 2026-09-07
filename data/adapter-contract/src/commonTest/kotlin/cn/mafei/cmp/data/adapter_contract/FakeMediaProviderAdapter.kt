package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.DomainError
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerId
import kotlin.coroutines.cancellation.CancellationException

/**
 * Provider-neutral fake implementing [MediaProviderAdapter] for contract
 * boundary tests and (after promotion to a testkit) later data-layer tests.
 *
 * Browsing is a deterministic offset-paged slice of [itemsSource]/[collectionsSource]
 * using [OffsetPageCursorCodec], so the cursor round-trip is exercised end to
 * end. Gated methods enforce their capability through the shared
 * [requireCapability] gate — an unsupported capability throws
 * [ClientConfigError] and never fakes success.
 *
 * Configurable outcomes:
 * - [failure] makes every call throw the given [DomainError] as-is.
 * - [cancellation] makes every call throw the given [CancellationException]
 *   untouched (never an "empty page success").
 * - [searchResults]/[playbackOutcome] preset the respective results.
 */
class FakeMediaProviderAdapter(
    override val provider: ProviderKind = ProviderKind.Jellyfin,
    override val serverId: ServerId = ServerId("fake-server"),
    override val accountId: AccountId = AccountId("fake-account"),
    override val capabilities: AdapterCapabilities = AdapterCapabilities(),
    collectionsSource: List<MediaCollection> = emptyList(),
    itemsSource: List<MediaItem> = emptyList(),
    var failure: DomainError? = null,
    var cancellation: CancellationException? = null,
    var searchResults: Page<MediaItem>? = null,
    var playbackOutcome: PlaybackPreparation = PlaybackPreparation.Refused(RefusalReason.NoPlayableSource),
) : MediaProviderAdapter {

    private val collectionsSource: List<MediaCollection> = collectionsSource
    private val itemsSource: List<MediaItem> = itemsSource

    /** Call log, one entry per contract method, for ordering assertions. */
    val calls: MutableList<String> = mutableListOf()
    val searchTerms: MutableList<String> = mutableListOf()
    val favoriteToggles: MutableList<Pair<MediaId, Boolean>> = mutableListOf()
    val watchedToggles: MutableList<Pair<MediaId, Boolean>> = mutableListOf()
    val reportedEvents: MutableList<Pair<PlaybackPreparation.Ready, PlaybackEvent>> = mutableListOf()

    private fun throwIfConfigured() {
        cancellation?.let { throw it }
        failure?.let { throw it }
    }

    override suspend fun collections(request: PageRequest): Page<MediaCollection> {
        calls += "collections"
        throwIfConfigured()
        return Page.of(collectionsSource, nextCursor = null, totalEstimate = collectionsSource.size)
    }

    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
        calls += "items"
        throwIfConfigured()
        return slice(itemsSource.filter { collectionId == null || it.collectionId == collectionId }, request)
    }

    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> {
        calls += "search"
        requireCapability(capabilities, AdapterCapability.Search)
        throwIfConfigured()
        searchTerms += term
        return searchResults ?: Page.empty()
    }

    override suspend fun item(id: MediaId): MediaItem {
        calls += "item"
        throwIfConfigured()
        return itemsSource.firstOrNull { it.id == id }
            ?: throw ResourceNotFoundError(resource = "item $id")
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        calls += "seasons"
        throwIfConfigured()
        val seasons = itemsSource.filter { it.mediaType == MediaType.Season && it.seriesId == seriesId }
        return Page.of(seasons, nextCursor = null, totalEstimate = seasons.size)
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        calls += "episodes"
        throwIfConfigured()
        val episodes = itemsSource.filter {
            it.mediaType == MediaType.Episode && it.seriesId == seriesId &&
                (seasonId == null || it.seasonId == seasonId)
        }
        return slice(episodes, request)
    }

    override suspend fun setFavorite(id: MediaId, favorite: Boolean) {
        calls += "setFavorite"
        requireCapability(capabilities, AdapterCapability.FavoritesEdit)
        throwIfConfigured()
        favoriteToggles += id to favorite
    }

    override suspend fun setWatched(id: MediaId, watched: Boolean) {
        calls += "setWatched"
        requireCapability(capabilities, AdapterCapability.WatchedEdit)
        throwIfConfigured()
        watchedToggles += id to watched
    }

    override suspend fun preparePlayback(item: MediaId): PlaybackPreparation {
        calls += "preparePlayback"
        throwIfConfigured()
        return playbackOutcome
    }

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        calls += "reportPlayback"
        requireCapability(capabilities, AdapterCapability.PlaybackReporting)
        throwIfConfigured()
        reportedEvents += stream to event
    }

    /** Offset slicing identical to what real adapters build on [OffsetPageCursorCodec]. */
    private fun slice(source: List<MediaItem>, request: PageRequest): Page<MediaItem> {
        val offset = request.cursor?.let(OffsetPageCursorCodec::decode) ?: 0
        val slice = source.drop(offset).take(request.pageSize)
        val nextOffset = offset + slice.size
        val nextCursor = if (slice.isNotEmpty() && nextOffset < source.size) {
            OffsetPageCursorCodec.encode(nextOffset)
        } else {
            null
        }
        return Page.of(slice, nextCursor = nextCursor, totalEstimate = source.size)
    }
}
