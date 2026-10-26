package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PlaybackHistoryItem
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class MineHistoryPreviewTest {
    @get:org.junit.Rule val rule = createComposeRule()

    private fun show(
        history: MutableState<List<PlaybackHistoryItem>>,
        loading: MutableState<Boolean>,
    ) {
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.requiredSize(390.dp, 844.dp)) {
                    MineScreen(
                        profile = MineProfile("Viewer", "Media"),
                        historyItems = history.value,
                        historyLoading = loading.value,
                        historyError = null,
                        layout = mafeiLayout(390f, 844f),
                        busy = false,
                        onHistoryItem = {},
                        onHistoryRetry = {},
                        onHistoryMore = {},
                        onSwitchAccount = {},
                        onSettings = {},
                        onPrivacy = {},
                        onAddGroup = {},
                        onLogout = {},
                        artwork = { _, modifier -> Box(modifier) },
                    )
                }
            }
        }
    }

    @Test
    fun emptyHistoryLoadingAndReloadingDoNotMoveTheAccountActions() {
        val history = mutableStateOf(emptyList<PlaybackHistoryItem>())
        val loading = mutableStateOf(false)
        show(history, loading)
        val settings = rule.onNodeWithText("设置")
        val originalBounds = settings.fetchSemanticsNode().boundsInRoot

        for (isLoading in listOf(true, false, true, false)) {
            rule.runOnIdle { loading.value = isLoading }
            rule.onNodeWithText("最近在看").assertDoesNotExist()
            rule.onNodeWithText("更多").assertDoesNotExist()
            assertEquals(originalBounds, settings.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun realHistoryAppearsAfterLoadingAndStaysVisibleDuringRefresh() {
        val history = mutableStateOf(emptyList<PlaybackHistoryItem>())
        val loading = mutableStateOf(true)
        show(history, loading)
        rule.onNodeWithText("最近在看").assertDoesNotExist()

        rule.runOnIdle {
            history.value = listOf(
                PlaybackHistoryItem(
                    MediaItem(
                        MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin,
                        "已加载的历史影片", mediaType = MediaType.Movie,
                    ),
                ),
            )
            loading.value = false
        }
        val poster = rule.onNodeWithText("已加载的历史影片")
        val originalBounds = poster.fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("最近在看").assertExists()
        rule.onNodeWithText("更多").assertExists()

        rule.runOnIdle { loading.value = true }
        assertEquals(originalBounds, poster.fetchSemanticsNode().boundsInRoot)
    }
}
