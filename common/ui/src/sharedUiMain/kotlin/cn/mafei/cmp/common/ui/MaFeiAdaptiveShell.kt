package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import cn.mafei.cmp.common.glass.components.LiquidSelectionDroplet
import cn.mafei.cmp.common.glass.components.mafeiProgressiveBlur
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidBottomTab
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
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
    val effectiveLayout: MaFeiLayout? = null,
    val windowWidthDp: Dp? = null,
)

val LocalMaFeiNavigationChrome = staticCompositionLocalOf {
    MaFeiNavigationChrome(NavigationPlacement.Bottom, NavigationEdge.Leading, 0.dp, 0.dp)
}

/** shell 根部的玻璃记录层：窗口背景（含首页轮播背景）与全部内容的采样源。
 * 页面里的玻璃组件用它，而不是 scene 局部捕获层 —— 背景提升到窗口画布后，
 * 只有根层能同时采到背景与内容。shell 之外为 null。 */
val LocalShellBackdrop = staticCompositionLocalOf<cn.mafei.cmp.common.glass.GlassBackdrop?> { null }

/** Geometry-only shell; session, navigation state and scroll state belong to caller.
 * Background is recorded once, separately from all its glass consumers.
 */
@Composable
fun MaFeiAdaptiveShell(
    layout: MaFeiLayout,
    destinations: List<MaFeiNavItem>,
    selectedDestination: String,
    onDestination: (String) -> Unit,
    libraries: List<MaFeiNavItem>,
    selectedLibrary: String?,
    onLibrary: (String) -> Unit,
    accountLabel: String,
    showBottomNavigation: Boolean = true,
    onSearch: (() -> Unit)? = null,
    selectedSearch: Boolean = false,
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
    val sideScroll = rememberScrollState()
    val sidebar = layout.navigation == NavigationPlacement.Sidebar
    val drawer = layout.navigation == NavigationPlacement.Drawer
    val rail = layout.navigation == NavigationPlacement.Rail
    val bottomNavigationVisible = layout.navigation == NavigationPlacement.Bottom && showBottomNavigation
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }
    val safeArea = currentMaFeiSafeArea()
    LaunchedEffect(layout.navigation) {
        // Legacy HomeScreen restores the expanded sidebar when a window enters
        // LargeSidebar from MediumDrawer/phone, rather than retaining a stale
        // collapsed state across a form-factor change.
        if (layout.navigation == NavigationPlacement.Sidebar) sidebarExpanded = true
        if (layout.navigation == NavigationPlacement.Rail) sidebarExpanded = false
    }
    val persistentNavigationExpanded = (sidebar || rail) && sidebarExpanded
    /** 顶部 Tab 条是否在场：中屏（Drawer）与收起态的大屏侧栏都走它。 */
    val topTabsVisible = (drawer || sidebar) && showBottomNavigation && (drawer || !sidebarExpanded)
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
    val animatedContentLeadingInset = animateDpAsState(
        targetValue = contentLeadingInset,
        animationSpec = tween(300),
        label = "mafei-sidebar-content-inset",
    )
    val animatedContentTrailingInset = animateDpAsState(
        targetValue = contentTrailingInset,
        animationSpec = tween(300),
        label = "mafei-navigation-content-inset",
    )
    val navigationPanelWidth by animateDpAsState(
        targetValue = when {
            sidebar && !sidebarExpanded -> 0.dp
            persistentNavigationExpanded -> MaFeiExpandedSidebarWidthDp
            else -> 56.dp
        },
        animationSpec = tween(300),
        label = "mafei-navigation-panel-width",
    )
    CompositionLocalProvider(LocalShellBackdrop provides backdrop, LocalMaFeiSafeArea provides safeArea) {
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
            effectiveLayout = effectiveLayout,
            windowWidthDp = maxWidth,
        )
        // The page background and the real scrolling route are recorded together.
        // Navigation remains a sibling consumer, so content moving beneath it is
        // sampled every frame without recursively recording the glass itself.
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            background()
            Box(
                Modifier.fillMaxSize().padding(
                    start = when {
                        layout.navigation == NavigationPlacement.Bottom -> 0.dp
                        leadingEdge -> animatedContentLeadingInset.value + safeArea.start
                        else -> 0.dp
                    },
                    end = when {
                        layout.navigation == NavigationPlacement.Bottom -> 0.dp
                        !leadingEdge -> animatedContentTrailingInset.value + safeArea.end
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
                val routeSafeArea = if (layout.navigation == NavigationPlacement.Bottom) {
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

        // 顶部导航区的毛玻璃：内容滚过状态栏之后才浮现（`opacity = 已滚动进度`）。
        // 画在捕获层之后、导航之前，所以它模糊的是**真实的滚动内容**（海报、标题），
        // 而不是往上盖一块灰色。高度取"状态栏 + 顶部导航栏"，Tab 形态下正好覆盖 Tab 条
        // 身后的整条区域，让内容从 Tab 条下面滚过去。
        //
        // 横向只铺在**内容区**：持久侧栏占据的那一栏没有滚动内容，给它也铺一层会让顶部
        // 看起来整条都在模糊。左右缩进必须与上面内容 Box 的那份完全一致。
        TopChromeProgressiveBlur(
            progress = topChromeScrolledProgress,
            backdrop = backdrop,
            height = safeArea.top + if (topTabsVisible) MaFeiTopChromeHeightDp else 16.dp,
            contentStartInset = if (leadingEdge) {
                animatedContentLeadingInset.value + safeArea.start
            } else {
                0.dp
            },
            contentEndInset = if (leadingEdge) {
                0.dp
            } else {
                animatedContentTrailingInset.value + safeArea.end
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        overlay(backdrop)

        val sidebarPanelVisible = !(sidebar && navigationPanelWidth <= 0.dp)
        if ((sidebar || rail) && sidebarPanelVisible) {
            SidebarPanel(
                destinations = destinations,
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
                compact = rail && !sidebarExpanded,
                showCloseButton = persistentNavigationExpanded,
                onClose = { sidebarExpanded = false },
                onExpand = { sidebarExpanded = true },
                modifier = Modifier
                    .align(if (leadingEdge) Alignment.TopStart else Alignment.TopEnd)
                    // 面板本身贴满窗口的上下边（安全区不再作为外边距，改成面板**内部**的
                    // 内边距，见 SidebarPanel）：留外边距会让侧栏读成"浮在小窗里的卡片"，
                    // 而不是窗口自己的一栏。左右只让开真正被遮挡的一侧。
                    .padding(
                        start = if (leadingEdge) safeArea.start else 0.dp,
                        end = if (!leadingEdge) safeArea.end else 0.dp,
                    )
                    .width(navigationPanelWidth),
            )
        }

        if ((drawer || sidebar) && showBottomNavigation) {
            AnimatedVisibility(
                visible = drawer || !sidebarExpanded,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = safeArea.top,
                    ),
            ) {
                MediumTopTabs(
                    destinations = destinations,
                    selectedDestination = selectedDestination,
                    onDestination = onDestination,
                    onOpenDrawer = {
                        if (drawer) drawerOpen = true else sidebarExpanded = true
                    },
                    backdrop = backdrop,
                )
            }
        }

        if (drawer) {
            // Keep the modal above MediumTopTabs. The legacy Drawer opens a
            // full-screen scrim with a 240dp panel, never an embedded sheet.
            AnimatedVisibility(
                visible = drawerOpen && showBottomNavigation,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .50f))
                        .semantics { contentDescription = "关闭侧边栏" }
                        .mafeiInteractive({ drawerOpen = false }, label = "关闭侧边栏"),
                ) {
                    SidebarPanel(
                        destinations = destinations,
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
                        showCloseButton = false,
                        onClose = {},
                        onExpand = {},
                        modifier = Modifier
                            .testTag("drawerSidebarPanel")
                            .align(Alignment.TopStart)
                            // 与常驻侧栏同一条规则：面板贴满上下边，安全区改成内部内边距。
                            .padding(start = safeArea.start)
                            .width(240.dp),
                    )
                }
            }
        }

        if (bottomNavigationVisible) {
            BottomNavigationLayer(
                destinations = destinations,
                selectedDestination = selectedDestination,
                onDestination = onDestination,
                backdrop = backdrop,
                compactProgress = bottomNavigationCompactProgress,
                modifier = Modifier.align(Alignment.BottomCenter).padding(
                    start = safeArea.start + 18.dp,
                    end = safeArea.end + 18.dp,
                    top = 14.dp,
                    bottom = safeArea.bottom,
                )
                    .widthIn(max = 430.dp).fillMaxWidth(),
            )
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
    height: Dp,
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
            .padding(start = contentStartInset, end = contentEndInset)
            .height(height)
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
    showCloseButton: Boolean,
    onClose: () -> Unit,
    onExpand: () -> Unit,
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
            GlassLevel.Full,
            // 整块侧栏（含顶部 logo 区与底部账号区）就是这一种材质，顶/底不再
            // 叠任何额外玻璃层；滚进来的列表项由滚动区的上下渐隐淡出。
            tintAlpha = .16f,
            intensity = 1f,
            // 贴窗口边缘的整块面板：不要圆角、不要液态玻璃的折射与高光描边，只做高斯模糊
            // + 染色。折射会在四条边上各画一条可见的线（读成"浮起来的卡片"），而整屏高度
            // 的折射没有视觉收益；这一栏与内容之间只留一条分界线（见下）。
            cornerRadiusDp = 0f,
            material = GlassMaterial.Frosted,
        ),
        modifier.fillMaxHeight(),
        backdrop,
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
            // 顶部与底部都消费窗口安全区，再各留 8dp 呼吸。底部手势条即使透明也仍是
            // 系统交互区域，账号入口不能压进去；此前只留 8dp 会让 MatePad 上 80dp
            // 账号卡片进入手势区，滚动到尾部时尤其像是整块侧栏错位。
            Modifier.fillMaxSize().padding(
                top = windowSafeArea.top + 8.dp,
                bottom = windowSafeArea.bottom + 8.dp,
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
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        // 展开按钮：液态玻璃圆钮（侧栏在 backdrop 记录者之外，
                        // 这里是合法消费者）。
                        LiquidButton(
                            onClick = onExpand,
                            backdrop = sidebarControlBackdrop,
                            modifier = Modifier.size(40.dp)
                                .semantics { contentDescription = "展开侧边栏" },
                            height = 40.dp,
                            horizontalPadding = 0.dp,
                        ) {
                            SidebarIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painterResource(Res.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Text(
                            "马飞",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Spacer(Modifier.weight(1f))
                        if (showCloseButton) {
                            LiquidButton(
                                onClick = onClose,
                                backdrop = sidebarControlBackdrop,
                                modifier = Modifier.size(40.dp)
                                    .semantics { contentDescription = "收起侧边栏" },
                                height = 40.dp,
                                horizontalPadding = 0.dp,
                            ) {
                                SidebarIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
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
    onOpenDrawer: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val testModifier = modifier.testTag("mediumTopTabs")
    val selectedIndex = destinations.indexOfFirst { it.key == selectedDestination }.coerceAtLeast(0)
    val indicatorScope = rememberCoroutineScope()
    // 选中液滴是一层真玻璃，采样的源必须是"页面 + 本条导航栏自己的材质"。少了后者，液滴
    // 底下会直接透出滚动列表的内容 —— 看起来就是选中项"没有背景"。
    //
    // 导出层由一个**没有子节点**的材质层承担：导出会记录该子树的全部绘制，液滴若也在被
    // 记录的子树里，源里就包含"读取自己"，skiko 的 drawable 图会递归到栈溢出
    // （`GlassSurface` 文档：never record a consumer into its own source）。这里材质层为空、
    // 液滴是它的兄弟，所以安全。
    val stripMaterial = rememberGlassBackdrop()
    val dropletBackdrop = rememberCombinedGlassBackdrop(backdrop, stripMaterial)
    val lastIndex = (destinations.size - 1).coerceAtLeast(0)
    // 指示器的手势挂在**每个 Tab** 上（液滴画在标签下面，拿不到事件），所以拖拽量要按
    // Tab 的等宽换算成"走了几格"。按住时先出现液滴（`press()`），再横向拖动 → 液滴跟着走
    // 并按速度拉伸，松手吸附到最近一格并选中它 —— 与底部导航的手感一致。
    val tabWidthPx = with(LocalDensity.current) { MediumTopTabWidth.toPx() }
    val latestOnDestination by rememberUpdatedState(onDestination)
    val latestKeys = rememberUpdatedState(destinations.map { it.key })
    val indicator = remember(indicatorScope, destinations.size) {
        // 只在**真的拖动过**时才在松手时改选中：单击也会走 onDragStart/onDragStopped
        // （`inspectDragGestures` 不设滑动阈值），那只该由 Tab 自己的点击负责，否则会多
        // 出一次没必要的按压回弹。
        var dragged = false
        DampedDragAnimation(
            animationScope = indicatorScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..lastIndex.toFloat(),
            visibilityThreshold = .001f,
            initialScale = 1f,
            pressedScale = 1.12f,
            onDragStarted = { dragged = false },
            onDragStopped = {
                if (dragged) {
                    val index = targetValue.fastRoundToInt().coerceIn(0, lastIndex)
                    animateToValue(index.toFloat())
                    latestKeys.value.getOrNull(index)?.let { latestOnDestination(it) }
                }
            },
            onDrag = { _, dragAmount ->
                if (dragAmount.x != 0f) dragged = true
                if (lastIndex > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx).coerceIn(0f, lastIndex.toFloat()),
                    )
                }
            },
        )
    }
    LaunchedEffect(selectedIndex, destinations.size) {
        if (indicator.value != selectedIndex.toFloat()) {
            indicator.animateToValue(selectedIndex.toFloat())
        }
    }
    // 这条导航对应小屏的底部导航，所以 Tab 组按 bottom dock 的规则来：**宽度随内容自适应**
    // （`内边距 ×2 + 展开按钮 + N × 等宽`），居中浮着，而不是拉满整条窗口宽度。拉满会把
    // 内容挤在左半边、右侧留一大片空玻璃。
    // 展开按钮留在同一颗胶囊里（它是这组导航的第一项），分开会读成两个不相干的控件。
    val tabGroupWidth = MediumTopStripInset * 2 + MediumTopExpandWidth +
        MediumTopTabWidth * destinations.size
    // 外壳把这个条放在顶部导航区的**顶端**，所以这里自己撑满导航高度并垂直居中 ——
    // 否则 40dp 的玻璃条会贴着状态栏，看着偏上。
    //
    // 分层（自下而上）：玻璃条材质 → 选中液滴 → 展开按钮与 Tab 标签。
    // 液滴必须画在标签**下面**：`drawBackdrop` 是把折射后的背景铺满整个形状，画在上层
    // 会把选中项的标签整个盖掉（只剩一颗空胶囊）。上游 `LiquidBottomTabs` 靠"再记录一份
    // 不可见的染色副本给液滴采样"来实现玻璃里放大的标签，代价是整条内容画两遍、还要
    // 一个额外的记录层；这里改用"标签压在液滴上"，静止态观感与 bottom tab 的选中态一致
    // （浅色玻璃胶囊 + 强调色文字），结构上也不再有自引用风险。
    Box(
        testModifier.fillMaxWidth().height(MaFeiTopChromeHeightDp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.height(MediumTopStripHeight).width(tabGroupWidth)) {
            MaFeiSurface(
                GlassSurfaceSpec(
                    GlassRole.Navigation,
                    GlassLevel.Full,
                    tintAlpha = .16f,
                    intensity = .9f,
                    cornerRadiusDp = 20f,
                ),
                Modifier.fillMaxSize(),
                backdrop,
                // 只导出材质（内容为空）：给上面的液滴采样。内容留在这一层外面，避免自引用。
                exportedBackdrop = stripMaterial,
            ) {}
            // 液滴：真玻璃（折射 + 色散 + 按下高光/阴影），位移与缩放由阻尼弹簧驱动。
            LiquidSelectionDroplet(
                backdrop = dropletBackdrop,
                animation = indicator,
                travelPerStep = MediumTopTabWidth,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // 行内留白 + 展开按钮的占位：液滴从第一个 Tab 的左边缘起步。
                    .offset(x = MediumTopStripInset + MediumTopExpandWidth)
                    .height(MediumTopStripHeight - MediumTopStripInset * 2)
                    .width(MediumTopTabWidth),
            )
            Row(
                Modifier.fillMaxHeight().padding(horizontal = MediumTopStripInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(MediumTopExpandWidth).fillMaxHeight()
                        .semantics { contentDescription = "展开侧边栏" }
                        .mafeiInteractive(onOpenDrawer, radiusDp = 17f, label = "展开侧边栏"),
                    contentAlignment = Alignment.Center,
                ) {
                    SidebarIcon(Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
                }
                // 目的地 Tab：等宽 + 一颗滑动的液滴选中指示器。
                // 不用 LiquidBottomTabs：那个组件自带蓝色强调色与自己的几何，套上来会把这条
                // 导航的样子改掉；这里只取"选中指示器滑动 + 真玻璃液滴"这一核心手感。
                destinations.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier.width(MediumTopTabWidth).fillMaxHeight()
                            .semantics { this.selected = selected }
                            .mafeiInteractive(
                                { onDestination(item.key) },
                                radiusDp = 17f,
                                label = item.label,
                            )
                            .then(indicator.modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.label,
                            // 选中样式对齐液态玻璃 bottom tab：选中内容用它的强调色染色
                            // （`LiquidBottomTabs` 里深色主题硬编码 0xFF0091FF），底下那颗
                            // 浅色液滴由同层的滑动指示器担任。
                            color = if (selected) MediumTopTabAccent
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** 顶部导航区高度：Tab 条在这个高度里垂直居中，二级页双行头部也以它为单位（×2）。 */
internal val MaFeiTopChromeHeightDp = 52.dp

/**
 * 展开态侧栏（大屏常驻侧栏）的宽度。内容让位量必须与它相等 —— 面板贴窗口边缘通栏、
 * 没有悬浮边距，所以"面板宽度"和"内容起点"是同一个数。
 */
internal val MaFeiExpandedSidebarWidthDp = 240.dp

/** 顶部玻璃条本体的高度（导航区里垂直居中）。 */
private val MediumTopStripHeight = 40.dp

/** 玻璃条内左右留白，液滴的起始位置也要加这一份。 */
private val MediumTopStripInset = 3.dp

/** 条首的"展开侧边栏"按钮占位宽度。 */
private val MediumTopExpandWidth = 40.dp

/** 顶部 Tab 的等宽宽度 —— 与供应商切换胶囊同宽，保证两条导航的手感一致。 */
private val MediumTopTabWidth = 80.dp

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
    modifier: Modifier = Modifier,
) {
    val selectedIndex = destinations.indexOfFirst { it.key == selectedDestination }.coerceAtLeast(0)
    val resolvedCompactProgress = compactProgress().coerceIn(0f, 1f)
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = { index -> destinations.getOrNull(index)?.let { onDestination(it.key) } },
        backdrop = backdrop,
        tabsCount = destinations.size,
        widthBaselineTabCount = 4,
        compactProgress = { resolvedCompactProgress },
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
