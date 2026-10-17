package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cn.mafei.cmp.common.glass.components.LiquidSelectionDroplet
import cn.mafei.cmp.common.glass.components.mafeiProgressiveBlur
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
import org.jetbrains.compose.resources.painterResource

/**
 * Isolates the per-frame scroll-state read from [MaFeiAdaptiveShell].
 *
 * Home scroll progress changes on almost every frame. Reading it in the shell's
 * root restart group invalidated the complete scene/navigation/modal subtree,
 * even though only this narrow blur band depends on the value. Keeping the read
 * in a small child means the large adaptive shell remains skippable while the
 * list is moving.
 */
@Composable
internal fun TopChromeProgressiveBlur(
    progress: () -> Float,
    backdrop: GlassBackdrop,
    topInset: Dp,
    contentStartInset: Dp,
    contentEndInset: Dp,
    modifier: Modifier = Modifier,
) {
    val opacity = progress().coerceIn(0f, 1f)
    if (opacity <= 0f) return
    val tint = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(topInset)
            .padding(start = contentStartInset, end = contentEndInset)
            .mafeiProgressiveBlur(
                backdrop = backdrop,
                tint = tint,
                tintIntensity = if (tint.luminance() >= .5f) .72f else .68f,
                gradientStartFraction = 1f / 3f,
                opacity = opacity,
            ),
    )
}

