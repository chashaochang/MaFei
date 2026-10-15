package cn.mafei.cmp.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalSharedTransitionApi::class)
class HomeArtworkTransitionPerformanceTest {
    @get:org.junit.Rule val compose = createComposeRule()
    private val library = MediaCollection(CollectionId("movies"), ServerId("server"), ProviderKind.Jellyfin, "电影库")
    private val movie = MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin,
        "测试影片", mediaType = MediaType.Movie)
    private val registrations = mutableListOf<String>()

    @Test fun idleWideShelvesDoNotRegisterSharedArtwork() = verifyIdle(1200f, 800f)

    @Test fun idlePhoneGridDoesNotRegisterSharedArtwork() = verifyIdle(390f, 844f)

    @Test fun selectedArtworkStillMatchesOnPushAndPopWithoutRegisteringDuplicateResumeCard() {
        val detail = mutableStateOf(false)
        var active = false
        var matched = false
        compose.setContent {
            MaFeiTheme {
                val holder = rememberSaveableStateHolder()
                Box(Modifier.size(1200.dp, 800.dp)) {
                    SharedTransitionLayout {
                        val shared = this
                        AnimatedContent(detail.value, transitionSpec = {
                            fadeIn(tween(420)) togetherWith fadeOut(tween(420))
                        }) { showDetail ->
                            val animated = this
                            val transform: @Composable (MediaItem, Modifier) -> Modifier = { item, modifier ->
                                with(shared) {
                                    val content = rememberSharedContentState("artwork:${item.id.value}")
                                    if (!showDetail) track(item)
                                    SideEffect {
                                        active = isTransitionActive
                                        matched = content.isMatchFound
                                    }
                                    modifier.sharedBounds(content, animated,
                                        boundsTransform = { _, _ -> tween(420) })
                                }
                            }
                            holder.SaveableStateProvider(showDetail) {
                                if (showDetail) {
                                    Box(transform(movie, Modifier.size(300.dp, 220.dp)))
                                } else {
                                    home(1200f, 800f, transform, onItem = { detail.value = true })
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText(movie.title).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            assertEquals(listOf(movie.title), registrations,
                "Only the clicked shelf artwork should register, not the same movie's resume artwork")
            assertTrue(matched, "Dynamically selected artwork must match the detail")
            assertTrue(active, "Selecting on demand must retain the card-to-detail animation")
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { detail.value = false }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            assertEquals(listOf(movie.title), registrations)
            assertTrue(matched, "Saveable source selection must survive returning home")
            assertTrue(active, "Returning must retain the detail-to-card animation")
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(movie.title).assertExists()
    }

    private fun verifyIdle(width: Float, height: Float) {
        compose.setContent {
            MaFeiTheme {
                Box(Modifier.size(width.dp, height.dp)) {
                    home(width, height, transform = { item, modifier -> track(item); modifier })
                }
            }
        }
        compose.waitForIdle()
        assertEquals(emptyList(), registrations, "Idle posters must not allocate shared-transition nodes")
    }

    @Composable private fun track(item: MediaItem) {
        DisposableEffect(Unit) {
            registrations += item.title
            onDispose { registrations.remove(item.title) }
        }
    }

    @Composable private fun home(
        width: Float,
        height: Float,
        transform: @Composable (MediaItem, Modifier) -> Modifier,
        onItem: (MediaItem) -> Unit = {},
    ) {
        HomeScreen(
            collections = listOf(library), items = emptyList(),
            homeLatest = listOf(movie), homeLatestByCollection = mapOf(library.id to listOf(movie)),
            homeResume = listOf(movie.copy(title = "继续观看的同一影片")), homeNextUp = emptyList(),
            selectedCollection = null, loading = false, homeLoading = false,
            error = null, stale = false, canLoadMore = false,
            overview = true, layout = mafeiLayout(width, height),
            onCollection = {}, onItem = onItem, onRetry = {}, onLoadMore = {}, onOpenLibrary = {},
            showHomeHero = false, gridState = rememberLazyGridState(), sharedArtworkModifier = transform,
        )
    }
}
