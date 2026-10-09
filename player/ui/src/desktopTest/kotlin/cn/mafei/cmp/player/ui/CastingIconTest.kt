package cn.mafei.cmp.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.player.api.*
import kotlin.test.*

class CastingIconTest {
    @get:org.junit.Rule val rule = createComposeRule()
    @Test fun phoneUsesAnIconWithAnAccessibleLabelNotVisibleText() = checkIcon(360, 780)
    @Test fun wideUsesTheSameIconAndClickAction() = checkIcon(900, 600)
    private fun checkIcon(width: Int, height: Int) {
        var clicks = 0
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(width.dp, height.dp)) {
                NowPlayingScreen(NowPlayingMedia("v", "电影", ""), PlayerState(phase = PlayerPhase.Paused),
                    capabilities = PlayerCapabilities(), onBack = {}, onTogglePlay = {}, onRetry = {},
                    onSeek = {}, onSpeed = {}, onSelectTrack = { _, _ -> },
                    videoSurface = { Box(it.fillMaxSize()) }, onCasting = { clicks++ })
            }
        } }
        rule.onNodeWithText("投屏").assertDoesNotExist()
        rule.onNodeWithContentDescription("投屏").assertExists().performClick()
        assertEquals(1, clicks)
    }
}
