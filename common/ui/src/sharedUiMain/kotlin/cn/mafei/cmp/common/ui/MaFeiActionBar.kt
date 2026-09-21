package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidActionBar
import cn.mafei.cmp.common.glass.components.LiquidActionBarButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import org.jetbrains.compose.resources.painterResource

/** Material's top app bar height, and the inset that centres a 48dp control in it. */
private val MaFeiActionBarHeightDp = 64.dp
private val MaFeiActionBarVerticalInsetDp = 10.dp

/**
 * 一级页面标题栏的高度（不含状态栏）。取值与旧版 `UIConstants.ACTION_BAR_HEIGHT = 56` 一致 ——
 * 旧版的 ActionBar 就是 56dp，页面用 `Blank(safeTop + ACTION_BAR_HEIGHT)` 给它让位。
 */
val MaFeiPageTitleBarHeight = 56.dp

/**
 * 一级页面（媒体库 / 收藏 / 我的）共用的顶部标题：无返回按钮、玻璃材质。
 *
 * 调用方是**记录者**：先 `captureGlassBackdrop(backdrop)` 把页面内容录进 `backdrop`，再把同一个
 * backdrop 交给这条栏。栏是消费者，必须是捕获层的**兄弟** —— 放进记录层里就成了自引用，
 * skiko 的 drawable 图会递归到 native 栈耗尽（见项目记忆里的"层记录者与消费者必须互为兄弟"）。
 *
 * 内容要让开 `safeArea.top + MaFeiPageTitleBarHeight`，并且是**滚动让位**：内容从栏下面穿过去，
 * 穿过去的部分被玻璃折射 —— 这是「我的」原本就有的观感，另外两个标签页照它对齐。
 */
@Composable
fun MaFeiPageTitleBar(
    title: String,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    MaFeiActionBar(
        title = title,
        onBack = null,
        backdrop = backdrop,
        modifier = modifier,
    )
}

/**
 * Tab（中屏）形态下二级页面头部双倍高：两行各 [MaFeiTopChromeHeightDp]，第一行是返回/
 * 操作，第二行是居中标题。这是导航重构的规格 —— 中屏二级页导航栏高度 = 2 × 顶部导航栏高度。
 */
private fun actionBarHeightDp(stackedTitle: Boolean): Dp =
    if (stackedTitle) MaFeiTopChromeHeightDp * 2 else MaFeiActionBarHeightDp

/** Shared route chrome translated from the legacy AppRouteDestination/ActionBar contract. */
@Composable
fun MaFeiActionBar(
    title: String,
    onBack: (() -> Unit)?,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    actionCount: Int = 0,
    materialAlpha: Float = 1f,
    titleAlpha: Float = 1f,
    contentColor: Color = Color.Unspecified,
    centerTitleOnScreen: Boolean = true,
    titleContent: (@Composable BoxScope.(buttonBackdrop: GlassBackdrop) -> Unit)? = null,
    actions: @Composable RowScope.(buttonBackdrop: GlassBackdrop) -> Unit = {},
) {
    val safeArea = LocalMaFeiSafeArea.current
    // 中屏（Drawer）下没有持久侧栏，顶部让给 Tab 条那一行的高度，所以二级页头部改成
    // 双行：返回按钮与 Tab 条同排，标题另起一行居中。大屏（Sidebar/Rail）与手机保持
    // 单行 64dp，避免宽窗口里出现一条空荡荡的高栏。
    val stackedTitle = LocalMaFeiNavigationChrome.current.placement == NavigationPlacement.Drawer
    val barHeight = actionBarHeightDp(stackedTitle)
    val resolvedContentColor = if (contentColor.isSpecified) contentColor
    else MaterialTheme.colorScheme.onSurface
    LiquidActionBar(
        backdrop = backdrop,
        topInset = safeArea.top,
        startInset = safeArea.start,
        endInset = safeArea.end,
        modifier = modifier,
        actionCount = actionCount,
        materialAlpha = materialAlpha,
        // Material's top app bar height. The 48dp control then has 8dp clear
        // above and below instead of sitting flush against the bar's edge, which
        // is what made the back button read as too high in the 56dp bar.
        barHeight = barHeight,
        // 双行头部里第一行只有 52dp，44dp 的按钮要在里面居中（上下各 4dp），不能沿用
        // 单行 64dp 的 10dp 内边距，否则按钮会压到第二行的标题上。
        verticalInset = if (stackedTitle) (MaFeiTopChromeHeightDp - 44.dp) / 2
        else MaFeiActionBarVerticalInsetDp,
        centerTitleOnScreen = centerTitleOnScreen,
        stackedTitle = stackedTitle,
        navigation = onBack?.let { back ->
            { buttonBackdrop ->
                LiquidActionBarButton(
                    onClick = back,
                    backdrop = buttonBackdrop,
                    modifier = Modifier.semantics { contentDescription = "返回" },
                ) {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.back),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(resolvedContentColor),
                    )
                }
            }
        },
        title = { buttonBackdrop ->
            Box(Modifier.fillMaxSize().alpha(titleAlpha), contentAlignment = Alignment.Center) {
                if (titleContent == null) {
                    Text(
                        title,
                        Modifier.semantics { heading() },
                        color = resolvedContentColor,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    titleContent(buttonBackdrop)
                }
            }
        },
        actions = actions,
    )
}

/** Standard non-hero destination: content and the glass bar are sibling layers. */
@Composable
fun MaFeiActionBarScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionCount: Int = 0,
    actions: @Composable RowScope.(buttonBackdrop: GlassBackdrop) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    // 与 MaFeiActionBar 用同一条规则决定头部高度，否则中屏双行头部会把内容顶掉。
    val stackedTitle = LocalMaFeiNavigationChrome.current.placement == NavigationPlacement.Drawer
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .captureGlassBackdrop(backdrop),
        ) {
            // Reserve the full bar height so the bar never overlaps the first row.
            Spacer(Modifier.height(safeArea.top + actionBarHeightDp(stackedTitle)))
            Box(Modifier.weight(1f).fillMaxSize(), content = content)
        }
        MaFeiActionBar(
            title = title,
            onBack = onBack,
            backdrop = backdrop,
            actionCount = actionCount,
            actions = actions,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
fun MaFeiActionBarButton(
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidActionBarButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}
