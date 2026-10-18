package cn.mafei.cmp.browser

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.HomeFeed
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.MediaPlaybackPreferences
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class BrowserTabConcurrencyTest {
    @Test fun lateDocumentRequestsCannotPublishIntoTheNewTabAndPendingWorkRetriesOnReturn() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val b = tabBrowser(a).browser
            b.start()
            val entered = List(4) { CompletableDeferred<Unit>() }
            val release = CompletableDeferred<Unit>()
            val detailCalls = AtomicInteger()
            a.itemsFetch = { id, _ -> entered[0].complete(Unit); release.await(); Page(listOf(a.item("${id?.value}-retried"))) }
            a.itemFetch = { id ->
                if (id.value == "old-detail") {
                    val count = detailCalls.incrementAndGet()
                    entered[1].complete(Unit); release.await()
                    a.item(id.value).copy(title = "response-$count")
                } else a.item(id.value)
            }
            a.personFetch = { id -> entered[2].complete(Unit); release.await(); MediaPerson(id, a.serverId, a.provider, id.value) }
            a.searchFetch = { term -> entered[3].complete(Unit); release.await(); Page(listOf(a.item(term))) }
            val jobs = listOf(
                launch { b.select(CollectionId("second")) },
                launch { b.openDetail(MediaId("old-detail")) },
                launch { b.openPerson(MediaId("old-person"), "Old person") },
                launch { b.search("old-query") },
            )
            entered.forEach { it.await() }
            b.switchTabContext("library")
            b.resumeTabContext()
            b.openDetail(MediaId("new-detail"))
            val foreground = b.stateForTab("library").value
            release.complete(Unit)
            jobs.joinAll()
            assertEquals(foreground, b.stateForTab("library").value)
            assertNull(b.stateForTab("home").value.detail)
            b.switchTabContext("home")
            b.resumeTabContext()
            assertEquals("response-2", b.state.value.detail?.title)
            assertEquals("old-person", b.state.value.person?.name)
            assertEquals("old-query", b.state.value.searchItems.single().title)
            assertEquals("second-retried", b.state.value.items.single().title)
            val requests = a.calls.size
            b.switchTabContext("library"); b.resumeTabContext()
            b.switchTabContext("home"); b.resumeTabContext()
            assertEquals(requests, a.calls.size)
        }
    }

    @Test fun preferencesSuspensionCannotPublishAnOldDetailAfterItsTabWasHidden() = runBlocking<Unit> {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val preferences = object : MediaPlaybackPreferences {
                override suspend fun preferredMediaSourceId(accountId: AccountId, mediaId: MediaId): String? {
                    entered.complete(Unit); release.await(); return "second-source"
                }
                override suspend fun setPreferredMediaSourceId(accountId: AccountId, mediaId: MediaId, sourceId: String) = Unit
            }
            val a = TabBrowserAdapter()
            a.media[MediaId("old")] = a.item("old").copy(mediaSources = listOf(MediaSourceInfo(id = "first-source"), MediaSourceInfo(id = "second-source")))
            val b = tabBrowser(a, preferences).browser
            val old = launch { b.openDetail(MediaId("old")) }
            entered.await()
            b.switchTabContext("library"); b.resumeTabContext(); b.openDetail(MediaId("new"))
            release.complete(Unit); old.join()
            assertEquals(MediaId("new"), b.state.value.detail?.id)
            assertNull(b.state.value.selectedMediaSourceId)
            b.switchTabContext("home"); b.resumeTabContext()
            assertEquals(MediaId("old"), b.state.value.detail?.id)
            assertEquals("second-source", b.state.value.selectedMediaSourceId)
        }
    }

    @Test fun anInterruptedBackgroundRefreshIsRetriedWithoutBlankingTheRetainedDetail() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val b = tabBrowser(a).browser
            b.openDetail(MediaId("movie"))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            a.itemFetch = { id ->
                val count = calls.incrementAndGet()
                if (count == 1) { entered.complete(Unit); release.await() }
                a.item(id.value).copy(title = "fresh-$count")
            }
            val refresh = launch { b.refreshDetail(MediaId("movie")) }
            entered.await()
            b.switchTabContext("library"); b.resumeTabContext()
            release.complete(Unit); refresh.join()
            b.switchTabContext("home")
            assertEquals("movie", b.state.value.detail?.title)
            b.resumeTabContext()
            assertEquals("fresh-2", b.state.value.detail?.title)
            assertFalse(b.state.value.detailLoading)
        }
    }

    @Test fun inFlightDetailFavoriteSuccessUpdatesItsDormantDocumentWithoutChangingTheNewPage() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val b = tabBrowser(a).browser
            b.openDetail(MediaId("old"))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.favoriteAction = { id, value ->
                entered.complete(Unit); release.await()
                a.media[id] = a.item(id.value).copy(userData = MediaUserData(isFavorite = value))
            }
            val mutation = launch { b.toggleFavorite() }
            entered.await()
            b.switchTabContext("library"); b.resumeTabContext(); b.openDetail(MediaId("new"))
            release.complete(Unit); mutation.join()
            assertEquals(MediaId("new"), b.state.value.detailId)
            assertFalse(b.state.value.detail!!.userData.isFavorite)
            assertNull(b.state.value.detailActionError)
            assertTrue(b.stateForTab("home").value.detail!!.userData.isFavorite)
            b.switchTabContext("home"); b.resumeTabContext()
            assertTrue(b.state.value.detail!!.userData.isFavorite)
            assertFalse(b.state.value.detailActionBusy)
        }
    }

    @Test fun listMutationSuccessReconcilesTheSameEntityLoadedByAnotherTabDuringTheRequest() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val b = tabBrowser(a).browser
            val item = a.item("same")
            b.openDetail(item.id)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.favoriteAction = { _, _ -> entered.complete(Unit); release.await() }
            val mutation = launch { b.toggleFavorite(item) }
            entered.await()
            assertTrue(b.state.value.detail!!.userData.isFavorite)
            b.switchTabContext("library"); b.resumeTabContext(); b.openDetail(item.id)
            assertFalse(b.state.value.detail!!.userData.isFavorite)
            release.complete(Unit); mutation.join()
            assertTrue(b.state.value.detail!!.userData.isFavorite)
            assertTrue(b.stateForTab("home").value.detail!!.userData.isFavorite)
        }
    }

    @Test fun personFavoriteCompletionUpdatesDormantPersonAndLateFailureCannotPolluteNewPerson() = runBlocking<Unit> {
        withTimeout(5_000) {
            for (fails in listOf(false, true)) {
                val a = TabBrowserAdapter()
                val b = tabBrowser(a).browser
                b.openPerson(MediaId("old"), "Old")
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                a.favoriteAction = { _, _ -> entered.complete(Unit); release.await(); if (fails) throw NetworkUnreachableError() }
                val mutation = launch { b.togglePersonFavorite() }
                entered.await()
                b.switchTabContext("library"); b.resumeTabContext(); b.openPerson(MediaId("new"), "New")
                release.complete(Unit); mutation.join()
                assertEquals(MediaId("new"), b.state.value.personId)
                assertNull(b.state.value.personError)
                assertFalse(b.state.value.personFavoriteBusy)
                assertEquals(!fails, b.stateForTab("home").value.person?.isFavorite)
            }
        }
    }

    @Test fun obsoleteAuthenticationFailureCannotRaiseRecoveryOnAnotherTab() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            a.searchFetch = { entered.complete(Unit); release.await(); throw AuthenticationError(AuthFailureKind.TokenExpired) }
            val b = tabBrowser(a).browser
            val pending = launch { b.search("old") }
            entered.await()
            b.switchTabContext("library"); b.resumeTabContext()
            release.complete(Unit); pending.join()
            assertNull(b.authenticationFailure.value)
            assertNull(b.state.value.searchError)
            assertEquals("", b.state.value.searchQuery)
        }
    }

    @Test fun cancellingAnOldTabRequestDoesNotClearTheNewTabsLoadingState() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val oldEntered = CompletableDeferred<Unit>()
            val newEntered = CompletableDeferred<Unit>()
            val newRelease = CompletableDeferred<Unit>()
            a.itemFetch = { id ->
                if (id.value == "old") { oldEntered.complete(Unit); awaitCancellation() }
                newEntered.complete(Unit); newRelease.await(); a.item(id.value)
            }
            val b = tabBrowser(a).browser
            val old = launch { b.openDetail(MediaId("old")) }
            oldEntered.await()
            b.switchTabContext("library"); b.resumeTabContext()
            val fresh = launch { b.openDetail(MediaId("new")) }
            newEntered.await()
            old.cancelAndJoin()
            assertTrue(b.state.value.detailLoading)
            assertEquals(MediaId("new"), b.state.value.detailId)
            newRelease.complete(Unit); fresh.join()
            assertEquals(MediaId("new"), b.state.value.detail?.id)
        }
    }

    @Test fun adoptingTheInitialTabDuringStartupDoesNotDuplicateRequestsAndBackgroundDiscoveryRemainsShared() = runBlocking<Unit> {
        withTimeout(5_000) {
            val a = TabBrowserAdapter()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val discover = a.collectionsFetch
            a.collectionsFetch = { entered.complete(Unit); release.await(); discover() }
            val repository = MediaRepository(FixedEpochClock())
            val b = MediaBrowser(repository, repository.open(a), a.capabilities)
            val startup = launch { b.start() }
            entered.await()
            b.switchTabContext("home"); b.resumeTabContext()
            assertEquals(1, a.calls.count { it == "collections" })
            b.switchTabContext("library"); b.resumeTabContext()
            release.complete(Unit); startup.join()
            assertEquals(listOf("first", "second"), b.state.value.collections.map { it.id.value })
            assertNull(b.state.value.selectedCollection)
            b.switchTabContext("home"); b.resumeTabContext()
            assertEquals(CollectionId("first"), b.state.value.selectedCollection)
            assertEquals(listOf("first-1"), b.state.value.items.map { it.id.value })
            assertEquals(1, a.calls.count { it == "collections" })
            assertEquals(HomeFeed(), b.state.value.homeFeed)
        }
    }
}
