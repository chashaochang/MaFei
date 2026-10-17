package cn.mafei.cmp.feature.home

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val HeroAutoplayMillis = 6_500

private const val HeroSnapDampingRatio = .92f

private const val HeroSnapStiffness = 420f

private const val HeroSnapThreshold = .58f

internal const val HeroVirtualPageCount = Int.MAX_VALUE

/**
 * Stops the carousel clock once its meaningful foreground has left the viewport.
 *
 * Keeping autoplay alive below the hero changed the full-window ambient image
 * every 6.5 seconds while the user was reading shelves. On slower GPUs that
 * periodic crossfade was visible as an unrelated list hitch. A missing layout
 * entry is allowed only before the first layout; once another lazy item is first,
 * the hero has been disposed and must stay inactive.
 */
internal fun homeHeroAutoplayVisible(
    heroOffsetY: Int?,
    heroHeightPx: Int?,
    firstVisibleItemIndex: Int,
    minimumVisiblePx: Int,
): Boolean {
    if (heroOffsetY != null && heroHeightPx != null) {
        return heroOffsetY + heroHeightPx > minimumVisiblePx.coerceAtLeast(0)
    }
    return firstVisibleItemIndex == 0
}

/** Current, next and previous are the only pages needed before the first autoplay tick. */
internal fun homeHeroPrefetchOrder(itemCount: Int, currentIndex: Int): List<Int> {
    if (itemCount <= 0) return emptyList()
    val settled = currentIndex.mod(itemCount)
    return listOf(settled, (settled + 1).mod(itemCount), (settled - 1).mod(itemCount))
        .distinct()
}

