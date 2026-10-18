package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSectionFeedbackTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun failedLibrariesShareOneOverlayAndKeepLoadedMediaInPlace() {
        val libraries = listOf("电影", "电视剧", "纪录片").mapIndexed { index, name ->
            MediaCollection(CollectionId("library-$index"), ServerId("server"), ProviderKind.Jellyfin, name)
        }
        val movie = MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin,
            "已加载影片", mediaType = MediaType.Movie)
        val errors = mutableStateOf<Map<CollectionId, String>>(emptyMap())
        var retries = 0
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(900.dp, 640.dp)) {
                    HomeLatestByLibraryScreen(
                        collections = libraries, latestByCollection = mapOf(libraries.first().id to listOf(movie)),
                        loadingCollections = emptySet(), errors = errors.value, layout = mafeiLayout(900f, 640f),
                        gridState = rememberLazyGridState(), onBack = {}, onRetry = { retries++ }, onItem = {},
                    )
                }
            }
        }
        val before = rule.onNodeWithText("已加载影片").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            errors.value = mapOf(libraries[1].id to "连接中断", libraries[2].id to "请求超时")
        }
        rule.onAllNodesWithTag("mafeiGlassConfirmCard").assertCountEquals(1)
        rule.onNodeWithText("电视剧：连接中断\n纪录片：请求超时").assertExists()
        assertEquals(before, rule.onNodeWithText("已加载影片").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        assertEquals(2, errors.value.size, "Acknowledging feedback must not clear request errors")
        // Until the owner starts the request or clears its error, an immediate
        // identical failure remains visible rather than being silently consumed.
        rule.onAllNodesWithTag("mafeiGlassConfirmCard").assertCountEquals(1)
        rule.onNodeWithText("已加载影片").assertExists()
    }
}
