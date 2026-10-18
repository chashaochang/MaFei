package cn.mafei.cmp.feature.home

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Account-scoped compatibility facade. New work belongs to the focused browser controllers. The facade preserves existing caller APIs and exposes one coherent
 * read model.
 */
class MediaBrowser(
    repository: MediaRepository,
    session: MediaSession,
    val capabilities: AdapterCapabilities = AdapterCapabilities(),
    playbackPreferences: MediaPlaybackPreferences? = null,
    nowEpochMs: () -> Long = SystemEpochClock::nowEpochMs,
) {
    private val context = BrowserContext(repository, session, capabilities)
    val state: StateFlow<MediaBrowserState> = context.state
    val authenticationFailure: StateFlow<AuthFailureKind?> = context.authenticationFailure

    private val catalog = CatalogBrowser(context)
    private val search = SearchBrowser(context)
    private val favorite = FavoriteBrowser(context, nowEpochMs)
    private val detail = DetailBrowser(context, playbackPreferences)
    private val playbackSelection = PlaybackSelectionBrowser(context, playbackPreferences)
    private val person = PersonBrowser(context)
    private val history = HistoryBrowser(context)
    private val mediaActions = MediaActionsBrowser(context, { detail.generation })
    private val tabContexts = BrowserTabContexts(context, listOf(catalog, search, detail, person))

    fun switchTabContext(key: String, retainedKeys: Set<String>? = null) = tabContexts.switchTabContext(key, retainedKeys)

    fun stateForTab(key: String): StateFlow<MediaBrowserState> = tabContexts.stateForTab(key)

    fun retainTabContexts(keys: Set<String>) = tabContexts.retainTabContexts(keys)

    suspend fun resumeTabContext() = tabContexts.resumeTabContext()

    suspend fun start() = catalog.start()

    suspend fun refresh() = catalog.refresh()

    suspend fun refreshHome() = catalog.refreshHome()

    suspend fun select(id: CollectionId) = catalog.select(id)

    suspend fun loadHomeLatest(id: CollectionId) = catalog.loadHomeLatest(id)

    suspend fun loadMore() = catalog.loadMore()

    suspend fun setLibrarySort(sort: MediaSort) = catalog.setLibrarySort(sort)

    suspend fun search(term: String) = search.search(term)

    fun clearSearch() = search.clearSearch()

    suspend fun refreshFavorites() = favorite.refreshFavorites()

    suspend fun refreshFavoritesIfStale() = favorite.refreshFavoritesIfStale()

    suspend fun loadMoreFavorites() = favorite.loadMoreFavorites()

    suspend fun setFavoriteSort(sort: MediaSort) = favorite.setFavoriteSort(sort)

    suspend fun openDetail(id: MediaId, initialItem: MediaItem? = null) = detail.openDetail(id, initialItem)

    suspend fun refreshDetail(id: MediaId) = detail.refreshDetail(id)

    fun restoreSeasonContext(seriesId: MediaId, seasonId: MediaId) = detail.restoreSeasonContext(seriesId, seasonId)

    suspend fun selectSeason(id: MediaId) = detail.selectSeason(id)

    fun back() = detail.back()

    suspend fun selectMediaSource(sourceId: String?) = playbackSelection.selectMediaSource(sourceId)

    fun selectAudioTrack(index: Int?) = playbackSelection.selectAudioTrack(index)

    fun selectSubtitleTrack(index: Int?) = playbackSelection.selectSubtitleTrack(index)

    fun playbackOptionsFor(item: MediaItem) = playbackSelection.playbackOptionsFor(item)

    suspend fun playbackQueueFor(item: MediaItem) = playbackSelection.playbackQueueFor(item)

    suspend fun openPerson(id: MediaId, name: String) = person.openPerson(id, name)

    suspend fun togglePersonFavorite() = person.togglePersonFavorite()

    fun clearPerson() = person.clearPerson()

    suspend fun loadHistory() = history.loadHistory()

    suspend fun loadHistoryPreview() = history.loadHistoryPreview()

    suspend fun loadMoreHistory() = history.loadMoreHistory()

    suspend fun retryLoadMoreHistory() = history.retryLoadMoreHistory()

    suspend fun toggleFavorite() = mediaActions.toggleFavorite()

    suspend fun toggleWatched() = mediaActions.toggleWatched()

    suspend fun toggleWatched(item: MediaItem) = mediaActions.toggleWatched(item)

    suspend fun toggleFavorite(item: MediaItem) = mediaActions.toggleFavorite(item)

    suspend fun refreshDeleteAvailability() = mediaActions.refreshDeleteAvailability()

    suspend fun refreshAdministratorStatus() = mediaActions.refreshAdministratorStatus()

    suspend fun deleteMedia(ids: Collection<MediaId>) = mediaActions.deleteMedia(ids)
}
