package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.common.ui.LegacyFailedView
import cn.mafei.cmp.common.ui.LegacyListGroup
import cn.mafei.cmp.common.ui.LegacyListGroupItem
import cn.mafei.cmp.common.ui.LegacyLoadingView
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiPageTitleBar
import cn.mafei.cmp.common.ui.MaFeiPageTitleBarHeight
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaType
import org.jetbrains.compose.resources.DrawableResource

/** Mechanical Compose translation of ArkUI `MediaTab`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryIndexScreen(
    collections: List<MediaCollection>,
    loading: Boolean,
    error: String?,
    layout: MaFeiLayout,
    onCollection: (MediaCollection) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean = false,
    bottomOverlayInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val contentStart = layout.pagePaddingDp.dp +
        if (layout.navigation == NavigationPlacement.Bottom) safeArea.start else 0.dp
    val contentEnd = layout.pagePaddingDp.dp + safeArea.end
    val contentBottom = safeArea.bottom +
        if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 32.dp
    // Keep the tail as a real list item, matching the legacy MediaTab. CPF's
    // PullToRefreshBox can otherwise let the final LazyColumn item remain
    // underneath the sibling Bottom Tab even when bottom content padding is set.
    val scrollTail = contentBottom + bottomOverlayInset
    // 页面自己是记录者：内容录进 backdrop，标题栏作为它的**兄弟**来消费（自引用会栈溢出）。
    val backdrop = rememberGlassBackdrop()

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            when {
                loading && collections.isEmpty() -> LegacyLoadingView(Modifier.fillMaxSize())
                error != null && collections.isEmpty() -> LegacyFailedView(onRetry, Modifier.fillMaxSize())
                collections.isEmpty() -> LegacyEmptyView(modifier = Modifier.fillMaxSize())
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRetry,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = contentStart,
                            // 让开标题栏（含状态栏），且是滚动让位：列表从栏下面穿过去。
                            top = safeArea.top + MaFeiPageTitleBarHeight + 12.dp,
                            end = contentEnd,
                            bottom = 0.dp,
                        ),
                    ) {
                        item {
                            LegacyListGroup(
                                items = collections.map { collection ->
                                    LegacyListGroupItem(
                                        title = collection.name,
                                        icon = collection.legacyLibraryIcon(),
                                        trailing = collection.itemCount?.toString().orEmpty(),
                                        accessibilityLabel = "打开${collection.name}",
                                    )
                                },
                                onItemClick = { index -> collections.getOrNull(index)?.let(onCollection) },
                            )
                        }
                        item { Spacer(Modifier.height(scrollTail)) }
                    }
                }
            }
        }
        MaFeiPageTitleBar(
            title = "媒体库",
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private fun MediaCollection.legacyLibraryIcon(): DrawableResource = when {
    mediaTypes.any { it == MediaType.Photo || it == MediaType.PhotoAlbum } -> MaFeiLegacyDrawables.libraryPhoto
    mediaTypes.any { it == MediaType.Audio || it == MediaType.MusicAlbum || it == MediaType.MusicArtist } ->
        MaFeiLegacyDrawables.libraryMusic
    mediaTypes.any { it == MediaType.Movie || it == MediaType.Video } -> MaFeiLegacyDrawables.libraryMovie
    mediaTypes.any { it == MediaType.Series || it == MediaType.Season || it == MediaType.Episode } ->
        MaFeiLegacyDrawables.librarySeries
    else -> MaFeiLegacyDrawables.libraryFolder
}
