package cn.mafei.cmp.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.ui.MaFeiNavigationChrome
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchGeometryTest {
    @get:org.junit.Rule val rule = createComposeRule()
    @Test fun portraitTabletResultsAlignWithThePageTitle() = verify(736, 740, true)
    @Test fun wideResultsAlignWithThePageTitle() = verify(900, 700, false)
    @Test fun phoneResultsKeepTheExistingPageMargin() = verify(390, 740, false)

    private fun verify(width: Int, height: Int, topTabs: Boolean) {
        val layout = mafeiLayout(width.toFloat(), height.toFloat())
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(
                    LocalMaFeiSafeArea provides MaFeiSafeArea(),
                    LocalMaFeiNavigationChrome provides MaFeiNavigationChrome(
                        layout.navigation, layout.navigationEdge, 0.dp, 0.dp,
                        topTabsVisible = topTabs, effectiveLayout = layout,
                    ),
                ) {
                    Box(Modifier.size(width.dp, height.dp).testTag("viewport")) {
                        SearchScreen("影片", listOf(MediaItem(MediaId("movie"), ServerId("server"),
                            ProviderKind.Jellyfin, "影片", mediaType = MediaType.Movie)),
                            histories = emptyList(), loading = false, error = null, layout = layout,
                            gridState = rememberLazyGridState(), onBack = {}, onQueryChange = {}, onSubmit = {},
                            onRecordHistory = {}, onClearHistories = {}, onOpen = {},
                            artwork = { _, modifier -> Box(modifier.testTag("poster")) },
                        )
                    }
                }
            }
        }
        val poster = rule.onNodeWithTag("poster", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(layout.pagePaddingDp, poster.left - viewport.left, .5f)
        if (width >= 600) {
            val title = rule.onNode(hasText("搜索") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
                useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(title.left, poster.left, .5f, "Search must use the same gutter as its page title and library cards")
        }
    }
}
