package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsFeedbackTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun failedPreferenceSavesKeepChoicesInPlaceAndAcknowledgementDoesNotSaveAgain() {
        val appearance = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        var saves = 0
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 700.dp)) {
                    if (appearance.value) AppearanceScreen(
                        onBack = {}, error = error.value, onSelect = { saves++ },
                    ) else GlassStyleScreen(
                        onBack = {}, style = GlassStyle.GaussianBlur, error = error.value,
                        onSelectStyle = { saves++ }, onSelectBottomNavigation = { saves++ },
                    )
                }
            }
        }
        for (isAppearance in listOf(false, true)) {
            rule.runOnIdle { appearance.value = isAppearance; error.value = null }
            val choice = rule.onNodeWithContentDescription(if (isAppearance) "深色" else "高斯模糊")
            val before = choice.assertIsSelected().fetchSemanticsNode().boundsInRoot
            rule.runOnIdle { error.value = "无法保存偏好，请重试" }
            rule.onNodeWithTag("mafeiGlassConfirmCard").assertExists()
            assertEquals(before, choice.assertIsSelected().fetchSemanticsNode().boundsInRoot)
            rule.onNodeWithText("知道了").performClick()
            rule.onNodeWithText("无法保存偏好，请重试").assertDoesNotExist()
            assertEquals(0, saves)
            assertEquals("无法保存偏好，请重试", error.value)
            assertEquals(before, choice.assertIsSelected().fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun historyFailuresKeepLoadedPostersAndDismissalDoesNotRetryOrResumePagination() {
        val pageFailed = mutableStateOf(false)
        val refreshError = mutableStateOf<String?>(null)
        val history = listOf(PlaybackHistoryItem(MediaItem(MediaId("movie"), ServerId("server"),
            ProviderKind.Jellyfin, "已加载的历史影片", mediaType = MediaType.Movie)))
        var refreshes = 0
        var pageRetries = 0
        var automaticPages = 0
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 700.dp)) {
                    MineHistoryScreen(
                        items = history, loading = false, error = refreshError.value,
                        layout = mafeiLayout(390f, 700f), onBack = {}, onRetry = { refreshes++ }, onItem = {},
                        canLoadMore = true, loadMoreFailed = pageFailed.value,
                        onLoadMore = { automaticPages++ }, onRetryLoadMore = { pageRetries++ },
                    )
                }
            }
        }
        val poster = rule.onNodeWithText("已加载的历史影片")
        val before = poster.fetchSemanticsNode().boundsInRoot
        var initialAutomaticPages = 0
        rule.runOnIdle { initialAutomaticPages = automaticPages; pageFailed.value = true }
        rule.onNodeWithText("加载更多失败，请重试").assertExists()
        assertEquals(before, poster.fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("关闭").performClick()
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertDoesNotExist()
        assertTrue(pageFailed.value)
        assertEquals(initialAutomaticPages, automaticPages)
        assertEquals(0, pageRetries)
        assertEquals(0, refreshes)

        rule.runOnIdle { pageFailed.value = false; refreshError.value = "刷新播放历史失败" }
        rule.onNodeWithText("刷新播放历史失败").assertExists()
        assertEquals(before, poster.fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("关闭").performClick()
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertDoesNotExist()
        assertEquals("刷新播放历史失败", refreshError.value)
        assertEquals(0, refreshes)
        assertEquals(0, pageRetries)
        assertEquals(before, poster.fetchSemanticsNode().boundsInRoot)
    }
}
