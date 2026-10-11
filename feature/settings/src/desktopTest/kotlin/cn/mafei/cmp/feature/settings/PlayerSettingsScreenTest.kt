package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.player.api.PlayerPlaybackPreferences
import kotlin.test.*

class PlayerSettingsScreenTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun phoneChoicesSendWholePreferenceAndDoNotPretendSaved() {
        var requested: PlayerPlaybackPreferences? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    PlayerSettingsScreen(PlayerPlaybackPreferences(), false, false, false, null,
                        onRetry = {}, onSelect = { requested = it }, onBack = {})
                }
            }
        }
        compose.onNodeWithText("软解").performClick()
        assertEquals(PlayerPlaybackPreferences(false, 1024), requested)
        compose.onNodeWithText("硬解").assertIsSelected()
        compose.onNodeWithText("512 MB").performScrollTo().performClick()
        assertEquals(PlayerPlaybackPreferences(true, 512), requested)
        compose.onNodeWithText("1024 MB").assertIsSelected()
    }

    @Test fun wideLayoutKeepsChoicesAndDisablesThemWhileSaving() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(900.dp)) {
                    PlayerSettingsScreen(PlayerPlaybackPreferences(false, 512), false, false, true, null,
                        onRetry = {}, onSelect = { error("saving must disable choices") }, onBack = {})
                }
            }
        }
        compose.onNodeWithText("软解").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithText("512 MB").assertIsSelected().assertIsNotEnabled()
    }

    @Test fun loadFailureOffersRetryInsteadOfEditableDefaults() {
        var retries = 0
        compose.setContent {
            MaterialTheme {
                PlayerSettingsScreen(PlayerPlaybackPreferences(), false, true, false, null,
                    onRetry = { retries++ }, onSelect = { error("not loaded") }, onBack = {})
            }
        }
        compose.onNodeWithText("软解").assertDoesNotExist()
        compose.onNodeWithText("加载失败,点击重试").performClick()
        assertEquals(1, retries)
    }
}
