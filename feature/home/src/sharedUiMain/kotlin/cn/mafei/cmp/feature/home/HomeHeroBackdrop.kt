package cn.mafei.cmp.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*

// The ambient is an environment layer driven by the selected hero item. The
// selection only changes when the pager settles, so a slow crossfade stands in
// for the continuous pager parallax the ambient used to draw.
private const val HeroAmbientFadeMillis = 360

// 背景清晰轮播图的羽化起点（占清晰层自身高度的比例）：80% 处才开始渐隐。
private const val HomeHeroFeatherStartFraction = 0.8f

// 清晰层在 hero 占位底边之下继续延伸的高度：图片清晰部分一直铺到「最近添加」
// 标题区域后面（内容浮在图片渐隐段之上），可见清晰区约达屏幕高度的 70%，
// 羽化带压缩在底部 —— 观感上图片清晰的部分占大头，同时内容仍完整可读。
private val HomeHeroClearExtraDp = 180.dp

// The wide-screen title/subtitle/overview block itself is roughly 150dp tall.
// Fade it before that complete block reaches the viewport edge; using only a
// 72dp tail left the final overview line floating at the top on short tablet
// feeds when the grid had already reached its maximum scroll position.
internal val HomeHeroForegroundHiddenTail = 160.dp

private val HomeHeroForegroundFadeDistance = 128.dp

// Once only a narrow strip of the clear hero remains, hand the whole viewport
// to the already-present ambient layer. Besides removing an otherwise visible
// sliver, alpha=0 lets the renderer skip the large DstIn offscreen layer while
// users scroll the shelves at the bottom of a short home feed.
private val HomeHeroClearHiddenTail = 96.dp

private val HomeHeroClearFadeDistance = 96.dp

// The legacy phone carousel only blended its final 96vp into the page canvas.
// Keep that physical depth on phone instead of reusing the wide-screen mask's
// 64%-100% range, which starts fading a 430dp phone hero far above its bottom.
internal val HomePhoneHeroFeatherHeight = 96.dp

// Wide layouts need a little more vertical breathing room than the phone
// composition: the artwork, copy block and progress indicator share one hero
// interval. Keep this adjustment local to the large-screen home hero so the
// phone's established 430dp rhythm does not change.
private val HomeLargeHeroHeightBonus = 36.dp

internal fun homeHeroHeight(layout: MaFeiLayout): Dp =
    if (layout.family == MaFeiLayoutFamily.Phone) {
        layout.heroHeightDp.dp
    } else {
        layout.heroHeightDp.dp + HomeLargeHeroHeightBonus
    }

/**
 * Keeps the window-level hero layers attached to their lazy-grid placeholder.
 *
 * Once item zero has scrolled out, LazyGrid removes it from [visibleItemsInfo].
 * Treating that missing entry as offset zero makes the detached hero foreground
 * jump back over the feed. Before the first layout the same entry is also
 * missing, so [firstVisibleItemIndex] distinguishes the two states.
 */
internal fun homeHeroTranslationY(
    heroOffsetY: Int?,
    firstVisibleItemIndex: Int,
    clearHeightPx: Float,
): Float {
    if (clearHeightPx <= 0f) return 0f
    val offset = when {
        heroOffsetY != null -> heroOffsetY.toFloat()
        firstVisibleItemIndex > 0 -> -clearHeightPx
        else -> 0f
    }
    return offset.coerceIn(-clearHeightPx, 0f)
}

/**
 * Fades the detached hero foreground before only a clipped text tail remains.
 *
 * On short wide-screen home feeds, the lazy grid can reach its maximum scroll
 * while the large hero item still has a few pixels in the viewport. In that
 * state item zero is technically visible, so disposal-based hiding is too
 * late and the final line of the overview sits over the first shelf. Keep the
 * artwork transition continuous, but make the text/indicator fully invisible
 * for the final [hiddenTailPx] and ease it out over [fadeDistancePx].
 */
