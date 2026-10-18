package cn.mafei.cmp.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.ui.LocalMaFeiPageActive
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeHeroActiveLifecycleTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test
    fun retainedInactiveHeroStopsItsClockAndCallbacksThenResumesFromTheSamePage() {
        val active = mutableStateOf(true)
        val items = (0..2).map { index ->
            MediaItem(MediaId("hero-$index"), ServerId("server"), ProviderKind.Jellyfin,
                "Hero $index", mediaType = MediaType.Movie)
        }
        lateinit var pager: PagerState
        lateinit var progress: Animatable<Float, AnimationVector1D>
        val callbacks = mutableListOf<MediaId?>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalMaFeiPageActive provides active.value) {
                    pager = rememberPagerState { HeroVirtualPageCount }
                    progress = remember { Animatable(0f) }
                    val indicator = remember { mutableStateOf(0) }
                    Box(Modifier.size(390.dp, 844.dp)) {
                        RecommendationHero(
                            items = items, pagerState = pager, autoplayProgress = progress,
                            indicatorPage = indicator, layout = mafeiLayout(390f, 844f),
                            onItem = {}, artwork = null, autoplay = true, scrolling = false,
                            onActiveItemChanged = { callbacks += it?.id }, extraHeight = 0.dp,
                            refreshProgress = 0f, refreshing = false, windowBackdropActive = false,
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle { assertTrue(progress.value > 0f); active.value = false }
        rule.mainClock.advanceTimeBy(48)
        val pausedProgress = progress.value
        val pausedPage = pager.currentPage
        val pausedCallbacks = callbacks.size
        rule.mainClock.advanceTimeBy(10_000)
        rule.runOnIdle {
            assertFalse(progress.isRunning)
            assertEquals(pausedProgress, progress.value)
            assertEquals(pausedPage, pager.currentPage)
            assertEquals(pausedCallbacks, callbacks.size)
            active.value = true
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertEquals(pausedPage, pager.currentPage) }
        rule.mainClock.advanceTimeBy(8_000)
        rule.runOnIdle { assertEquals(pausedPage + 1, pager.settledPage) }
    }
}
