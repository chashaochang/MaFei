package cn.mafei.cmp.browser

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.*
import cn.mafei.cmp.data.repositories.*
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.MediaPlaybackPreferences
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class TabBrowserAdapter : MediaProviderAdapter {
    override val provider = ProviderKind.Jellyfin
    override val serverId = ServerId("server")
    override val accountId = AccountId("account")
    override val capabilities = AdapterCapabilities(search = true, favoritesEdit = true, watchedEdit = true, mediaDelete = true)
    val calls = CopyOnWriteArrayList<String>()
    val media = ConcurrentHashMap<MediaId, MediaItem>()
    val people = ConcurrentHashMap<MediaId, MediaPerson>()
    val deleted = ConcurrentHashMap.newKeySet<MediaId>()
    fun item(id: String): MediaItem = media[MediaId(id)] ?: MediaItem(MediaId(id), serverId, provider, id, mediaType = MediaType.Movie)
    var collectionsFetch: suspend () -> Page<MediaCollection> = {
        Page(listOf("first", "second").map { MediaCollection(CollectionId(it), serverId, provider, it) })
    }
    var itemsFetch: suspend (CollectionId?, PageRequest) -> Page<MediaItem> = { id, request ->
        val prefix = id?.value ?: "all"
        val number = if (request.cursor == null) 1 else 2
        Page(listOf(item("$prefix-$number")).filter { it.id !in deleted }, nextCursor = if (number == 1) PageCursor("tail") else null)
    }
    var itemFetch: suspend (MediaId) -> MediaItem = { id ->
        if (id in deleted) throw ResourceNotFoundError()
        item(id.value)
    }
    var personFetch: suspend (MediaId) -> MediaPerson = { id -> people[id] ?: MediaPerson(id, serverId, provider, id.value) }
    var searchFetch: suspend (String) -> Page<MediaItem> = { Page(listOf(item(it))) }
    var seasonsFetch: suspend (MediaId) -> Page<MediaItem> = { Page.empty() }
    var episodesFetch: suspend (MediaId, MediaId?) -> Page<MediaItem> = { _, _ -> Page.empty() }
    var favoriteAction: suspend (MediaId, Boolean) -> Unit = { id, value ->
        media[id] = item(id.value).let { it.copy(userData = it.userData.copy(isFavorite = value)) }
        people[id]?.let { people[id] = it.copy(isFavorite = value) }
    }
    var watchedAction: suspend (MediaId, Boolean) -> Unit = { id, value ->
        media[id] = item(id.value).let { it.copy(userData = it.userData.copy(isPlayed = value)) }
    }
    override suspend fun collections(request: PageRequest): Page<MediaCollection> { calls += "collections"; return collectionsFetch() }
    override suspend fun home(request: HomeFeedRequest) = HomeFeed()
    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
        calls += "items:${collectionId?.value}:${request.cursor?.value}"
        return itemsFetch(collectionId, request)
    }
    override suspend fun item(id: MediaId): MediaItem { calls += "detail:${id.value}"; return itemFetch(id) }
    override suspend fun person(id: MediaId): MediaPerson { calls += "person:${id.value}"; return personFetch(id) }
    override suspend fun itemsByPerson(personId: MediaId, request: PageRequest): Page<MediaItem> =
        Page(listOf(item("${personId.value}:${request.filter.mediaTypes.single().name}")))
    override suspend fun search(term: String, request: PageRequest): Page<MediaItem> { calls += "search:$term"; return searchFetch(term) }
    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> { calls += "seasons:${seriesId.value}"; return seasonsFetch(seriesId) }
    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        calls += "episodes:${seasonId?.value}"
        return episodesFetch(seriesId, seasonId)
    }
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) = favoriteAction(id, favorite)
    override suspend fun setWatched(id: MediaId, watched: Boolean) = watchedAction(id, watched)
    override suspend fun deleteItems(ids: Collection<MediaId>) { deleted.addAll(ids) }
    override suspend fun preparePlayback(item: MediaId, options: PlaybackOptions): PlaybackPreparation = error("unused")
    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent): Unit = error("unused")
}

internal data class TabBrowserHarness(val browser: MediaBrowser, val repository: MediaRepository, val session: MediaSession)

internal suspend fun tabBrowser(
    adapter: TabBrowserAdapter,
    preferences: MediaPlaybackPreferences? = null,
): TabBrowserHarness {
    val repository = MediaRepository(FixedEpochClock())
    val session = repository.open(adapter)
    val browser = MediaBrowser(repository, session, adapter.capabilities, preferences)
    browser.switchTabContext("home")
    browser.resumeTabContext()
    return TabBrowserHarness(browser, repository, session)
}
