package cn.mafei.cmp

import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiNavItem
import cn.mafei.cmp.core.domain.ProviderKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseShellMappingTest {
    private val libraries = listOf(
        MaFeiNavItem("movies", "电影", MaFeiGlyph.Library),
        MaFeiNavItem("series", "剧集", MaFeiGlyph.Library),
    )

    @Test fun homeFavoriteAndSearchDetailsIgnoreTheLastLoadedLibrary() {
        val origins = listOf(
            BrowseDetailOrigin.Home to "home",
            BrowseDetailOrigin.Favorite to "favorite",
            BrowseDetailOrigin.Search to "",
            BrowseDetailOrigin.Chasing to "chasing",
        )
        for ((origin, destination) in origins) {
            for (route in details(origin, "stale-library")) {
                assertEquals(BrowseShellContext(destination, origin = origin), route.shellContext(), "$route")
                assertEquals(destination, route.shellContext().topTabDestination, "$route")
            }
        }
    }

    @Test fun libraryContextSurvivesSeriesSeasonEpisodeAndOtherDetailRoutes() {
        for (route in listOf(BrowseLibraryRoute("series")) + details(BrowseDetailOrigin.Library, "series")) {
            assertEquals(BrowseShellContext("library", "series", BrowseDetailOrigin.Library), route.shellContext())
            assertEquals("series", route.shellContext().topTabDestination)
        }
    }

    @Test fun searchIsAPrimaryIconActionAndItsDetailsAreSecondaryContent() {
        val search = BrowseSearchRoute.shellPresentation()
        assertTrue(search.search)
        assertFalse(search.secondaryContent)
        assertEquals("", search.context.destination)
        assertEquals("", search.context.topTabDestination)
        assertTrue(search.fullPage, "Search owns a full phone page without becoming a wide-screen secondary route")

        for (route in details(BrowseDetailOrigin.Search, null)) {
            val presentation = route.shellPresentation()
            assertFalse(presentation.search, "A detail opened from Search is not the Search page itself")
            assertTrue(presentation.secondaryContent)
            assertEquals("", presentation.context.destination)
        }
    }

    @Test fun homeLatestKeepsHomeSelectedEvenWhenItFiltersOneLibrary() {
        val presentation = BrowseLatestRoute("series").shellPresentation()
        assertEquals(BrowseShellContext("home", origin = BrowseDetailOrigin.Home), presentation.context)
        assertEquals("home", presentation.context.topTabDestination)
        assertTrue(presentation.secondaryContent)
        assertTrue(presentation.fullPage)
        assertFalse(presentation.detail)
    }

    @Test fun mediaAndPersonDetailRolesStayDistinctForExistingPageBindings() {
        val routes = details(BrowseDetailOrigin.Home, null)
        for (route in routes) {
            val presentation = route.shellPresentation()
            assertTrue(presentation.detail)
            assertTrue(presentation.fullPage)
            assertTrue(presentation.secondaryContent)
        }
        assertEquals(listOf(true, true, true, false, false, false, false), routes.map { it.shellPresentation().mediaDetail })
        assertEquals(listOf(false, false, false, true, false, false, false), routes.map { it.shellPresentation().personDetail })
    }

    @Test fun retainedLibraryShortcutDoesNotChangeTheActiveRootSelection() {
        val items = browseNavigationItems(false, libraries, libraryTabId = "series")
        assertEquals(listOf("home", "favorite", "series"), items.topTabs.map { it.key })
        assertEquals("剧集", items.topTabs.last().label)
        for ((route, selected) in listOf(
            BrowseHomeRoute to "home", BrowseFavoriteRoute to "favorite", BrowseSearchRoute to "",
        )) {
            assertEquals(selected, route.shellPresentation().context.topTabDestination)
        }
        assertEquals("series", BrowseLibraryRoute("series").shellContext().topTabDestination)
    }

    @Test fun unknownOrUnselectedLibrariesDoNotCreateAPlaceholderTab() {
        for (libraryId in listOf(null, "removed-library")) {
            val items = browseNavigationItems(false, libraries, libraryId)
            assertEquals(listOf("home", "favorite"), items.topTabs.map { it.key })
        }
        assertEquals("", BrowseLibraryIndexRoute.shellContext().topTabDestination)
        val renamed = libraries.map { if (it.key == "series") it.copy(label = "连续剧") else it }
        assertEquals("连续剧", browseNavigationItems(false, renamed, "series").topTabs.last().label)
    }

    @Test fun navigationGroupsKeepAccountAndSearchOutOfTextTabs() {
        for (chasing in listOf(false, true)) {
            val items = browseNavigationItems(chasing, libraries, "movies")
            val expectedBottom = if (chasing) listOf("home", "chasing", "library", "mine")
                else listOf("home", "library", "mine")
            assertEquals(expectedBottom, items.bottom.map { it.key })
            assertEquals(listOf("home", "favorite", "library", "mine"), items.sidebar.map { it.key })
            assertEquals(listOf("home", "favorite", "movies"), items.topTabs.map { it.key })
            assertEquals(listOf("首页", "收藏", "电影"), items.topTabs.map { it.label })
        }
    }

    @Test fun phoneFullPagesHideTheDockWithoutHidingWideScreenNavigation() {
        val fullPages = details(BrowseDetailOrigin.Home, null) + listOf(
            BrowseLatestRoute(), BrowseSearchRoute, BrowseFavoriteRoute, BrowseLibraryRoute("movies"),
        )
        for (route in fullPages) {
            val presentation = route.shellPresentation()
            assertFalse(presentation.showBottomNavigation(true, false, 1), "$route")
            assertTrue(presentation.showBottomNavigation(false, false, 1), "$route")
        }
        for (route in listOf(BrowseHomeRoute, BrowseLibraryIndexRoute, BrowseChasingRoute)) {
            assertTrue(route.shellPresentation().showBottomNavigation(true, false, 1), "$route")
        }
        // These full phone pages remain primary destinations in a wide shell.
        assertFalse(BrowseFavoriteRoute.shellPresentation().secondaryContent)
        assertFalse(BrowseLibraryRoute("movies").shellPresentation().secondaryContent)
    }

    @Test fun accountChildPagesHideOnlyThePhoneDockWhileTheAccountSurfaceIsOpen() {
        val home = BrowseHomeRoute.shellPresentation()
        assertTrue(home.showBottomNavigation(true, true, 1))
        assertFalse(home.showBottomNavigation(true, true, 2))
        assertTrue(home.showBottomNavigation(false, true, 2))
        assertTrue(home.showBottomNavigation(true, false, 2), "Dismissed account stacks must not hide the browse dock")
    }

    @Test fun accountRoutesRemainOwnedByTheAccountArea() {
        val accountRoutes = listOf(
            BrowseMineRoute, BrowseMineHistoryRoute, BrowseAccountSwitchRoute, BrowseAddAccountRoute,
            BrowseConnectAccountRoute(ProviderKind.Jellyfin), BrowseServerDiscoveryRoute,
            BrowseSettingsRoute, BrowseManagementRoute(taskId = "task"), BrowseManagementDevicesRoute("device"),
            BrowseFeiniuConnectionPreferenceRoute, BrowseAppearanceRoute, BrowseGlassStyleRoute,
            BrowsePlayerSettingsRoute, BrowseAddGroupRoute, BrowseAboutPrivacyRoute,
            BrowseUserAgreementRoute, BrowseWebITongRoute, BrowsePrivacyWebRoute,
        )
        for (route in accountRoutes) {
            assertEquals(BrowseShellContext("mine"), route.shellContext(), "$route")
        }
    }

    @Test fun routeExtractionKeepsExistingSerializationNamesAndArguments() {
        val oldDetail = """{"type":"cn.mafei.cmp.BrowseDetailRoute","mediaId":"movie","mediaName":"电影","origin":"Library","libraryId":"movies"}"""
        val detail = Json.decodeFromString<BrowseRoute>(oldDetail)
        assertEquals(BrowseDetailRoute("movie", "电影", BrowseDetailOrigin.Library, "movies"), detail)
        assertEquals("cn.mafei.cmp.BrowseDetailRoute", Json.parseToJsonElement(Json.encodeToString(detail))
            .jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(BrowseDetailRoute("movie"), Json.decodeFromString<BrowseRoute>(
            """{"type":"cn.mafei.cmp.BrowseDetailRoute","mediaId":"movie"}"""))

        for (route in details(BrowseDetailOrigin.Library, "series") + listOf(
            BrowseSearchRoute, BrowseLibraryRoute("series"), BrowseLatestRoute("series"),
            BrowseConnectAccountRoute(ProviderKind.Jellyfin, "https://example.invalid", "user", "credential-ref", true),
        )) {
            assertEquals(route, Json.decodeFromString<BrowseRoute>(Json.encodeToString(route)))
        }
        val signedOut: SignedOutRoute = ServerDiscoveryRoute
        assertEquals("cn.mafei.cmp.ServerDiscoveryRoute", Json.parseToJsonElement(Json.encodeToString(signedOut))
            .jsonObject.getValue("type").jsonPrimitive.content)
        for (route in listOf(ConnectRoute, AccountHistoryRoute, ServerDiscoveryRoute)) {
            assertEquals(route, Json.decodeFromString<SignedOutRoute>(Json.encodeToString<SignedOutRoute>(route)))
        }
    }

    private fun details(origin: BrowseDetailOrigin, libraryId: String?): List<BrowseRoute> = listOf(
        BrowseDetailRoute("series", "剧集", origin, libraryId),
        BrowseSeasonRoute("series", "season", "剧集", origin, libraryId),
        BrowseEpisodeRoute("series", "season", "episode", "剧集", origin, libraryId),
        BrowsePersonRoute("person", "演员", origin, libraryId),
        BrowseMusicDetailRoute("album", "专辑", false, libraryId, origin),
        BrowsePhotoAlbumRoute("album", "相册", libraryId, origin),
        BrowsePhotoViewerRoute("album", "photo", libraryId, origin),
    )
}