@Composable
internal fun MediumTopTabs(
    destinations: List<MaFeiNavItem>,
    selectedDestination: String,
    onDestination: (String) -> Unit,
    onSearch: (() -> Unit)?,
    selectedSearch: Boolean,
    onBack: (() -> Unit)?,
    onOpenDrawer: () -> Unit,
    backdrop: GlassBackdrop,
    morphModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val testModifier = modifier.testTag("mediumTopTabs")
    // A route such as Search has no top-strip tab. Keep the tab labels
    // unselected instead of silently falling back to Home; the sidebar owns
    // the explicit Search selection in that state.
    val destinationIndex = destinations.indexOfFirst { it.key == selectedDestination }
    val indicatorScope = rememberCoroutineScope()
    // 选中液滴是一层真玻璃，采样的源必须是"页面 + 本条导航栏自己的材质"。少了后者，液滴
    // 底下会直接透出滚动列表的内容 —— 看起来就是选中项"没有背景"。
    //
    // Record a sibling copy of the complete strip for the droplet. Without
    // this layer the droplet only samples the page and can scale it, but has
    // no top/bottom edge or label content to refract.
    val tabsBackdrop = rememberGlassBackdrop()
    val dropletBackdrop = rememberCombinedGlassBackdrop(backdrop, tabsBackdrop)
    // 指示器的手势挂在**每个 Tab** 上（液滴画在标签下面，拿不到事件）。顶栏文字项不
    // 使用底部导航那种等宽格子：宽度由文字测量结果 + 固定左右 padding 决定，拖拽轨道
    // 也使用这些真实的起点/中心点，避免长媒体库名称仍被塞进 80dp 的固定按钮。
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val hasSearch = onSearch != null
    val tabWidths = remember(destinations, hasSearch, density, textMeasurer) {
        buildList {
            val maxLabelWidthPx = with(density) { MediumTopMaxLabelWidth.roundToPx() }
            destinations.forEach { item ->
                val measured = textMeasurer.measure(
                    text = AnnotatedString(item.label),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    constraints = Constraints(maxWidth = maxLabelWidthPx),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                add(
                    with(density) { measured.size.width.toDp() } +
                        MediumTopTabHorizontalPadding * 2,
                )
            }
            if (hasSearch) add(MediumTopSearchWidth)
        }
    }
    val tabStartsDp = remember(tabWidths) {
        buildList {
            var cursor = 0.dp
            tabWidths.forEach { width ->
                add(cursor)
                cursor += width
            }
        }
    }
    val tabStartsPx = remember(tabStartsDp, density) {
        tabStartsDp.map { with(density) { it.toPx() } }
    }
    val tabCentersPx = remember(tabStartsPx, tabWidths, density) {
        tabStartsPx.indices.map { index ->
            tabStartsPx[index] + with(density) { tabWidths[index].toPx() } / 2f
        }
    }
    val maxStartPx = tabStartsPx.lastOrNull() ?: 0f
    val latestOnDestination by rememberUpdatedState(onDestination)
    val latestOnSearch by rememberUpdatedState(onSearch)
    val latestKeys = rememberUpdatedState(destinations.map { it.key })
    val searchIndex = if (onSearch != null) destinations.size else -1
    val tabCount = destinations.size + if (onSearch != null) 1 else 0
    val selectedIndex = if (selectedSearch && searchIndex >= 0) searchIndex else destinationIndex
    val indicatorIndex = selectedIndex.coerceAtLeast(0)
    val indicatorWidth by animateDpAsState(
        targetValue = tabWidths.getOrNull(indicatorIndex) ?: 0.dp,
        animationSpec = tween(180),
        label = "medium-top-tab-width",
    )
    val latestTabStartsPx = rememberUpdatedState(tabStartsPx)
    val latestTabCentersPx = rememberUpdatedState(tabCentersPx)
    val latestSelectedWidthPx = rememberUpdatedState(
        tabWidths.getOrNull(indicatorIndex)?.let { with(density) { it.toPx() } } ?: 0f,
    )
    val indicator = remember(indicatorScope, tabCount, tabWidths) {
        // 只在**真的拖动过**时才在松手时改选中：单击也会走 onDragStart/onDragStopped
        // （`inspectDragGestures` 不设滑动阈值），那只该由 Tab 自己的点击负责，否则会多
        // 出一次没必要的按压回弹。
        var dragged = false
        DampedDragAnimation(
            animationScope = indicatorScope,
            initialValue = tabStartsPx.getOrNull(indicatorIndex) ?: 0f,
            valueRange = 0f..maxStartPx,
            visibilityThreshold = .001f,
            initialScale = 1f,
            // Keep horizontal growth restrained so neighbouring labels stay
            // readable, but let the lens rise above/below the 40dp track.
            // The vertical overshoot is what makes the track's upper/lower
            // edges readable through the pressed droplet.
            pressedScale = 1.24f,
            pressedScaleY = 1.38f,
            onDragStarted = { dragged = false },
            onDragStopped = {
                if (dragged) {
                    val targetCenter = targetValue + latestSelectedWidthPx.value / 2f
                    val index = latestTabCentersPx.value.indices.minByOrNull { candidate ->
                        kotlin.math.abs(latestTabCentersPx.value[candidate] - targetCenter)
                    } ?: 0
                    val targetStart = latestTabStartsPx.value.getOrNull(index) ?: 0f
                    animateToValue(targetStart)
                    if (index == searchIndex) {
                        latestOnSearch?.invoke()
                    } else {
                        latestKeys.value.getOrNull(index)?.let { latestOnDestination(it) }
                    }
                }
            },
            onDrag = { _, dragAmount ->
                if (dragAmount.x != 0f) dragged = true
                if (maxStartPx > 0f) {
                    updateValue((targetValue + dragAmount.x).coerceIn(0f, maxStartPx))
                }
            },
        )
    }
    LaunchedEffect(indicatorIndex, tabCount, tabStartsPx) {
        val targetStart = tabStartsPx.getOrNull(indicatorIndex) ?: 0f
        if (indicator.value != targetStart) {
            indicator.animateToValue(targetStart)
        }
    }
    // 这条导航对应小屏的底部导航，所以 Tab 胶囊按内容自适应宽度。
    // 展开按钮始终属于顶栏胶囊首项；二级页的返回按钮是另一层导航上的独立控制，
    // 不能拿返回按钮替换掉顶栏自己的展开按钮。
    val tabTrackWidth = MediumTopStripInset * 2 +
        MediumTopExpandWidth +
        tabWidths.fold(0.dp) { total, width -> total + width }
    val tabGroupWidth = tabTrackWidth
    // 外壳把这个条放在顶部导航区的**顶端**，所以这里自己撑满导航高度并垂直居中 ——
    // 否则 40dp 的玻璃条会贴着状态栏，看着偏上。
    //
    // 分层（自下而上）：玻璃条材质 → 选中液滴 → 展开按钮与 Tab 标签。
    // 液滴必须画在标签**下面**：`drawBackdrop` 是把折射后的背景铺满整个形状，画在上层
    // 会把选中项的标签整个盖掉（只剩一颗空胶囊）。上游 `LiquidBottomTabs` 靠"再记录一份
    // 不可见的内容副本给液滴采样"来实现玻璃里放大的标签；顶栏也采用这个安全的兄弟记录层，
    // 而不是把液滴放进自己的采样源里造成自引用。
    // Bottom tabs scale both the navigation surface and its selection droplet
    // while pressed. Keep the same width-dependent panel curve here; only the
    // droplet itself remains outside this layer so it is not scaled twice.
    val panelPressedScale = with(LocalDensity.current) {
        1f + 16.dp.toPx() / tabTrackWidth.toPx()
    }
    // Keep one source of truth for the visible row and the hidden recorder
    // row. The recorder must never receive pointer/semantics modifiers: it is
    // only a backdrop source for the liquid droplet.
    val renderTabContent: @Composable RowScope.(interactive: Boolean) -> Unit = { interactive ->
        val expandInteraction = if (interactive) {
            Modifier
                .semantics { contentDescription = "展开侧边栏" }
                .mafeiInteractive(onOpenDrawer, radiusDp = 17f, label = "展开侧边栏")
        } else {
            Modifier
        }
        Box(
            Modifier.width(MediumTopExpandWidth).fillMaxHeight().then(expandInteraction),
            contentAlignment = Alignment.Center,
        ) {
            SidebarIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
        }
        destinations.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val tabInteraction = if (interactive) {
                Modifier
                    .mediumTopTabGesture(
                        animation = indicator,
                        onClick = { onDestination(item.key) },
                    )
                    .semantics { this.selected = selected }
                    .semantics { onClick(item.label) { onDestination(item.key); true } }
            } else {
                Modifier
            }
            Box(
                Modifier
                    .width(tabWidths[index])
                    .fillMaxHeight()
                    .then(tabInteraction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    Modifier.padding(horizontal = MediumTopTabHorizontalPadding),
                    color = if (selected) MediumTopTabAccent
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        onSearch?.let { action ->
            val searchInteraction = if (interactive) {
                Modifier
                    .mediumTopTabGesture(
                        animation = indicator,
                        onClick = action,
                    )
                    .semantics {
                        contentDescription = "搜索"
                        selected = selectedSearch
                        onClick("搜索") { action(); true }
                    }
            } else {
                Modifier
            }
            Box(
                Modifier
                    .width(tabWidths[destinations.size])
                    .fillMaxHeight()
                    .then(searchInteraction),
                contentAlignment = Alignment.Center,
            ) {
                NavigationIcon(
                    glyph = MaFeiGlyph.Search,
                    selected = selectedSearch,
                    modifier = Modifier.size(20.dp),
                    color = if (selectedSearch) MediumTopTabAccent
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    Box(testModifier.fillMaxWidth().height(MaFeiTopChromeHeightDp), contentAlignment = Alignment.Center) {
        // Keep an extra 6dp above and below the 40dp track. The pressed
        // droplet is allowed to occupy this top-chrome space instead of
        // being clipped by the track's own layout bounds.
        Box(
            morphModifier
                .requiredHeight(MaFeiTopChromeHeightDp)
                .width(tabGroupWidth),
        ) {
            val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .16f)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .height(MediumTopStripHeight)
                    .width(tabTrackWidth),
            ) {
                // Record the complete track (material, edge highlight and labels)
                // as a sibling source. This is the same source/consumer split used
                // by LiquidBottomTabs; recording only a MaFeiSurface leaves the
                // droplet with no track edge to refract.
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .captureGlassBackdrop(tabsBackdrop)
                        .liquidTrackSurface(backdrop, trackColor)
                        .fillMaxHeight()
                        .width(tabTrackWidth)
                        .padding(horizontal = MediumTopStripInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    renderTabContent(false)
                }
                // The visible track uses the same backdrop material. Its scale is
                // applied through drawBackdrop's layerBlock so the sampled source
                // and the pressed geometry remain in the same coordinate space.
                Row(
                    Modifier
                        .liquidTrackSurface(
                            backdrop = backdrop,
                            tint = trackColor,
                            layerBlock = {
                                val progress = indicator.pressProgress.coerceIn(0f, 1f)
                                val scale = 1f + (panelPressedScale - 1f) * progress
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                        .fillMaxHeight()
                        .width(tabTrackWidth)
                        .padding(horizontal = MediumTopStripInset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 目的地 Tab：文字自适应宽度 + 一颗沿真实轨道滑动的液滴选中指示器。
                    // 不用 LiquidBottomTabs：那个组件自带蓝色强调色与自己的几何，套上来会把这条
                    // 导航的样子改掉；这里只取"选中指示器滑动 + 真玻璃液滴"这一核心手感。
                    renderTabContent(true)
                }
            }
            // Draw the liquid consumer after the visible row, just like the
            // bottom dock. Its sibling recorder above prevents self-sampling,
            // while this order lets the lens visibly bend the row's labels and
            // the capsule's upper/lower edges instead of leaving the effect
            // hidden underneath the text.
            LiquidSelectionDroplet(
                backdrop = dropletBackdrop,
                animation = indicator,
                // The animation value is the pixel position on the variable-
                // width track; the callback keeps the shared glass component
                // independent from the top-bar layout policy.
                travelPerStep = 1.dp,
                translationXInPx = { it },
                modifier = Modifier
                    .alpha(if (selectedIndex >= 0) 1f else 0f)
                    .align(Alignment.CenterStart)
                    // 胶囊内留白 + 顶栏展开按钮的占位。二级页的返回键属于
                    // 另一层全宽导航，不参与这里的 Tab 几何计算。
                    .offset(x = MediumTopStripInset + MediumTopExpandWidth)
                    // Match the bottom dock's geometry: the top track is 40dp,
                    // while its selection capsule has 4dp of breathing room on
                    // both sides inside the 52dp chrome. The previous 34dp
                    // height made the droplet look like a thin strip glued to
                    // the track edge.
                    .height(MaFeiTopChromeHeightDp - 8.dp)
                    .width(indicatorWidth),
            )
        }
        if (onBack != null) {
            // The top navigation row is full width. On a secondary route the
            // back control belongs to its far-left leading slot, while the
            // destination capsule remains independently centered. The leading
            // edge uses the same page gutter as the second-row title and the
            // route content; it is not tied to the capsule's measured width.
            val pageHorizontalInset = LocalMaFeiNavigationChrome.current.effectiveLayout
                ?.pagePaddingDp?.dp
                ?: MaFeiLargePageHorizontalInsetDp
            MaFeiActionBarButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = LocalMaFeiSafeArea.current.start + pageHorizontalInset),
                contentDescription = "返回",
            ) {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.back),
                    contentDescription = null,
                    modifier = Modifier.size(MaFeiNavigationControlIconSizeDp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

/**
 * The top strip uses one pointer owner per item. A separate clickable and drag
 * observer can compete for the same Desktop pointer stream; in that setup a
 * normal click may work while a drag is cancelled. This modifier distinguishes
 * the two gestures while leaving the semantics action available to keyboard
 * and assistive input.
 */
private fun Modifier.mediumTopTabGesture(
    animation: DampedDragAnimation,
    onClick: () -> Unit,
): Modifier = pointerInput(animation) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        animation.onDragStarted.invoke(animation, down.position)
        animation.press()
        var pointerId = down.id
        var previous = down.position
        var dragged = false
        var completed = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    if (dragged) animation.onDragStopped.invoke(animation) else onClick()
                    completed = true
                    break
                }
                val delta = change.position - previous
                if (delta.x != 0f || delta.y != 0f) {
                    previous = change.position
                    if (kotlin.math.abs(change.position.x - down.position.x) > 3f) {
                        dragged = true
                        animation.onDrag.invoke(animation, size, delta)
                    }
                }
            }
        } finally {
            animation.release()
            // A cancelled gesture must not invoke either navigation callback.
            if (!completed) dragged = false
        }
    }
}

/** 顶部玻璃条本体的高度（导航区里垂直居中）。 */
private val MediumTopStripHeight = 40.dp

/** 玻璃条内左右留白，液滴的起始位置也要加这一份。 */
private val MediumTopStripInset = 3.dp

/** 条首的"展开侧边栏"按钮占位宽度。 */
private val MediumTopExpandWidth = 40.dp

/** 顶部文字 Tab 的固定左右内边距；按钮宽度由文字测量结果自适应。 */
private val MediumTopTabHorizontalPadding = 14.dp

/** 长媒体库名称仍保持一行，超出后由 Text 的省略策略处理。 */
private val MediumTopMaxLabelWidth = 180.dp

/** 顶部最后一个搜索项是纯图标，保留固定触控宽度。 */
private val MediumTopSearchWidth = 48.dp

/** 选中强调色：取值与 `LiquidBottomTabs` 深色主题一致（那边是硬编码）。 */
private val MediumTopTabAccent = Color(0xFF0091FF)
