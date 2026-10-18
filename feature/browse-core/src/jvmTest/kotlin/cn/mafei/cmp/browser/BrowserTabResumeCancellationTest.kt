package cn.mafei.cmp.browser

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.feature.home.BrowserTabRequest
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Mine can pause the foreground host without changing its Tab key or generation. */
class BrowserTabResumeCancellationTest {
    private class RetryGate {
        val entered = List(2) { CompletableDeferred<Unit>() }
        private val calls = AtomicInteger()
        suspend fun holdFirstTwoAttempts() {
            val attempt = calls.incrementAndGet()
            if (attempt <= 2) { entered[attempt - 1].complete(Unit); awaitCancellation() }
        }
    }

    @Test fun sameContextCancellationKeepsDetailRetryAfterTheResumeOwnerWasCancelled() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val gate = RetryGate()
            a.itemFetch = { gate.holdFirstTwoAttempts(); a.item(it.value) }
            val b = tabBrowser(a).browser
            val initial = launch { b.openDetail(MediaId("movie")) }
            gate.entered[0].await(); initial.cancelAndJoin()
            assertFalse(b.state.value.detailLoading)
            assertTrue(BrowserTabRequest.Detail in b.state.value.tabPendingRequests)
            val resume = launch { b.resumeTabContext() }
            gate.entered[1].await(); resume.cancelAndJoin()
            assertEquals("home", b.state.value.activeTabContext)
            assertFalse(b.state.value.detailLoading)
            assertTrue(BrowserTabRequest.Detail in b.state.value.tabPendingRequests)
            b.resumeTabContext()
            assertEquals(MediaId("movie"), b.state.value.detail?.id)
            assertTrue(b.state.value.tabPendingRequests.isEmpty())
        }
    }

    @Test fun sameContextCancellationKeepsPersonAndSearchRetries() = runBlocking<Unit> {
        withTimeout(5_000) {
            for (request in listOf(BrowserTabRequest.Person, BrowserTabRequest.Search)) {
                val a = TabBrowserAdapter()
                val gate = RetryGate()
                a.personFetch = { gate.holdFirstTwoAttempts(); MediaPerson(it, a.serverId, a.provider, it.value) }
                a.searchFetch = { gate.holdFirstTwoAttempts(); Page(listOf(a.item(it))) }
                val b = tabBrowser(a).browser
                val initial = launch {
                    if (request == BrowserTabRequest.Person) b.openPerson(MediaId("actor"), "Actor") else b.search("query")
                }
                gate.entered[0].await(); initial.cancelAndJoin()
                val resume = launch { b.resumeTabContext() }
                gate.entered[1].await(); resume.cancelAndJoin()
                assertFalse(b.state.value.personLoading || b.state.value.personSectionsLoading || b.state.value.searchLoading)
                assertTrue(request in b.state.value.tabPendingRequests)
                b.resumeTabContext()
                if (request == BrowserTabRequest.Person) assertEquals(MediaId("actor"), b.state.value.person?.id)
                else assertEquals(listOf("query"), b.state.value.searchItems.map { it.title })
                assertTrue(b.state.value.tabPendingRequests.isEmpty())
            }
        }
    }

    @Test fun sameContextCancellationKeepsLibraryAndContinuationRetriesWithTheirCursor() = runBlocking<Unit> {
        withTimeout(5_000) {
            for (request in listOf(BrowserTabRequest.Library, BrowserTabRequest.LibraryMore)) {
                val a = TabBrowserAdapter()
                val b = tabBrowser(a).browser
                b.start()
                val gate = RetryGate()
                val fetch = a.itemsFetch
                a.itemsFetch = { id, page -> gate.holdFirstTwoAttempts(); fetch(id, page) }
                val initial = launch {
                    if (request == BrowserTabRequest.Library) b.select(CollectionId("second")) else b.loadMore()
                }
                gate.entered[0].await(); initial.cancelAndJoin()
                val resume = launch { b.resumeTabContext() }
                gate.entered[1].await(); resume.cancelAndJoin()
                assertFalse(b.state.value.loading || b.state.value.refreshing || b.state.value.loadingMore)
                assertTrue(request in b.state.value.tabPendingRequests)
                b.resumeTabContext()
                val expected = if (request == BrowserTabRequest.Library) listOf("second-1") else listOf("first-1", "first-2")
                assertEquals(expected, b.state.value.items.map { it.id.value })
                assertTrue(b.state.value.tabPendingRequests.isEmpty())
            }
        }
    }

    @Test fun everyCancelledDetailSectionStageCanResumeWithinTheSameContext() = runBlocking<Unit> {
        withTimeout(10_000) {
            for (stage in listOf("seasons", "season-detail", "episodes")) {
                val a = TabBrowserAdapter()
                val series = a.item("series").copy(mediaType = MediaType.Series)
                val season = a.item("season").copy(mediaType = MediaType.Season, seasonNumber = 1)
                val episode = a.item("episode").copy(mediaType = MediaType.Episode)
                a.media[series.id] = series; a.media[season.id] = season
                a.seasonsFetch = { Page(listOf(season)) }
                a.episodesFetch = { _, _ -> Page(listOf(episode)) }
                val b = tabBrowser(a).browser
                if (stage == "season-detail") b.openDetail(series.id)
                val gate = RetryGate()
                when (stage) {
                    "seasons" -> a.seasonsFetch = { gate.holdFirstTwoAttempts(); Page(listOf(season)) }
                    "season-detail" -> a.itemFetch = { gate.holdFirstTwoAttempts(); a.item(it.value) }
                    "episodes" -> a.episodesFetch = { _, _ -> gate.holdFirstTwoAttempts(); Page(listOf(episode)) }
                }
                val initial = launch {
                    if (stage == "season-detail") b.selectSeason(season.id) else b.openDetail(series.id)
                }
                gate.entered[0].await(); initial.cancelAndJoin()
                assertFalse(b.state.value.detailLoading || b.state.value.detailSectionsLoading, stage)
                val resume = launch { b.resumeTabContext() }
                gate.entered[1].await(); resume.cancelAndJoin()
                assertFalse(b.state.value.detailSectionsLoading, stage)
                assertTrue(BrowserTabRequest.DetailSections in b.state.value.tabPendingRequests, stage)
                b.resumeTabContext()
                assertEquals(listOf(episode.id), b.state.value.detailEpisodes.map { it.id }, stage)
                assertFalse(b.state.value.detailSectionsLoading, stage)
                assertTrue(b.state.value.tabPendingRequests.isEmpty(), stage)
            }
        }
    }

    @Test fun cancelledBackgroundDetailReconciliationRetriesWithoutSwitchingTabs() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val b = tabBrowser(a).browser
            b.openDetail(MediaId("movie"))
            val gate = RetryGate()
            a.itemFetch = { gate.holdFirstTwoAttempts(); a.item(it.value).copy(title = "fresh") }
            val initial = launch { b.refreshDetail(MediaId("movie")) }
            gate.entered[0].await(); initial.cancelAndJoin()
            val resume = launch { b.resumeTabContext() }
            gate.entered[1].await(); resume.cancelAndJoin()
            assertEquals("movie", b.state.value.detail?.title)
            assertTrue(BrowserTabRequest.DetailRefresh in b.state.value.tabPendingRequests)
            b.resumeTabContext()
            assertEquals("fresh", b.state.value.detail?.title)
            assertTrue(b.state.value.tabPendingRequests.isEmpty())
        }
    }

    @Test fun aPersonFavoriteActionIsLockedAcrossTabsUntilTheProviderCompletes() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val b = tabBrowser(a).browser
            b.openPerson(MediaId("actor"), "Actor")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            a.favoriteAction = { _, _ -> calls.incrementAndGet(); entered.complete(Unit); release.await() }
            val mutation = launch { b.togglePersonFavorite() }
            entered.await()
            b.switchTabContext("library"); b.resumeTabContext(); b.openPerson(MediaId("actor"), "Actor")
            b.togglePersonFavorite()
            assertEquals(1, calls.get(), "A second Tab cannot submit a competing write for the same person")
            release.complete(Unit); mutation.join()
            assertTrue(b.state.value.person!!.isFavorite)
            assertTrue(b.stateForTab("home").value.person!!.isFavorite)
        }
    }

    @Test fun switchingFromATemporaryRootAtCapacityKeepsEveryFinalRetainedDocument() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val b = tabBrowser(a).browser
        for (key in listOf("home", "library", "favorites", "temporary")) {
            b.switchTabContext(key); b.resumeTabContext(); b.openDetail(MediaId(key))
        }
        b.switchTabContext("chasing", setOf("home", "library", "favorites", "chasing"))
        assertEquals(setOf("home", "library", "favorites"), b.state.value.tabDocuments.keys)
        for (key in listOf("home", "library", "favorites")) assertEquals(MediaId(key), b.stateForTab(key).value.detailId)
        assertNull(b.stateForTab("temporary").value.detail)
        assertTrue(b.state.value.tabDocuments.size <= 3)
    }
}
