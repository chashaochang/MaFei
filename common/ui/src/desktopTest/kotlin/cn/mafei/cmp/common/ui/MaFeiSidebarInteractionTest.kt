package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import cn.mafei.cmp.common.design_system.mafeiLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MaFeiSidebarInteractionTest {
    private val destinations = listOf(
        MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
        MaFeiNavItem("favorite", "收藏", MaFeiGlyph.Favorite),
        MaFeiNavItem("library", "媒体库", MaFeiGlyph.Library),
        MaFeiNavItem("mine", "我的", MaFeiGlyph.Account),
    )

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    @Test
    fun collapseHidesSidebarAndShowsLegacyTopTabs() {
        composeRule.setContent {
            MaterialTheme {
                MaFeiAdaptiveShell(
                    layout = mafeiLayout(widthDp = 1194f, heightDp = 834f),
                    destinations = destinations,
                    selectedDestination = "home",
                    topTabDestinations = destinations.filter { it.key != "mine" && it.key != "library" },
                    onDestination = {},
                    onSearch = {},
                    libraries = listOf(MaFeiNavItem("movies", "电影", MaFeiGlyph.Library)),
                    selectedLibrary = null,
                    onLibrary = {},
                    accountLabel = "Account",
                    background = {},
                    accountActions = {},
                    scene = { Surface(Modifier.fillMaxSize()) { Text("scene") } },
                    overlay = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("收起侧边栏").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("收起侧边栏").assertCountEquals(0)
        composeRule.onNodeWithTag("mediumTopTabs").assertIsDisplayed()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val topTabBounds = composeRule.onNodeWithTag("mediumTopTabs")
            .fetchSemanticsNode().boundsInRoot
        val rootCenterX = rootBounds.size.width / 2f
        val topTabCenterX = (topTabBounds.left + topTabBounds.right) / 2f
        assertTrue(abs(topTabCenterX - rootCenterX) <= 0.5f)
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
        composeRule.onAllNodesWithText("媒体库").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("搜索").assertIsDisplayed()
        composeRule.onAllNodesWithText("我的").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("展开侧边栏").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("收起侧边栏").assertIsDisplayed()
        composeRule.onAllNodesWithTag("mediumTopTabs").assertCountEquals(0)
    }

    @Test
    fun collapsedSecondaryKeepsBackControlSeparateFromTopTabExpander() {
        composeRule.setContent {
            MaterialTheme {
                MaFeiAdaptiveShell(
                    layout = mafeiLayout(widthDp = 1194f, heightDp = 834f),
                    destinations = destinations,
                    selectedDestination = "home",
                    topTabDestinations = destinations.filter { it.key != "mine" && it.key != "library" },
                    onDestination = {},
                    onTopBarBack = {},
                    contentNavigationActive = true,
                    onSearch = {},
                    libraries = listOf(MaFeiNavItem("movies", "电影", MaFeiGlyph.Library)),
                    selectedLibrary = null,
                    onLibrary = {},
                    accountLabel = "Account",
                    background = {},
                    accountActions = {},
                    scene = { Surface(Modifier.fillMaxSize()) { Text("secondary") } },
                    overlay = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("收起侧边栏").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
        // The back control is a full-row navigation control; the tab capsule
        // still owns its independent sidebar-expander control.
        composeRule.onNodeWithContentDescription("展开侧边栏").assertIsDisplayed()
    }

    @Test
    fun mediumDrawerUsesLegacyOverlayPanel() {
        composeRule.setContent {
            MaterialTheme {
                MaFeiAdaptiveShell(
                    layout = mafeiLayout(widthDp = 740f, heightDp = 1000f),
                    destinations = destinations,
                    selectedDestination = "home",
                    onDestination = {},
                    libraries = listOf(MaFeiNavItem("movies", "电影", MaFeiGlyph.Library)),
                    selectedLibrary = null,
                    onLibrary = {},
                    accountLabel = "Account",
                    background = {},
                    accountActions = {},
                    scene = { Surface(Modifier.fillMaxSize()) { Text("scene") } },
                    overlay = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("展开侧边栏").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("关闭侧边栏").assertIsDisplayed()
        composeRule.onNodeWithTag("drawerSidebarPanel", useUnmergedTree = true).assertIsDisplayed()

        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.size.width
        val drawerBounds = composeRule.onNodeWithTag(
            "drawerSidebarPanel",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        assertTrue(drawerBounds.width < rootWidth * .45f)
    }

    @Test
    fun largeNavigationIsNotHiddenByAFullPageRoute() {
        composeRule.setContent {
            MaterialTheme {
                MaFeiAdaptiveShell(
                    layout = mafeiLayout(widthDp = 1194f, heightDp = 834f),
                    destinations = destinations,
                    selectedDestination = "library",
                    onDestination = {},
                    libraries = listOf(MaFeiNavItem("movies", "电影", MaFeiGlyph.Library)),
                    selectedLibrary = "movies",
                    onLibrary = {},
                    accountLabel = "Account",
                    // A detail/library route hides the phone dock only. The
                    // persistent large-screen navigation remains addressable.
                    showBottomNavigation = false,
                    background = {},
                    accountActions = {},
                    scene = { Surface(Modifier.fillMaxSize()) { Text("secondary") } },
                    overlay = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("收起侧边栏").assertIsDisplayed()
        composeRule.onNodeWithText("我的媒体库").assertIsDisplayed()
        composeRule.onNodeWithText("电影").assertIsDisplayed()
    }
}
