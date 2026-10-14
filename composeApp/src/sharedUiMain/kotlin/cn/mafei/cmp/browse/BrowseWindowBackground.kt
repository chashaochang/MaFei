package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.media.ArtworkMemoryCache
import cn.mafei.cmp.common.media.CachedBlurredArtwork
import cn.mafei.cmp.common.media.MediaArtwork
import cn.mafei.cmp.common.media.landscapeArtworkCandidates
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.feature.home.HomeHeroBackdropState
import cn.mafei.cmp.feature.home.HomeHeroWindowBackdrop
import cn.mafei.cmp.session.*

@Composable
internal fun BrowseWindowBackground(
    state: cn.mafei.cmp.feature.home.MediaBrowserState,
    layout: cn.mafei.cmp.common.design_system.MaFeiLayout,
    artworkCache: ArtworkMemoryCache,
    fetchArtwork: suspend (MediaImage, Int) -> ByteArray?,
    homeCarouselVisible: State<Boolean>,
    heroBackdrop: HomeHeroBackdropState,
    currentRoute: BrowseRoute,
    showingDetail: Boolean,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    // 首页轮播背景画在窗口画布上：全窗、与侧栏让位无关。轮播背景是
    // 「窗口的背景」而不是「被侧栏挤开的内容区里伸回去的补丁」——
    // 侧栏收不收起，它都逐像素静止。
    if (currentRoute == BrowseHomeRoute && !showingDetail && homeCarouselVisible.value) {
        HomeHeroWindowBackdrop(
            state = heroBackdrop,
            ambientArtwork = { item, radius, modifier ->
                MediaArtwork(
                    item.landscapeArtworkCandidates(), item.title, artworkCache, fetchArtwork, modifier,
                    targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
                    bitmapContent = { bitmap, imageModifier ->
                        CachedBlurredArtwork(bitmap, radius, imageModifier, cache = artworkCache)
                    },
                )
            },
            artwork = { item, modifier ->
                MediaArtwork(
                    item.landscapeArtworkCandidates(),
                    item.title,
                    artworkCache,
                    fetchArtwork,
                    modifier,
                    // The phone hero is a single-column visual band. A 720px
                    // decode is enough for its viewport and avoids keeping two
                    // 960px copies (clear hero + ambient backdrop) alive during
                    // the first scroll.
                    targetWidthPx = if (layout.family == MaFeiLayoutFamily.Phone) 720 else 960,
                )
            },
        )
    }
}
