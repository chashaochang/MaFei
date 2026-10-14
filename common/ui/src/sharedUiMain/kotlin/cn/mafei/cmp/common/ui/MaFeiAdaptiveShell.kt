package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import cn.mafei.cmp.common.glass.components.LiquidSelectionDroplet
import cn.mafei.cmp.common.glass.components.mafeiProgressiveBlur
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.glass.components.LiquidBottomTab
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
import cn.mafei.cmp.common.glass.components.LiquidBottomTabsStyle
import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.ic_home_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_home_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_favorite_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_favorite_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_media_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_media_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_mine_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_mine_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_search
import cn.mafei.cmp.common.ui.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

data class MaFeiNavItem(val key: String, val label: String, val glyph: MaFeiGlyph)

/** Runtime navigation geometry. A window breakpoint alone cannot describe a collapsed sidebar. */
data class MaFeiNavigationChrome(
    val placement: NavigationPlacement,
    val edge: NavigationEdge,
    val contentLeadingInset: Dp,
    val contentTrailingInset: Dp,
    val sidebarExpanded: Boolean = true,
    /** Whether the shell is currently presenting the collapsed primary top bar. */
    val topTabsVisible: Boolean = false,
    val effectiveLayout: MaFeiLayout? = null,
    val windowWidthDp: Dp? = null,
)

val LocalMaFeiNavigationChrome = staticCompositionLocalOf {
    MaFeiNavigationChrome(NavigationPlacement.Bottom, NavigationEdge.Leading, 0.dp, 0.dp)
}

/** Window background + scene, for shell overlays only; scene descendants must not sample this layer. */
val LocalShellBackdrop = staticCompositionLocalOf<cn.mafei.cmp.common.glass.GlassBackdrop?> { null }

/** Background only, safe to combine with a page's scrolling-content layer without self-sampling. */
val LocalShellBackgroundBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

internal data class MaFeiWindowModalEntry(
    val owner: Any,
    val content: @Composable BoxScope.(GlassBackdrop) -> Unit,
)

/**
 * Window-level modal portal shared by pages hosted inside a navigation pane.
 * Pages publish modal content here; [MaFeiAdaptiveShell] renders it after the
 * sidebar/top bar so a large-screen dialog is centered in the whole window.
 */
class MaFeiWindowModalHostState internal constructor() {
    internal var entry by mutableStateOf<MaFeiWindowModalEntry?>(null)
        private set

    internal fun present(
        owner: Any,
        content: @Composable BoxScope.(GlassBackdrop) -> Unit,
    ) {
        entry = MaFeiWindowModalEntry(owner, content)
    }

    internal fun dismiss(owner: Any) {
        if (entry?.owner === owner) entry = null
    }
}

val LocalMaFeiWindowModalHost = staticCompositionLocalOf<MaFeiWindowModalHostState?> { null }

