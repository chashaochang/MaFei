package cn.mafei.cmp.feature.home

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class HomeLoadingStatusTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun show(homeLoading: Boolean, resume: List<MediaItem> = emptyList()) {
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    collections = emptyList(), items = emptyList(),
                    homeResume = resume, selectedCollection = null,
                    loading = false, homeLoading = homeLoading,
                    error = null, stale = false, canLoadMore = false,
                    overview = true, layout = mafeiLayout(390f, 844f),
                    onCollection = {}, onItem = {}, onRetry = {},
                    onLoadMore = {}, onOpenLibrary = {}, showHomeHero = false,
                    gridState = rememberLazyGridState(),
                )
            }
        }
    }

    @Test fun pendingHomeFeedMustNotShowAnEmptyLibraryMessage() {
        show(homeLoading = true)
        composeRule.onNodeWithText("这里还没有媒体库").assertDoesNotExist()
    }

    @Test fun cachedHomeContentMustNotShowAnEmptyLibraryMessage() {
        show(homeLoading = false, resume = listOf(
            MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin, "继续看这部影片", mediaType = MediaType.Movie),
        ))
        composeRule.onNodeWithText("继续观看").assertExists()
        composeRule.onNodeWithText("这里还没有媒体库").assertDoesNotExist()
    }

    @Test fun expiredAuthenticationNeverInsertsANoticeOrMisleadingEmptyStateIntoTheFeed() {
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    collections = emptyList(), items = emptyList(), selectedCollection = null,
                    loading = false, homeLoading = false, error = null,
                    authenticationRequired = true,
                    stale = false, canLoadMore = false, overview = true,
                    layout = mafeiLayout(390f, 844f), onCollection = {}, onItem = {},
                    onRetry = {}, onLoadMore = {}, onOpenLibrary = {},
                    showHomeHero = false, gridState = rememberLazyGridState(),
                )
            }
        }
        composeRule.onNodeWithText("重新登录").assertDoesNotExist()
        composeRule.onNodeWithText("当前账号登录已过期，请重新登录").assertDoesNotExist()
        composeRule.onNodeWithText("这里还没有媒体库").assertDoesNotExist()
    }

    @Test fun wideRecentMoreOpensThatLibrarysRecentListNotTheLibraryEntry() {
        val collection = MediaCollection(CollectionId("shows"), ServerId("server"),
            ProviderKind.Jellyfin, "电视剧", mediaTypes = setOf(MediaType.Series))
        var opened: MediaCollection? = null
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    collections = listOf(collection), items = emptyList(),
                    homeLatestByCollection = mapOf(collection.id to emptyList()),
                    selectedCollection = null, loading = false, homeLoading = false,
                    error = null, stale = false, canLoadMore = false,
                    overview = true, layout = mafeiLayout(1280f, 800f),
                    onCollection = { fail("More must not select the phone Floating Tab") },
                    onOpenCollectionLatest = { opened = it },
                    onItem = {}, onRetry = {}, onLoadMore = {}, onOpenLibrary = {},
                    showHomeHero = false, gridState = rememberLazyGridState(),
                )
            }
        }
        composeRule.onNodeWithText("更多").performClick()
        assertEquals(collection, opened)
    }
}