internal fun homeHeroForegroundAlpha(
    heroOffsetY: Int?,
    firstVisibleItemIndex: Int,
    clearHeightPx: Float,
    hiddenTailPx: Float,
    fadeDistancePx: Float,
): Float {
    if (clearHeightPx <= 0f) return 1f
    val remainingHeight = (
        clearHeightPx + homeHeroTranslationY(heroOffsetY, firstVisibleItemIndex, clearHeightPx)
        ).coerceAtLeast(0f)
    if (fadeDistancePx <= 0f) return if (remainingHeight > hiddenTailPx) 1f else 0f
    return ((remainingHeight - hiddenTailPx) / fadeDistancePx).coerceIn(0f, 1f)
}

/** Only materialize adjacent full-screen pages while the horizontal pager moves. */
internal fun homeHeroPageRange(
    currentPage: Int,
    itemCount: Int,
    inMotion: Boolean,
): IntRange = if (itemCount > 1 && inMotion) {
    (currentPage - 1)..(currentPage + 1)
} else {
    currentPage..currentPage
}

/**
 * 窗口级轮播背景的状态。背景由 App.kt 的 shell 窗口画布绘制（fillMaxSize 天然
 * 全窗，与侧栏让位量、scene 视口完全无关），HomeScreen 负责把实时状态喂进来。
 */
class HomeHeroBackdropState {
    var items by mutableStateOf(emptyList<MediaItem>())
    var activeItem by mutableStateOf<MediaItem?>(null)
    var pagerState by mutableStateOf<PagerState?>(null)
    var gridState by mutableStateOf<LazyGridState?>(null)
    var blurRadius by mutableStateOf(44.dp)
    // hero 占位的固有高度（heroHeight + 下拉拉伸 extra）：窗口层自己用**窗口原始
    // 安全区**算清晰层高度 —— 不含顶部 Tab 的让位（Tab 是浮层，不该推动背景），
    // 侧栏收起/展开、Tab 条出现消失，背景都逐像素静止。
    var heroHeight by mutableStateOf(0.dp)
    var extraHeight by mutableStateOf(0.dp)
    // 宽屏的清晰 Hero 由窗口层绘制，以跨过侧栏让位区域；手机则由 Pager
    // 保持 430dp 的原始构图，并在底部羽化进这里只保留的模糊氛围层。
    var clearCarouselEnabled by mutableStateOf(true)
    var precomposeNeighbours by mutableStateOf(false)
    /** Release a disposed HomeScreen without clearing a newer screen attachment. */
    fun detach(pager: PagerState, grid: LazyGridState) {
        if (pagerState !== pager || gridState !== grid) return
        items = emptyList()
        activeItem = null
        pagerState = null
        gridState = null
        heroHeight = 0.dp
        extraHeight = 0.dp
        clearCarouselEnabled = true
        precomposeNeighbours = false
    }
}

