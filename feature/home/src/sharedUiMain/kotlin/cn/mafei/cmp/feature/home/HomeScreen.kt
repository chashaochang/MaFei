package cn.mafei.cmp.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidFloatingTabs
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

private const val RecentFeedKey = "__home_recent__"
private const val HeroAutoplayMillis = 6_500
private const val HeroInitialPrefetchDelayMillis = 800L
private const val HeroSnapDampingRatio = .92f
private const val HeroSnapStiffness = 420f
private const val HeroSnapThreshold = .58f
// The ambient is an environment layer driven by the selected hero item. The
// selection only changes when the pager settles, so a slow crossfade stands in
// for the continuous pager parallax the ambient used to draw.
private const val HeroAmbientFadeMillis = 360
private const val HeroVirtualPageCount = Int.MAX_VALUE
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
private val HomeHeroForegroundHiddenTail = 160.dp
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
private val HomePhoneHeroFeatherHeight = 96.dp
// Wide layouts need a little more vertical breathing room than the phone
// composition: the artwork, copy block and progress indicator share one hero
// interval. Keep this adjustment local to the large-screen home hero so the
// phone's established 430dp rhythm does not change.
private val HomeLargeHeroHeightBonus = 36.dp
private const val HomeHeroKey = "home-recommendation-hero"
private const val LatestFeedAnchorKey = "home-latest-feed-anchor"
private val HomeHeroRefreshThreshold = 64.dp
private val HomeHeroMaxStretch = 160.dp
private const val HomeHeroStretchResistance = 3f
private val HomeFloatingTabsInlineInset = 8.dp
private val HomeFloatingTabsSlotHeight = 56.dp
private val HomeFloatingActionSize = 40.dp
private val HomeFloatingActionGap = 8.dp
private const val HomeFeedCardChromeHeightDp = 44f

private fun homeHeroHeight(layout: MaFeiLayout): Dp =
    if (layout.family == MaFeiLayoutFamily.Phone) {
        layout.heroHeightDp.dp
    } else {
        layout.heroHeightDp.dp + HomeLargeHeroHeightBonus
    }

internal fun homeFloatingTabsEndInset(
    pinned: Boolean,
    hasSearch: Boolean,
    showRefresh: Boolean,
): Dp = when {
    !pinned || !hasSearch -> 0.dp
    showRefresh -> 96.dp
    else -> 48.dp
}

internal data class HomeFloatingTabsPlacement(
    val visible: Boolean,
    val pinned: Boolean,
    val topPx: Int,
    val anchorIndex: Int?,
)

internal fun resolveHomeFloatingTabsPlacement(
    anchorOffsetY: Int?,
    anchorIndex: Int?,
    inlineInsetPx: Int,
    stickyTopPx: Int,
): HomeFloatingTabsPlacement {
    if (anchorOffsetY == null || anchorIndex == null) {
        return HomeFloatingTabsPlacement(
            visible = false,
            pinned = false,
            topPx = stickyTopPx,
            anchorIndex = null,
        )
    }
    val naturalTop = anchorOffsetY + inlineInsetPx
    val pinned = naturalTop <= stickyTopPx
    return HomeFloatingTabsPlacement(
        visible = true,
        pinned = pinned,
        topPx = maxOf(stickyTopPx, naturalTop),
        anchorIndex = anchorIndex,
    )
}

internal fun homeLatestFeedFillHeightDp(
    minimumHeightDp: Float,
    contentWidthDp: Float,
    columns: Int,
    columnGapDp: Float,
    rowGapDp: Float,
    itemCount: Int,
    emptyContentHeightDp: Float,
    trailingContentHeightDp: Float = 0f,
): Float {
    val safeColumns = columns.coerceAtLeast(1)
    val rows = if (itemCount <= 0) 0 else (itemCount + safeColumns - 1) / safeColumns
    val availableWidth = (
        contentWidthDp - columnGapDp.coerceAtLeast(0f) * (safeColumns - 1)
        ).coerceAtLeast(1f)
    val cardWidth = availableWidth / safeColumns
    val estimatedContentHeight = if (rows == 0) {
        emptyContentHeightDp.coerceAtLeast(0f)
    } else {
        rows * (cardWidth * 1.5f + HomeFeedCardChromeHeightDp) +
            (rows - 1) * rowGapDp.coerceAtLeast(0f)
    } + trailingContentHeightDp.coerceAtLeast(0f)
    return (minimumHeightDp - estimatedContentHeight).coerceAtLeast(0f)
}

private enum class BrowseSortField(val label: String) {
    DateAdded("添加日期"),
    Title("名称"),
    Runtime("时长"),
}

