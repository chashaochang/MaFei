package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals

class MaFeiActionBarGeometryTest {
    @get:org.junit.Rule val rule = createComposeRule()
    @Test fun phoneContentStartsBelowTheActualActionBar() = verify(390, 844, false)
    @Test fun expandedTabletContentStartsBelowTheActualActionBar() = verify(1120, 736, false)
    @Test fun collapsedTabletContentStartsBelowBothHeaderRows() = verify(736, 1120, true)

    private fun verify(width: Int, height: Int, topTabs: Boolean) {
        rule.setContent {
            MaFeiTheme {
                val layout = mafeiLayout(width.toFloat(), height.toFloat())
                val safe = MaFeiSafeArea(top = 32.dp)
                CompositionLocalProvider(
                    LocalMaFeiSafeArea provides safe,
                    LocalMaFeiNavigationChrome provides MaFeiNavigationChrome(
                        layout.navigation, layout.navigationEdge, 0.dp, 0.dp,
                        topTabsVisible = topTabs, effectiveLayout = layout,
                    ),
                ) {
                    Box(Modifier.size(width.dp, height.dp)) {
                        Column(Modifier.fillMaxSize()) {
                            Spacer(Modifier.height(safe.top + mafeiPageTitleBarContentHeight()))
                            Box(Modifier.fillMaxWidth().height(32.dp).testTag("first-content"))
                        }
                        MaFeiActionBar("页面标题", onBack = {}, backdrop = rememberGlassBackdrop(),
                            modifier = Modifier.testTag("action-bar"))
                    }
                }
            }
        }
        val bar = rule.onNodeWithTag("action-bar").fetchSemanticsNode().boundsInRoot
        val content = rule.onNodeWithTag("first-content").fetchSemanticsNode().boundsInRoot
        assertEquals(bar.bottom, content.top, .5f, "Page reservation must equal the rendered header, including safe area")
    }
}
