package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*

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

/** 顶部导航区高度：Tab 条在这个高度里垂直居中，二级页双行头部也以它为单位（×2）。 */
internal val MaFeiTopChromeHeightDp = 52.dp

/**
 * 展开态侧栏（大屏常驻侧栏）的宽度。内容让位量必须与它相等 —— 面板贴窗口边缘通栏、
 * 没有悬浮边距，所以"面板宽度"和"内容起点"是同一个数。
 */
internal val MaFeiExpandedSidebarWidthDp = MaFeiPersistentSidebarWidthDp
