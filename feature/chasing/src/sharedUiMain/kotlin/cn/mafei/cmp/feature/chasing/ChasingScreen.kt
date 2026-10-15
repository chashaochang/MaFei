package cn.mafei.cmp.feature.chasing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.common.ui.LegacyFailedView
import cn.mafei.cmp.common.ui.LegacyLoadingView
import cn.mafei.cmp.common.ui.MaFeiPageTitleBar
import cn.mafei.cmp.common.ui.mafeiPageTitleBarContentHeight
import cn.mafei.cmp.common.ui.MaFeiProgressLine
import cn.mafei.cmp.common.ui.MaFeiPullRefresh
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType

/** Mechanical Compose translation of ArkUI `ChasingTab(fromHome = true)`. */
@Composable
fun ChasingScreen(
    state: ChasingState,
    layout: MaFeiLayout,
    listState: LazyListState,
    onRetry: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onOpenSeries: (MediaId) -> Unit,
    bottomOverlayInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val horizontalSpacing = if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp
    val horizontalPadding = horizontalSpacing * 2
    val bottomPadding = safeArea.bottom + bottomOverlayInset +
        if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 30.dp
    val backdrop = rememberGlassBackdrop()
    var selectedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    val sections = remember(state.sections) { state.sections.associateBy(ChasingSection::kind) }
    val nextUp = sections[ChasePlanner.NextUpKind]?.items.orEmpty()
    val continueItems = sections[ChasePlanner.ContinueKind]?.items.orEmpty()
    val completed = sections[ChasePlanner.CompletedKind]?.items.orEmpty()
    val all = remember(nextUp, continueItems, completed) {
        buildList {
            addAll(nextUp)
            addAll(continueItems)
            addAll(completed)
        }.distinctBy { (it.media.seriesId ?: it.media.id).value }
    }
    val filtered = when (selectedFilterIndex) {
        0 -> nextUp
        1 -> continueItems
        2 -> completed
        else -> all
    }
    // 一级页面与「媒体库」共用同一条顶部栏。内容记录 backdrop，顶部栏作为兄弟层消费，
    // 避免把玻璃消费者重新录进同一个 layer 形成自采样递归。
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            MaFeiPullRefresh(
                isRefreshing = state.refreshing,
                onRefresh = onRetry,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = safeArea.top + mafeiPageTitleBarContentHeight() + 12.dp,
                        bottom = bottomPadding,
                    ),
                ) {
                    if (state.followedCount > 0 || state.totalCount > 0) {
                        item(key = "filters") {
                            ChasingFilters(
                                selectedIndex = selectedFilterIndex,
                                onSelect = { selectedFilterIndex = it },
                                modifier = Modifier.padding(
                                    start = safeArea.start + horizontalPadding,
                                    end = safeArea.end + horizontalPadding,
                                ),
                            )
                        }
                    }

                    if (state.followedCount > 0 && filtered.isEmpty()) {
                        item(key = "filter-empty-$selectedFilterIndex") {
                            ChasingFilterEmpty(
                                title = filterEmptyTitle(selectedFilterIndex),
                                subtitle = filterSubtitle(selectedFilterIndex),
                            )
                        }
                    } else when (selectedFilterIndex) {
                        0 -> {
                            nextUp.firstOrNull()?.let { featured ->
                                item(key = "featured-${featured.media.id.value}") {
                                    ChasingFeaturedUpdate(
                                        item = featured,
                                        onClick = { openChasingItem(featured, onPlay, onOpenSeries) },
                                        artwork = artwork,
                                        modifier = Modifier.padding(
                                            start = safeArea.start + horizontalPadding,
                                            top = 14.dp,
                                            end = safeArea.end + horizontalPadding,
                                        ),
                                    )
                                }
                            }
                            if (nextUp.size > 1) {
                                item(key = "next-up") {
                                    ChasingSectionList(
                                        title = "接下来",
                                        subtitle = "${nextUp.size - 1} 集等待播放",
                                        items = nextUp.drop(1),
                                        layout = layout,
                                        horizontalPadding = horizontalPadding,
                                        safeStart = safeArea.start,
                                        safeEnd = safeArea.end,
                                        onItem = { openChasingItem(it, onPlay, onOpenSeries) },
                                        artwork = artwork,
                                    )
                                }
                            }
                        }
                        1 -> item(key = "continue") {
                            ChasingSectionList(
                                title = "继续观看",
                                subtitle = "${continueItems.size} 个内容可继续播放",
                                items = continueItems,
                                layout = layout,
                                horizontalPadding = horizontalPadding,
                                safeStart = safeArea.start,
                                safeEnd = safeArea.end,
                                onItem = { onPlay(it.media) },
                                artwork = artwork,
                            )
                        }
                        2 -> item(key = "completed") {
                            ChasingSectionList(
                                title = "已看完",
                                subtitle = "${completed.size} 部剧集已看完",
                                items = completed,
                                layout = layout,
                                horizontalPadding = horizontalPadding,
                                safeStart = safeArea.start,
                                safeEnd = safeArea.end,
                                onItem = { onOpenSeries(it.seriesId ?: it.media.id) },
                                artwork = artwork,
                            )
                        }
                        else -> item(key = "all") {
                            ChasingSectionList(
                                title = "全部追剧",
                                subtitle = filterSubtitle(selectedFilterIndex),
                                items = all,
                                layout = layout,
                                horizontalPadding = horizontalPadding,
                                safeStart = safeArea.start,
                                safeEnd = safeArea.end,
                                onItem = { openChasingItem(it, onPlay, onOpenSeries) },
                                artwork = artwork,
                            )
                        }
                    }
                }
            }

            when {
                state.loading -> LegacyLoadingView()
                state.error != null && state.sections.all { it.items.isEmpty() } -> LegacyFailedView(onRetry)
                !state.loading && state.error == null && state.followedCount == 0 ->
                    LegacyEmptyView(title = "暂无追剧", subtitle = "去剧集详情页添加")
            }
        }
        MaFeiPageTitleBar(
            title = "追剧",
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun ChasingFilters(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("待看", "继续看", "已看完", "全部").forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Text(
                text = label,
                modifier = Modifier.weight(1f).height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) LegacyMainBlue else LegacyFilterBackground)
                    .border(
                        width = if (selected) 0.dp else .5.dp,
                        color = if (selected) Color.Transparent else LegacyBorder,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .mafeiInteractive(
                        onClick = { onSelect(index) },
                        radiusDp = 18f,
                        label = label,
                    )
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                color = if (selected) Color.White else LegacyPrimaryText,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChasingFeaturedUpdate(
    item: ChasingItem,
    onClick: () -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().height(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .mafeiInteractive(onClick, radiusDp = 22f, label = item.title),
        contentAlignment = Alignment.BottomStart,
    ) {
        artwork?.invoke(item.media, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = .70f)),
                ),
            ),
        )
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "下一集待看",
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(LegacyMainBlue)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                item.title,
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                color = Color.White.copy(alpha = .82f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChasingSectionList(
    title: String,
    subtitle: String,
    items: List<ChasingItem>,
    layout: MaFeiLayout,
    horizontalPadding: Dp,
    safeStart: Dp,
    safeEnd: Dp,
    onItem: (ChasingItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    val compact = layout.family == MaFeiLayoutFamily.Phone
    val cardWidth = if (compact) 160.dp else 240.dp
    val cardHeight = if (compact) 90.dp else 135.dp
    val listHeight = if (compact) 134.dp else 201.dp
    val gap = if (compact) 8.dp else 16.dp

    Column(
        Modifier.fillMaxWidth().padding(top = 22.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = safeStart + horizontalPadding,
                end = safeEnd + horizontalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            Text(
                items.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(listHeight).padding(top = 12.dp),
            contentPadding = PaddingValues(start = safeStart + horizontalPadding, end = safeEnd + horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            itemsIndexed(items, key = { _, item -> item.media.id.value }) { _, item ->
                ChasingHorizontalCard(
                    item = item,
                    width = cardWidth,
                    imageHeight = cardHeight,
                    onClick = { onItem(item) },
                    artwork = artwork,
                )
            }
        }
    }
}

@Composable
private fun ChasingHorizontalCard(
    item: ChasingItem,
    width: Dp,
    imageHeight: Dp,
    onClick: () -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
) {
    Column(
        Modifier.width(width).mafeiInteractive(onClick, radiusDp = 10f, label = item.title),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            Modifier.width(width).height(imageHeight)
                .border(.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            artwork?.invoke(item.media, Modifier.fillMaxSize())
            MaFeiProgressLine(
                progress = item.progressPercent.coerceIn(0, 100) / 100f,
                modifier = Modifier.fillMaxWidth().height(4.dp),
                progressColor = Color(0xFF3B82F6),
                trackColor = Color.Transparent,
            )
        }
        Text(
            item.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.subtitle,
            color = Color.Gray,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChasingFilterEmpty(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            subtitle,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun openChasingItem(
    item: ChasingItem,
    onPlay: (MediaItem) -> Unit,
    onOpenSeries: (MediaId) -> Unit,
) {
    if (item.media.mediaType == MediaType.Episode) onPlay(item.media)
    else onOpenSeries(item.seriesId ?: item.media.id)
}

private fun filterSubtitle(index: Int): String = when (index) {
    0 -> "按下一集优先展示"
    1 -> "只看已有播放进度"
    2 -> "只看已完整看完的剧集"
    else -> "全部已关注剧集"
}

private fun filterEmptyTitle(index: Int): String = when (index) {
    0 -> "暂无待看的下一集"
    1 -> "暂无可继续观看的内容"
    2 -> "暂无已看完的剧集"
    else -> "暂无可展示的追剧内容"
}

private val LegacyMainBlue = Color(0xFF2173DF)
private val LegacyFilterBackground = Color(0xFF1E262B)
private val LegacyPrimaryText = Color(0xFFCBD5E0)
private val LegacyBorder = Color(0x40A0AEC0)