@Composable
internal fun HomeHeroPlaceholder(layout: MaFeiLayout) {
    Box(
        Modifier
            .then(
                if (layout.navigation == NavigationPlacement.Bottom) {
                    Modifier.requiredWidth((layout.gridWidthDp + layout.pagePaddingDp * 2f).dp)
                } else Modifier.fillMaxWidth()
            )
            .height(homeHeroHeight(layout))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .82f),
                        MaterialTheme.colorScheme.surface.copy(alpha = .45f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
                    ),
                ),
            ),
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(.72f)
                .padding(start = if (layout.horizontalHero) 36.dp else 18.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth(.62f).height(30.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)))
            Box(Modifier.fillMaxWidth(.42f).height(14.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecommendationHero(
    items: List<MediaItem>,
    pagerState: PagerState,
    autoplayProgress: Animatable<Float, AnimationVector1D>,
    indicatorPage: MutableState<Int>,
    layout: MaFeiLayout,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    autoplay: Boolean,
    scrolling: Boolean,
    onActiveItemChanged: (MediaItem?) -> Unit,
    extraHeight: Dp,
    refreshProgress: Float,
    refreshing: Boolean,
    windowBackdropActive: Boolean,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val itemKeys = remember(items) { items.map { it.id.value } }
    // 指针按住（鼠标左键 / 触屏手指）期间必须暂停自动轮播：按着不动时
    // interactionSource 不产生 drag 事件，collectIsDraggedAsState 检测不到，
    // 这里用独立的 down/up 跟踪补上「按下即暂停」的语义。
    val heroPointerPressed = remember { mutableStateOf(false) }
    // A mouse drag starts on the same surface as the hero click target. Keep a
    // one-gesture suppression bit so releasing after a swipe cannot also open
    // the page that happened to be underneath the pointer.
    val heroClickSuppressed = remember { mutableStateOf(false) }
    val heroGestureScope = rememberCoroutineScope()
    val pagerPressTracker = Modifier.pointerInput(itemKeys, pagerState) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            heroPointerPressed.value = true
            val mouseDrag = down.type == PointerType.Mouse
            heroClickSuppressed.value = false
            val startPage = pagerState.currentPage
            var totalDragX = 0f
            var dragging = false
            try {
                if (!mouseDrag) {
                    // Keep the existing pager's touch/trackpad path untouched;
                    // this observer only fills the desktop mouse gap.
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pressed = event.changes
                            .firstOrNull { it.id == down.id }
                            ?.pressed == true
                    }
                } else {
                    // HorizontalPager's child clickable consumes the mouse down
                    // before the pager's draggable sees it. Observe the stream in
                    // the Initial pass and take ownership only after horizontal
                    // slop, so a normal click still opens the selected item.
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            pressed = false
                            continue
                        }
                        pressed = change.pressed
                        if (!pressed) continue
                        val deltaX = change.position.x - change.previousPosition.x
                        if (deltaX == 0f) continue
                        totalDragX += deltaX
                        if (!dragging && abs(totalDragX) > viewConfiguration.touchSlop) {
                            dragging = true
                            heroClickSuppressed.value = true
                        }
                        // Once slop is crossed the child clickable sees a
                        // consumed position change and cancels its click. The
                        // actual page settle runs in the regular composition
                        // scope below, outside AwaitPointerEventScope.
                        if (dragging) change.consume()
                    }
                }
            } finally {
                heroPointerPressed.value = false
            }
            if (mouseDrag && dragging) {
                val targetPage = if (totalDragX < 0f) {
                    startPage + 1
                } else {
                    startPage - 1
                }.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                heroGestureScope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
            }
        }
    }
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        // Use one lightly damped settle model for touch, trackpad and autoplay.
        // The old fixed 320ms tween felt rigid after a short drag and delayed
        // the response until 72% of a page had moved.
        snapAnimationSpec = spring(
            dampingRatio = HeroSnapDampingRatio,
            stiffness = HeroSnapStiffness,
            visibilityThreshold = .001f,
        ),
        snapPositionalThreshold = HeroSnapThreshold,
    )
    val settledItemIndex = pagerState.settledPage % items.size
    val isUserDragging by pagerState.interactionSource.collectIsDraggedAsState()
    val automaticPagerScroll = remember { mutableStateOf(false) }
    LaunchedEffect(itemKeys, settledItemIndex) {
        onActiveItemChanged(items.getOrNull(settledItemIndex))
    }
    LaunchedEffect(pagerState, itemKeys) {
        var previousSettledPage: Int? = null
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                indicatorPage.value = settledPage
                if (previousSettledPage != null) {
                    autoplayProgress.snapTo(0f)
                }
                previousSettledPage = settledPage
            }
    }
    LaunchedEffect(itemKeys, autoplay, scrolling) {
        if (!autoplay || items.size <= 1 || scrolling) return@LaunchedEffect
        // User input cancels the current countdown, but releasing the gesture
        // must start a fresh countdown reliably. Keeping the loop inside
        // collectLatest avoids depending on a transient drag-state recomposition
        // to recreate the LaunchedEffect itself.
        snapshotFlow {
            isUserDragging || heroPointerPressed.value ||
                (pagerState.isScrollInProgress && !automaticPagerScroll.value)
        }
            .distinctUntilChanged()
            .collectLatest { userInteracting ->
                if (userInteracting) return@collectLatest
                // Keep the countdown and the page transition in one cancellable
                // loop. The old page remains full while Pager is sliding; only
                // after it settles do we atomically select the next bar at 0%.
                while (true) {
                    autoplayProgress.snapTo(0f)
                    autoplayProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(HeroAutoplayMillis, easing = LinearEasing),
                    )
                    val targetPage = pagerState.settledPage + 1
                    automaticPagerScroll.value = true
                    try {
                        pagerState.animateScrollToPage(
                            page = targetPage,
                            animationSpec = spring(
                                dampingRatio = HeroSnapDampingRatio,
                                stiffness = HeroSnapStiffness,
                                visibilityThreshold = .001f,
                            ),
                        )
                    } finally {
                        automaticPagerScroll.value = false
                    }
                    // CPF/OHOS can occasionally finish a long frame between pages.
                    // Align only the completed automatic step; direct swipes stay native.
                    if (pagerState.settledPage != targetPage || pagerState.currentPageOffsetFraction != 0f) {
                        pagerState.scrollToPage(targetPage)
                    }
                    // Commit both pieces of indicator state only after the page has
                    // settled. The completed bar therefore does not disappear ahead
                    // of the artwork transition, and the next bar starts at zero.
                    indicatorPage.value = targetPage
                    autoplayProgress.snapTo(0f)
                }
            }
    }
    Box(
        Modifier
            .then(
                when {
                    layout.navigation == NavigationPlacement.Bottom -> {
                        // The grid centres a child that is wider than its padded content area.
                        // Requiring the full viewport width is therefore enough to bleed the
                        // hero to both edges; an extra negative offset clips narrow windows.
                        Modifier.requiredWidth((layout.gridWidthDp + layout.pagePaddingDp * 2f).dp)
                    }
                    else -> Modifier.fillMaxWidth()
                }
            )
            .height(homeHeroHeight(layout) + extraHeight)
            // 轮播图已下沉到背景层：这里只保留占位尺寸、手势与文字浮层，
            // 不再裁圆角 —— 图片是页面背景的一部分，不是悬浮卡片。
            .then(pagerPressTracker),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = pagerFlingBehavior,
            // On phone every precomposed page owns a full 960px artwork layer.
            // Keep only the visible page in the first frame; the delayed cache
            // warm-up above makes adjacent pages ready before autoplay.
            beyondViewportPageCount = if (
                layout.family != MaFeiLayoutFamily.Phone && items.size > 1
            ) 1 else 0,
            overscrollEffect = null,
        ) { page ->
            val item = items[page % items.size]
            Box(
                Modifier.fillMaxSize().mafeiInteractive(
                    onClick = { if (!heroClickSuppressed.value) onItem(item) },
                    radiusDp = 0f,
                    label = "查看${item.title}",
                    // 全宽轮播是背景的一部分：hover 染色/微放大/按压 ripple 在
                    // 无边框大面上就是「一个框」，只保留点击语义。
                    visualFeedback = false,
                ),
            ) {
                // 宽屏的清晰图由窗口层负责；手机保留 Pager 的 430dp 原始构图。
                // 手机只借用「清晰图退进同图模糊层」的层级关系，羽化范围仍按
                // 旧手机轮播图自己的底部 96dp 计算，不复用大屏的整图比例。
                val drawImageInPager = !windowBackdropActive || layout.family == MaFeiLayoutFamily.Phone
                val featherIntoAmbient = windowBackdropActive && layout.family == MaFeiLayoutFamily.Phone
                val readabilityScrim = if (layout.horizontalHero) {
                    Brush.horizontalGradient(
                        listOf(Color(0xDA071019), Color(0x8F071019), Color(0x16071019), Color.Transparent),
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .08f),
                        .42f to Color.Black.copy(alpha = .16f),
                        1f to Color.Black.copy(alpha = .88f),
                    )
                }
                if (drawImageInPager) {
                    Box(
                        Modifier.fillMaxSize()
                            .then(
                                if (featherIntoAmbient) {
                                    Modifier.graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }.drawWithContent {
                                        drawContent()
                                        val featherHeightPx = HomePhoneHeroFeatherHeight.toPx()
                                            .coerceAtMost(size.height)
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(Color.White, Color.Transparent),
                                                startY = size.height - featherHeightPx,
                                                endY = size.height,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (artwork != null) {
                            val artworkModifier = Modifier.fillMaxSize()
                            artwork(item, artworkModifier)
                        } else {
                            TextPoster(item.title, Modifier.fillMaxSize())
                        }
                        // The phone readability scrim belongs to the clear
                        // artwork. Feather it with that artwork; leaving this
                        // full-size overlay outside the mask cuts a dark stripe
                        // exactly at the carousel's bottom edge.
                        if (featherIntoAmbient && layout.navigation != NavigationPlacement.Sidebar) {
                            Box(Modifier.fillMaxSize().background(readabilityScrim))
                        }
                    }
                }
                if (drawImageInPager && !featherIntoAmbient) {
                    // A scrim belongs to the image it shades, not to a navigation
                    // type. Tablet Drawer/Rail also use the window artwork;
                    // shading their otherwise empty pager painted a dark rectangle.
                    Box(Modifier.fillMaxSize().background(readabilityScrim))
                }
                // 仅 fallback 图片需要在 Pager 底部收进普通画布。窗口背景启用时
                // 再盖一层不透明背景，会在 Hero 底边把连续图片截断，下一像素又
                // 露出窗口背景，正是手机截图里那条割裂接缝。
                if (layout.family == MaFeiLayoutFamily.Phone && !windowBackdropActive) {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(96.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                    )
                }
                // 大屏（非 Bottom）的文字与指示器已上移到 HomeHeroForegroundOverlay
                //（scene 内 grid 之上的前景层）：pager 页视口被侧栏让位挤窄，文字
                // 一滑就出页界被裁、左边距与 grid 体系也对不上。手机端保留原结构。
                if (layout.navigation == NavigationPlacement.Bottom) {
                Column(
                    // 文字一律靠下对齐（指示器上方）。38dp 的
                    // bottom 同时给底部指示器（Sidebar 点状 20dp / 其余进度条
                    // 14dp）留出空间。
                    Modifier.align(Alignment.BottomStart)
                        .widthIn(max = if (layout.horizontalHero) 560.dp else 680.dp)
                        .padding(
                            start = when {
                                layout.navigation == NavigationPlacement.Sidebar -> 28.dp
                                layout.horizontalHero -> 36.dp
                                else -> 18.dp
                            },
                            end = 20.dp,
                            bottom = if (items.size > 1) 38.dp else 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        if (layout.navigation == NavigationPlacement.Sidebar || layout.horizontalHero) 8.dp else 6.dp,
                    ),
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = when {
                            layout.navigation == NavigationPlacement.Sidebar -> 30.sp
                            layout.horizontalHero -> 36.sp
                            else -> 28.sp
                        },
                        lineHeight = when {
                            layout.navigation == NavigationPlacement.Sidebar -> 38.sp
                            layout.horizontalHero -> 44.sp
                            else -> 34.sp
                        },
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    homeHeroSubtitle(item)?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = .82f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            overview,
                            color = Color.White.copy(alpha = .78f),
                            fontSize = if (layout.navigation == NavigationPlacement.Sidebar) 14.sp else 13.sp,
                            lineHeight = if (layout.navigation == NavigationPlacement.Sidebar) 21.sp else 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                } // END Bottom-only text/indicator
            }
        }
        // The phone indicator is an overlay of the pager, not part of an
        // individual page. Keeping it here prevents it from translating with
        // the artwork during a horizontal swipe; only its active progress is
        // driven by the shared autoplay clock.
        if (layout.navigation == NavigationPlacement.Bottom && items.size > 1) {
            val indicatorItemIndex = indicatorPage.value.mod(items.size)
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (layout.horizontalHero) 36.dp else 16.dp,
                        vertical = 14.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.indices.forEach { index ->
                    MaFeiProgressLine(
                        progress = when {
                            index < indicatorItemIndex -> 1f
                            index == indicatorItemIndex -> autoplayProgress.value
                            else -> 0f
                        },
                        modifier = Modifier.weight(1f).height(3.dp),
                        progressColor = Color.White,
                        trackColor = Color.White.copy(alpha = .22f),
                    )
                }
            }
        }
        if (refreshProgress > 0f) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = safeArea.top + 8.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = .30f)),
                contentAlignment = Alignment.Center,
            ) {
                MaFeiProgressRing(
                    progress = if (refreshing) null else refreshProgress,
                    tint = Color.White,
                    trackColor = Color.White.copy(alpha = .20f),
                )
            }
        }
    }
}

internal fun heroInitialPage(itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val middlePage = HeroVirtualPageCount / 2
    return middlePage - middlePage % itemCount
}
