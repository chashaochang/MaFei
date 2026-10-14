package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.repositories.*

data class MediaBrowserState(
    val collections: List<MediaCollection> = emptyList(),
    val selectedCollection: CollectionId? = null,
    val items: List<MediaItem> = emptyList(),
    val homeFeed: HomeFeed = HomeFeed(),
    val homeLoading: Boolean = false,
    val homeLatestByCollection: Map<CollectionId, List<MediaItem>> = emptyMap(),
    val homeLatestLoading: Set<CollectionId> = emptySet(),
    val homeLatestErrors: Map<CollectionId, String> = emptyMap(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val stale: Boolean = false,
    val homeError: String? = null,
    val homeStale: Boolean = false,
    val nextCursor: PageCursor? = null,
    val totalEstimate: Int? = null,
    val librarySort: MediaSort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
    val favoriteItems: List<MediaItem> = emptyList(),
    val favoriteLoading: Boolean = false,
    val favoriteRefreshing: Boolean = false,
    val favoriteLoadingMore: Boolean = false,
    val favoriteError: String? = null,
    val favoriteNextCursor: PageCursor? = null,
    val favoriteTotalEstimate: Int? = null,
    val favoriteSort: MediaSort = MediaSort(MediaSortField.DateAdded, SortDirection.Descending),
    val searchQuery: String = "",
    val searchItems: List<MediaItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchLoadingMore: Boolean = false,
    val searchError: String? = null,
    val searchNextCursor: PageCursor? = null,
    val searchTotalEstimate: Int? = null,
    val detailId: MediaId? = null,
    val detail: MediaItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detailSeasons: List<MediaItem> = emptyList(),
    val selectedSeasonId: MediaId? = null,
    val selectedSeasonDetail: MediaItem? = null,
    val detailEpisodes: List<MediaItem> = emptyList(),
    /** Legacy series-detail play button target (provider target resolved to a real episode). */
    val detailPlayTarget: MediaItem? = null,
    /** Legacy season-detail play button target for the currently selected season. */
    val selectedSeasonPlayTarget: MediaItem? = null,
    val detailSectionsLoading: Boolean = false,
    val detailSectionsError: String? = null,
    val detailActionBusy: Boolean = false,
    val detailActionError: String? = null,
    /** 详情页上用户指定的播放版本/轨道（仅对当前 detail 生效）。 */
    val selectedMediaSourceId: String? = null,
    val selectedAudioStreamIndex: Int? = null,
    val selectedSubtitleStreamIndex: Int? = null,
    val personId: MediaId? = null,
    val person: MediaPerson? = null,
    val personLoading: Boolean = false,
    val personError: String? = null,
    val personMovies: List<MediaItem> = emptyList(),
    val personSeries: List<MediaItem> = emptyList(),
    val personEpisodes: List<MediaItem> = emptyList(),
    val personSectionsLoading: Boolean = false,
    val personFavoriteBusy: Boolean = false,
    /** 独立播放历史：resume（进行中）+ 已看完两路分页合并，剧集按剧去重。 */
    val historyItems: List<PlaybackHistoryItem> = emptyList(),
    val historyLoading: Boolean = false,
    val historyRefreshing: Boolean = false,
    val historyLoadingMore: Boolean = false,
    val historyLoadMoreFailed: Boolean = false,
    val historyCanLoadMore: Boolean = false,
    val historyError: String? = null,
    val historyResumeCursor: PageCursor? = null,
    val historyCompletedCursor: PageCursor? = null,
    /** null = 未探测；探测由 [refreshDeleteAvailability] 触发。 */
    val deleteAvailable: Boolean? = null,
    /** null = 尚未完成 Provider 用户角色探测。 */
    val administrator: Boolean? = null,
    val deleteBusy: Boolean = false,
    val deleteError: String? = null,
)

/** Durable playback selections keyed exactly like legacy: provider account + media item. */
interface MediaPlaybackPreferences {
    suspend fun preferredMediaSourceId(accountId: AccountId, mediaId: MediaId): String?

    suspend fun setPreferredMediaSourceId(accountId: AccountId, mediaId: MediaId, sourceId: String)
}
