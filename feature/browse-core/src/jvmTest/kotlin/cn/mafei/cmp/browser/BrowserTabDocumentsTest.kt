package cn.mafei.cmp.browser

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.feature.home.MediaBrowserState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.test.*

class BrowserTabDocumentsTest {
    @Test fun completedDocumentsRestoreLibraryPagingSortDetailPersonAndSearchWithoutRequests() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val b = tabBrowser(a).browser
        b.start()
        val titleSort = MediaSort(MediaSortField.Title, SortDirection.Ascending)
        b.setLibrarySort(titleSort)
        b.loadMore()
        b.openDetail(MediaId("home-movie"))
        b.openPerson(MediaId("home-person"), "Home person")
        b.search("home-query")
        val home = b.stateForTab("home").value

        b.switchTabContext("library")
        b.resumeTabContext()
        b.select(CollectionId("second"))
        b.openDetail(MediaId("library-movie"))
        b.openPerson(MediaId("library-person"), "Library person")
        b.search("library-query")
        val library = b.stateForTab("library").value
        assertEquals(home, b.stateForTab("home").value)
        val calls = a.calls.toList()

        repeat(3) {
            b.switchTabContext("home")
            b.resumeTabContext()
            assertEquals(home, b.stateForTab("home").value)
            assertEquals(titleSort, b.state.value.librarySort)
            assertEquals(listOf("first-1", "first-2"), b.state.value.items.map { it.id.value })
            b.switchTabContext("library")
            b.resumeTabContext()
            assertEquals(library, b.stateForTab("library").value)
        }
        assertEquals(calls, a.calls.toList(), "A completed retained document must not refetch on every Tab visit")
    }

    @Test fun inactiveProjectionNeverEmitsAnotherTabsDetailOrSearch() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val b = tabBrowser(a).browser
        b.openDetail(MediaId("home-movie"))
        b.search("home-query")
        val observed = mutableListOf<MediaBrowserState>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) { b.stateForTab("home").collect { observed += it } }
        b.switchTabContext("library")
        b.resumeTabContext()
        b.openDetail(MediaId("library-movie"))
        b.search("library-query")
        yield()
        b.switchTabContext("home")
        yield()
        collection.cancelAndJoin()
        assertTrue(observed.isNotEmpty())
        assertTrue(observed.all { it.detailId == MediaId("home-movie") && it.searchQuery == "home-query" })
        assertEquals(b.stateForTab("home").value, b.stateForTab("home").replayCache.single())
    }

    @Test fun seasonReturnDocumentSurvivesTabSwitchAndReceivesWatchedAndFavoriteFacts() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val series = a.item("series").copy(mediaType = MediaType.Series)
        val season = a.item("season").copy(mediaType = MediaType.Season, seasonNumber = 1)
        val episode = a.item("episode").copy(mediaType = MediaType.Episode, seriesId = series.id, seasonId = season.id, canPlay = true)
        a.media[series.id] = series; a.media[season.id] = season; a.media[episode.id] = episode
        a.seasonsFetch = { Page(listOf(season)) }
        a.episodesFetch = { _, _ -> Page(listOf(episode)) }
        val b = tabBrowser(a).browser
        b.openDetail(series.id)
        b.selectSeason(season.id)
        b.openDetail(episode.id)
        b.switchTabContext("library")
        b.resumeTabContext()
        b.toggleWatched(episode)
        b.toggleFavorite(episode)
        assertTrue(b.stateForTab("home").value.seasonRestoreDocument!!.episodes.single().userData.isPlayed)
        b.switchTabContext("home")
        assertTrue(b.restoreSeasonContext(series.id, season.id))
        assertEquals(series.id, b.state.value.detailId)
        assertEquals(season.id, b.state.value.selectedSeasonId)
        assertTrue(b.state.value.detailEpisodes.single().userData.isFavorite)
        assertTrue(b.state.value.detailEpisodes.single().userData.isPlayed)
        assertTrue(b.state.value.selectedSeasonPlayTarget!!.userData.isPlayed)
    }

    @Test fun mutationsUpdateDormantDocumentsAndPagingCannotResurrectDeletedRowsOrOldUserData() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val edited = a.item("edited")
        val removed = a.item("removed")
        a.itemsFetch = { _, request ->
            if (request.cursor == null) Page(listOf(edited, removed), nextCursor = PageCursor("tail"))
            else Page(listOf(edited, a.item("tail")))
        }
        a.searchFetch = { Page(listOf(edited, removed)) }
        val b = tabBrowser(a).browser
        b.start(); b.search("items"); b.openDetail(removed.id)
        b.switchTabContext("library")
        b.resumeTabContext()
        b.toggleFavorite(edited)
        b.toggleWatched(edited)
        b.deleteMedia(listOf(removed.id))
        val dormant = b.stateForTab("home").value
        assertNull(dormant.detail)
        assertNull(dormant.detailId)
        assertEquals(listOf(edited.id), dormant.searchItems.map { it.id })
        assertTrue(dormant.items.single().userData.isFavorite)
        assertTrue(dormant.items.single().userData.isPlayed)
        b.switchTabContext("home")
        b.loadMore()
        assertEquals(listOf("edited", "tail"), b.state.value.items.map { it.id.value })
        assertTrue(b.state.value.items.first().userData.isFavorite)
        assertTrue(b.state.value.items.first().userData.isPlayed)
        assertNull(b.state.value.nextCursor)
    }

    @Test fun playbackStopExpiresACompletedDocumentAndRefreshKeepsTheSelectedSeason() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val series = a.item("series").copy(mediaType = MediaType.Series)
        val seasons = (1..2).map { a.item("season-$it").copy(mediaType = MediaType.Season, seasonNumber = it) }
        seasons.forEach { a.media[it.id] = it }; a.media[series.id] = series
        a.seasonsFetch = { Page(seasons) }
        a.episodesFetch = { _, season -> Page(listOf(a.item("${season?.value}-episode"))) }
        val harness = tabBrowser(a)
        val b = harness.browser
        b.openDetail(series.id); b.selectSeason(seasons[1].id)
        b.switchTabContext("library"); b.resumeTabContext()
        val fresh = a.item("season-2-episode").copy(userData = MediaUserData(playbackPositionMs = 90_000L))
        a.media[fresh.id] = fresh
        harness.repository.markPlaybackChanged(harness.session)
        b.switchTabContext("home")
        b.resumeTabContext()
        assertEquals(seasons[1].id, b.state.value.selectedSeasonId)
        assertEquals(90_000L, b.state.value.detailEpisodes.single().userData.playbackPositionMs)
        assertEquals(2, a.calls.count { it == "detail:series" })
        val after = a.calls.size
        b.switchTabContext("library"); b.resumeTabContext()
        b.switchTabContext("home"); b.resumeTabContext()
        assertEquals(after, a.calls.size)
    }

    @Test fun stopAfterAnEpisodeWasOpenedInvalidatesTheOldSeasonReturnDocument() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val series = a.item("series").copy(mediaType = MediaType.Series)
        val season = a.item("season").copy(mediaType = MediaType.Season, seasonNumber = 1)
        val episode = a.item("episode").copy(mediaType = MediaType.Episode)
        a.media[series.id] = series; a.media[episode.id] = episode
        a.seasonsFetch = { Page(listOf(season)) }; a.episodesFetch = { _, _ -> Page(listOf(episode)) }
        val harness = tabBrowser(a)
        val b = harness.browser
        b.openDetail(series.id); b.openDetail(episode.id)
        b.switchTabContext("library"); b.resumeTabContext()
        harness.repository.markPlaybackChanged(harness.session)
        b.switchTabContext("home"); b.resumeTabContext()
        assertFalse(b.restoreSeasonContext(series.id, season.id), "Returning must reload instead of restoring pre-Stop progress")
    }

    @Test fun fourSlotsAreBoundedAndNavigationRetentionDropsTemporaryRoots() = runBlocking<Unit> {
        val a = TabBrowserAdapter()
        val b = tabBrowser(a).browser
        for (key in listOf("home", "library", "favorites", "temporary")) {
            b.switchTabContext(key); b.resumeTabContext(); b.openDetail(MediaId(key))
            assertTrue(b.state.value.tabDocuments.size <= 3)
        }
        b.switchTabContext("home")
        b.retainTabContexts(setOf("home", "library", "favorites"))
        assertEquals(setOf("library", "favorites"), b.state.value.tabDocuments.keys)
        b.switchTabContext("chasing"); b.resumeTabContext(); b.openDetail(MediaId("chasing"))
        assertEquals(MediaId("library"), b.stateForTab("library").value.detailId)
        assertEquals(MediaId("favorites"), b.stateForTab("favorites").value.detailId)
        assertNull(b.stateForTab("temporary").value.detail)
        b.switchTabContext("fifth")
        assertEquals(3, b.state.value.tabDocuments.size)
        assertNull(b.stateForTab("library").value.detail)
    }
}
