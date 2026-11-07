package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.player.api.PlayerPlaybackPreferences
import cn.mafei.cmp.player.api.PlayerEngineType
import kotlin.test.*

class PlayerSettingsScreenTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun generalSettingsMustNotExposeMpvRenderMode() {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(onBack = {}, onOpenAppearance = {})
            }
        }
        compose.onNodeWithText("播放器渲染模式").assertDoesNotExist()
    }

    @Test fun renderModeAppearsOnlyForExplicitMpvAndKeepsItsValueAcrossEngineChanges() {
        val preferences = mutableStateOf(PlayerPlaybackPreferences())
        val mode = mutableStateOf("buffer")
        compose.setContent {
            MaterialTheme {
                PlayerSettingsScreen(
                    preferences.value, false, false, false, null,
                    onRetry = {}, onSelect = { preferences.value = it }, onBack = {},
                    engineSelection = true, showPlayerRenderMode = true,
                    playerRenderMode = mode.value, onPlayerRenderModeChanged = { mode.value = it },
                )
            }
        }
        compose.onNodeWithText("播放器渲染模式").assertDoesNotExist()
        compose.onNodeWithText("MPV").performClick()
        compose.onNodeWithText("播放器渲染模式").assertExists()
        compose.onNodeWithText("直出").performClick()
        assertEquals("direct", mode.value)
        compose.onNodeWithText("系统播放器").performClick()
        compose.onNodeWithText("播放器渲染模式").assertDoesNotExist()
        compose.onNodeWithText("自动").performClick()
        compose.onNodeWithText("播放器渲染模式").assertDoesNotExist()
        compose.onNodeWithText("MPV").performClick()
        compose.onNodeWithText("直出").assertExists()
        assertEquals("direct", mode.value)
    }

    @Test fun unsupportedPlatformDoesNotExposeRenderModeEvenForMpv() {
        compose.setContent {
            MaterialTheme {
                PlayerSettingsScreen(
                    PlayerPlaybackPreferences(engine = PlayerEngineType.Mpv), false, false, false, null,
                    onRetry = {}, onSelect = {}, onBack = {}, engineSelection = true,
                )
            }
        }
        compose.onNodeWithText("播放器渲染模式").assertDoesNotExist()
    }

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
