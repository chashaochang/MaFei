package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test

class HomeCardBadgesTest {
    @get:org.junit.Rule
    val compose = createComposeRule()

    @Test
    fun phoneLatestCardsDoNotShowWatchedOrFavoriteLabels() =
        assertNoStatusBadges(width = 390, height = 844)

    @Test
    fun wideLibraryShelvesDoNotShowWatchedOrFavoriteLabels() =
        assertNoStatusBadges(width = 1280, height = 800)

    @Test
    fun browseCardsDoNotShowWatchedOrFavoriteLabels() =
        assertNoStatusBadges(width = 390, height = 844, overview = false)

    private fun assertNoStatusBadges(width: Int, height: Int, overview: Boolean = true) {
        val library = MediaCollection(
            CollectionId("movies"), ServerId("server"), ProviderKind.Jellyfin, "电影库",
        )
        val movies = listOf(
            movie("watched", "电影甲", MediaUserData(isPlayed = true)),
            movie("favorite", "电影乙", MediaUserData(isFavorite = true)),
            movie("both", "电影丙", MediaUserData(isPlayed = true, isFavorite = true)),
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width.dp, height.dp)) {
                    HomeScreen(
                        collections = listOf(library), items = movies,
                        homeLatest = movies, homeLatestByCollection = mapOf(library.id to movies),
                        homeResume = emptyList(), homeNextUp = emptyList(),
                        selectedCollection = if (overview) null else library.id,
                        loading = false, homeLoading = false, error = null, stale = false,
                        canLoadMore = false, overview = overview,
                        layout = mafeiLayout(width.toFloat(), height.toFloat()),
                        onCollection = {}, onItem = {}, onRetry = {}, onLoadMore = {},
                        onOpenLibrary = {}, showHomeHero = false,
                        gridState = rememberLazyGridState(),
                    )
                }
            }
        }
        compose.waitForIdle()
        movies.forEach { compose.onNodeWithText(it.title).assertExists() }
        compose.onAllNodesWithText("已看").assertCountEquals(0)
        compose.onAllNodesWithText("已收藏").assertCountEquals(0)
    }

    private fun movie(id: String, title: String, userData: MediaUserData) = MediaItem(
        MediaId(id), ServerId("server"), ProviderKind.Jellyfin, title,
        mediaType = MediaType.Movie, userData = userData,
    )
}
