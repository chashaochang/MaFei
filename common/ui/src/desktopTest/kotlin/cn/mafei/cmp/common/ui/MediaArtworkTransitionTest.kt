package cn.mafei.cmp.common.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalSharedTransitionApi::class)
class MediaArtworkTransitionTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private val item = MediaItem(MediaId("movie"), ServerId("server"), ProviderKind.Jellyfin,
        "影片", mediaType = MediaType.Movie)
    private var active = false
    private var matched = false

    @Test fun libraryFavoriteAndSearchCardShareArtworkWithoutStretchingText() = verifyTransition(baseCard = false)
    @Test fun personWorkCardSharesArtworkWithoutStretchingText() = verifyTransition(baseCard = true)

    @Test fun explicitSeasonArtworkDoesNotRegisterTheHostTransitionTwice() {
        var hostRegistrations = 0
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMediaArtworkTransition provides { _, modifier ->
                    SideEffect { hostRegistrations++ }
                    modifier
                }) {
                    LegacyVideoCard(item, onClick = {}, modifier = Modifier.width(120.dp),
                        posterModifier = Modifier.testTag("season-artwork"))
                }
            }
        }
        assertEquals(0, hostRegistrations)
        assertEquals(180f, rule.onNodeWithTag("season-artwork", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height, .5f)
    }

    private fun verifyTransition(baseCard: Boolean) {
        val detail = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(600.dp, 500.dp)) {
                    SharedTransitionLayout {
                        val sharedScope = this
                        AnimatedContent(detail.value, transitionSpec = {
                            fadeIn(tween(420)) togetherWith fadeOut(tween(420))
                        }) { showDetail ->
                            val animatedScope = this
                            val transform: @Composable (MediaItem, Modifier) -> Modifier = { media, modifier ->
                                with(sharedScope) {
                                    val content = rememberSharedContentState("media-artwork:${media.id.value}")
                                    SideEffect {
                                        active = isTransitionActive
                                        matched = content.isMatchFound
                                    }
                                    modifier.sharedBounds(content, animatedScope,
                                        boundsTransform = { _, _ -> tween(420) }).testTag("artwork-${if (showDetail) "detail" else "card"}")
                                }
                            }
                            CompositionLocalProvider(LocalMediaArtworkTransition provides transform) {
                                if (showDetail) {
                                    Box(Modifier.size(300.dp, 220.dp).mediaArtworkTransition(item).background(Color.Blue))
                                } else if (baseCard) {
                                    LegacyBaseItemCard(item, onClick = { detail.value = true }, modifier = Modifier.testTag("source-card"))
                                } else {
                                    LegacyVideoCard(item, onClick = { detail.value = true }, modifier = Modifier.width(120.dp).testTag("source-card"))
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(32)
        val artwork = rule.onNodeWithTag("artwork-card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("source-card").fetchSemanticsNode().boundsInRoot
        assertEquals(if (baseCard) 150f else 180f, artwork.height, .5f)
        assertTrue(card.height > artwork.height, "Only the artwork, not the title/subtitle, should be shared")
        rule.onNodeWithTag("source-card").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle {
            assertTrue(matched, "Card artwork must match the detail artwork")
            assertTrue(active, "The navigation must animate shared bounds, not just fade the page")
        }
        rule.mainClock.advanceTimeBy(600)
        rule.runOnIdle { assertFalse(active) }
        rule.runOnIdle { detail.value = false }
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertTrue(active, "Returning must restore the artwork to its original card") }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("source-card").assertExists()
    }
}
