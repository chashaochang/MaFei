package cn.mafei.cmp

import androidx.compose.ui.test.junit4.createComposeRule
import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.rememberArtworkMemoryCache
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.adapter_contract.HomeFeedRequest
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.session.RecordingAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule

class BrowseNavigatorTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun suspendedSeasonRestoreCannotSelectTheOldSeasonInAnotherTab() = runBlocking<Unit> {
        withTimeout(5_000) {
            val fixture = fixture(this)
            val (browser, navigation, navigator, adapter) = fixture

            // Both roots may legitimately show the same series, with independent
            // season selections. Populate the destination before the old request.
            navigator.selectRoot(BrowseLibraryIndexRoute)
            browser.openDetail(MediaId("series"))
            browser.selectSeason(MediaId("season-b"))
            navigation.browseBackStack += BrowseSeasonRoute("series", "season-b")
            navigator.selectRoot(BrowseHomeRoute)
            browser.openDetail(MediaId("episode-a"))
            navigation.browseBackStack += BrowseSeasonRoute("series", "season-a")
            navigation.browseBackStack += BrowseEpisodeRoute("series", "season-a", "episode-a")

            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            adapter.beforeItem = { id ->
                if (id == MediaId("series")) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            navigator.pop()
            entered.await()
            navigator.selectRoot(BrowseLibraryIndexRoute)
            assertEquals(MediaId("season-b"), browser.state.value.selectedSeasonId)
            val destination = browser.stateForTab(BrowseLibraryIndexRoute.toString()).value
            val requests = adapter.calls.toList()

            release.complete(Unit)
            currentCoroutineContext().job.children.toList().joinAll()

            assertEquals(requests, adapter.calls, "The old pop continuation must issue no season or episode request")
            assertEquals(destination, browser.stateForTab(BrowseLibraryIndexRoute.toString()).value)
            assertEquals(BrowseSeasonRoute("series", "season-b"), navigation.currentRoute)
        }
    }

    @Test
    fun queuedOldTabCommandIsIgnoredWhileTheNewTabsCommandStillRuns() = runBlocking<Unit> {
        withTimeout(5_000) {
            val (browser, navigation, navigator, adapter) = fixture(this)
            navigator.openDetailId(MediaId("old-movie"), null)
            navigator.selectRoot(BrowseLibraryIndexRoute)
            navigator.openDetailId(MediaId("new-movie"), null)
            currentCoroutineContext().job.children.toList().joinAll()

            assertEquals(listOf("item:new-movie"), adapter.calls)
            assertEquals(MediaId("new-movie"), browser.state.value.detailId)
            assertNull(browser.stateForTab(BrowseHomeRoute.toString()).value.detailId)
            assertEquals(BrowseDetailRoute("new-movie", origin = BrowseDetailOrigin.Library), navigation.currentRoute)
        }
    }

    @Test
    fun returningToTheSameRootAndStackDoesNotReviveAQueuedOldCommand() = runBlocking<Unit> {
        withTimeout(5_000) {
            val (browser, navigation, navigator, adapter) = fixture(this)
            navigator.openDetailId(MediaId("old-movie"), null)
            val originalStack = navigation.browseBackStack.toList()
            navigator.selectRoot(BrowseLibraryIndexRoute)
            navigator.selectRoot(BrowseHomeRoute)
            assertEquals(originalStack, navigation.browseBackStack.toList())
            currentCoroutineContext().job.children.toList().joinAll()

            assertEquals(emptyList(), adapter.calls)
            assertNull(browser.state.value.detailId)
            assertEquals(originalStack, navigation.browseBackStack.toList())
        }
    }

