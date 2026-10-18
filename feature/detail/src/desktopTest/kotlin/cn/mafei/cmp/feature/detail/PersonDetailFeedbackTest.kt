package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonDetailFeedbackTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun refreshFailureOverlaysTheExistingPersonWithoutReplacingTheirWork() {
        val error = mutableStateOf<String?>(null)
        val person = MediaPerson(MediaId("person"), ServerId("server"), ProviderKind.Jellyfin,
            "演员", overview = "演员简介")
        val movie = MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin, "演员作品")
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 800.dp)) {
                    PersonDetailScreen(
                        person = person, movies = listOf(movie), series = emptyList(), episodes = emptyList(),
                        loading = false, error = error.value, onBack = {}, onRetry = {}, onItem = {},
                    )
                }
            }
        }
        val before = rule.onNodeWithText("演员作品").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { error.value = "刷新失败，请重试" }
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertExists()
        rule.onNodeWithText("刷新失败，请重试").assertExists()
        assertEquals(before, rule.onNodeWithText("演员作品").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("关闭").performClick()
        rule.onNodeWithText("刷新失败，请重试").assertDoesNotExist()
        assertEquals("刷新失败，请重试", error.value)
        rule.onNodeWithText("演员作品").assertExists()
    }
}
