package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeHeroIndicatorGeometryTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun portraitTabletIndicatorAlignsWithTheListGutters() = assertListAlignment(MaFeiSafeArea())

    @Test fun portraitTabletIndicatorSharesTheListsTrailingSafeInset() =
        assertListAlignment(MaFeiSafeArea(end = 24.dp))

    private fun assertListAlignment(safeArea: MaFeiSafeArea) {
        lateinit var grid: LazyGridState
        val heroItems = listOf(movie("hero-1"), movie("hero-2"))
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMaFeiSafeArea provides safeArea) {
                    grid = rememberLazyGridState()
                    Box(Modifier.requiredSize(736.dp, 1000.dp).testTag("homeViewport")) {
                        HomeScreen(
                            collections = emptyList(), items = emptyList(), homeLatest = heroItems,
                            homeResume = listOf(movie("resume")), selectedCollection = null,
                            loading = false, homeLoading = false, error = null, stale = false,
                            canLoadMore = false, overview = true, layout = mafeiLayout(736f, 1000f),
                            onCollection = {}, onItem = {}, onRetry = {}, onLoadMore = {}, onOpenLibrary = {},
                            heroAutoplay = false, gridState = grid,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val indicator = rule.onNodeWithTag("homeHeroIndicator").fetchSemanticsNode().boundsInRoot
        val shelf = grid.layoutInfo.visibleItemsInfo.first { it.index == 1 }
        val shelfLeading = rule.onNodeWithText("继续观看").fetchSemanticsNode().boundsInRoot.left
        assertEquals(shelfLeading, indicator.left, 1f,
            "The indicator must start at the same edge as the list below it")
        assertEquals(shelfLeading + shelf.size.width, indicator.right, 1f,
            "The indicator must end at the same edge as the list below it")
    }

    private fun movie(id: String) = MediaItem(
        MediaId(id), ServerId("server"), ProviderKind.Jellyfin, id, mediaType = MediaType.Movie,
    )
}