    @Test
    fun queuedColdLibrarySelectionCannotReplaceAWarmTabsLibrary() = runBlocking<Unit> {
        withTimeout(5_000) {
            val (browser, navigation, navigator, adapter) = fixture(this)
            browser.start()
            val cold = browser.state.value.collections.single { it.id == CollectionId("library-a") }
            val warm = browser.state.value.collections.single { it.id == CollectionId("library-b") }
            navigator.selectRoot(BrowseLibraryIndexRoute)
            navigator.browseCollection(warm)
            currentCoroutineContext().job.children.toList().joinAll()
            assertEquals(warm.id, browser.state.value.selectedCollection)
            val destination = browser.stateForTab(BrowseLibraryIndexRoute.toString()).value
            adapter.calls.clear()

            // A sidebar library command is queued, then the user returns to the
            // Library tab's retained B page before the request can start.
            navigator.selectLibraryRoot(cold)
            navigator.selectRoot(BrowseLibraryIndexRoute)
            currentCoroutineContext().job.children.toList().joinAll()

            assertEquals(emptyList(), adapter.calls)
            assertEquals(destination, browser.stateForTab(BrowseLibraryIndexRoute.toString()).value)
            assertEquals(BrowseLibraryRoute(warm.id.value), navigation.currentRoute)

            // The same cold destination still loads when its command stays current.
            navigator.selectLibraryRoot(cold)
            currentCoroutineContext().job.children.toList().joinAll()
            assertEquals(listOf("items:library-a"), adapter.calls)
            assertEquals(cold.id, browser.state.value.selectedCollection)
            assertEquals(listOf(MediaId("library-a-item")), browser.state.value.items.map { it.id })
        }
    }

    private suspend fun fixture(scope: CoroutineScope): NavigatorFixture {
        lateinit var artworkCache: ArtworkMemoryCache
        rule.setContent {
            artworkCache = rememberArtworkMemoryCache(this@BrowseNavigatorTest, "browse-navigator-test")
        }
        val adapter = NavigatorCatalog()
        val repository = MediaRepository(FixedEpochClock(), fetchDispatcher = Dispatchers.Unconfined)
        val session = repository.open(adapter)
        val browser = MediaBrowser(repository, session, adapter.capabilities)
        val navigation = BrowseNavigationState()
        browser.switchTabContext(navigation.activeRoot.toString())
        val search = BrowseSearchState(browser::search, { emptyList() }, {}, scope)
        val navigator = BrowseNavigator(browser, navigation, scope, search, artworkCache, { _, _ -> null }, { true })
        return NavigatorFixture(browser, navigation, navigator, adapter)
    }
}

private data class NavigatorFixture(
    val browser: MediaBrowser,
    val navigation: BrowseNavigationState,
    val navigator: BrowseNavigator,
    val adapter: NavigatorCatalog,
)

/** Reuse the existing adapter stub; only the catalog used by these commands is supplied. */
private class NavigatorCatalog : MediaProviderAdapter by RecordingAdapter() {
    override val capabilities = AdapterCapabilities()
    val calls = mutableListOf<String>()
    var beforeItem: suspend (MediaId) -> Unit = {}

    private fun media(id: MediaId, type: MediaType) = MediaItem(id, serverId, provider, id.value, mediaType = type)

    override suspend fun collections(request: PageRequest): Page<MediaCollection> =
        Page(listOf("library-a", "library-b").map { MediaCollection(CollectionId(it), serverId, provider, it) })

    override suspend fun home(request: HomeFeedRequest) = HomeFeed()

    override suspend fun items(collectionId: CollectionId?, request: PageRequest): Page<MediaItem> {
        calls += "items:${collectionId?.value}"
        return Page(listOf(media(MediaId("${collectionId?.value}-item"), MediaType.Movie)))
    }

    override suspend fun item(id: MediaId): MediaItem {
        calls += "item:${id.value}"
        beforeItem(id)
        val type = when {
            id.value == "series" -> MediaType.Series
            id.value.startsWith("season-") -> MediaType.Season
            id.value.startsWith("episode-") -> MediaType.Episode
            else -> MediaType.Movie
        }
        return media(id, type)
    }

    override suspend fun seasons(seriesId: MediaId): Page<MediaItem> {
        calls += "seasons:${seriesId.value}"
        return Page(listOf("season-a", "season-b").mapIndexed { index, id ->
            media(MediaId(id), MediaType.Season).copy(seasonNumber = index + 1)
        })
    }

    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest): Page<MediaItem> {
        calls += "episodes:${seasonId?.value}"
        return Page(listOf(media(MediaId("episode-${seasonId?.value}"), MediaType.Episode).copy(
            seriesId = seriesId, seasonId = seasonId, episodeNumber = 1,
        )))
    }
}