/**
 * 首页轮播背景（窗口级）：
 * - 模糊层：当前 item 的全窗模糊版，Crossfade 在 settle 后切换 —— 全页氛围。
 * - 清晰层：从视口顶部铺到 hero 占位底边，底部 DstIn 羽化渐隐进模糊层；
 *   翻页跟手（currentPage±1 页序列 + 相对页差位移）；随列表滚动上移。
 * - scrim：全局明度渐变。
 *
 * 所有一层都是 fillMaxSize —— 背景属于窗口，不属于被侧栏挤开的内容，任何
 * 让位补偿（requiredWidth/负偏移）都不需要也不允许出现。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeHeroWindowBackdrop(
    state: HomeHeroBackdropState,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    ambientArtwork: (@Composable (MediaItem, Dp, Modifier) -> Unit)? = null,
) {
    val pageActive = LocalMaFeiPageActive.current
    val ambientState = remember { SeekableTransitionState(state.activeItem) }
    val ambientTransition = rememberTransition(ambientState, label = "mafei-hero-ambient")
    LaunchedEffect(state.activeItem, pageActive) {
        if (pageActive) ambientState.animateTo(state.activeItem)
        else ambientState.snapTo(state.activeItem)
    }
    val darkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brightnessFilter = remember(darkMode) {
        val brightness = if (darkMode) .54f else .82f
        ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) })
    }
    val scrimColor = if (darkMode) Color.Black else Color.White
    // 清晰层高度 = 窗口 safeTop + 12dp 顶距 + hero 固有高度 + 下拉拉伸 + 向下
    // 延伸量：清晰图从视口顶铺到「最近添加」标题区域之后，底部羽化带压缩在
    // 内容区下部 —— 图片清晰部分约占屏幕 70%，内容（海报/文字）浮在渐隐段之上。
    val windowSafeTop = LocalMaFeiSafeArea.current.top
    val clearHeight = windowSafeTop + 12.dp + state.heroHeight + state.extraHeight +
        HomeHeroClearExtraDp
    Box(modifier.fillMaxSize()) {
        // One blurred copy of the selected hero item, crossfaded whenever the
        // pager settles on a new one. Materializing three pager pages and
        // translating them by the pager's fractional offset moved the whole
        // background on every drag and every autoplay tick, which is exactly the
        // continuous parallax the UI research doc rules out. This is also
        // cheaper: one blurred layer at rest instead of three.
        // Crossfade the source images first, then blur the combined result once.
        // Blurring both outgoing and incoming full-window layers separately made
        // every autoplay transition pay for two 2800x1840 blur passes on tablet.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.18f
                    scaleY = 1.18f
                    colorFilter = brightnessFilter
                }
                .then(if (ambientArtwork == null) Modifier.blur(state.blurRadius) else Modifier),
        ) {
            ambientTransition.Crossfade(
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(durationMillis = HeroAmbientFadeMillis),
            ) { target ->
                if (target != null) {
                    if (ambientArtwork != null) {
                        ambientArtwork(target, state.blurRadius, Modifier.fillMaxSize())
                    } else if (artwork != null) {
                        artwork(target, Modifier.fillMaxSize())
                    }
                }
            }
        }
        // 清晰轮播图层：轮播图下沉到背景后的主画面。从视口顶部铺到 hero 占位
        // 底边（clearHeight），底部用 DstIn 真 alpha 羽化渐隐 —— 露出下层同一
        // 画面的模糊版，清晰→模糊无痕融合，不再有「带底边」。
        //
        // 翻页跟手：不再用 Crossfade 等 settle（那会「文字过去了背景才换」）。
        // 画出 currentPage±1 的页序列，每页横向位移 = (相对 current 的整页差 −
        // fraction) × 页宽，逐帧只更新 graphicsLayer、不 recompose。页宽 = 窗宽，
        // 静止时相邻页恰好停在窗外、当前页铺满整窗。位移必须用 Int 页差：
        // pager 的绝对页号从 heroInitialPage（约 2^30）起步，大数 Float 相减
        // 会丢精度，所有页的位移塌成 0（背景与文字错位的根源）。
        //
        // 滚动跟随：清晰层 translationY = hero item 的主轴 offset（未滚动为 0，
        // 滚动后为负），负负得正——图随列表一起滚出视口；模糊氛围层保持视口
        // 锚定，hero 滚走后内容继续在模糊背景上滚。
        //
        // 之前用「beforeContentPadding − offset.y」：LazyGrid 的 offset.y 参照系
        // 是 before padding 之后的 content start（未滚动 = 0），再减 beforeCP
        // 等于把整个 padding 区当成了滚动量——背景和前景文字被恒定上移
        // （桌面 12dp、平板 47dp），「最近添加」与指示器之间凭空多出这段距离。
        // offset.y 与 beforeCP 同帧同源，收起/展开让位动画中不会窜。
        val pager = state.pagerState
        val grid = state.gridState
        if (state.clearCarouselEnabled) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(clearHeight)
                    .clipToBounds()
                    .graphicsLayer {
                        // One offscreen layer applies the feather to the complete
                        // carousel. Applying DstIn to every visible page allocated
                        // three/four full-width render targets during every drag.
                        compositingStrategy = CompositingStrategy.Offscreen
                        if (grid != null) {
                            val heroInfo = grid.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.key == HomeHeroKey }
                            val heroOffsetY = heroInfo?.offset?.y
                            val clearHeightPx = clearHeight.toPx()
                            translationY = homeHeroTranslationY(
                                heroOffsetY = heroOffsetY,
                                firstVisibleItemIndex = grid.firstVisibleItemIndex,
                                clearHeightPx = clearHeightPx,
                            )
                            alpha = homeHeroForegroundAlpha(
                                heroOffsetY = heroOffsetY,
                                firstVisibleItemIndex = grid.firstVisibleItemIndex,
                                clearHeightPx = clearHeightPx,
                                hiddenTailPx = HomeHeroClearHiddenTail.toPx(),
                                fadeDistancePx = HomeHeroClearFadeDistance.toPx(),
                            )
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White,
                                HomeHeroFeatherStartFraction to Color.White,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                if (artwork != null && state.items.isNotEmpty() && pager != null) {
                    val current = pager.currentPage
                    val pageRange = homeHeroPageRange(
                        currentPage = current,
                        itemCount = state.items.size,
                        inMotion = pager.isScrollInProgress || state.precomposeNeighbours,
                    )
                    for (page in pageRange) {
                        key(page) {
                            val pageItem = state.items[page.mod(state.items.size)]
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val rel = (page - pager.currentPage) -
                                            pager.currentPageOffsetFraction
                                        translationX = rel * size.width
                                    },
                            ) {
                                artwork(
                                    pageItem,
                                    Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to scrimColor.copy(alpha = .05f),
                    .48f to scrimColor.copy(alpha = if (darkMode) .16f else .14f),
                    1f to scrimColor.copy(alpha = if (darkMode) .42f else .46f),
                ),
            ),
        )
    }
}

/**
 * 轮播前景浮层（文字 + 指示器）：大屏端文字不再住在 pager 页里 —— pager 页的
 * 视口被侧栏让位挤窄，文字一滑就出页界被裁，左边距也和 grid 的 pagePadding 体系
 * 对不上。这里在 scene 内、grid 之上铺一层与窗口背景同构的页序列：
 * - 横向位移公式与背景清晰层完全一致（Int 页差 − fraction，乘窗宽）—— 文字和
 *   背景同速滑动，且没有页界裁剪，可以一路滑到窗口左缘；
 * - start 边距取 grid 的 pagePadding（32dp），与「最近添加」等模块左对齐；
 * - 纵向随 hero 占位滚动跟随（与背景同源参数），文字始终钉在占位底边上方。
 * 手机端（Bottom）不使用：文字仍住在 pager 页里。
 */