/** Stateless responsive browser. Session, insets and destination state stay in the shell. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    collections: List<MediaCollection>,
    items: List<MediaItem>,
    homeRecommendations: List<MediaItem>? = null,
    homeResume: List<MediaItem>? = null,
    homeNextUp: List<MediaItem>? = null,
    homeLatest: List<MediaItem>? = null,
    homeLatestByCollection: Map<CollectionId, List<MediaItem>> = emptyMap(),
    homeLatestLoading: Set<CollectionId> = emptySet(),
    homeLatestErrors: Map<CollectionId, String> = emptyMap(),
    selectedCollection: CollectionId?,
    loading: Boolean,
    homeLoading: Boolean = loading,
    refreshing: Boolean = false,
    loadingMore: Boolean = false,
    error: String?,
    stale: Boolean,
    canLoadMore: Boolean,
    overview: Boolean,
    layout: MaFeiLayout,
    onCollection: (MediaCollection) -> Unit,
    onItem: (MediaItem) -> Unit,
    onHeroItem: (MediaItem) -> Unit = onItem,
    onPlay: ((MediaItem) -> Unit)? = null,
    onToggleWatched: ((MediaItem) -> Unit)? = null,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLatest: () -> Unit = onOpenLibrary,
    showHomeHero: Boolean = true,
    onSearch: (() -> Unit)? = null,
    onHeroItemChanged: (MediaItem?) -> Unit = {},
    onOpenCollectionLatest: (MediaCollection) -> Unit = onCollection,
    onBack: (() -> Unit)? = null,
    heroAutoplay: Boolean = true,
    bottomOverlayInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)? = null,
    backdropArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = artwork,
    shelfArtwork: (@Composable (MediaItem, Modifier) -> Unit)? = backdropArtwork,
    // 预载轮播 item 的横版图：背景的翻页跟手序列与模糊层 Crossfade 都依赖
    // 「图已在缓存」，否则翻页瞬间两层同时透明，露出 shell 背景色（黑带）。
    prefetchBackdrop: (suspend (MediaItem) -> Unit)? = null,
    // 首页轮播背景的窗口级状态：背景画在 shell 的窗口画布上（fillMaxSize 天然
    // 全窗，与侧栏让位无关），HomeScreen 只负责把状态喂给它。
    heroBackdropState: HomeHeroBackdropState? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val navigationChrome = LocalMaFeiNavigationChrome.current
    val layout = navigationChrome.effectiveLayout ?: layout
    val selected = collections.firstOrNull { it.id == selectedCollection }
    // Provider feeds can contain the same media more than once (the Jellyfin
    // test server currently does). Lazy layouts require unique keys, and the
    // home page should not render duplicate cards anyway, so normalize every
    // independently rendered feed at the UI boundary.
    val recentItems = remember(homeLatest, items) {
        if (homeLatest != null) {
            // Dedicated provider feeds already carry the old home-page order
            // (Jellyfin is interleaved across libraries). Resorting here made
            // CMP show different content even when the adapter was correct.
            homeLatest.distinctByMediaId()
        } else {
            items.distinctByMediaId()
                .sortedWith(compareByDescending<MediaItem> { it.dateAddedEpochMs ?: Long.MIN_VALUE }
                    .thenBy { it.title })
        }
    }
    val heroCandidates = remember(homeRecommendations, recentItems) {
        (homeRecommendations ?: recentItems).distinctByMediaId()
    }
    // A media-first hero cannot be led by an item that has no artwork at all.
    // Keep the provider order, but exclude image-less entries when at least one
    // usable candidate exists; the latest grid still shows every item.
    val heroItems = remember(heroCandidates) {
        heroCandidates.filter { item ->
            item.images.any { image ->
                image.kind == ImageKind.Backdrop || image.kind == ImageKind.Thumb || image.kind == ImageKind.Primary
            }
        }.ifEmpty { heroCandidates }.take(6)
    }
    val progressItems = remember(homeResume, items) {
        (homeResume ?: items.filter { it.userData.hasProgress && !it.userData.isPlayed }.take(8))
            .distinctByMediaId()
    }
    val nextUpItems = remember(homeNextUp, items) {
        (homeNextUp ?: items.filter {
            it.mediaType == MediaType.Episode && !it.userData.isPlayed && !it.userData.hasProgress
        }.take(8)).distinctByMediaId()
    }
    var selectedFeedKey by rememberSaveable { mutableStateOf(RecentFeedKey) }
    // LazyGrid retains key callbacks alongside an index map. Freeze the feed
    // identity with this composition's items so switching tabs cannot change
    // the keys of an old interval before that interval is replaced.
    val renderedFeedKey = selectedFeedKey
    var browseSortIndex by rememberSaveable { mutableStateOf(0) }
    var browseDescending by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(collections) {
        // A refresh can briefly publish an empty collection list. Keep the
        // current feed through that transient state instead of visibly jumping
        // back to "最近添加"; reset only after a real collection snapshot exists.
        if (
            collections.isNotEmpty() && selectedFeedKey != RecentFeedKey &&
            collections.none { it.id.value == selectedFeedKey }
        ) {
            selectedFeedKey = RecentFeedKey
        }
    }
    val selectedFeedCollection = collections.firstOrNull { it.id.value == selectedFeedKey }
    // Sidebar/Rail layouts do not render the phone Floating Tabs. Their shelf
    // is always the global home feed, even if a previous phone session left a
    // library tab selected in rememberSaveable state.
    val wideHomeShelf = layout.navigation == NavigationPlacement.Sidebar ||
        layout.navigation == NavigationPlacement.Rail
    val latestItems = remember(
        selectedFeedKey,
        selectedFeedCollection,
        recentItems,
        homeLatestByCollection,
        wideHomeShelf,
    ) {
        if (selectedFeedKey == RecentFeedKey || wideHomeShelf) recentItems
        else selectedFeedCollection?.let { homeLatestByCollection[it.id] }.orEmpty().distinctByMediaId()
    }
    // Selecting an uncached library changes [selectedFeedKey] in the same
    // frame, while the coroutine that publishes homeLatestLoading starts on
    // the next dispatcher turn. Treat the missing cache entry as loading
    // immediately so the grid never collapses for one frame and loses its
    // sticky anchor.
    val latestFeedLoading = selectedFeedCollection?.let { collection ->
        collection.id in homeLatestLoading || (
            collection.id !in homeLatestByCollection && collection.id !in homeLatestErrors
            )
    } == true
    val latestFeedError = selectedFeedCollection?.id?.let(homeLatestErrors::get)
    val browseSort = BrowseSortField.entries[browseSortIndex.coerceIn(BrowseSortField.entries.indices)]
    val displayed = remember(overview, latestItems, items, browseSort, browseDescending) {
        if (overview) latestItems else {
            sortBrowseItems(items.distinctByMediaId(), browseSort, browseDescending)
        }
    }
    val showFeedPlaceholders = ((if (overview) homeLoading else loading) || latestFeedLoading) && displayed.isEmpty()
    // Grid density follows the usable content width. Collapsing/expanding the
    // sidebar only changes the available width; each window size therefore
    // gets an appropriate number of posters instead of a fixed five-column
    // desktop grid with oversized cards.
    val latestColumns = legacyMediaColumns(layout.gridWidthDp)
    // The current HarmonyOS home uses the recommendation hero on phone as well
    // as on wide layouts.  Do not replace it with a generic app header.
    val showHero = overview && showHomeHero && heroItems.isNotEmpty()
    // Keep the first lazy item stable while the provider loads. Otherwise the
    // tabs become the initial anchor and stay pinned when the real hero arrives.
    val showHeroSlot = overview && showHomeHero && (showHero || homeLoading)
    val overviewHasContent = heroItems.isNotEmpty() || progressItems.isNotEmpty() || nextUpItems.isNotEmpty() ||
        recentItems.isNotEmpty() || collections.isNotEmpty()
    val showRefreshAction = refreshing && overviewHasContent
    val showStatus = stale || error != null ||
        if (overview) !overviewHasContent else collections.isEmpty() || items.isEmpty()
    val latestGap = if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp
    // Home library tabs have their own cached request stream. The ordinary
    // media-library cursor belongs to a different screen and must never be
    // consumed merely because the home grid reached its tail.
    val paginationEnabled = !overview && canLoadMore && !loading && !refreshing && !loadingMore
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    var initialHeroAlignmentPending by remember(gridState) {
        mutableStateOf(
            shouldAlignHomeHeroInitially(
                overview = overview,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            ),
        )
    }
    LaunchedEffect(overview, showHomeHero, showHero) {
        // Align once immediately and once when the first real hero is ready.
        // The latter closes the one-frame anchor race without interrupting
        // later provider refreshes or a user's established scroll position.
        if (overview && initialHeroAlignmentPending) {
            gridState.scrollToItem(0)
            if (showHero || !showHomeHero) initialHeroAlignmentPending = false
        }
    }
    LaunchedEffect(gridState, paginationEnabled, displayed.size) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisible, totalCount) ->
            if (paginationEnabled && totalCount > 0 && lastVisible >= totalCount - 5) {
                currentLoadMore()
            }
        }
    }
    // 首页浮层必须使用页面自己的记录层。HomeScreen 本身位于 shell 的捕获子树中，
    // 若在这里消费 LocalShellBackdrop，就会把“读取 shell layer”的节点重新录进同一个
    // shell layer，Skiko 绘制时形成 RenderNode -> SkDrawable 的无限递归并耗尽 native 栈。
    // 下方滚动内容负责记录，Floating Tabs / 搜索按钮 / ActionBar 是它的兄弟消费者。
    val floatingTabsContentBackdrop = rememberGlassBackdrop()
    val windowBackgroundBackdrop = LocalShellBackgroundBackdrop.current
    // Refraction must sample an opaque scene, not isolated text on transparency:
    // otherwise shifted/blurred glyphs let the original sharp text show through.
    val floatingTabsBackdrop = if (windowBackgroundBackdrop != null) {
        rememberCombinedGlassBackdrop(windowBackgroundBackdrop, floatingTabsContentBackdrop)
    } else {
        floatingTabsContentBackdrop
    }
    val floatingTabLabels = remember(collections) {
        listOf("最近添加") + collections.map { it.name }
    }
    val selectedFloatingTab = if (selectedFeedKey == RecentFeedKey) 0 else {
        collections.indexOfFirst { it.id.value == selectedFeedKey }.let { if (it < 0) 0 else it + 1 }
    }
    val density = LocalDensity.current
    val stretchHeroRefresh = overview && showHero && layout.family == MaFeiLayoutFamily.Phone
    val heroRefreshThresholdPx = with(density) { HomeHeroRefreshThreshold.toPx() }
    val heroMaxStretchPx = with(density) { HomeHeroMaxStretch.toPx() }
    val heroStretchPx = remember { mutableFloatStateOf(0f) }
    val heroSettleJob = remember { mutableStateOf<Job?>(null) }
    val feedSelectionJob = remember { mutableStateOf<Job?>(null) }
    val heroRefreshScope = rememberCoroutineScope()
    val currentRetry = rememberUpdatedState(onRetry)
    val currentRefreshing = rememberUpdatedState(refreshing)
    val heroStretchConnection = remember(
        gridState,
        stretchHeroRefresh,
        heroRefreshThresholdPx,
        heroMaxStretchPx,
    ) {
        object : NestedScrollConnection {
            private fun atAbsoluteTop(): Boolean = gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset == 0

            private fun consumeHeroDelta(deltaY: Float, mayStartStretch: Boolean): Float {
                if (deltaY > 0f && (mayStartStretch || heroStretchPx.floatValue > 0f)) {
                    heroSettleJob.value?.cancel()
                    heroStretchPx.floatValue = (
                        heroStretchPx.floatValue + deltaY / HomeHeroStretchResistance
                        ).coerceAtMost(heroMaxStretchPx)
                    // Consume even after reaching the visual cap so the whole
                    // lazy grid never moves down and exposes the window canvas.
                    return deltaY
                }
                if (deltaY < 0f && heroStretchPx.floatValue > 0f) {
                    heroSettleJob.value?.cancel()
                    val previous = heroStretchPx.floatValue
                    val next = (previous + deltaY / HomeHeroStretchResistance).coerceAtLeast(0f)
                    heroStretchPx.floatValue = next
                    return (next - previous) * HomeHeroStretchResistance
                }
                return 0f
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!stretchHeroRefresh || source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, consumeHeroDelta(available.y, mayStartStretch = atAbsoluteTop()))
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!stretchHeroRefresh || source != NestedScrollSource.UserInput) return Offset.Zero
                // A single fast drag can reach index 0 inside the child scroll.
                // Its unconsumed remainder arrives here, so absorb it into the
                // hero during the same gesture instead of waiting for a second pull.
                return Offset(0f, consumeHeroDelta(available.y, mayStartStretch = atAbsoluteTop()))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val start = heroStretchPx.floatValue
                if (!stretchHeroRefresh || start <= 0f) return Velocity.Zero
                val requestRefresh = start >= heroRefreshThresholdPx && !currentRefreshing.value
                heroSettleJob.value?.cancel()
                heroSettleJob.value = heroRefreshScope.launch {
                    if (requestRefresh) currentRetry.value()
                    Animatable(start).animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = .78f, stiffness = 288f),
                    ) {
                        heroStretchPx.floatValue = value
                    }
                }
                return Velocity(0f, available.y)
            }
        }
    }
    LaunchedEffect(stretchHeroRefresh) {
        if (!stretchHeroRefresh) heroStretchPx.floatValue = 0f
    }
    val heroExtraHeight = with(density) { heroStretchPx.floatValue.toDp() }
    val heroRefreshProgress = (heroStretchPx.floatValue / heroRefreshThresholdPx).coerceIn(0f, 1f)
    val floatingTabsInsetPx = with(density) { HomeFloatingTabsInlineInset.roundToPx() }
    val floatingTabsStickyTopPx = with(density) {
        when (layout.navigation) {
            NavigationPlacement.Drawer -> 88.dp
            NavigationPlacement.Sidebar -> safeArea.top
            NavigationPlacement.Rail -> safeArea.top
            NavigationPlacement.Bottom -> safeArea.top + 12.dp
        }.roundToPx()
    }
    // 浮动 feed 切换条（最近添加 / 各媒体库 Tab）是手机与中屏的信息结构。大屏的
    // 常驻导航形态（Sidebar / Rail）改用「最近添加」横向 shelf + 查看更多；侧栏
    // 收起只是让内容区变宽，不改变信息结构 —— Pad 上收起侧栏不允许退回小屏布局。
    val showFloatingTabs = when (layout.navigation) {
        NavigationPlacement.Drawer, NavigationPlacement.Bottom -> true
        NavigationPlacement.Rail, NavigationPlacement.Sidebar -> false
    }
    // The real sticky header remains in the lazy grid, so its measured item is
    // the sole source of truth for visibility, pinning and position. The glass
    // surface is only a sibling renderer outside the captured scene; it no
    // longer tries to reconstruct sticky state from a separately maintained
    // item index.
    val floatingTabsPlacement = remember(
        gridState,
        floatingTabsInsetPx,
        floatingTabsStickyTopPx,
    ) {
        derivedStateOf {
            val anchor = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == LatestFeedAnchorKey }
            resolveHomeFloatingTabsPlacement(
                anchorOffsetY = anchor?.offset?.y,
                anchorIndex = anchor?.index,
                inlineInsetPx = floatingTabsInsetPx,
                stickyTopPx = floatingTabsStickyTopPx,
            )
        }
    }
    val floatingTabsVisible by remember(floatingTabsPlacement) {
        derivedStateOf { floatingTabsPlacement.value.visible }
    }
    val floatingTabsPinned by remember(floatingTabsPlacement) {
        derivedStateOf { floatingTabsPlacement.value.pinned }
    }
    val gridScrolling by remember(gridState) { derivedStateOf { gridState.isScrollInProgress } }
    val heroAutoplayMinimumVisiblePx = with(density) { HomeHeroForegroundHiddenTail.roundToPx() }
    val heroVisibleForAutoplay by remember(gridState, heroAutoplayMinimumVisiblePx) {
        derivedStateOf {
            val heroInfo = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == HomeHeroKey }
            homeHeroAutoplayVisible(
                heroOffsetY = heroInfo?.offset?.y,
                heroHeightPx = heroInfo?.size?.height,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                minimumVisiblePx = heroAutoplayMinimumVisiblePx,
            )
        }
    }
    var activeHeroItem by remember(heroItems) { mutableStateOf(heroItems.firstOrNull()) }
    val heroItemKeys = remember(heroItems) { heroItems.map { it.id.value } }
    // One clock is shared by the phone pager indicator and the wide-screen
    // foreground indicator. Keeping it above both render paths prevents one
    // of them from falling back to a completed bar while the other is moving.
    val heroAutoplayProgress = remember(heroItemKeys) { Animatable(0f) }
    val heroPagerState = key(heroItemKeys) {
        rememberPagerState(
            initialPage = if (heroItems.isEmpty()) 0 else heroInitialPage(heroItems.size),
            pageCount = {
                if (heroItems.size > 1) HeroVirtualPageCount else heroItems.size.coerceAtLeast(1)
            },
        )
    }
    // The indicator commits to a new page only after the pager settles. This
    // keeps the old bar full during the slide and swaps it to 0% atomically
    // with the newly visible artwork instead of clearing it in advance.
    val heroIndicatorPage = remember(heroItemKeys) {
        mutableStateOf(heroPagerState.settledPage)
    }
    // Only keep the settled page and its two neighbours warm. Warming every
    // remaining 960px hero after a fixed delay still competed with the first
    // user scroll on slower devices. A settled-page change cancels stale work
    // and advances this three-image window instead.
    LaunchedEffect(heroItems, prefetchBackdrop, heroPagerState, showHomeHero) {
        val prefetch = prefetchBackdrop ?: return@LaunchedEffect
        if (!showHomeHero || heroItems.isEmpty()) return@LaunchedEffect
        var initialEmission = true
        snapshotFlow { heroPagerState.settledPage }
            .distinctUntilChanged()
            .collectLatest { settledPage ->
                // The visible page already loads itself. Starting two neighbour
                // requests and decodes in the first composition competes with
                // the first home frame on slower Android devices. Warm them
                // after the initial scene has settled; a user page change
                // cancels this delay and warms the new neighbourhood directly.
                if (initialEmission) {
                    initialEmission = false
                    delay(HeroInitialPrefetchDelayMillis)
                }
                homeHeroPrefetchOrder(heroItems.size, settledPage).forEach { index ->
                    prefetch(heroItems[index])
                }
            }
    }
    DisposableEffect(heroBackdropState, heroPagerState, gridState) {
        onDispose {
            heroBackdropState?.detach(heroPagerState, gridState)
        }
    }
    // 同步窗口级轮播背景的状态（背景画在 shell 窗口画布，不在 scene 里）。
    LaunchedEffect(heroItems, heroBackdropState) {
        heroBackdropState?.items = heroItems
    }
    LaunchedEffect(heroBackdropState, heroPagerState, gridState, layout) {
        heroBackdropState?.let { state ->
            state.pagerState = heroPagerState
            state.gridState = gridState
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
        heroBackdropState?.extraHeight = heroExtraHeight
    }
    LaunchedEffect(heroBackdropState, heroVisibleForAutoplay) {
        heroBackdropState?.precomposeNeighbours = heroVisibleForAutoplay
    }
    LaunchedEffect(activeHeroItem, heroBackdropState) {
        heroBackdropState?.activeItem = activeHeroItem
    }
    // The shell already paints the full-window canvas. Painting another opaque
    // canvas inside the content pane creates a visible seam behind the sidebar.
    BoxWithConstraints(modifier.fillMaxSize()) {
      // The lazy grid's contentPadding is the single leading inset for every home
      // item. Re-applying a leading inset inside items and their rows doubled the
      // left gutter (64dp against a 32dp trailing gutter), narrowed the content
      // column by 32dp, clipped horizontal shelves at that column edge and left a
      // bare 32dp strip on the right of the window. Items and rows therefore must
      // not add a second leading inset; the grid padding already carries it.
      //
      // 页面节奏恒定：侧栏让位（240dp）由外壳的 padding 动画统一负责，内容自己的
      // leading 边距始终是 pagePadding —— 展开与收起之间边距不再塌陷。
      val sceneControlLeading = 0.dp
      val gridStartPadding = if (layout.navigation == NavigationPlacement.Bottom) {
          safeArea.start + layout.pagePaddingDp.dp
      } else {
          layout.pagePaddingDp.dp
      }
      val gridEndPadding = layout.pagePaddingDp.dp + safeArea.end
      val gridBottomPadding = (if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 48.dp) +
          safeArea.bottom + bottomOverlayInset
      val gridVerticalGap = if (layout.family == MaFeiLayoutFamily.Phone) 20.dp else 16.dp
      // Match the legacy HomeLatestFeedContent.constraintSize(minHeight): a
      // short/loading/empty feed must still be tall enough for its sticky
      // header to remain at the top. Without this tail the lazy grid clamps to
      // its new, shorter maximum scroll range immediately after a tab switch.
      val latestFeedMinimumHeight = maxOf(
          360.dp,
          maxHeight - (safeArea.top + HomeActionBarHeight) - gridBottomPadding,
      )
      val gridContentWidth = (
          minOf(maxWidth, layout.contentMaxWidthDp.dp) - gridStartPadding - gridEndPadding
          ).coerceAtLeast(1.dp)
      val feedVisualItemCount = if (showFeedPlaceholders) HomeLoadingPlaceholderCount else displayed.size
      val latestFeedFillHeight = if (overview && showFloatingTabs) {
          homeLatestFeedFillHeightDp(
              minimumHeightDp = latestFeedMinimumHeight.value,
              contentWidthDp = gridContentWidth.value,
              columns = latestColumns,
              columnGapDp = latestGap.value,
              rowGapDp = gridVerticalGap.value,
              itemCount = feedVisualItemCount,
              emptyContentHeightDp = if (latestFeedError != null) 120f else 96f,
              trailingContentHeightDp = if (loadingMore) 64f + gridVerticalGap.value else 0f,
          ).dp
      } else {
          0.dp
      }
      // 首页轮播背景已提升到 shell 的窗口画布（heroBackdropState 同步状态，
      // App.kt 的 background 里绘制）—— 它天然全窗、与侧栏让位无关，scene 里
      // 只保留内容、手势和文字浮层。
      Box(
          Modifier.fillMaxSize().captureGlassBackdrop(floatingTabsContentBackdrop)
              .then(
                  if (windowBackgroundBackdrop == null) Modifier.background(MaterialTheme.colorScheme.background)
                  else Modifier,
              ),
      ) {
      HomeRefreshContainer(
        stretchHero = stretchHeroRefresh,
        refreshing = refreshing,
        onRefresh = onRetry,
        heroStretchConnection = heroStretchConnection,
        modifier = Modifier.fillMaxSize(),
      ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (overview) latestColumns else legacyMediaColumns(layout.gridWidthDp)),
            state = gridState,
            modifier = Modifier.fillMaxHeight().widthIn(max = layout.contentMaxWidthDp.dp).fillMaxWidth(),
            overscrollEffect = null,
            contentPadding = PaddingValues(
                start = gridStartPadding,
                end = gridEndPadding,
                top = when {
                    overview && layout.family == MaFeiLayoutFamily.Phone && showHeroSlot -> 0.dp
                    overview && layout.family == MaFeiLayoutFamily.Phone -> safeArea.top + 12.dp
                    // Sidebar/Rail layouts share the same top margin as the
                    // sidebar panel so the hero and sidebar are vertically
                    // aligned. The collapsed top tabs are a floating overlay,
                    // not a content row; reserving their 52dp here makes the
                    // hero and "继续观看" jump down when the sidebar closes.
                    overview && layout.navigation != NavigationPlacement.Bottom ->
                        safeArea.top + 12.dp
                    overview -> 0.dp
                    else -> safeArea.top + HomeActionBarHeight + 12.dp
                },
                bottom = gridBottomPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(latestGap),
            verticalArrangement = Arrangement.spacedBy(gridVerticalGap),
        ) {
        if (showHeroSlot) {
            item(key = HomeHeroKey, span = { GridItemSpan(maxLineSpan) }) {
                if (showHero) {
                    RecommendationHero(
                        items = heroItems,
                        pagerState = heroPagerState,
                        autoplayProgress = heroAutoplayProgress,
                        indicatorPage = heroIndicatorPage,
                        layout = layout,
                        onItem = onHeroItem,
                        artwork = backdropArtwork,
                        autoplay = heroAutoplay && heroVisibleForAutoplay,
                        scrolling = gridScrolling,
                        onActiveItemChanged = { item ->
                            activeHeroItem = item
                            onHeroItemChanged(item)
                        },
                        extraHeight = heroExtraHeight,
                        refreshProgress = heroRefreshProgress,
                        refreshing = refreshing,
                        windowBackdropActive = heroBackdropState != null && backdropArtwork != null,
                    )
                } else {
                    Box {
                        HomeHeroPlaceholder(layout)
                    }
                }
            }
        }
        if (showStatus) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(start = sceneControlLeading)) {
                    StatusContent(
                        collections, items, selectedCollection,
                        loading = loading || (overview && homeLoading),
                        error = error, stale = stale, overview = overview,
                        overviewHasContent = overviewHasContent, onRetry = onRetry,
                    )
                }
            }
        }

        if (overview && overviewHasContent) {
            if (progressItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeMediaShelf(
                        title = "继续观看",
                        items = progressItems,
                        onItem = onPlay ?: onItem,
                        artwork = shelfArtwork,
                        sharedArtworkModifier = sharedArtworkModifier,
                        kind = HomeShelfKind.Resume,
                        layout = layout,
                        leadingInset = sceneControlLeading,
                        onPlay = onPlay,
                        onToggleWatched = onToggleWatched,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
            if (nextUpItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeMediaShelf(
                        title = "接下来",
                        items = nextUpItems,
                        onItem = onPlay ?: onItem,
                        artwork = shelfArtwork,
                        sharedArtworkModifier = sharedArtworkModifier,
                        kind = HomeShelfKind.NextUp,
                        layout = layout,
                        leadingInset = sceneControlLeading,
                        onPlay = onPlay,
                        onToggleWatched = onToggleWatched,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
            if (showFloatingTabs) {
                // Keep a real sticky item in the lazy layout, but draw only a
                // transparent reservation here. The visible glass tabs remain a
                // sibling of the captured scrolling scene, so they do not sample
                // themselves. Making the reservation sticky is important: when a
                // tab replaces every media-card key, LazyGrid can retain this
                // stable first-visible key instead of falling back to the hero.
                stickyHeader(key = LatestFeedAnchorKey) {
                    Spacer(Modifier.fillMaxWidth().height(HomeFloatingTabsSlotHeight))
                }
            }
        } else if (!overview) {
            if (items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseToolbar(
                    sort = browseSort,
                    descending = browseDescending,
                    itemCount = items.size,
                    canLoadMore = canLoadMore,
                    onNextSort = {
                        browseSortIndex = (browseSortIndex + 1) % BrowseSortField.entries.size
                    },
                    onToggleDirection = { browseDescending = !browseDescending },
                )
            }
        }

        if (overview) {
            if (!showFloatingTabs) {
                // Desktop sidebar layout keeps each provider media library as
                // its own recent-additions shelf. A single merged row loses
                // the library context and made the More action jump to an
                // unrelated global route.
                collections.forEach { collection ->
                    val collectionItems = homeLatestByCollection[collection.id].orEmpty()
                    val collectionLoading = collection.id in homeLatestLoading ||
                        (collection.id !in homeLatestByCollection && collection.id !in homeLatestErrors)
                    val collectionError = homeLatestErrors[collection.id]
                    item(
                        key = "home-latest-shelf:${collection.id.value}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = sceneControlLeading),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "最近添加 · ${collection.name}",
                                Modifier.semantics { heading() },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            // 「更多」= 玻璃胶囊按钮：文字 + 右箭头。
                            // 注意：这里**不能**用需要 backdrop 的玻璃组件（MaFeiSurface /
                            // LiquidButton）——首页内容本身是被窗口 backdrop 记录的对象，
                            // 在 scene 内采样它等于自引用，在 macOS 桌面会直接原生崩溃
                            // （skiko unsupported unit type / exit 132）。所以用自绘的
                            // 半透明胶囊 + 高光描边还原玻璃观感。
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
                                    .border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = .20f),
                                        RoundedCornerShape(18.dp),
                                    )
                                    .mafeiInteractive(
                                        onClick = {
                                            onOpenCollectionLatest(collection)
                                        },
                                        radiusDp = 18f,
                                        label = "更多",
                                    )
                                    .height(36.dp)
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "更多",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                )
                                MaFeiIcon(
                                    MaFeiGlyph.Chevron,
                                    Modifier.size(14.dp),
                                    MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (collectionLoading && collectionItems.isEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = sceneControlLeading),
                            ) {
                                items(HomeLoadingPlaceholderCount) { MediaPlaceholder(Modifier.width(140.dp)) }
                            }
                        } else if (collectionItems.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = sceneControlLeading),
                            ) {
                                items(collectionItems, key = { it.id.value }) { item ->
                                    val text = homeLatestText(item)
                                    MediaCard(
                                        text.title,
                                        text.subtitle,
                                        onClick = { onItem(item) },
                                        poster = artwork?.let { painter ->
                                            { posterModifier ->
                                                painter(item, posterModifier)
                                            }
                                        },
                                        progress = progress(item),
                                        onPlay = onPlay?.let { play -> { play(item) } },
                                        onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                                        onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                                        isWatched = item.userData.isPlayed,
                                        isFavorite = item.userData.isFavorite,
                                        modifier = sharedArtworkModifier?.invoke(item, Modifier.width(140.dp))
                                            ?: Modifier.width(140.dp),
                                    )
                                }
                            }
                        } else {
                            val feedError = collectionError?.takeIf { !collectionLoading }
                            if (feedError != null) {
                                HomeLatestFeedError(feedError) {
                                    onCollection(collection)
                                }
                            } else {
                                Box(
                                    Modifier.fillMaxWidth().padding(horizontal = sceneControlLeading)
                                        .height(96.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        "该媒体库暂无最近添加内容",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            if (showFloatingTabs) {
            // Each poster is a real lazy-grid item. The previous single outer
            // item eagerly composed every image in the feed (up to 50), which
            // caused visible frame drops. The sticky anchor remains stable and
            // the selection coroutine re-pins it while keys are replaced.
            if (showFeedPlaceholders) {
                items(
                    count = HomeLoadingPlaceholderCount,
                    key = { index -> "$renderedFeedKey:loading:$index" },
                ) { MediaPlaceholder() }
            } else if (displayed.isNotEmpty()) {
                items(displayed, key = { item -> "$renderedFeedKey:${item.id.value}" }) { item ->
                    val text = homeLatestText(item)
                    MediaCard(
                        text.title,
                        text.subtitle,
                        onClick = { onItem(item) },
                        poster = artwork?.let { painter ->
                            { posterModifier ->
                                painter(item, posterModifier)
                            }
                        },
                        modifier = sharedArtworkModifier?.invoke(item, Modifier) ?: Modifier,
                        progress = progress(item),
                        onPlay = onPlay?.let { play -> { play(item) } },
                        onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                        onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                        isWatched = item.userData.isPlayed,
                        isFavorite = item.userData.isFavorite,
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val feedError = latestFeedError?.takeIf { !latestFeedLoading }
                    if (feedError != null) {
                        HomeLatestFeedError(feedError) {
                            selectedFeedCollection?.let(onCollection)
                        }
                    } else {
                                Box(
                                    Modifier.fillMaxWidth().padding(horizontal = sceneControlLeading)
                                        .height(96.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                if (selectedFeedKey == RecentFeedKey) "暂无最近添加内容" else "该媒体库暂无内容",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        MaFeiLoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            style = MaFeiLoadingStyle.Inline,
                        )
                    }
                }
            }
            if (latestFeedFillHeight > 0.dp) {
                item(key = "home-latest-feed-min-height", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.fillMaxWidth().height(latestFeedFillHeight))
                }
            }
            }
        } else {
            if (showFeedPlaceholders) {
                items(HomeLoadingPlaceholderCount) { MediaPlaceholder() }
            }
            items(displayed, key = { it.id.value }) { item ->
                val text = HomeCardText(item.title, item.metaLine)
                MediaCard(
                    text.title, text.subtitle, onClick = { onItem(item) },
                    poster = artwork?.let { painter ->
                        { posterModifier ->
                            painter(item, posterModifier)
                        }
                    },
                    modifier = sharedArtworkModifier?.invoke(item, Modifier) ?: Modifier,
                    progress = progress(item),
                    onPlay = onPlay?.let { play -> { play(item) } },
                    onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                    onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                    isWatched = item.userData.isPlayed,
                    isFavorite = item.userData.isFavorite,
                )
            }
        }
        if (!overview && loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                MaFeiLoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    style = MaFeiLoadingStyle.Inline,
                )
            }
        }
      }
      }
      }
      // 大屏轮播前景：文字与指示器。与窗口背景清晰层同构的页序列（同款位移公式、
      // 同速跟手），在 grid 之上、无页界裁剪 —— 文字能跟着轮播一路滑到窗口左缘，
      // start 边距 32dp 与「最近添加」等模块左对齐。
      if (showHero && heroItems.isNotEmpty() && layout.family != MaFeiLayoutFamily.Phone) {
          HomeHeroForegroundOverlay(
              items = heroItems,
              pagerState = heroPagerState,
              autoplayProgress = heroAutoplayProgress,
              indicatorPage = heroIndicatorPage,
              layout = layout,
              gridState = gridState,
              topPadding = safeArea.top + 12.dp,
              heroHeight = homeHeroHeight(layout),
              extraHeight = heroExtraHeight,
              windowWidth = navigationChrome.windowWidthDp ?: maxWidth,
              precomposeNeighbours = heroVisibleForAutoplay,
          )
      }
      if (overview && overviewHasContent && floatingTabsVisible && showFloatingTabs) {
          Row(
              modifier = Modifier.align(Alignment.TopCenter)
                  .widthIn(max = layout.contentMaxWidthDp.dp)
                  .fillMaxWidth()
                  .offset { IntOffset(0, floatingTabsPlacement.value.topPx) }
                  .padding(
                      start = layout.pagePaddingDp.dp +
                          if (layout.navigation == NavigationPlacement.Bottom) safeArea.start else 0.dp,
                      end = layout.pagePaddingDp.dp + safeArea.end +
                          homeFloatingTabsEndInset(
                              pinned = floatingTabsPinned,
                              hasSearch = onSearch != null,
                              showRefresh = showRefreshAction,
                          ),
                  ),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              LiquidFloatingTabs(
                  labels = floatingTabLabels,
                  selectedIndex = selectedFloatingTab,
                  onSelected = { index ->
                      if (index == selectedFloatingTab) return@LiquidFloatingTabs
                      val selectFeed = {
                          if (index == 0) {
                              selectedFeedKey = RecentFeedKey
                          } else {
                              collections.getOrNull(index - 1)?.let { collection ->
                                  selectedFeedKey = collection.id.value
                                  onCollection(collection)
                              }
                          }
                      }
                      val anchorIndex = floatingTabsPlacement.value.anchorIndex
                          ?: return@LiquidFloatingTabs
                      // Pin the real sticky item once, then replace the feed.
                      // Its stable key keeps the grid anchored while poster
                      // keys change; frame-by-frame corrective scrolls caused
                      // the visible jump and fast-tap races this path used to
                      // exhibit.
                      feedSelectionJob.value?.cancel()
                      feedSelectionJob.value = heroRefreshScope.launch {
                          gridState.animateScrollToItem(
                              index = anchorIndex,
                              // Leave the same 8dp breathing room below the
                              // floating strip that it has in the inline state.
                              // Pinning the spacer at offset 0 put the first row
                              // slightly underneath the glass tabs on phone.
                              scrollOffset = -floatingTabsInsetPx,
                          )
                          selectFeed()
                          // selectedFeedKey replaces every poster key below
                          // the header. Pin the stable header during that same
                          // remeasure so LazyGrid never restores an old media
                          // item and moves the page down after the animation.
                          gridState.requestScrollToItem(
                              index = anchorIndex,
                              scrollOffset = -floatingTabsInsetPx,
                          )
                      }
                  },
                  backdrop = floatingTabsBackdrop,
                  modifier = Modifier.weight(1f),
              )
          }
      }
      if (
          overview && onSearch != null && showFloatingTabs &&
          (layout.navigation == NavigationPlacement.Bottom || floatingTabsPinned)
      ) {
          Box(
              Modifier.align(Alignment.TopCenter)
                  .widthIn(max = layout.contentMaxWidthDp.dp)
                  .fillMaxWidth()
                  .offset { IntOffset(0, floatingTabsStickyTopPx) }
                  .padding(
                      start = layout.pagePaddingDp.dp +
                          if (layout.navigation == NavigationPlacement.Bottom) safeArea.start else 0.dp,
                      end = layout.pagePaddingDp.dp + safeArea.end,
                  ),
          ) {
              Row(
                  modifier = Modifier.align(Alignment.TopEnd),
                  horizontalArrangement = Arrangement.spacedBy(HomeFloatingActionGap),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  // Remove both the idle action and its inter-item spacing.
                  if (showRefreshAction) {
                      MaFeiRefreshIndicatorButton(
                          refreshing = true,
                          backdrop = floatingTabsBackdrop,
                      )
                  }
                  HomeSearchButton(
                      onClick = onSearch,
                      backdrop = floatingTabsBackdrop,
                  )
              }
          }
      }
      if (!overview) {
          MaFeiActionBar(
              title = selected?.name ?: "媒体库",
              onBack = onBack,
              backdrop = floatingTabsBackdrop,
              contentColor = MaterialTheme.colorScheme.onSurface,
              actionCount = 1,
              modifier = Modifier.align(Alignment.TopCenter),
              actions = { buttonBackdrop ->
                  MaFeiActionBarButton(
                      onClick = { if (!loading) onRetry() },
                      backdrop = buttonBackdrop,
                      contentDescription = "刷新",
                  ) {
                      MaFeiIcon(
                          MaFeiGlyph.Refresh,
                          Modifier.size(20.dp),
                          if (loading) MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)
                          else MaterialTheme.colorScheme.onSurface,
                      )
                  }
              },
          )
      }
      }
}

internal fun shouldAlignHomeHeroInitially(
    overview: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean = overview && firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

@Composable
private fun HomeLatestFeedError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MaFeiInlineNotice(
            message = message,
            actionLabel = "重试",
            onAction = onRetry,
        )
    }
}

private const val HomeLoadingPlaceholderCount = 6
private val HomeActionBarHeight = 56.dp

@Composable
private fun HomeRefreshContainer(
    stretchHero: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    heroStretchConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (stretchHero) {
        // Phone home follows the original HarmonyOS interaction: the list stays
        // anchored while the hero itself absorbs and visualises the pull.
        // Keeping Material's pull connection here would make both containers
        // compete for the same downward gesture.
        Box(
            modifier = modifier.nestedScroll(heroStretchConnection),
            contentAlignment = Alignment.TopCenter,
        ) {
            content()
        }
    } else {
        MaFeiPullRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            content()
        }
    }
}

@Composable
private fun HomeSearchButton(
    onClick: () -> Unit,
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(HomeFloatingActionSize).semantics { contentDescription = "搜索" },
        height = HomeFloatingActionSize,
        horizontalPadding = 8.dp,
    ) {
        MaFeiIcon(
            MaFeiGlyph.Search,
            Modifier.size(22.dp),
            MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HomeLibraryShelf(
    collections: List<MediaCollection>,
    onBrowseCollection: (MediaCollection) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "媒体库",
                Modifier.weight(1f).semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                Modifier.mafeiInteractive(onOpenLibrary, radiusDp = 12f, label = "查看全部媒体库")
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("查看全部", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                MaFeiIcon(MaFeiGlyph.Arrow, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(collections, key = { it.id.value }) { collection ->
                val palette = when (collection.mediaTypes.firstOrNull()) {
                    MediaType.Series, MediaType.Season, MediaType.Episode -> listOf(Color(0xFF244EA5), Color(0xFF477ED1))
                    MediaType.Movie, MediaType.Video -> listOf(Color(0xFF176B78), Color(0xFF36A7B3))
                    MediaType.Audio, MediaType.MusicAlbum, MediaType.MusicArtist -> listOf(Color(0xFF6B3D92), Color(0xFF9E70C4))
                    else -> listOf(Color(0xFF315A83), Color(0xFF5F89AE))
                }
                Box(
                    Modifier.width(104.dp).height(76.dp).clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(palette))
                        .mafeiInteractive(
                            onClick = { onBrowseCollection(collection) },
                            radiusDp = 18f,
                            label = "打开${collection.name}",
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        collection.name,
                        Modifier.align(Alignment.TopStart).fillMaxWidth(.82f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MaFeiIcon(
                        MaFeiGlyph.Library,
                        Modifier.align(Alignment.BottomEnd).size(30.dp),
                        Color.White.copy(alpha = .34f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseToolbar(
    sort: BrowseSortField,
    descending: Boolean,
    itemCount: Int,
    canLoadMore: Boolean,
    onNextSort: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sort.label,
            Modifier.mafeiInteractive(onNextSort, radiusDp = 10f, label = "切换排序方式")
                .padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            Modifier.size(38.dp).mafeiInteractive(onToggleDirection, radiusDp = 19f, label = "切换排序方向")
                .semantics { contentDescription = "切换排序方向" },
            contentAlignment = Alignment.Center,
        ) {
            MaFeiIcon(
                MaFeiGlyph.Arrow,
                Modifier.size(16.dp).graphicsLayer { rotationZ = if (descending) 90f else -90f },
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (canLoadMore) "$itemCount+" else itemCount.toString(),
            Modifier.clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

private fun sortBrowseItems(
    items: List<MediaItem>,
    field: BrowseSortField,
    descending: Boolean,
): List<MediaItem> {
    fun direction(value: Int): Int = if (descending) -value else value
    val comparator = Comparator<MediaItem> { left, right ->
        val compared = when (field) {
            BrowseSortField.Title -> direction(left.title.compareTo(right.title, ignoreCase = true))
            BrowseSortField.DateAdded -> compareNullable(left.dateAddedEpochMs, right.dateAddedEpochMs, descending)
            BrowseSortField.Runtime -> compareNullable(left.runtimeMs, right.runtimeMs, descending)
        }
        if (compared != 0) compared else left.id.value.compareTo(right.id.value)
    }
    return items.sortedWith(comparator)
}

private fun List<MediaItem>.distinctByMediaId(): List<MediaItem> = distinctBy { it.id.value }

private fun <T : Comparable<T>> compareNullable(left: T?, right: T?, descending: Boolean): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right).let { if (descending) -it else it }
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
@Composable
fun HomeHeroWindowBackdrop(
    state: HomeHeroBackdropState,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    ambientArtwork: (@Composable (MediaItem, Dp, Modifier) -> Unit)? = null,
) {
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
            Crossfade(
                targetState = state.activeItem,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(durationMillis = HeroAmbientFadeMillis),
                label = "mafei-hero-ambient",
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
private fun HomeHeroForegroundOverlay(
    items: List<MediaItem>,
    pagerState: PagerState,
    autoplayProgress: Animatable<Float, AnimationVector1D>,
    indicatorPage: State<Int>,
    layout: MaFeiLayout,
    gridState: LazyGridState,
    topPadding: Dp,
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
                            horizontal = if (layout.horizontalHero) 36.dp else 16.dp,
                            vertical = 14.dp,
                        ),
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

@Composable
private fun HomeHeroPlaceholder(layout: MaFeiLayout) {
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

@Composable
private fun StatusContent(
    collections: List<MediaCollection>, items: List<MediaItem>, selectedCollection: CollectionId?,
    loading: Boolean, error: String?, stale: Boolean, overview: Boolean,
    overviewHasContent: Boolean, onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stale) Box(
            Modifier
                .fillMaxWidth()
                .clip(MaFeiShape.field)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text("离线内容 · 当前显示缓存，恢复连接后可刷新。", Modifier.fillMaxWidth().padding(14.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        error?.let { InlineProblem(it, if (loading) null else onRetry) }
        if (loading) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)
            }
        }
        if (!loading && error == null && !(overview && overviewHasContent) && collections.isEmpty()) {
            LibraryEmptyState("这里还没有媒体库", "这个账号暂时没有可访问的媒体库。请检查服务器中的媒体库和账号权限。")
        } else if (!loading && error == null && !(overview && overviewHasContent) && items.isEmpty()) {
            LibraryEmptyState(
                if (selectedCollection == null) "选择一个媒体库" else "还没有发现内容",
                if (selectedCollection == null && !overview) "从导航中选择媒体库，开始浏览。" else "这个媒体库暂无内容，完成服务器扫描后可再刷新。",
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecommendationHero(
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
                if (layout.navigation != NavigationPlacement.Sidebar && !featherIntoAmbient) {
                    // Sidebar 大屏不再叠加文字底部的黑色渐变遮罩：图片下沉到背景
                    // 后它就是「文字区域下面一块黑色半透明」，可读性由背景层的
                    // 全局 scrim 与羽化负责，这层只贡献脏块。
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

private fun heroInitialPage(itemCount: Int): Int {
    if (itemCount <= 1) return 0
    val middlePage = HeroVirtualPageCount / 2
    return middlePage - middlePage % itemCount
}

@Composable
private fun HomeMediaShelf(
    title: String,
    items: List<MediaItem>,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)? = null,
    kind: HomeShelfKind,
    layout: MaFeiLayout,
    leadingInset: Dp,
    onPlay: ((MediaItem) -> Unit)? = null,
    onToggleWatched: ((MediaItem) -> Unit)? = null,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
    onOpenMore: (() -> Unit)? = null,
) {
    val width = if (layout.family == MaFeiLayoutFamily.Phone) 160.dp else 240.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = leadingInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            if (onOpenMore != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .20f),
                            RoundedCornerShape(18.dp),
                        )
                        .mafeiInteractive(onClick = onOpenMore, radiusDp = 18f, label = "更多")
                        .height(36.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("更多", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    MaFeiIcon(MaFeiGlyph.Chevron, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp),
            contentPadding = PaddingValues(horizontal = leadingInset),
        ) {
            items(items, key = { it.id.value }) { item ->
                val text = homeShelfText(item, kind)
                val interactionSource = remember(item.id) { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val focused by interactionSource.collectIsFocusedAsState()
                val cardBounds = Modifier.width(width)
                Column(
                    (sharedArtworkModifier?.invoke(item, cardBounds) ?: cardBounds).mafeiInteractive(
                        onClick = { onItem(item) },
                        label = "查看${text.title}",
                        interactionSource = interactionSource,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                        val posterModifier = Modifier.fillMaxSize()
                        if (artwork != null) {
                            artwork(item, posterModifier)
                        } else {
                            TextPoster(text.title, posterModifier)
                        }
                        if (kind == HomeShelfKind.Resume) progress(item)?.let { value ->
                            MaFeiProgressLine(
                                progress = value,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                progressColor = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Black.copy(alpha = .36f),
                            )
                        }
                        if (hovered || focused) {
                            MediaCardHoverOverlay(
                                title = text.title,
                                onPlay = { (onPlay ?: onItem)(item) },
                                onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                                onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                                isWatched = item.userData.isPlayed,
                                isFavorite = item.userData.isFavorite,
                            )
                        }
                    }
                    Text(
                        text.title,
                        color = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private enum class HomeShelfKind { Resume, NextUp, Recent }

private data class HomeCardText(val title: String, val subtitle: String)

private fun homeShelfText(item: MediaItem, kind: HomeShelfKind): HomeCardText {
    // 卡片文案风格与所有展示文本一样由 model 提供（见 core:domain/MediaPresentation.kt ），
    // 此处不再判断 provider。
    if (item.displayStyle.cardStyle == CardTextStyle.Plain) {
        return HomeCardText(item.title, item.metaLine)
    }
    return when (kind) {
        HomeShelfKind.Resume -> when (item.mediaType) {
            MediaType.Movie -> HomeCardText(item.title, item.typeLabel ?: item.metaLine)
            MediaType.Episode -> HomeCardText(
                item.cardTitle,
                listOfNotNull(item.seasonTitle?.takeIf { it.isNotBlank() }, item.title.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
            )
            else -> HomeCardText(item.title, item.metaLine)
        }
        HomeShelfKind.NextUp -> if (item.mediaType == MediaType.Episode) {
            HomeCardText(item.cardTitle, item.episodeCardLine ?: item.metaLine)
        } else HomeCardText(item.title, item.metaLine)
        HomeShelfKind.Recent -> homeLatestText(item)
    }
}

private fun homeLatestText(item: MediaItem): HomeCardText {
    if (item.displayStyle.cardStyle == CardTextStyle.EpisodeAware && item.mediaType == MediaType.Episode) {
        return HomeCardText(item.cardTitle, item.episodeCardLine ?: item.metaLine)
    }
    // Feiniu's legacy latest cards show the TV episode total before the year.
    // The provider maps this to childCount; keeping the rule here means the
    // ordinary movie/folder cards retain their existing year-only subtitle.
    if (item.provider == ProviderKind.Feiniu && item.mediaType == MediaType.Series) {
        val subtitle = buildList {
            item.childCount?.takeIf { it > 0 }?.let { add("共${it}集") }
            item.year?.let { add(it.toString()) }
        }.joinToString(" · ")
        return HomeCardText(item.title, subtitle)
    }
    return HomeCardText(item.title, item.year?.toString() ?: "")
}

@Composable
private fun LibraryEmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
        Text(message, Modifier.widthIn(max = 460.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun MediaPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaFeiShape.poster)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)))
        Box(Modifier.fillMaxWidth(.75f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(Modifier.fillMaxWidth(.45f).height(10.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)))
    }
}

private fun progress(item: MediaItem): Float? = item.runtimeMs?.takeIf { it > 0 && item.userData.hasProgress }?.let {
    (item.userData.playbackPositionMs.toFloat() / it).coerceIn(0f, 1f)
}

private fun progressLabel(item: MediaItem): String {
    val minutes = item.userData.playbackPositionMs / 60_000
    return "看到 $minutes 分钟"
}