/** Publishes [content] into the shell's topmost modal layer while [visible]. */
@Composable
fun MaFeiWindowModalPortal(
    visible: Boolean,
    content: @Composable BoxScope.(GlassBackdrop) -> Unit,
) {
    val host = LocalMaFeiWindowModalHost.current
    val owner = remember { Any() }
    val latestContent = rememberUpdatedState(content)
    DisposableEffect(host, owner, visible) {
        if (host != null && visible) {
            host.present(owner) { backdrop -> latestContent.value(this, backdrop) }
        }
        onDispose { host?.dismiss(owner) }
    }
}

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
                        top = if (desktop) MaFeiNavigationControlSizeDp else 0.dp,
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

        // The undecorated Mac window has no native title bar. Reserve the same
        // 44dp strip used by the sidebar header on the content side as a real
        // drag surface. Keeping it outside the scene means a pointer on the
        // hero artwork still clicks the artwork instead of being interpreted as
        // a window move; the artwork itself starts immediately below the strip.
        if (desktop) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = synchronizedContentLeadingInset + safeArea.start)
                    .fillMaxWidth()
                    .height(MaFeiNavigationControlSizeDp),
            ) {
                windowDragArea {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }

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
                        .mafeiInteractive({ drawerOpen = false }, label = "关闭侧边栏"),
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
                        modalDrawer = true,
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
private fun TopChromeProgressiveBlur(
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
private fun SidebarPanel(
    destinations: List<MaFeiNavItem>,
    libraries: List<MaFeiNavItem>,
    selectedDestination: String,
    selectedLibrary: String?,
    onDestination: (String) -> Unit,
    onLibrary: (String) -> Unit,
    onSearch: (() -> Unit)?,
    selectedSearch: Boolean,
    backdrop: GlassBackdrop,
    accountName: String?,
    accountActions: @Composable () -> Unit,
    sideScroll: ScrollState,
    compact: Boolean,
    consumeBottomSafeArea: Boolean,
    showCloseButton: Boolean,
    onClose: () -> Unit,
    onExpand: () -> Unit,
    windowControls: @Composable RowScope.() -> Unit,
    windowDragArea: @Composable (@Composable () -> Unit) -> Unit,
    desktop: Boolean,
    modalDrawer: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 媒体库列表的展开/收起由侧边栏自己持有，不上升为外壳状态。
    var librariesExpanded by rememberSaveable { mutableStateOf(true) }
    // 面板贴满窗口上下边，安全区改由内容自己让开（内边距）。这里能读到窗口的原始安全区，
    // 因为外壳在 scene 之前就提供了它，而侧栏在 routeSafeArea 之外。
    val windowSafeArea = LocalMaFeiSafeArea.current
    // The panel exports the material it actually drew. Header controls must sample
    // page + panel; sampling the route backdrop alone visually drills through the
    // sidebar and reproduces the media artwork underneath it.
    val sidebarMaterialBackdrop = rememberGlassBackdrop()
    val sidebarControlBackdrop = rememberCombinedGlassBackdrop(backdrop, sidebarMaterialBackdrop)
    MaFeiSurface(
        GlassSurfaceSpec(
            GlassRole.Navigation,
            if (modalDrawer) GlassLevel.Opaque else GlassLevel.Full,
            // 整块侧栏（含顶部 logo 区与底部账号区）就是这一种材质，顶/底不再
            // 叠任何额外玻璃层；滚进来的列表项由滚动区的上下渐隐淡出。
            tintAlpha = if (modalDrawer) 1f else .16f,
            intensity = 1f,
            // 贴窗口边缘的整块面板：不要圆角、不要液态玻璃的折射与高光描边，只做高斯模糊
            // + 染色。折射会在四条边上各画一条可见的线（读成"浮起来的卡片"），而整屏高度
            // 的折射没有视觉收益；这一栏与内容之间只留一条分界线（见下）。
            cornerRadiusDp = 0f,
            material = GlassMaterial.Frosted,
        ),
        modifier.fillMaxHeight(),
        // A modal drawer is an ordinary theme panel. Sampling the page here
        // bypasses the scrim and makes the sidebar appear to shine through it.
        if (modalDrawer) null else backdrop,
        exportedBackdrop = sidebarMaterialBackdrop,
    ) {
        Box(Modifier.fillMaxSize()) {
        // 整块侧栏是**一种材质**（面板的磨砂玻璃），顶/底不再叠任何玻璃层。
        // 滚动内容由固定视口内边距让开 logo / 账号卡片，因此不再给整条侧栏
        // 分配全尺寸 Offscreen + DstIn 遮罩。大屏滚动时这层离屏合成成本远高于
        // 它实际提供的视觉收益。
        val sidebarHeaderInset = 48.dp
        val sidebarBottomInset = if (compact) 64.dp else 88.dp
        Column(
            // The wide persistent sidebar already owns a fixed-height account
            // entry at the bottom. Adding the tablet bottom safe area to that
            // fixed block made the account region visibly too tall; drawer/rail
            // variants still consume it because their chrome floats over content.
            Modifier.fillMaxSize().padding(
                // Match the 44dp control's 10dp inset in MaFeiActionBar. The
                // sidebar header is the same navigation row, not a separate
                // 40dp toolbar with a smaller hit target.
                top = windowSafeArea.top + if (desktop) 2.dp else MaFeiActionBarVerticalInsetDp,
                bottom = (if (consumeBottomSafeArea) windowSafeArea.bottom else 0.dp) + 8.dp,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
            Column(
                // padding 必须在 verticalScroll **之外**：否则让位内边距会变成
                // 可滚走的滚动内容，列表能一路滚到面板最底、钻进账号卡片下面
                // （文字叠在一起）。放在外面 = 固定的视口内边距。
                Modifier.fillMaxSize().padding(
                    top = sidebarHeaderInset,
                    bottom = sidebarBottomInset,
                    start = if (compact) 8.dp else 12.dp,
                    end = if (compact) 8.dp else 12.dp,
                ).verticalScroll(sideScroll),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                onSearch?.let { action ->
                    NavigationEntry(
                        MaFeiNavItem("search", "搜索", MaFeiGlyph.Search),
                        selectedSearch,
                    ) { action() }
                }
                // "mine" is pinned above the account block at the bottom of the
                // panel, so it must not be listed a second time up here.
                destinations.filter { it.key != "library" && it.key != "mine" }.forEach { item ->
                    NavigationEntry(item, selectedDestination == item.key, compact) { onDestination(item.key) }
                }
                if (libraries.isNotEmpty() && !compact) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = 10.dp, end = 2.dp, top = 20.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "我的媒体库",
                            Modifier.weight(1f),
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                        )
                        // 收起/展开媒体库列表。箭头默认朝下（展开态），收起后转向右。
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .semantics {
                                    contentDescription =
                                        if (librariesExpanded) "收起媒体库" else "展开媒体库"
                                }
                                .mafeiInteractive(
                                    onClick = { librariesExpanded = !librariesExpanded },
                                    radiusDp = 14f,
                                    label = if (librariesExpanded) "收起媒体库" else "展开媒体库",
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(MaFeiLegacyDrawables.arrowRight),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                                    .rotate(if (librariesExpanded) 90f else 0f)
                                    .alpha(.72f),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                    AnimatedVisibility(visible = librariesExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            libraries.forEach { item ->
                                NavigationEntry(
                                    item,
                                    selectedDestination == "library" && selectedLibrary == item.key,
                                ) { onLibrary(item.key) }
                            }
                        }
                    }
                }
            }

            // —— 浮层：logo 头部 —— 只有内容，没有额外玻璃层（整块侧栏已经是
            // 同一种材质）；滚进来的列表项由上面的渐隐遮罩淡出。
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                if (compact) {
                    Box(
                        Modifier.fillMaxWidth().height(MaFeiNavigationControlSizeDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 展开按钮：液态玻璃圆钮（侧栏在 backdrop 记录者之外，
                        // 这里是合法消费者）。
                        MaFeiActionBarButton(
                            onClick = onExpand,
                            backdrop = sidebarControlBackdrop,
                            contentDescription = "展开侧边栏",
                        ) {
                            SidebarIcon(
                                Modifier.size(MaFeiNavigationControlIconSizeDp),
                                MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth()
                            .height(if (desktop) 36.dp else MaFeiNavigationControlSizeDp)
                            .padding(horizontal = if (desktop) 10.dp else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (desktop) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Window controls must remain outside the draggable surface.
                        // WindowDraggableArea consumes the pointer stream by design;
                        // wrapping this whole row made the Mac traffic lights look
                        // clickable while swallowing their hover/click events.
                        windowControls()
                        Image(
                            painterResource(Res.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(if (desktop) 24.dp else 32.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            "马飞",
                            fontSize = if (desktop) 14.sp else 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(if (desktop) 4.dp else 8.dp))
                        // Only the empty remainder is draggable. The logo, title,
                        // close button and traffic lights keep their own input.
                        windowDragArea {
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                        }
                        if (showCloseButton) {
                            MaFeiActionBarButton(
                                onClick = onClose,
                                backdrop = sidebarControlBackdrop,
                                contentDescription = "收起侧边栏",
                            ) {
                                SidebarIcon(
                                    Modifier.size(MaFeiNavigationControlIconSizeDp),
                                    MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            // —— 浮层：底部「我的」与账号区 —— 同样只有内容，无额外玻璃层。
            // 「我的」上方不再画分割线（用户要求去掉）。
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(top = 24.dp)
                    .padding(horizontal = if (compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 账号卡片（「我的」入口）：头像 + 当前登录用户名，高度是普通
                // 导航条目的两倍、内容垂直居中 —— 比一个「我的」文字条目更
                // 能说明现在是谁在登录。收起态（compact）只留头像。
                if (compact) {
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .semantics { contentDescription = "我的" }
                                .mafeiInteractive(
                                    onClick = { onDestination("mine") },
                                    radiusDp = 20f,
                                    label = "我的",
                                ),
                        ) {
                            Image(
                                painter = painterResource(MaFeiLegacyDrawables.defaultAvatar),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                } else {
                    val mineSelected = selectedDestination == "mine"
                    Row(
                        Modifier.fillMaxWidth().height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (mineSelected) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .semantics { contentDescription = "我的" }
                            .mafeiInteractive(
                                onClick = { onDestination("mine") },
                                radiusDp = 12f,
                                label = "我的",
                            )
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape)) {
                            Image(
                                painter = painterResource(MaFeiLegacyDrawables.defaultAvatar),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            accountName?.takeIf { it.isNotBlank() } ?: "我的",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!compact) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { accountActions() }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun MediumTopTabs(
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

private fun mafeiSmoothStep(value: Float): Float {
    val progress = value.coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

/**
 * Clips the fixed sidebar content with an organic bubble that is pulled into
 * the top navigation capsule. Nothing inside the panel is scaled or moved:
 * lower rows disappear behind the rising mask and reappear in place when the
 * sidebar expands.
 */
private data class SidebarBubbleMorphShape(
    val progress: Float,
    val compactWidthProgress: Float,
    val compactTop: Dp,
    val compactHeight: Dp,
    val leadingEdge: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val collapse = progress.coerceIn(0f, 1f)
        val widthProgress = mafeiSmoothStep((collapse - .06f) / .78f)
        // The bottom is pulled away before the top edge moves, which hides the
        // lower rows instead of compressing them into the remaining space.
        val bottomProgress = mafeiSmoothStep(collapse / .76f)
        val topProgress = mafeiSmoothStep((collapse - .12f) / .72f)
        val roundProgress = mafeiSmoothStep((collapse - .10f) / .70f)

        val targetTop = with(density) { compactTop.toPx() }
        val targetHeight = with(density) { compactHeight.toPx() }
        val targetBottom = targetTop + targetHeight
        val targetWidth = size.width * compactWidthProgress.coerceIn(.18f, 1f)

        val top = targetTop * topProgress
        val bottom = size.height + (targetBottom - size.height) * bottomProgress
        val width = size.width + (targetWidth - size.width) * widthProgress
        val height = (bottom - top).coerceAtLeast(1f)
        val radius = (kotlin.math.min(width, height) / 2f) * roundProgress
        // The free edge bows towards the page during the middle of the morph.
        // It returns to zero at both endpoints, leaving a rectangle expanded
        // and a capsule collapsed.
        val bell = 4f * collapse * (1f - collapse)
        val bulge = with(density) { 34.dp.toPx() } * bell
        val kappa = .5522848f

        fun physicalX(logicalX: Float): Float =
            if (leadingEdge) logicalX else size.width - logicalX

        val path = Path()
        path.moveTo(physicalX(radius), top)
        path.lineTo(physicalX(width - radius), top)
        path.cubicTo(
            physicalX(width - radius + radius * kappa),
            top,
            physicalX(width),
            top + radius - radius * kappa,
            physicalX(width),
            top + radius,
        )

        val middle = top + height / 2f
        path.cubicTo(
            physicalX(width + bulge * .55f),
            top + height * .24f,
            physicalX(width + bulge),
            top + height * .36f,
            physicalX(width + bulge),
            middle,
        )
        path.cubicTo(
            physicalX(width + bulge),
            top + height * .64f,
            physicalX(width + bulge * .55f),
            bottom - height * .24f,
            physicalX(width),
            bottom - radius,
        )
        path.cubicTo(
            physicalX(width),
            bottom - radius + radius * kappa,
            physicalX(width - radius + radius * kappa),
            bottom,
            physicalX(width - radius),
            bottom,
        )
        path.lineTo(physicalX(radius), bottom)
        path.cubicTo(
            physicalX(radius - radius * kappa),
            bottom,
            physicalX(0f),
            bottom - radius + radius * kappa,
            physicalX(0f),
            bottom - radius,
        )
        path.lineTo(physicalX(0f), top + radius)
        path.cubicTo(
            physicalX(0f),
            top + radius - radius * kappa,
            physicalX(radius - radius * kappa),
            top,
            physicalX(radius),
            top,
        )
        path.close()
        return Outline.Generic(path)
    }
}

/** 顶部导航区高度：Tab 条在这个高度里垂直居中，二级页双行头部也以它为单位（×2）。 */
internal val MaFeiTopChromeHeightDp = 52.dp

/**
 * 展开态侧栏（大屏常驻侧栏）的宽度。内容让位量必须与它相等 —— 面板贴窗口边缘通栏、
 * 没有悬浮边距，所以"面板宽度"和"内容起点"是同一个数。
 */
internal val MaFeiExpandedSidebarWidthDp = MaFeiPersistentSidebarWidthDp

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

@Composable
private fun SidebarIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        val outlineLeft = 3.dp.toPx()
        val outlineTop = 4.dp.toPx()
        val dividerX = size.width * .68f
        drawRoundRect(
            color,
            topLeft = Offset(outlineLeft, outlineTop),
            size = Size(size.width - outlineLeft * 2f, size.height - outlineTop * 2f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(color, Offset(dividerX, 5.dp.toPx()), Offset(dividerX, size.height - 5.dp.toPx()), stroke)
    }
}

/** Keep scroll-driven dock morphing out of the shell's scene composition. */
@Composable
private fun BottomNavigationLayer(
    destinations: List<MaFeiNavItem>,
    selectedDestination: String,
    onDestination: (String) -> Unit,
    backdrop: GlassBackdrop,
    compactProgress: () -> Float,
    type: BottomNavigationType,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = destinations.indexOfFirst { it.key == selectedDestination }.coerceAtLeast(0)
    val resolvedCompactProgress = if (type == BottomNavigationType.Bar) 0f else compactProgress().coerceIn(0f, 1f)
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = { index -> destinations.getOrNull(index)?.let { onDestination(it.key) } },
        backdrop = backdrop,
        tabsCount = destinations.size,
        widthBaselineTabCount = 4,
        compactProgress = { resolvedCompactProgress },
        style = if (type == BottomNavigationType.Bar) LiquidBottomTabsStyle.Bar else LiquidBottomTabsStyle.Standard,
        bottomInset = bottomInset,
        modifier = modifier,
    ) {
        destinations.forEachIndexed { index, item ->
            val selected = selectedDestination == item.key
            LiquidBottomTab(
                onClick = { onDestination(item.key) },
                index = index,
                isSelected = selected,
                collapseProgress = resolvedCompactProgress,
            ) {
                NavigationIcon(
                    glyph = item.glyph,
                    selected = selected,
                    modifier = Modifier.size(22.dp),
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NavigationEntry(
    item: MaFeiNavItem,
    isSelected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    // Legacy HomeSidebarEntry always uses text_primary for actionable rows;
    // secondary gray is reserved for non-interactive section titles.
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(if (compact) 12.dp else 8.dp)
    val modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(shape)
        .background(
            // 选中底色必须在深色与浅色主题下都能看出来：深色提亮（白）、浅色压暗
            // （黑）。原来两端都用 Color.Black —— 深色背景上黑叠黑等于没有选中的
            // 视觉反馈（用户反馈"在深色桌面上基本看不出来"）。
            if (isSelected) {
                if (dark) Color.White.copy(alpha = .16f) else Color.Black.copy(alpha = .10f)
            } else Color.Transparent,
        )
        .semantics { selected = isSelected }
        .mafeiInteractive(onClick, radiusDp = if (compact) 12f else 8f, label = item.label)
    if (compact) {
        Box(
            modifier.height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            NavigationIcon(item.glyph, isSelected, Modifier.size(20.dp), color)
        }
    } else {
        Row(
            modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavigationIcon(item.glyph, isSelected, Modifier.size(20.dp), color)
            Text(item.label, fontSize = 14.sp, lineHeight = 20.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Uses the original HarmonyOS navigation artwork instead of a newly drawn approximation. */
@Composable
private fun NavigationIcon(
    glyph: MaFeiGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val resource = when (glyph) {
        MaFeiGlyph.Home -> if (selected) Res.drawable.ic_home_sel else Res.drawable.ic_home_unsel
        MaFeiGlyph.Favorite -> if (selected) Res.drawable.ic_favorite_sel else Res.drawable.ic_favorite_unsel
        MaFeiGlyph.Library -> if (selected) Res.drawable.ic_media_sel else Res.drawable.ic_media_unsel
        MaFeiGlyph.Account -> if (selected) Res.drawable.ic_mine_sel else Res.drawable.ic_mine_unsel
        MaFeiGlyph.Search -> Res.drawable.ic_search
        else -> null
    }
    if (resource == null) {
        MaFeiIcon(glyph, modifier, color)
    } else {
        Icon(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = modifier.size(24.dp),
            tint = color,
        )
    }
}
