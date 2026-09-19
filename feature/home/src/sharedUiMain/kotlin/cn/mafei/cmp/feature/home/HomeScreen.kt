package cn.mafei.cmp.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidFloatingTabs
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val RecentFeedKey = "__home_recent__"
private const val HeroAutoplayMillis = 6_500
private const val HeroPageAnimationMillis = 320
// The ambient is an environment layer driven by the selected hero item. The
// selection only changes when the pager settles, so a slow crossfade stands in
// for the continuous pager parallax the ambient used to draw.
private const val HeroAmbientFadeMillis = 600
private const val HeroVirtualPageCount = Int.MAX_VALUE
// 背景清晰轮播图的羽化起点（占清晰层自身高度的比例）：80% 处才开始渐隐。
private const val HomeHeroFeatherStartFraction = 0.8f
// 清晰层在 hero 占位底边之下继续延伸的高度：图片清晰部分一直铺到「最近添加」
// 标题区域后面（内容浮在图片渐隐段之上），可见清晰区约达屏幕高度的 70%，
// 羽化带压缩在底部 —— 观感上图片清晰的部分占大头，同时内容仍完整可读。
private val HomeHeroClearExtraDp = 180.dp
private const val HomeHeroKey = "home-recommendation-hero"
private const val LatestFeedAnchorKey = "home-latest-feed-anchor"
private val HomeHeroRefreshThreshold = 64.dp
private val HomeHeroMaxStretch = 160.dp
private const val HomeHeroStretchResistance = 3f

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
    refreshing: Boolean = false,
    loadingMore: Boolean = false,
    error: String?,
    stale: Boolean,
    canLoadMore: Boolean,
    overview: Boolean,
    layout: MaFeiLayout,
    onCollection: (MediaCollection) -> Unit,
    onItem: (MediaItem) -> Unit,
    onPlay: ((MediaItem) -> Unit)? = null,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onHeroItemChanged: (MediaItem?) -> Unit = {},
    onBrowseCollection: (MediaCollection) -> Unit = onCollection,
    onBack: (() -> Unit)? = null,
    heroAutoplay: Boolean = true,
    bottomOverlayInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
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
    var browseSortIndex by rememberSaveable { mutableStateOf(0) }
    var browseDescending by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(collections) {
        if (selectedFeedKey != RecentFeedKey && collections.none { it.id.value == selectedFeedKey }) {
            selectedFeedKey = RecentFeedKey
        }
    }
    val selectedFeedCollection = collections.firstOrNull { it.id.value == selectedFeedKey }
    val latestItems = remember(selectedFeedKey, selectedFeedCollection, recentItems, homeLatestByCollection) {
        if (selectedFeedKey == RecentFeedKey) recentItems
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
    // 网格列数是**结构度量**：只跟 family/navigation 走，与侧栏展开状态无关。
    // 侧栏收起/展开只把内容整体平移，不重排网格。
    val latestColumns = when (layout.family) {
        MaFeiLayoutFamily.Phone -> layout.posterColumns.coerceIn(2, 3)
        MaFeiLayoutFamily.Tablet -> when (layout.navigation) {
            NavigationPlacement.Drawer -> 4
            else -> 6
        }
        MaFeiLayoutFamily.Desktop -> 5
    }
    // The current HarmonyOS home uses the recommendation hero on phone as well
    // as on wide layouts.  Do not replace it with a generic app header.
    val showHero = overview && heroItems.isNotEmpty()
    // Keep the first lazy item stable while the provider loads. Otherwise the
    // tabs become the initial anchor and stay pinned when the real hero arrives.
    val showHeroSlot = overview && (showHero || loading)
    val overviewHasContent = heroItems.isNotEmpty() || progressItems.isNotEmpty() || nextUpItems.isNotEmpty() ||
        recentItems.isNotEmpty() || collections.isNotEmpty()
    val showStatus = stale || error != null || collections.isEmpty() ||
        if (overview) !overviewHasContent else items.isEmpty()
    val latestGap = if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp
    // Home library tabs have their own cached request stream. The ordinary
    // media-library cursor belongs to a different screen and must never be
    // consumed merely because the home grid reached its tail.
    val paginationEnabled = !overview && canLoadMore && !loading && !refreshing && !loadingMore
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    var initialHeroAlignmentPending by remember(gridState) { mutableStateOf(overview) }
    LaunchedEffect(overview, showHero) {
        // Align once immediately and once when the first real hero is ready.
        // The latter closes the one-frame anchor race without interrupting
        // later provider refreshes or a user's established scroll position.
        if (overview && initialHeroAlignmentPending) {
            gridState.scrollToItem(0)
            if (showHero) initialHeroAlignmentPending = false
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
    // 浮动 Tab / 搜索按钮的玻璃采样源 = shell 根部的窗口记录层（含窗口级轮播
    // 背景与全部内容）。scene 里不再自建捕获层 —— 背景提升到窗口画布后，局部
    // 捕获层反而采不到它。shell 之外（独立预览等）退回局部记录。
    val floatingTabsBackdrop = LocalShellBackdrop.current ?: rememberGlassBackdrop()
    val floatingTabLabels = remember(collections) {
        listOf("最近添加") + collections.map { it.name }
    }
    val selectedFloatingTab = if (selectedFeedKey == RecentFeedKey) 0 else {
        collections.indexOfFirst { it.id.value == selectedFeedKey }.let { if (it < 0) 0 else it + 1 }
    }
    val latestFeedAnchorIndex = buildList {
        if (showHeroSlot) add(Unit)
        if (showStatus) add(Unit)
        if (overview && overviewHasContent && progressItems.isNotEmpty()) add(Unit)
        if (overview && overviewHasContent && nextUpItems.isNotEmpty()) add(Unit)
    }.size
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
    val floatingTabsInsetPx = with(density) { 16.dp.roundToPx() }
    val floatingTabsStickyTopPx = with(density) {
        when (layout.navigation) {
            NavigationPlacement.Drawer -> 88.dp
            NavigationPlacement.Sidebar -> safeArea.top
            NavigationPlacement.Rail -> safeArea.top
            NavigationPlacement.Bottom -> safeArea.top + 12.dp
        }.roundToPx()
    }
    val floatingTabsVisible by remember(
        gridState,
        latestFeedAnchorIndex,
    ) {
        derivedStateOf {
            val anchor = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == LatestFeedAnchorKey }
            val scrolledPastAnchor = gridState.firstVisibleItemIndex > latestFeedAnchorIndex
            anchor != null || scrolledPastAnchor
        }
    }
    // 浮动 feed 切换条（最近添加 / 各媒体库 Tab）是手机与中屏的信息结构。大屏的
    // 常驻导航形态（Sidebar / Rail）改用「最近添加」横向 shelf + 查看更多；侧栏
    // 收起只是让内容区变宽，不改变信息结构 —— Pad 上收起侧栏不允许退回小屏布局。
    val showFloatingTabs = when (layout.navigation) {
        NavigationPlacement.Drawer, NavigationPlacement.Bottom -> true
        NavigationPlacement.Rail, NavigationPlacement.Sidebar -> false
    }
    val floatingTabsPinned by remember(
        gridState,
        latestFeedAnchorIndex,
        floatingTabsInsetPx,
        floatingTabsStickyTopPx,
    ) {
        derivedStateOf {
            val anchor = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == LatestFeedAnchorKey }
            val scrolledPastAnchor = gridState.firstVisibleItemIndex > latestFeedAnchorIndex
            val naturalTop = anchor?.let { it.offset.y + floatingTabsInsetPx }
            scrolledPastAnchor || (naturalTop != null && naturalTop <= floatingTabsStickyTopPx)
        }
    }
    val floatingTabsTopPx = remember(
        gridState,
        floatingTabsInsetPx,
        floatingTabsStickyTopPx,
    ) {
        derivedStateOf {
            val anchor = gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == LatestFeedAnchorKey }
            anchor?.let { maxOf(floatingTabsStickyTopPx, it.offset.y + floatingTabsInsetPx) }
                ?: floatingTabsStickyTopPx
        }
    }
    val gridScrolling by remember(gridState) { derivedStateOf { gridState.isScrollInProgress } }
    var activeHeroItem by remember(heroItems) { mutableStateOf(heroItems.firstOrNull()) }
    val heroItemKeys = remember(heroItems) { heroItems.map { it.id.value } }
    val heroPagerState = key(heroItemKeys) {
        rememberPagerState(
            initialPage = if (heroItems.isEmpty()) 0 else heroInitialPage(heroItems.size),
            pageCount = {
                if (heroItems.size > 1) HeroVirtualPageCount else heroItems.size.coerceAtLeast(1)
            },
        )
    }
    // 轮播全部 item 的横版图并行预载：翻页跟手序列把相邻页组合进视口、模糊层
    // Crossfade 在 settle 时切图，两者都要求「图已在缓存」—— 否则翻页瞬间两层
    // 同时透明，露出 shell 背景色（右缘黑带）。轮播 item 数量少，全部预载即可。
    LaunchedEffect(heroItems, prefetchBackdrop) {
        heroItems.forEach { item ->
            launch { prefetchBackdrop?.invoke(item) }
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
            state.blurRadius = if (layout.family == MaFeiLayoutFamily.Phone) 48.dp else 72.dp
            state.heroHeight = layout.heroHeightDp.dp
            state.extraHeight = heroExtraHeight
        }
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
      // 首页轮播背景已提升到 shell 的窗口画布（heroBackdropState 同步状态，
      // App.kt 的 background 里绘制）—— 它天然全窗、与侧栏让位无关，scene 里
      // 只保留内容、手势和文字浮层。
      Box(Modifier.fillMaxSize()) {
      HomeRefreshContainer(
        stretchHero = stretchHeroRefresh,
        refreshing = refreshing,
        onRefresh = onRetry,
        heroStretchConnection = heroStretchConnection,
        modifier = Modifier.fillMaxSize(),
      ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (overview) latestColumns else layout.posterColumns),
            state = gridState,
            modifier = Modifier.fillMaxHeight().widthIn(max = layout.contentMaxWidthDp.dp).fillMaxWidth(),
            overscrollEffect = null,
            contentPadding = PaddingValues(
                start = if (layout.navigation == NavigationPlacement.Bottom) {
                    safeArea.start + layout.pagePaddingDp.dp
                } else {
                    layout.pagePaddingDp.dp
                },
                end = layout.pagePaddingDp.dp + safeArea.end,
                top = when {
                    overview && layout.family == MaFeiLayoutFamily.Phone && showHeroSlot -> 0.dp
                    overview && layout.family == MaFeiLayoutFamily.Phone -> safeArea.top + 12.dp
                    // Sidebar/Rail layouts share the same top margin as the
                    // sidebar panel so the hero and sidebar are vertically
                    // aligned. Phone bleeds to the top for the hero effect.
                    overview && layout.navigation != NavigationPlacement.Bottom -> safeArea.top + 12.dp
                    overview -> 0.dp
                    else -> safeArea.top + HomeActionBarHeight + 12.dp
                },
                bottom = (if (layout.navigation == NavigationPlacement.Bottom) 112.dp else 48.dp) +
                    safeArea.bottom + bottomOverlayInset,
            ),
            horizontalArrangement = Arrangement.spacedBy(latestGap),
            verticalArrangement = Arrangement.spacedBy(if (layout.family == MaFeiLayoutFamily.Phone) 20.dp else 16.dp),
        ) {
        if (showHeroSlot) {
            item(key = HomeHeroKey, span = { GridItemSpan(maxLineSpan) }) {
                if (showHero) {
                    RecommendationHero(
                        items = heroItems,
                        pagerState = heroPagerState,
                        layout = layout,
                        onItem = onItem,
                        artwork = backdropArtwork,
                        autoplay = heroAutoplay,
                        scrolling = gridScrolling,
                        onActiveItemChanged = { item ->
                            activeHeroItem = item
                            onHeroItemChanged(item)
                        },
                        extraHeight = heroExtraHeight,
                        refreshProgress = heroRefreshProgress,
                        refreshing = refreshing,
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
                    StatusContent(collections, items, selectedCollection, loading, error, stale, overview, onRetry)
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
                        kind = HomeShelfKind.Resume,
                        layout = layout,
                        leadingInset = sceneControlLeading,
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
                        kind = HomeShelfKind.NextUp,
                        layout = layout,
                        leadingInset = sceneControlLeading,
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
                    Spacer(Modifier.fillMaxWidth().height(72.dp))
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

        val showFeedPlaceholders = (loading || latestFeedLoading) && displayed.isEmpty()
        if (overview) {
            if (!showFloatingTabs) {
                // Desktop sidebar layout: "Recently Added" is a horizontal
                // shelf with a "See More" action, not a scrolling grid.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = sceneControlLeading),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "最近添加",
                                Modifier.semantics { heading() },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            TextButton(
                                onClick = {
                                    val target = selectedFeedCollection
                                    if (target != null) onBrowseCollection(target) else onOpenLibrary()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text("查看更多", fontSize = 14.sp)
                            }
                        }
                        if (showFeedPlaceholders) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = sceneControlLeading),
                            ) {
                                items(HomeLoadingPlaceholderCount) { MediaPlaceholder(Modifier.width(140.dp)) }
                            }
                        } else if (displayed.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = sceneControlLeading),
                            ) {
                                items(displayed, key = { it.id.value }) { item ->
                                    val text = homeLatestText(item)
                                    MediaCard(
                                        text.title,
                                        text.subtitle,
                                        onClick = { onItem(item) },
                                        status = status(item),
                                        poster = artwork?.let { painter ->
                                            { posterModifier -> painter(item, posterModifier) }
                                        },
                                        progress = progress(item),
                                        modifier = Modifier.width(140.dp),
                                    )
                                }
                            }
                        } else {
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
                    key = { index -> "$selectedFeedKey:loading:$index" },
                ) { MediaPlaceholder() }
            } else if (displayed.isNotEmpty()) {
                items(displayed, key = { item -> "$selectedFeedKey:${item.id.value}" }) { item ->
                    val text = homeLatestText(item)
                    MediaCard(
                        text.title,
                        text.subtitle,
                        onClick = { onItem(item) },
                        status = status(item),
                        poster = artwork?.let { painter ->
                            { posterModifier -> painter(item, posterModifier) }
                        },
                        progress = progress(item),
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
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
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
                    status = status(item),
                    poster = artwork?.let { painter -> { posterModifier -> painter(item, posterModifier) } },
                    progress = progress(item),
                )
            }
        }
        if (!overview && loadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
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
              layout = layout,
              gridState = gridState,
              topPadding = safeArea.top + 12.dp,
              heroHeight = layout.heroHeightDp.dp,
              extraHeight = heroExtraHeight,
              windowWidth = navigationChrome.windowWidthDp ?: maxWidth,
          )
      }
      // 浮层导航（置顶 Tab 条 / 搜索按钮 / 二级页头部）必须留在**捕获层之外**：它们采样
      // `floatingTabsBackdrop`，而这个捕获层记录的正是它里面那个 Box 的内容 —— 采样的
      // 节点如果也在被记录的子树里，记录内容就包含"读取自己"，skiko 的 drawable 图会
      // 递归到 native 栈耗尽（Stack Guard）直接 SIGILL。层记录者与其消费者必须是兄弟。
      if (floatingTabsVisible && floatingTabsPinned && showFloatingTabs) {
          Box(
              Modifier.align(Alignment.TopCenter)
                  .fillMaxWidth()
                  .height(safeArea.top + 76.dp)
                  .background(
                      Brush.verticalGradient(
                          0f to MaterialTheme.colorScheme.background.copy(alpha = .38f),
                          .58f to MaterialTheme.colorScheme.background.copy(alpha = .20f),
                          1f to Color.Transparent,
                      ),
                  ),
          )
      }
      if (overview && overviewHasContent && floatingTabsVisible && showFloatingTabs) {
          Row(
              modifier = Modifier.align(Alignment.TopCenter)
                  .widthIn(max = layout.contentMaxWidthDp.dp)
                  .fillMaxWidth()
                  .offset {
                      val stretchOffset = if (
                          stretchHeroRefresh && heroStretchPx.floatValue > 0f && !floatingTabsPinned
                      ) heroStretchPx.floatValue.roundToInt() else 0
                      IntOffset(0, floatingTabsTopPx.value + stretchOffset)
                  }
                  .padding(
                      start = layout.pagePaddingDp.dp +
                          if (layout.navigation == NavigationPlacement.Bottom) safeArea.start else 0.dp,
                      end = layout.pagePaddingDp.dp + safeArea.end,
                  ),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                      // The provider data must not change until the stable
                      // ListItemGroup-equivalent anchor has reached the sticky
                      // edge. A relative scrollBy depends on the current
                      // measurement and CPF/OHOS can apply it to a remeasured
                      // grid, which restores item zero. Address the invariant
                      // outer item directly, then defend that anchor across the
                      // two frames in which the selected feed is replaced.
                      feedSelectionJob.value?.cancel()
                      feedSelectionJob.value = heroRefreshScope.launch {
                          val pinScrollOffset = (floatingTabsInsetPx - floatingTabsStickyTopPx)
                              .coerceAtLeast(0)
                          gridState.animateScrollToItem(
                              index = latestFeedAnchorIndex,
                              scrollOffset = pinScrollOffset,
                          )
                          withFrameNanos { }
                          selectFeed()
                          repeat(2) {
                              withFrameNanos { }
                              if (gridState.firstVisibleItemIndex < latestFeedAnchorIndex) {
                                  gridState.scrollToItem(
                                      index = latestFeedAnchorIndex,
                                      scrollOffset = pinScrollOffset,
                                  )
                              }
                          }
                      }
                  },
                  backdrop = floatingTabsBackdrop,
                  modifier = Modifier.weight(1f),
              )
              if (floatingTabsPinned && onSearch != null) {
                  HomeSearchButton(onSearch, floatingTabsBackdrop)
              }
          }
      }
      if (
          overview && onSearch != null && !floatingTabsPinned &&
          layout.navigation == NavigationPlacement.Bottom
      ) {
          HomeSearchButton(
              onClick = onSearch,
              backdrop = floatingTabsBackdrop,
              modifier = Modifier.align(Alignment.TopEnd).padding(
                  top = safeArea.top + 12.dp,
                  end = safeArea.end + layout.pagePaddingDp.dp,
              ),
          )
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

@Composable
private fun HomeLatestFeedError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text("重试") }
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
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            contentAlignment = Alignment.TopCenter,
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
        modifier = modifier.size(40.dp).semantics { contentDescription = "搜索" },
        height = 40.dp,
        horizontalPadding = 8.dp,
    ) {
        MaFeiIcon(MaFeiGlyph.Search, Modifier.size(22.dp))
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
 * 窗口级轮播背景的状态。背景由 App.kt 的 shell 窗口画布绘制（fillMaxSize 天然
 * 全窗，与侧栏让位量、scene 视口完全无关），HomeScreen 负责把实时状态喂进来。
 */
class HomeHeroBackdropState {
    var items by mutableStateOf(emptyList<MediaItem>())
    var activeItem by mutableStateOf<MediaItem?>(null)
    var pagerState by mutableStateOf<PagerState?>(null)
    var gridState by mutableStateOf<LazyGridState?>(null)
    var blurRadius by mutableStateOf(72.dp)
    // hero 占位的固有高度（heroHeight + 下拉拉伸 extra）：窗口层自己用**窗口原始
    // 安全区**算清晰层高度 —— 不含顶部 Tab 的让位（Tab 是浮层，不该推动背景），
    // 侧栏收起/展开、Tab 条出现消失，背景都逐像素静止。
    var heroHeight by mutableStateOf(0.dp)
    var extraHeight by mutableStateOf(0.dp)
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
        Crossfade(
            targetState = state.activeItem,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(durationMillis = HeroAmbientFadeMillis),
            label = "mafei-hero-ambient",
        ) { target ->
            if (target != null && artwork != null) {
                artwork(
                    target,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.18f
                            scaleY = 1.18f
                            colorFilter = brightnessFilter
                        }
                        .blur(state.blurRadius),
                )
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(clearHeight)
                .clipToBounds()
                .graphicsLayer {
                    if (grid != null) {
                        val heroInfo = grid.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.key == HomeHeroKey }
                        translationY = (heroInfo?.offset?.y?.toFloat() ?: 0f)
                            .coerceIn(-clearHeight.toPx(), 0f)
                    }
                },
        ) {
            if (artwork != null && state.items.isNotEmpty() && pager != null) {
                val current = pager.currentPage
                val pageRange = if (state.items.size > 1) (current - 1)..(current + 2) else current..current
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
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
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
                            )
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
    layout: MaFeiLayout,
    gridState: LazyGridState,
    topPadding: Dp,
    heroHeight: Dp,
    extraHeight: Dp,
    windowWidth: Dp,
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
            .fillMaxSize()
            .graphicsLayer {
                val heroInfo = gridState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == HomeHeroKey }
                translationY = (heroInfo?.offset?.y?.toFloat() ?: 0f)
                    .coerceIn(-fullClear.toPx(), 0f)
            },
    ) {
        // 文字/指示器钉在占位底边（不是视口底）：这层 Box 的高度就是占位的
        // 纵向区间，BottomStart 对齐的是占位底而不是整个视口的底。
        Box(Modifier.fillMaxWidth().height(fullClear)) {
        val current = pagerState.currentPage
        val activeItemIndex = pagerState.settledPage.mod(items.size)
        val pageRange = if (items.size > 1) (current - 1)..(current + 2) else current..current
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
                        LinearProgressIndicator(
                            progress = {
                                when {
                                    index < activeItemIndex -> 1f
                                    index == activeItemIndex -> 1f
                                    else -> 0f
                                }
                            },
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = .22f),
                        )
                    }
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
            .height(layout.heroHeightDp.dp)
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
    loading: Boolean, error: String?, stale: Boolean, overview: Boolean, onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stale) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaFeiShape.field) {
            Text("离线内容 · 当前显示缓存，恢复连接后可刷新。", Modifier.fillMaxWidth().padding(14.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        error?.let { InlineProblem(it, if (loading) null else onRetry) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        if (!loading && error == null && collections.isEmpty()) {
            LibraryEmptyState("这里还没有媒体库", "这个账号暂时没有可访问的媒体库。请检查服务器中的媒体库和账号权限。")
        } else if (!loading && error == null && items.isEmpty()) {
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
    layout: MaFeiLayout,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    autoplay: Boolean,
    scrolling: Boolean,
    onActiveItemChanged: (MediaItem?) -> Unit,
    extraHeight: Dp,
    refreshProgress: Float,
    refreshing: Boolean,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val itemKeys = remember(items) { items.map { it.id.value } }
    // 指针按住（鼠标左键 / 触屏手指）期间必须暂停自动轮播：按着不动时
    // interactionSource 不产生 drag 事件，collectIsDraggedAsState 检测不到，
    // 这里用独立的 down/up 跟踪补上「按下即暂停」的语义。
    val heroPointerPressed = remember { mutableStateOf(false) }
    val pagerPressTracker = Modifier.pointerInput(itemKeys) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            heroPointerPressed.value = true
            try {
                waitForUpOrCancellation()
            } finally {
                heroPointerPressed.value = false
            }
        }
    }
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        // A normal trackpad momentum should not throw the banner to the next
        // card. Require a more deliberate positional drag, then settle without
        // the desktop rubber-band overshoot.
        snapAnimationSpec = tween(
            durationMillis = HeroPageAnimationMillis,
            easing = FastOutSlowInEasing,
        ),
        snapPositionalThreshold = .72f,
    )
    val activeItemIndex = pagerState.settledPage % items.size
    val isUserDragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(itemKeys, activeItemIndex) {
        onActiveItemChanged(items.getOrNull(activeItemIndex))
    }
    LaunchedEffect(pagerState.settledPage, itemKeys, autoplay, isUserDragging, scrolling, heroPointerPressed.value) {
        if (autoplay && items.size > 1 && !isUserDragging && !scrolling && !heroPointerPressed.value) {
            // A frame-by-frame countdown invalidated both full-screen backdrop
            // recorders on CPF/OHOS, keeping the UI thread busy even while the
            // home page was idle. The carousel clock only needs one wake-up;
            // the pager transition itself remains animated and infinite.
            delay(HeroAutoplayMillis.toLong())
            val targetPage = pagerState.settledPage + 1
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(HeroPageAnimationMillis),
            )
            // CPF/OHOS can occasionally finish a long frame between pages.
            // Align only the completed automatic step; direct swipes stay native.
            if (pagerState.settledPage != targetPage || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.scrollToPage(targetPage)
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
            .height(layout.heroHeightDp.dp + extraHeight)
            // 轮播图已下沉到背景层：这里只保留占位尺寸、手势与文字浮层，
            // 不再裁圆角 —— 图片是页面背景的一部分，不是悬浮卡片。
            .then(pagerPressTracker),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = pagerFlingBehavior,
            overscrollEffect = null,
        ) { page ->
            val item = items[page % items.size]
            Box(
                Modifier.fillMaxSize().mafeiInteractive(
                    onClick = { onItem(item) },
                    radiusDp = 0f,
                    label = "查看${item.title}",
                    // 全宽轮播是背景的一部分：hover 染色/微放大/按压 ripple 在
                    // 无边框大面上就是「一个框」，只保留点击语义。
                    visualFeedback = false,
                ),
            ) {
                // 轮播图图片已下沉到背景层（HomeHeroAmbientBackground 的清晰
                // 图层）：pager 页里只保留文字 scrim、文字与指示器，占位大小
                // 不变。手机（Bottom）没有背景层，图片仍画在页里；图片加载器
                // 不可用时退回 TextPoster 兜底。
                val drawImageInPager = layout.navigation == NavigationPlacement.Bottom || artwork == null
                if (drawImageInPager) {
                    if (artwork != null) {
                        artwork(item, Modifier.fillMaxSize())
                    } else {
                        TextPoster(item.title, Modifier.fillMaxSize())
                    }
                }
                if (layout.navigation != NavigationPlacement.Sidebar) {
                    // Sidebar 大屏不再叠加文字底部的黑色渐变遮罩：图片下沉到背景
                    // 后它就是「文字区域下面一块黑色半透明」，可读性由背景层的
                    // 全局 scrim 与羽化负责，这层只贡献脏块。
                    Box(
                        Modifier.fillMaxSize().background(
                            if (layout.horizontalHero) {
                                Brush.horizontalGradient(
                                    listOf(Color(0xDA071019), Color(0x8F071019), Color(0x16071019), Color.Transparent),
                                )
                            } else Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = .08f),
                                .42f to Color.Black.copy(alpha = .16f),
                                1f to Color.Black.copy(alpha = .88f),
                            ),
                        ),
                    )
                }
                // 手机端的羽化：图片仍在 pager 里，靠底部 96dp 渐变融入画布。
                // 非 Phone 端不再叠加 background 渐变 —— 图片下沉到背景层后由
                // DstIn 羽化自然渐隐进模糊背景，叠这层会重新洗出一条「带底边」。
                if (layout.family == MaFeiLayoutFamily.Phone) {
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
                if (items.size > 1) {
                    if (layout.navigation == NavigationPlacement.Sidebar) {
                        val visibleCount = minOf(items.size, 7)
                        val visibleStart = (activeItemIndex - visibleCount / 2)
                            .coerceIn(0, (items.size - visibleCount).coerceAtLeast(0))
                        Row(
                            Modifier.align(Alignment.BottomStart)
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
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .padding(horizontal = if (layout.horizontalHero) 36.dp else 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items.indices.forEach { index ->
                                LinearProgressIndicator(
                                    progress = {
                                        when {
                                            index < activeItemIndex -> 1f
                                            index == activeItemIndex -> 1f
                                            else -> 0f
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = .22f),
                                )
                            }
                        }
                    }
                }
                } // END Bottom-only text/indicator
            }
        }
        if (refreshing || refreshProgress > 0f) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = safeArea.top + 8.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = .30f)),
                contentAlignment = Alignment.Center,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { refreshProgress },
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = .20f),
                        strokeWidth = 3.dp,
                    )
                }
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
    kind: HomeShelfKind,
    layout: MaFeiLayout,
    leadingInset: Dp,
) {
    val width = if (layout.family == MaFeiLayoutFamily.Phone) 160.dp else 240.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            Modifier
                .padding(horizontal = leadingInset)
                .semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp),
            contentPadding = PaddingValues(horizontal = leadingInset),
        ) {
            items(items, key = { it.id.value }) { item ->
                val text = homeShelfText(item, kind)
                Column(
                    Modifier.width(width).mafeiInteractive({ onItem(item) }, label = "查看${text.title}"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                        if (artwork != null) artwork(item, Modifier.fillMaxSize()) else TextPoster(text.title, Modifier.fillMaxSize())
                        if (kind == HomeShelfKind.Resume) progress(item)?.let { value ->
                            LinearProgressIndicator({ value }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary, trackColor = Color.Black.copy(alpha = .36f))
                        }
                    }
                    Text(
                        text.title,
                        color = MaterialTheme.colorScheme.onSurface,
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

private enum class HomeShelfKind { Resume, NextUp }

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
    }
}

private fun homeLatestText(item: MediaItem): HomeCardText {
    if (item.displayStyle.cardStyle == CardTextStyle.EpisodeAware && item.mediaType == MediaType.Episode) {
        return HomeCardText(item.cardTitle, item.episodeCardLine ?: item.metaLine)
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

private fun status(item: MediaItem): String? = when {
    item.userData.isPlayed -> "已看"
    item.userData.isFavorite -> "已收藏"
    else -> null
}

private fun progress(item: MediaItem): Float? = item.runtimeMs?.takeIf { it > 0 && item.userData.hasProgress }?.let {
    (item.userData.playbackPositionMs.toFloat() / it).coerceIn(0f, 1f)
}

private fun progressLabel(item: MediaItem): String {
    val minutes = item.userData.playbackPositionMs / 60_000
    val remaining = item.runtimeMs?.let { ((it - item.userData.playbackPositionMs).coerceAtLeast(0) / 60_000) }
    return if (remaining != null) "看到 $minutes 分钟 · 剩余 $remaining 分钟" else "看到 $minutes 分钟"
}

