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
    val management: ServerManagement? get() = null
    /** Retires provider-owned server streams, even if playback never reached Start. */
    suspend fun releasePlayback(stream: PlaybackPreparation.Ready) {}
    suspend fun danmakuXml(itemId: MediaId): String? = null

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

    /** Exact legacy music surfaces; unsupported providers reject rather than return empty success. */
    suspend fun music(query: MusicCatalogQuery, request: PageRequest): Page<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose a music catalog")

    suspend fun liveChannels(request: PageRequest): Page<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose live channels")

    suspend fun photos(query: PhotoCatalogQuery, request: PageRequest): Page<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose photo catalogs")

    suspend fun photoOriginal(id: MediaId): cn.mafei.cmp.core.domain.MediaDownload =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose original photo downloads")

    /** Free-text search; capability [AdapterCapability.Search]. */
    suspend fun search(term: String, request: PageRequest): Page<MediaItem>

    /** Full detail for one item, including user state. Missing ids throw [ResourceNotFoundError]. */
    suspend fun item(id: MediaId): MediaItem

    /** Full person detail. Providers without person ids reject this default path. */
    suspend fun person(id: MediaId): MediaPerson =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose person details")

    /**
     * Person detail with the credited display name preserved from the source
     * item. Providers whose person endpoint is id-based keep the default;
     * Jellyfin uses the name-specific endpoint on servers where item lookup
     * does not complete for person ids.
     */
    suspend fun person(id: MediaId, name: String): MediaPerson = person(id)

    /** Media credited to one person, used by the legacy actor detail page. */
    suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> =
        throw cn.mafei.cmp.core.domain.ClientConfigError("$provider does not expose person catalog queries")

    /** Seasons of one series (single server call, `nextCursor` always `null`). */
    suspend fun seasons(seriesId: MediaId): Page<MediaItem>

    /** Episodes of one series, optionally narrowed to [seasonId]. */
    suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem>

    /**
     * Provider-authoritative next episode for one series, used by the legacy
     * detail-page play button. `null` means the provider has no target and the
     * browser applies the old first-unwatched/rewatch fallback to real episode
     * data; implementations must not invent a synthetic episode.
     */
    suspend fun nextUp(seriesId: MediaId): MediaItem? = null

    /**
     * The exact Next Up variant used by the legacy chasing page. Jellyfin
     * disables resumable/rewatch candidates and applies a one-year cutoff;
     * providers without a distinct variant may reuse [nextUp].
     */
    suspend fun chasingNextUp(seriesId: MediaId, nextUpDateCutoffEpochMs: Long): MediaItem? =
        nextUp(seriesId)

    /**
     * Provider "More like this" data retained for the legacy player-side
     * recommendation state. ArkUI `VideoDetailPage` does not render a similar
     * section, so this contract must not be used as justification for adding
     * one to the CMP detail page. Providers without this surface reject via
     * the default body; the provider picks the single-chunk count.
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

    /**
     * Provider-authoritative role shown by the legacy Mine profile header.
     * This is deliberately separate from feature capabilities: Feiniu exposes
     * `is_admin` even though the CMP adapter does not yet implement management
     * or media deletion, while Jellyfin reports the role in the user policy.
     */
    suspend fun isAdministrator(): Boolean = false

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
     * Returns a real video frame preview for the requested playback position,
     * or null when this provider/server has no preview source. The returned
     * bytes are a tile image, not a playable stream and must not be persisted.
     */
    suspend fun previewFrame(
        item: MediaId,
        positionMs: Long,
        mediaSourceId: String? = null,
    ): PlaybackPreviewFrame? = null

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
