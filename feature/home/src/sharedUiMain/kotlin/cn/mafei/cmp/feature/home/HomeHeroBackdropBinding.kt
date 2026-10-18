package cn.mafei.cmp.feature.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.core.domain.MediaItem

/** Keeps the window backdrop attached to the retained home page's last active owner. */
@Composable
internal fun BindHomeHeroBackdrop(
    pageActive: Boolean,
    heroBackdropState: HomeHeroBackdropState?,
    heroItems: List<MediaItem>,
    heroPagerState: PagerState,
    gridState: LazyGridState,
    layout: MaFeiLayout,
    heroExtraHeight: Dp,
    heroVisibleForAutoplay: Boolean,
    activeHeroItem: MediaItem?,
) {
    // A retained inactive Home may receive new feeds and create a new local
    // pager. Keep the last published owner until active again or truly disposed.
    var ownedHeroPager by remember(heroBackdropState) { mutableStateOf<PagerState?>(null) }
    var ownedHeroGrid by remember(heroBackdropState) { mutableStateOf<LazyGridState?>(null) }
    DisposableEffect(heroBackdropState) {
        onDispose {
            val pager = ownedHeroPager
            val grid = ownedHeroGrid
            if (pager != null && grid != null) heroBackdropState?.detach(pager, grid)
        }
    }
    // 同步窗口级轮播背景的状态（背景画在 shell 窗口画布，不在 scene 里）。
    LaunchedEffect(pageActive, heroItems, heroBackdropState) {
        if (!pageActive) return@LaunchedEffect
        heroBackdropState?.items = heroItems
    }
    LaunchedEffect(pageActive, heroBackdropState, heroPagerState, gridState, layout) {
        if (!pageActive) return@LaunchedEffect
        heroBackdropState?.let { state ->
            state.pagerState = heroPagerState
            state.gridState = gridState
            ownedHeroPager = heroPagerState
            ownedHeroGrid = gridState
            // A 72dp full-window blur is disproportionately expensive on a
            // 2800x1840 tablet: every ambient transition temporarily renders
            // two large blurred layers. The clear hero still supplies the
            // foreground detail, so the ambient only needs enough blur to
            // carry colour and light into the feed below it.
            state.blurRadius = when (layout.family) {
                MaFeiLayoutFamily.Phone -> 40.dp
                MaFeiLayoutFamily.Tablet -> 44.dp
                MaFeiLayoutFamily.Desktop -> 48.dp
            }
            state.heroHeight = homeHeroHeight(layout)
            state.clearCarouselEnabled = layout.family != MaFeiLayoutFamily.Phone
        }
    }
    // `heroExtraHeight` changes on every resisted pull-down frame. Keeping it
    // inside the attachment effect above left the window-level artwork at its
    // initial height because that effect's keys are otherwise stable. Publish
    // the latest value after each successful composition without restarting a
    // coroutine for every gesture frame.
    SideEffect {
        if (pageActive) heroBackdropState?.extraHeight = heroExtraHeight
    }
    LaunchedEffect(pageActive, heroBackdropState, heroVisibleForAutoplay) {
        if (!pageActive) return@LaunchedEffect
        heroBackdropState?.precomposeNeighbours = heroVisibleForAutoplay
    }
    LaunchedEffect(pageActive, activeHeroItem, heroBackdropState) {
        if (!pageActive) return@LaunchedEffect
        heroBackdropState?.activeItem = activeHeroItem
    }
}
