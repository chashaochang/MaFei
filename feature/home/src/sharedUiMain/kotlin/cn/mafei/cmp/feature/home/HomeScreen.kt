package cn.mafei.cmp.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
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

private const val RecentFeedKey = "__home_recent__"

private const val HeroInitialPrefetchDelayMillis = 800L

internal const val HomeHeroKey = "home-recommendation-hero"

private const val LatestFeedAnchorKey = "home-latest-feed-anchor"

private val HomeHeroRefreshThreshold = 64.dp

private val HomeHeroMaxStretch = 160.dp

private const val HomeHeroStretchResistance = 3f

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
    authenticationRequired: Boolean = false,
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
    // Idle posters need no shared-bounds layer or lookahead bookkeeping. Keep
    // only the clicked source registered, including on return from details.
    // The source identifies the shelf too: one media can appear in several rows.
    var selectedArtworkSource by rememberSaveable { mutableStateOf<String?>(null) }
    val openArtwork: (MediaItem, String) -> Unit = { item, source ->
        selectedArtworkSource = source
        onItem(item)
    }
    val transitionArtwork: @Composable (MediaItem, String, Modifier) -> Modifier = { item, source, bounds ->
        if (source == selectedArtworkSource) sharedArtworkModifier?.invoke(item, bounds) ?: bounds
        else bounds
    }
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
    // Tablet drawers and persistent sidebars own library navigation alike.
    // Only phone navigation uses the floating feed selector; rotation must
    // not change the tablet's per-library home structure.
    val showFloatingTabs = layout.isPhoneNavigation
    val latestItems = remember(
        selectedFeedKey,
        selectedFeedCollection,
        recentItems,
        homeLatestByCollection,
        showFloatingTabs,
    ) {
        if (selectedFeedKey == RecentFeedKey || !showFloatingTabs) recentItems
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
    val showStatus = !authenticationRequired && error == null &&
        (if (overview) !overviewHasContent else collections.isEmpty() || items.isEmpty())
    // Failed sections share one overlay, preserving the feed and its scroll anchor.
    val failedCollections = if (!overview) emptyList() else collections.filter { collection ->
        collection.id in homeLatestErrors && (!showFloatingTabs || collection == selectedFeedCollection)
    }
    val feedFeedback = failedCollections.takeIf { it.isNotEmpty() }?.joinToString("\n") {
        "${it.name}：${homeLatestErrors.getValue(it.id)}"
    }
    val feedbackMessage = if (authenticationRequired) null else error ?: feedFeedback ?: if (stale) {
        "当前显示离线缓存，恢复连接后可刷新。"
    } else null
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
      val gridVerticalGap = maxOf(20.dp, mediaArtworkGridHoverInset(
          layout.gridWidthDp.dp, if (overview) latestColumns else legacyMediaColumns(layout.gridWidthDp), latestGap,
      ))
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
              emptyContentHeightDp = 96f,
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
                    else -> safeArea.top + HomeActionBarHeight + gridVerticalGap
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
                        overview = overview, overviewHasContent = overviewHasContent,
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
                        onItem = onPlay ?: { item -> openArtwork(item, "resume:${item.id.value}") },
                        artwork = shelfArtwork,
                        sharedArtworkModifier = { item, bounds ->
                            transitionArtwork(item, "resume:${item.id.value}", bounds)
                        },
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
                        onItem = onPlay ?: { item -> openArtwork(item, "next-up:${item.id.value}") },
                        artwork = shelfArtwork,
                        sharedArtworkModifier = { item, bounds ->
                            transitionArtwork(item, "next-up:${item.id.value}", bounds)
                        },
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
                // Tablet/desktop home keeps each provider media library as
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
                        Column {
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
                                modifier = Modifier.mafeiArtworkShelfBleed(mediaArtworkHoverSideInset(140.dp)),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(
                                    start = sceneControlLeading + mediaArtworkHoverSideInset(140.dp),
                                    end = sceneControlLeading + mediaArtworkHoverSideInset(140.dp),
                                    top = mediaArtworkHoverTopInset(210.dp),
                                ),
                            ) {
                                items(HomeLoadingPlaceholderCount) { MediaPlaceholder(Modifier.width(140.dp)) }
                            }
                        } else if (collectionItems.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.mafeiArtworkShelfBleed(mediaArtworkHoverSideInset(140.dp)),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(
                                    start = sceneControlLeading + mediaArtworkHoverSideInset(140.dp),
                                    end = sceneControlLeading + mediaArtworkHoverSideInset(140.dp),
                                    top = mediaArtworkHoverTopInset(210.dp),
                                ),
                            ) {
                                items(collectionItems, key = { it.id.value }) { item ->
                                    val text = homeLatestText(item)
                                    val artworkSource = "latest:${collection.id.value}:${item.id.value}"
                                    MediaCard(
                                        text.title,
                                        text.subtitle,
                                        onClick = { openArtwork(item, artworkSource) },
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
                                        modifier = transitionArtwork(item, artworkSource, Modifier.width(140.dp)),
                                    )
                                }
                            }
                        } else {
                            val feedError = collectionError?.takeIf { !collectionLoading }
                            if (feedError == null) {
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
                    val artworkSource = "feed:$renderedFeedKey:${item.id.value}"
                    MediaCard(
                        text.title,
                        text.subtitle,
                        onClick = { openArtwork(item, artworkSource) },
                        poster = artwork?.let { painter ->
                            { posterModifier ->
                                painter(item, posterModifier)
                            }
                        },
                        modifier = transitionArtwork(item, artworkSource, Modifier),
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
                        Spacer(Modifier.height(96.dp))
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
                val artworkSource = "browse:${item.id.value}"
                MediaCard(
                    text.title, text.subtitle, onClick = { openArtwork(item, artworkSource) },
                    poster = artwork?.let { painter ->
                        { posterModifier ->
                            painter(item, posterModifier)
                        }
                    },
                    modifier = transitionArtwork(item, artworkSource, Modifier),
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
              contentStartPadding = gridStartPadding,
              contentEndPadding = gridEndPadding,
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
      MaFeiErrorDialog(
          message = feedbackMessage,
          title = when {
              error != null -> "连接失败"
              feedFeedback != null -> "加载失败"
              else -> "离线内容"
          },
          backdrop = floatingTabsBackdrop,
          busy = loading || homeLoading || refreshing || failedCollections.any { it.id in homeLatestLoading },
          onRetry = {
              if (error == null && failedCollections.isNotEmpty()) failedCollections.forEach(onCollection)
              else onRetry()
          },
      )
      }
}

internal fun shouldAlignHomeHeroInitially(
    overview: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean = overview && firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

private const val HomeLoadingPlaceholderCount = 6

private val HomeActionBarHeight = 56.dp
