package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule

class HomeBackdropRetentionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun switchingAwayKeepsTheCurrentArtworkAndStopsItsCrossfade() {
        val active = mutableStateOf(true)
        val first = item("first")
        val second = item("second")
        val backdrop = HomeHeroBackdropState().apply {
            activeItem = first
            clearCarouselEnabled = false
        }
        val attached = mutableMapOf<MediaId, Int>()
        val visibleSources = mutableSetOf<MediaId>()
        var draws = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                MaFeiRetainedContent(active.value, Modifier.size(390.dp, 844.dp)) {
                    HomeHeroWindowBackdrop(backdrop, artwork = null, ambientArtwork = { media, _, modifier ->
                        DisposableEffect(media.id) {
                            attached[media.id] = (attached[media.id] ?: 0) + 1
                            visibleSources += media.id
                            onDispose { visibleSources -= media.id }
                        }
                        Box(modifier.drawWithContent { draws++; drawContent() })
                    })
                }
            }
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { backdrop.activeItem = second }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnIdle {
            assertEquals(setOf(first.id, second.id), visibleSources)
            active.value = false
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertEquals(setOf(second.id), visibleSources, "Hidden crossfade must release its outgoing source") }
        val hiddenDraws = draws
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle {
            assertEquals(hiddenDraws, draws, "Hidden background must not keep drawing")
            active.value = true
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            assertEquals(1, attached[second.id], "Returning must reuse the retained artwork")
            assertTrue(draws > hiddenDraws)
        }
    }

    private fun item(id: String) = MediaItem(MediaId(id), ServerId("server"),
        ProviderKind.Jellyfin, id, mediaType = MediaType.Movie)
}
