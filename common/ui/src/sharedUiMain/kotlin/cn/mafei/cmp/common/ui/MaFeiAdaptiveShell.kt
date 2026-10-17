package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.design_system.BottomNavigationType

/** Geometry-only shell; session, navigation state and scroll state belong to caller.
 * Background is recorded once, separately from all its glass consumers.
 */
@Composable
fun MaFeiAdaptiveShell(
    layout: MaFeiLayout,
    destinations: List<MaFeiNavItem>,
    selectedDestination: String,
    sidebarDestinations: List<MaFeiNavItem> = destinations,
    topTabDestinations: List<MaFeiNavItem> = destinations,
    topTabSelectedDestination: String = selectedDestination,
    onDestination: (String) -> Unit,
    onSearch: (() -> Unit)? = null,
    /** Back action shown in the full-width first row beside the collapsed top-tab strip. */
    onTopBarBack: (() -> Unit)? = null,
    selectedSearch: Boolean = false,
    libraries: List<MaFeiNavItem>,
    selectedLibrary: String?,
    onLibrary: (String) -> Unit,
    accountLabel: String,
    showBottomNavigation: Boolean = true,
    bottomNavigationType: BottomNavigationType = BottomNavigationType.Floating,
    /**
     * True while the root content has pushed a detail/secondary route. On a
     * large shell this changes the leading control of the retained top-tab
     * strip to a back action when the full sidebar is collapsed.
     */
    contentNavigationActive: Boolean = false,
    bottomNavigationCompactProgress: () -> Float = { 0f },
    /**
     * 「页面内容已经滚过顶部」的进度（0..1）。一级页面的标题随内容一起滚走，内容一旦
     * 滚进状态栏区域，就在顶部导航区浮现一条**渐进模糊**（真玻璃，不是半透明色块）。
     */
    topChromeScrolledProgress: () -> Float = { 0f },
    background: @Composable () -> Unit,
    /** 侧栏底部账号卡片显示的用户名（当前登录账号的 display name）。 */
    accountName: String? = null,
    accountActions: @Composable () -> Unit,
    /** Optional desktop window controls rendered in the sidebar's chrome row. */
    windowControls: @Composable RowScope.() -> Unit = {},
    /** Host-provided drag wrapper for a custom, undecorated desktop titlebar. */
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
    scene: @Composable () -> Unit,
    overlay: @Composable (GlassBackdrop) -> Unit,
    /**
     * Topmost layer of the shell. Anything composed here sits above the sidebar,
     * the bottom navigation and [overlay], so a real modal (scrim + glass panel)
     * can be a sibling of the scene instead of an opaque platform Dialog.
     */
    modal: @Composable (GlassBackdrop) -> Unit = {},
) {
    val backdrop = rememberGlassBackdrop()
    val backgroundBackdrop = rememberGlassBackdrop()
    val windowModalHost = remember { MaFeiWindowModalHostState() }
    val sideScroll = rememberScrollState()
    // Keep form-factor ownership explicit. A page route may hide the phone
    // bottom tabs, but it must never hide the root navigation owned by a
    // tablet/desktop shell.
    val navigationMode = layout.navigationMode
    val phone = navigationMode == MaFeiNavigationMode.PhoneBottom
    val drawer = navigationMode == MaFeiNavigationMode.TabletDrawer
    // Desktop keeps a persistent sidebar. It does not use the tablet/iPadOS
    // collapse-to-top-tabs handoff; the desktop host also owns a minimum size
    // so resizing stays within this shell.
    val desktop = layout.family == MaFeiLayoutFamily.Desktop
    val sidebar = navigationMode == MaFeiNavigationMode.TabletSidebar ||
        navigationMode == MaFeiNavigationMode.DesktopSidebar
    val rail = navigationMode == MaFeiNavigationMode.CompactRail
    val bottomNavigationVisible = phone && showBottomNavigation
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }
    val safeArea = currentMaFeiSafeArea()
    val density = LocalDensity.current
    val sidebarWidthPx = with(density) { MaFeiExpandedSidebarWidthDp.toPx() }
    var topTabsGroupWidthPx by remember { mutableStateOf(0f) }
    LaunchedEffect(navigationMode) {
        // Legacy HomeScreen restores the expanded sidebar when a window enters
        // LargeSidebar from MediumDrawer/phone, rather than retaining a stale
        // collapsed state across a form-factor change.
        if (navigationMode == MaFeiNavigationMode.TabletSidebar ||
            navigationMode == MaFeiNavigationMode.DesktopSidebar
        ) sidebarExpanded = true
        if (navigationMode == MaFeiNavigationMode.CompactRail) sidebarExpanded = false
    }
    val persistentNavigationExpanded = (sidebar || rail) && sidebarExpanded
    // The collapsed large-screen shell always keeps the primary top-tab row.
    // On a secondary route the full-width first row adds a separate back
    // control; the top-tab strip keeps its own expand-sidebar control. The
    // page's ActionBar then supplies the second row of the stacked header.
    // A collapsed tablet/desktop sidebar is fully hidden; the compact icon rail
    // belongs only to CompactRail.
    val topTabsVisible = !desktop && (drawer || sidebar) &&
        (drawer || !sidebarExpanded)
    val leadingEdge = layout.navigationEdge == NavigationEdge.Leading
    // 内容让位量必须**就是面板的宽度**（两处共用 MaFeiExpandedSidebarWidthDp 常量，
    // 避免再漂移）。收起态让位归零：侧栏消失后内容回到全屏位置，顶部胶囊 Tab 是
    // 浮层，不需要内容让它 —— 旧的 12dp 收起让位会让首页左边距塌陷（32dp→15dp）。
    val contentLeadingInset = when {
        persistentNavigationExpanded && leadingEdge -> MaFeiExpandedSidebarWidthDp
        else -> 0.dp
    }
    val contentTrailingInset = when {
        persistentNavigationExpanded && !leadingEdge -> MaFeiExpandedSidebarWidthDp
        rail && !sidebarExpanded && !leadingEdge -> layout.navigationWidthDp.dp
        else -> 0.dp
    }
    // The persistent sidebar and the collapsed top strip are one navigation
    // surface in the large-screen design. Drive both ends of the handoff from
    // the same transition so neither one appears as an unrelated fade.
    val sidebarTransition = updateTransition(
        targetState = sidebarExpanded,
        label = "mafei-sidebar-top-tabs-morph",
    )
    val sidebarMorphProgress by sidebarTransition.animateFloat(
        transitionSpec = { tween(520, easing = FastOutSlowInEasing) },
        label = "mafei-sidebar-morph-progress",
    ) { expanded -> if (expanded) 1f else 0f }
    // Overlap the shape change, the visual handoff and the final travel. Hard
    // phase boundaries made the previous version read as "shrink, then slide".
    // Smooth-step windows keep velocity continuous in both directions.
    val sidebarCollapseProgress = (1f - sidebarMorphProgress).coerceIn(0f, 1f)
    val sidebarPanelMorphProgress = mafeiSmoothStep(sidebarCollapseProgress / .72f)
    val topTabsRevealProgress = mafeiSmoothStep((sidebarCollapseProgress - .18f) / .50f)
    val topTabsTravelProgress = mafeiSmoothStep((sidebarCollapseProgress - .68f) / .32f)
    val sidebarFadeProgress = mafeiSmoothStep((sidebarCollapseProgress - .58f) / .30f)
    // The compact endpoint is the measured top-tab group, not an arbitrary
    // percentage of the sidebar. This keeps the two silhouettes coincident on
    // every window width and for every tab-label combination.
    val compactSidebarWidthProgress = if (topTabsGroupWidthPx > 0f) {
        (topTabsGroupWidthPx / sidebarWidthPx).coerceIn(.18f, 1f)
    } else {
        .18f
    }
    val sidebarPanelWidthProgress =
        1f - (1f - compactSidebarWidthProgress) * sidebarPanelMorphProgress
    val sidebarContentWidthProgress =
        sidebarPanelWidthProgress * (1f - topTabsTravelProgress)
    val sidebarVisualAlpha = 1f - sidebarFadeProgress
    val animatedContentLeadingInset = animateDpAsState(
        targetValue = contentLeadingInset,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "mafei-sidebar-content-inset",
    )
    val animatedContentTrailingInset = animateDpAsState(
        targetValue = contentTrailingInset,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "mafei-navigation-content-inset",
    )
    // A persistent sidebar uses the same progress for its visual fold and for
    // the horizontal content gutter. Keeping this derived from the morph
    // transition prevents the page from lagging one frame behind the chrome.
    val synchronizedContentLeadingInset = if (sidebar && leadingEdge) {
        MaFeiExpandedSidebarWidthDp * sidebarContentWidthProgress
    } else {
        animatedContentLeadingInset.value
    }
    val synchronizedContentTrailingInset = if (sidebar && !leadingEdge) {
        MaFeiExpandedSidebarWidthDp * sidebarContentWidthProgress
    } else {
        animatedContentTrailingInset.value
    }
    val navigationPanelWidth by animateDpAsState(
        targetValue = when {
            sidebar && !sidebarExpanded -> 0.dp
            persistentNavigationExpanded -> MaFeiExpandedSidebarWidthDp
            else -> 56.dp
        },
        // This value also keeps the panel composed until the morph completes.
        // A shorter duration removes the layer early and creates a visible pop.
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "mafei-navigation-panel-width",
    )
    CompositionLocalProvider(
        LocalShellBackdrop provides backdrop,
        LocalShellBackgroundBackdrop provides backgroundBackdrop,
        LocalMaFeiSafeArea provides safeArea,
        LocalMaFeiWindowModalHost provides windowModalHost,
    ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 布局度量（family、列数、contentMaxWidth、hero 判定）只按**全窗口宽**算一次，
        // 与侧栏让位量无关：侧栏是浮在内容区之上的东西，出现时把内容整体挤到右边，
        // 收起时移回来 —— 内容的排版结构不随它重排。让位量仍通过 navigationChrome
        // 下发给页面；背景层（ambient/atmosphere）靠 navigationWidthDp 溢出视口铺满
        // 全窗（收起态让位归零后溢出自然为 0）。
        val targetNavigationGutter = if (leadingEdge) {
            contentLeadingInset
        } else {
            contentTrailingInset
        }
        val routeSafeEdge = if (leadingEdge) safeArea.start else safeArea.end
        val effectiveLayout = remember(
            layout,
            targetNavigationGutter,
            routeSafeEdge,
            maxWidth,
            maxHeight,
        ) {
            layout.withUsableWidth(
                (maxWidth - if (sidebar || rail) routeSafeEdge else 0.dp).value,
            ).copy(navigationWidthDp = targetNavigationGutter.value)
        }
        val navigationChrome = MaFeiNavigationChrome(
            placement = layout.navigation,
            edge = layout.navigationEdge,
            contentLeadingInset = contentLeadingInset,
            contentTrailingInset = contentTrailingInset,
            sidebarExpanded = sidebarExpanded,
            topTabsVisible = topTabsVisible,
            effectiveLayout = effectiveLayout,
            windowWidthDp = maxWidth,
        )
        // The page background and the real scrolling route are recorded together.
        // Navigation remains a sibling consumer, so content moving beneath it is
        // sampled every frame without recursively recording the glass itself.
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            Box(Modifier.fillMaxSize().captureGlassBackdrop(backgroundBackdrop)) {
                background()
            }
                Box(
                    Modifier.fillMaxSize().padding(
                        start = when {
                            phone -> 0.dp
                            leadingEdge -> synchronizedContentLeadingInset + safeArea.start
                            else -> 0.dp
                        },
                        end = when {
                            phone -> 0.dp
                            !leadingEdge -> synchronizedContentTrailingInset + safeArea.end
                            else -> 0.dp
                        },
                    ),
                ) {
                // Persistent navigation chrome owns its physical avoid
                // area. Route content must not consume that same inset again.
                // The route owns content layout, but it must not compose controls
                // underneath that shell-owned floating chrome.
                //
                // 顶部 Tab 条是**浮层**：它浮现/消失时盖在内容上，内容**不做任何
                // 纵向让位** —— 否则侧栏收起（Tab 条随之出现）会把整个内容区往下
                // 推 52dp，看起来就是"内容往左下方跑"。横向让位（侧栏）照旧由
                // 外层 padding 动画负责。
                val routeSafeArea = if (phone) {
                    safeArea
                } else {
                    if (leadingEdge) {
                        safeArea.copy(start = 0.dp)
                    } else {
                        safeArea.copy(end = 0.dp)
                    }
                }
        CompositionLocalProvider(LocalMaFeiSafeArea provides routeSafeArea) {
                    CompositionLocalProvider(LocalMaFeiNavigationChrome provides navigationChrome) {
                        scene()
                    }
                }
            }
        }

        // The sidebar owns custom window controls and dragging. Content reaches the window top;
        // each page handles its own ActionBar and the host's actual safe area, without a second
        // synthetic titlebar or invisible drag target over the right-hand pane.

        // 状态栏的渐进模糊：内容滚动后只处理真实的物理状态栏安全区。
        // 顶部 Tab / ActionBar 是独立的玻璃消费者，不能再在它下面叠一层同色渐变，
        // 否则平板滚动时会出现一条灰色阴影，并把状态栏的层级误读成顶部导航栏。
        //
        // 横向只铺在**内容区**：持久侧栏占据的那一栏没有滚动内容，给它也铺一层会让顶部
        // 看起来整条都在模糊。左右缩进必须与上面内容 Box 的那份完全一致。
        // This fade belongs to the physical system-status-bar avoid area.
        // Undecorated Mac windows and Harmony freeform/PC windows report no
        // top inset; drawing the old 16dp fallback there produced a stray dark
        // band even though no status bar existed.
        if (safeArea.top > 0.dp) {
            TopChromeProgressiveBlur(
                progress = topChromeScrolledProgress,
                backdrop = backdrop,
                topInset = safeArea.top,
                contentStartInset = if (leadingEdge) {
                    synchronizedContentLeadingInset + safeArea.start
                } else {
                    0.dp
                },
                contentEndInset = if (leadingEdge) {
                    0.dp
                } else {
                    synchronizedContentTrailingInset + safeArea.end
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        overlay(backdrop)

        // The sidebar and the compact top strip are one navigation surface.
        // The strip is composed during the handoff, but its actual geometry is
        // transformed inside MediumTopTabs (the measured capsule), not on the
        // full-screen centering container. That keeps both elements in one
        // coordinate system while the sidebar changes shape underneath it.
        val topTabsMorphVisible = drawer || (!desktop &&
            (sidebar && (topTabsVisible || sidebarMorphProgress < .999f)))
        val windowWidthPx = with(density) { maxWidth.toPx() }
        // Keep the panel in the composition for the whole morph. On expansion
        // it must be present on the first frame; on collapse it disappears only
        // after the scale transition has reached its compact endpoint.
        val sidebarPanelVisible = !sidebar || sidebarExpanded || navigationPanelWidth > 0.dp
        if ((sidebar || rail) && sidebarPanelVisible) {
            SidebarPanel(
                destinations = sidebarDestinations,
                libraries = libraries,
                selectedDestination = selectedDestination,
                selectedLibrary = selectedLibrary,
                onDestination = onDestination,
                onLibrary = onLibrary,
                onSearch = onSearch,
                selectedSearch = selectedSearch,
                backdrop = backdrop,
                accountName = accountName,
                accountActions = accountActions,
                sideScroll = sideScroll,
                compact = rail,
                consumeBottomSafeArea = !sidebar,
                showCloseButton = persistentNavigationExpanded && !desktop,
                onClose = { sidebarExpanded = false },
                onExpand = { sidebarExpanded = true },
                windowControls = windowControls,
                windowDragArea = windowDragArea,
                desktop = desktop,
                modifier = Modifier
                    .align(if (leadingEdge) Alignment.TopStart else Alignment.TopEnd)
                    // 面板本身贴满窗口的上下边（安全区不再作为外边距，改成面板**内部**的
                    // 内边距，见 SidebarPanel）：留外边距会让侧栏读成"浮在小窗里的卡片"，
                    // 而不是窗口自己的一栏。左右只让开真正被遮挡的一侧。
                    .padding(
                        start = if (leadingEdge) safeArea.start else 0.dp,
                        end = if (!leadingEdge) safeArea.end else 0.dp,
                    )
                    // Keep the measured panel stable while its layer follows a
                    // bubble-shaped clipping mask into the compact top capsule.
                    // The child layout never scales: text and icons retain their
                    // positions while the lower content is progressively hidden.
                    .width(if (sidebar) MaFeiExpandedSidebarWidthDp else navigationPanelWidth)
                    .graphicsLayer {
                        if (sidebar) {
                            shape = SidebarBubbleMorphShape(
                                progress = sidebarCollapseProgress,
                                compactWidthProgress = compactSidebarWidthProgress,
                                compactTop = safeArea.top,
                                compactHeight = MaFeiTopChromeHeightDp,
                                leadingEdge = leadingEdge,
                            )
                            clip = true
                            alpha = sidebarVisualAlpha
                        }
                    },
            )
        }

        // Draw the measured top-tab capsule above the shrinking panel. During
        // the first phase it grows into the exact rectangle occupied by the
        // panel; during the second phase the panel fades away and this same
        // capsule travels to the window centre. Expansion is the exact reverse.
        if ((drawer || sidebar) && topTabsMorphVisible) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = safeArea.top),
            ) {
                MediumTopTabs(
                    destinations = topTabDestinations,
                    selectedDestination = topTabSelectedDestination,
                    onDestination = onDestination,
                    onSearch = onSearch,
                    selectedSearch = selectedSearch,
                    onBack = onTopBarBack.takeIf { contentNavigationActive },
                    onOpenDrawer = {
                        if (drawer) drawerOpen = true else sidebarExpanded = true
                    },
                    backdrop = backdrop,
                    morphModifier = if (sidebar && !drawer) {
                        Modifier
                            .alpha(topTabsRevealProgress)
                            .onSizeChanged { topTabsGroupWidthPx = it.width.toFloat() }
                            .graphicsLayer {
                                // Transform the real measured capsule. Its
                                // edge position is therefore the same edge
                                // reached by the sidebar's leading transform.
                                val travel = ((windowWidthPx - size.width) / 2f)
                                    .coerceAtLeast(0f)
                                translationX = if (leadingEdge) {
                                    -travel * (1f - topTabsTravelProgress)
                                } else {
                                    travel * (1f - topTabsTravelProgress)
                                }
                            }
                    } else {
                        Modifier
                    },
                )
            }
        }

        if (drawer) {
            // The scrim is stationary: dim the whole window before the panel
            // enters, and retain the dimming until the panel finishes leaving.
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(120, delayMillis = 280)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .50f))
                        .semantics { contentDescription = "关闭侧边栏" }
                        .mafeiInteractive(
                            { drawerOpen = false }, label = "关闭侧边栏", visualFeedback = false,
                        ),
                )
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(tween(280, delayMillis = 100)) { -it },
                exit = slideOutHorizontally(tween(280)) { -it },
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = safeArea.start).width(240.dp).fillMaxHeight(),
            ) {
                    SidebarPanel(
                        destinations = sidebarDestinations,
                        libraries = libraries,
                        selectedDestination = selectedDestination,
                        selectedLibrary = selectedLibrary,
                        onDestination = {
                            onDestination(it)
                            drawerOpen = false
                        },
                        onLibrary = {
                            onLibrary(it)
                            drawerOpen = false
                        },
                        onSearch = onSearch?.let { action ->
                            {
                                action()
                                drawerOpen = false
                            }
                        },
                        selectedSearch = selectedSearch,
                        backdrop = backdrop,
                        accountName = accountName,
                        accountActions = accountActions,
                        sideScroll = sideScroll,
                        compact = false,
                        consumeBottomSafeArea = true,
                        showCloseButton = false,
                        onClose = {},
                        onExpand = {},
                        windowControls = {},
                        windowDragArea = { content -> content() },
                        desktop = false,
                        modifier = Modifier.fillMaxSize().testTag("drawerSidebarPanel"),
                    )
            }
        }

        if (bottomNavigationVisible) {
            val docked = bottomNavigationType == BottomNavigationType.Bar
            BottomNavigationLayer(
                destinations = destinations,
                selectedDestination = selectedDestination,
                onDestination = onDestination,
                backdrop = backdrop,
                compactProgress = bottomNavigationCompactProgress,
                type = bottomNavigationType,
                bottomInset = safeArea.bottom,
                modifier = Modifier.align(Alignment.BottomCenter).padding(
                    start = if (docked) 0.dp else safeArea.start + 18.dp,
                    end = if (docked) 0.dp else safeArea.end + 18.dp,
                    top = if (docked) 0.dp else 14.dp,
                    bottom = if (docked) 0.dp else safeArea.bottom,
                )
                    .then(if (docked) Modifier else Modifier.widthIn(max = 430.dp)).fillMaxWidth(),
            )
        }

        windowModalHost.entry?.let { entry ->
            CompositionLocalProvider(
                LocalMaFeiNavigationChrome provides navigationChrome,
                LocalMaFeiSafeArea provides safeArea,
            ) {
                Box(Modifier.fillMaxSize()) {
                    entry.content(this, backdrop)
                }
            }
        }

        modal(backdrop)
    }
    }
}
