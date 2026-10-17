package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import cn.mafei.cmp.common.glass.rememberCombinedGlassBackdrop
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SidebarPanel(
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
        // Both presentations use the same page material; the drawer scrim only
        // dims the surrounding page and is not part of the sidebar's sampling.
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

internal fun mafeiSmoothStep(value: Float): Float {
    val progress = value.coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

/**
 * Clips the fixed sidebar content with an organic bubble that is pulled into
 * the top navigation capsule. Nothing inside the panel is scaled or moved:
 * lower rows disappear behind the rising mask and reappear in place when the
 * sidebar expands.
 */
internal data class SidebarBubbleMorphShape(
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

@Composable
internal fun SidebarIcon(modifier: Modifier = Modifier, color: Color) {
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
