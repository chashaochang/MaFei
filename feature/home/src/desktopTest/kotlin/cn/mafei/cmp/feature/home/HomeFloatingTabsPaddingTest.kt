package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFloatingTabsPaddingTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test fun phoneWithoutCarouselAlignsTabsWithTheirReservedSlot() =
        assertTabsFollowSlot(width = 390, height = 700, resume = false)

    @Test fun phoneWithoutCarouselKeepsTabsAlignedBelowContinueWatching() =
        assertTabsFollowSlot(width = 390, height = 700, resume = true)

    @Test fun phoneWithoutCarouselReservesSearchSpaceBesideTabs() =
        assertTabsFollowSlot(width = 390, height = 700, resume = false, search = true)

    @Test fun phoneWithoutCarouselPinsSelectedLibraryAtTheSafeArea() =
        assertTabsFollowSlot(width = 390, height = 700, resume = false, switchFeed = true)

    @Test fun phoneWithoutCarouselPinsSelectedLibraryBelowContinueWatching() =
        assertTabsFollowSlot(width = 390, height = 700, resume = true, switchFeed = true)

    private fun assertTabsFollowSlot(
        width: Int, height: Int, resume: Boolean,
        search: Boolean = false, switchFeed: Boolean = false,
    ) {
        lateinit var grid: LazyGridState
        var density = 1f
        val latest = (1..12).map { movie("latest-$it") }
        val library = MediaCollection(CollectionId("movies"), ServerId("server"), ProviderKind.Jellyfin, "电影库")
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMaFeiSafeArea provides MaFeiSafeArea(top = 40.dp)) {
                    density = LocalDensity.current.density
                    grid = rememberLazyGridState()
                    Box(Modifier.size(width.dp, height.dp)) {
                        HomeScreen(
                            collections = if (switchFeed) listOf(library) else emptyList(), items = emptyList(),
                            homeLatest = latest,
                            homeLatestByCollection = mapOf(library.id to (1..12).map { movie("library-$it") }),
                            homeResume = if (resume) listOf(movie("resume")) else emptyList(),
                            selectedCollection = null, loading = false, homeLoading = false,
                            error = null, stale = false, canLoadMore = false,
                            overview = true, layout = mafeiLayout(width.toFloat(), height.toFloat()),
                            onCollection = {}, onItem = {}, onRetry = {},
                            onLoadMore = {}, onOpenLibrary = {}, showHomeHero = false,
                            onSearch = if (search) ({}) else null,
                            gridState = grid,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val anchor = grid.layoutInfo.visibleItemsInfo.first { it.key == "home-latest-feed-anchor" }
        val stickyTop = (if (width < 600) 52 else 88) * density
        val expected = maxOf(stickyTop, anchor.offset.y + grid.layoutInfo.beforeContentPadding + 8 * density)
        val actual = compose.onNodeWithText("最近添加").fetchSemanticsNode().boundsInRoot.top
        assertEquals(expected, actual, absoluteTolerance = 1f)
        if (search) {
            val tabsViewport = compose.onNodeWithText("最近添加").onParent().fetchSemanticsNode().boundsInRoot
            val searchButton = compose.onNodeWithContentDescription("搜索").fetchSemanticsNode().boundsInRoot
            assertTrue(tabsViewport.right <= searchButton.left, "Tabs must not extend behind the search button")
        }
        if (switchFeed) {
            compose.onNodeWithText("电影库").performClick()
            compose.waitForIdle()
            val selectedTop = compose.onNodeWithText("电影库").fetchSemanticsNode().boundsInRoot.top
            assertEquals(stickyTop, selectedTop, absoluteTolerance = 1f)
        }
    }

    private fun movie(id: String) = MediaItem(
        MediaId(id), ServerId("server"), ProviderKind.Jellyfin, id, mediaType = MediaType.Movie,
    )
}
