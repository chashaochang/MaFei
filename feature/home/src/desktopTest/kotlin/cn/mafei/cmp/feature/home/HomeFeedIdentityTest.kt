package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFeedIdentityTest {
    @get:org.junit.Rule val compose = createComposeRule()

    private val library = MediaCollection(
        CollectionId("movies"), ServerId("server"), ProviderKind.Jellyfin, "电影库",
    )
    private val recent = (1..40).map { movie("movie-$it") }
    private lateinit var grid: LazyGridState

    @Test fun switchingOverlappingFeedsKeepsEveryMountedKeyUnique() {
        mount()
        repeat(8) {
            compose.onNodeWithText("电影库").performClick()
            compose.waitForIdle()
            assertVisibleFeed("movies:")
            compose.onNodeWithText("最近添加").performClick()
            compose.waitForIdle()
            assertVisibleFeed("__home_recent__:")
        }
    }

    @Test fun refreshedHeadersAndItemsCanChangeWhileGridIsScrolled() {
        val resume = mutableStateOf(listOf(movie("resume")))
        val latest = mutableStateOf(recent)
        mount(resume = { resume.value }, latest = { latest.value })
        repeat(8) { iteration ->
            compose.runOnIdle { runBlocking { grid.scrollToItem(12) } }
            compose.runOnIdle {
                resume.value = if (iteration % 2 == 0) emptyList() else listOf(movie("resume"))
                latest.value = if (iteration % 2 == 0) recent.drop(3) else recent
            }
            compose.waitForIdle()
            assertVisibleFeed("__home_recent__:")
        }
    }

    private fun mount(
        resume: () -> List<MediaItem> = { emptyList() },
        latest: () -> List<MediaItem> = { recent },
    ) {
        compose.setContent {
            MaterialTheme {
                grid = rememberLazyGridState()
                Box(Modifier.size(390.dp, 844.dp)) {
                    HomeScreen(
                        collections = listOf(library), items = emptyList(),
                        homeLatest = latest(), homeLatestByCollection = mapOf(library.id to recent.reversed()),
                        homeResume = resume(), homeNextUp = emptyList(),
                        selectedCollection = null, loading = false, homeLoading = false,
                        error = null, stale = false, canLoadMore = false,
                        overview = true, layout = mafeiLayout(390f, 844f),
                        onCollection = {}, onItem = {}, onRetry = {}, onLoadMore = {},
                        onOpenLibrary = {}, showHomeHero = false, gridState = grid,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertVisibleFeed(prefix: String) {
        val keys = grid.layoutInfo.visibleItemsInfo.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.any { it.toString().startsWith(prefix) }, "Expected a visible card from $prefix")
    }

    private fun movie(id: String) = MediaItem(
        MediaId(id), ServerId("server"), ProviderKind.Jellyfin, id, mediaType = MediaType.Movie,
    )
}