@Composable
internal fun HomeHeroForegroundOverlay(
    items: List<MediaItem>,
    pagerState: PagerState,
    autoplayProgress: Animatable<Float, AnimationVector1D>,
    indicatorPage: State<Int>,
    layout: MaFeiLayout,
    gridState: LazyGridState,
    topPadding: Dp,
    contentStartPadding: Dp,
    contentEndPadding: Dp,
    heroHeight: Dp,
    extraHeight: Dp,
    windowWidth: Dp,
    precomposeNeighbours: Boolean,
) {
    // 前景的纵向基准 = contentPadding.top（safeTop + 12）+ hero 占位固有高度：
    // 与 grid 的占位、背景的 clearHeight **同源同值**（都含 safeTop）—— 三者
    // 底边严格对齐。之前前景用 12+heroH（不含 safeTop），平板的状态栏高度
    // （35dp）变成了指示器与「最近添加」之间的一段凭空距离（桌面 safeTop=0
    // 所以从未暴露）。滚动跟随直接取 hero item 的 offset.y（未滚动 = 0，
    // 滚动后为负），与背景同款，不会窜。
    val fullClear = topPadding + heroHeight + extraHeight
    Box(
        Modifier
            // Only the hero interval contains foreground content. Keeping this
            // as a full-window layer made every vertical scroll composite an
            // otherwise empty lower half, and alpha < 1 forced that oversized
            // layer offscreen on CPF/OHOS.
            .fillMaxWidth()
            .height(fullClear)
            .graphicsLayer {
                val heroInfo = gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == HomeHeroKey }
                val heroOffsetY = heroInfo?.offset?.y
                val clearHeightPx = fullClear.toPx()
                translationY = homeHeroTranslationY(
                    heroOffsetY = heroOffsetY,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    clearHeightPx = clearHeightPx,
                )
                alpha = homeHeroForegroundAlpha(
                    heroOffsetY = heroOffsetY,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    clearHeightPx = clearHeightPx,
                    hiddenTailPx = HomeHeroForegroundHiddenTail.toPx(),
                    fadeDistancePx = HomeHeroForegroundFadeDistance.toPx(),
                )
            },
    ) {
        // 文字/指示器钉在占位底边（不是视口底）：这层 Box 的高度就是占位的
        // 纵向区间，BottomStart 对齐的是占位底而不是整个视口的底。
        val current = pagerState.currentPage
        val activeItemIndex = indicatorPage.value.mod(items.size)
        val pageRange = homeHeroPageRange(
            currentPage = current,
            itemCount = items.size,
            inMotion = pagerState.isScrollInProgress || precomposeNeighbours,
        )
        for (page in pageRange) {
            key(page) {
                val pageItem = items[page.mod(items.size)]
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 与背景清晰层同款：Int 页差 − fraction，避免 2^30 页号
                            // 的 Float 精度丢失。
                            val rel = (page - pagerState.currentPage) -
                                pagerState.currentPageOffsetFraction
                            translationX = rel * windowWidth.toPx()
                        },
                ) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .widthIn(max = if (layout.horizontalHero) 560.dp else 680.dp)
                            .padding(
                                // 32dp = grid contentPadding.start（pagePadding）：
                                // 与「最近添加」等模块左对齐。
                                start = if (layout.navigation == NavigationPlacement.Sidebar) {
                                    32.dp
                                } else {
                                    36.dp
                                },
                                end = 20.dp,
                                bottom = if (items.size > 1) 38.dp else 24.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(
                            if (layout.navigation == NavigationPlacement.Sidebar || layout.horizontalHero) 8.dp else 6.dp,
                        ),
                    ) {
                        Text(
                            pageItem.title,
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
                        homeHeroSubtitle(pageItem)?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Text(
                                subtitle,
                                color = Color.White.copy(alpha = .82f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        pageItem.overview?.takeIf { it.isNotBlank() }?.let { overview ->
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
                }
            }
        }
        // 指示器固定一份（不进页序列）：轮播翻页时它钉在轮播底部不动，只有
        // 选中态随 activeItemIndex 变化 —— 常规轮播行为。之前放进页序列导致
        // 翻页时指示器跟着页面一起滑走。
        if (items.size > 1) {
            if (layout.navigation == NavigationPlacement.Sidebar) {
                val visibleCount = minOf(items.size, 7)
                val visibleStart = (activeItemIndex - visibleCount / 2)
                    .coerceIn(0, (items.size - visibleCount).coerceAtLeast(0))
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    (visibleStart until visibleStart + visibleCount).forEach { index ->
                        Box(
                            Modifier.size(if (index == activeItemIndex) 7.dp else 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index == activeItemIndex) Color.White
                                    else Color.White.copy(alpha = .42f),
                                ),
                        )
                    }
                }
            } else {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = contentStartPadding,
                            end = contentEndPadding,
                            top = 14.dp,
                            bottom = 14.dp,
                        )
                        .testTag("homeHeroIndicator"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items.indices.forEach { index ->
                        MaFeiProgressLine(
                            progress = when {
                                index < activeItemIndex -> 1f
                                index == activeItemIndex -> autoplayProgress.value
                                else -> 0f
                            },
                            modifier = Modifier.weight(1f).height(3.dp),
                            progressColor = Color.White,
                            trackColor = Color.White.copy(alpha = .22f),
                        )
                    }
                }
            }
        }
    }
}
