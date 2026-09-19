package cn.mafei.cmp.data.adapter_contract

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ResourceNotFoundError
import cn.mafei.cmp.core.domain.ServerId

/**
 * Provider-neutral adapter contract consumed by the data layer (GLM-04D).
 *
 * One implementation per [ProviderKind] (04B Jellyfin, 04C Feiniu). The
 * contract speaks only `core:domain` types — never provider wire DTOs — and
 * knows nothing about Provider SDKs, Compose, the player runtime or platform
 * storage. An adapter instance is bound to one server + account at
 * construction; every result it returns is stamped with that identity.
 *
 * Error contract (frozen):
 * - Failures surface as the applicable `DomainError` subclass, translated
 *   from the provider SDK taxonomy by the adapter.
 * - `CancellationException` always propagates untouched: a cancelled call is
 *   never reported as an "empty page success".
 * - Calling a method whose [AdapterCapabilities] flag is `false` throws
 *   [cn.mafei.cmp.core.domain.ClientConfigError] via [requireCapability] —
 *   unimplemented SDK write endpoints never fake success.
 *
 * Pagination contract (frozen):
 * - `PageRequest.cursor == null` requests the first page.
 * - Offsets travel inside opaque [cn.mafei.cmp.core.domain.PageCursor]s the
 *   adapter produced ([OffsetPageCursorCodec] for offset-paging providers).
 * - Adapters must reject cursors they did not issue instead of restarting
 *   from the first page, and return `nextCursor = null` on the last page.
 */
interface MediaProviderAdapter {

    val provider: ProviderKind
    val serverId: ServerId
    val accountId: AccountId
    val capabilities: AdapterCapabilities

    /**
     * Provider-authoritative home sections. The default keeps lightweight
     * test doubles source-compatible by deriving a feed from a latest-items
     * page; Jellyfin and Feiniu override it with their native home semantics.
     */
    suspend fun home(request: HomeFeedRequest = HomeFeedRequest()): HomeFeed = derivedHomeFeed(request)

    /**
     * Server libraries ("views") for the home screen. Current providers serve
     * this as one chunk: `nextCursor` is always `null`, and a non-null cursor
     * is rejected rather than re-served as page one.
     */
    suspend fun collections(request: PageRequest): Page<MediaCollection>

    /**
     * One page of catalog items, narrowed to [collectionId] when given
     * (`null` = server-wide). [PageRequest.filter] carries the user-state
     * flags: `favoritesOnly` routes to the favorites listing the provider
     * supports (see [AdapterCapabilities.favoritesRead]/[AdapterCapabilities.favoritesFilter]),
     * `playedOnly`/`unplayedOnly` to the watched filters. Unsupported filter
     * combinations throw [cn.mafei.cmp.core.domain.ClientConfigError] instead
     * of being silently dropped.
     */
    suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem>

    /** Free-text search; capability [AdapterCapability.Search]. */
    suspend fun search(term: String, request: PageRequest): Page<MediaItem>

    /** Full detail for one item, including user state. Missing ids throw [ResourceNotFoundError]. */
    suspend fun item(id: MediaId): MediaItem

    /** Full person detail. Providers without person ids reject this default path. */
    suspend fun person(id: MediaId): MediaPerson =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose person details")

    /** Media credited to one person, used by the legacy actor detail page. */
    suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose person catalog queries")

    /** Seasons of one series (single server call, `nextCursor` always `null`). */
    suspend fun seasons(seriesId: MediaId): Page<MediaItem>

    /** Episodes of one series, optionally narrowed to [seasonId]. */
    suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem>

    /**
     * "More like this" for [id] (legacy detail page 「相似推荐」). Providers
     * without a similar-items surface reject via the default body. Single
     * chunk: the provider picks the count.
     */
    suspend fun similar(id: MediaId): List<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose similar items")

    /**
     * One page of the account's resumable (in-progress) items — the data
     * behind the standalone watch-history page. Providers without a resume
     * listing reject via the default body.
     */
    suspend fun resumeItems(request: PageRequest): Page<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose a resume listing")

    /**
     * Deletes media items (administrator action; the server enforces the
     * policy — a non-admin caller surfaces the provider's authorization
     * error). Capability [AdapterCapability.MediaDelete]; UI visibility is
     * decided by [canDeleteMedia].
     */
    suspend fun deleteItems(ids: Collection<MediaId>) {
        requireCapability(capabilities, AdapterCapability.MediaDelete)
    }

    /**
     * Whether the current caller may see/use media deletion. Probed once per
     * session by the UI; the server remains authoritative at delete time.
     */
    suspend fun canDeleteMedia(): Boolean = capabilities.mediaDelete

    /** Toggles favorite state; capability [AdapterCapability.FavoritesEdit]. */
    suspend fun setFavorite(id: MediaId, favorite: Boolean)

    /** Toggles watched state; capability [AdapterCapability.WatchedEdit]. */
    suspend fun setWatched(id: MediaId, watched: Boolean)

    /**
     * Prepares playback for [item] with user-directed [options]. Returns
     * [PlaybackPreparation.Ready] with adapter-validated absolute URLs, or an
     * explicit [PlaybackPreparation.Refused]; transport/protocol failures
     * still throw the mapped [cn.mafei.cmp.core.domain.DomainError].
     */
    suspend fun preparePlayback(item: MediaId, options: PlaybackOptions = PlaybackOptions()): PlaybackPreparation

    /**
     * Reports progress for a stream previously returned by [preparePlayback];
     * capability [AdapterCapability.PlaybackReporting]. Adapters must verify
     * the stream was prepared by THIS adapter instance and reject foreign or
     * hand-built [PlaybackPreparation.Ready] values with
     * [cn.mafei.cmp.core.domain.ClientConfigError]. The stream's opaque
     * [PlaybackPreparation.Ready.reportingContext] carries whatever the
     * provider needs for reports (either provider may require it); it is
     * never persisted and never derived by re-parsing stream URLs.
     */
    suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent)
}
