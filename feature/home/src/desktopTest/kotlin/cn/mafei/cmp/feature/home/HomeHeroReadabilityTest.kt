package cn.mafei.cmp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LocalShellBackgroundBackdrop
import cn.mafei.cmp.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeHeroReadabilityTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun portraitTabletDoesNotPaintAPhoneScrimOverTheWindowArtwork() = assertWindowArtwork(736, 740)
    @Test fun sidebarDoesNotPaintAPhoneScrimOverTheWindowArtwork() = assertWindowArtwork(1000, 740)

    private fun assertWindowArtwork(width: Int, height: Int) {
        val backdropColor = Color(0xFFC08050)
        val item = MediaItem(MediaId("hero"), ServerId("server"), ProviderKind.Jellyfin,
            "影片", mediaType = MediaType.Movie)
        rule.setContent {
            MaterialTheme {
                val windowBackdrop = rememberGlassBackdrop()
                val state = remember { HomeHeroBackdropState() }
                CompositionLocalProvider(LocalShellBackgroundBackdrop provides windowBackdrop) {
                    Box(Modifier.requiredSize(width.dp, height.dp).background(backdropColor).testTag("heroFixture")) {
                        HomeScreen(
                            collections = emptyList(), items = emptyList(), homeLatest = listOf(item),
                            selectedCollection = null, loading = false, error = null, stale = false,
                            canLoadMore = false, overview = true, layout = mafeiLayout(width.toFloat(), height.toFloat()),
                            onCollection = {}, onItem = {}, onRetry = {}, onLoadMore = {}, onOpenLibrary = {},
                            heroAutoplay = false, heroBackdropState = state, gridState = rememberLazyGridState(),
                            backdropArtwork = { _, modifier -> Box(modifier.background(backdropColor)) },
                        )
                    }
                }
            }
        }
        val pixels = rule.onNodeWithTag("heroFixture").captureToImage().toPixelMap()
        val color = pixels[width - 48, 300]
        assertEquals(backdropColor.red, color.red, .01f, "Window artwork must not receive a second rectangular scrim")
        assertEquals(backdropColor.green, color.green, .01f)
    }
}
