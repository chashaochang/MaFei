package cn.mafei.cmp.common.design_system

import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Shared horizontal page gutters.  Every medium/large page and its chrome use
 * this same token, so a title, a leading navigation control, and the content
 * below it share one vertical alignment line.
 */
val MaFeiPhonePageHorizontalInsetDp = 16.dp
val MaFeiLargePageHorizontalInsetDp = 32.dp

/**
 * The navigation presentation selected from usable-window geometry.
 *
 * Rail is intentionally distinct from Drawer and Sidebar: it is a persistent
 * vertical compact navigation strip used by broad folding inner screens where a
 * top tab bar would consume the short viewport and a full sidebar would consume
 * too much content width.
 */
enum class NavigationPlacement { Bottom, Drawer, Sidebar, Rail }

/**
 * Navigation shell selected from the current window shape.
 *
 * The shell is deliberately separate from [NavigationPlacement]. Placement is
 * geometry (where the chrome is drawn); this value is the ownership boundary
 * for navigation state. Phone routes own a bottom-tab stack, while tablet and
 * desktop routes keep the root navigation chrome outside the content stack.
 */
enum class MaFeiNavigationMode {
    PhoneBottom,
    TabletDrawer,
    TabletSidebar,
    DesktopSidebar,
    CompactRail,
}

/** The physical window edge owned by persistent navigation chrome. */
enum class NavigationEdge { Leading, Trailing }

/** P0 product structure selected from the current window, never from a device model. */
enum class MaFeiLayoutFamily { Phone, Tablet, Desktop }

data class MaFeiLayout(
    val family: MaFeiLayoutFamily,
    val navigation: NavigationPlacement,
    val navigationEdge: NavigationEdge = NavigationEdge.Leading,
    val navigationWidthDp: Float,
    val pagePaddingDp: Float,
    val contentMaxWidthDp: Float,
    val gridWidthDp: Float,
    val posterColumns: Int,
    val gapDp: Float,
    val heroHeightDp: Float,
    val horizontalHero: Boolean,
    val paneCount: Int = 1,
    val occlusionAxis: MaFeiOcclusionAxis = MaFeiOcclusionAxis.None,
    val displayRole: MaFeiDisplayRole = MaFeiDisplayRole.Default,
    val fontScale: Float = 1f,
)

val MaFeiLayout.navigationMode: MaFeiNavigationMode
    get() = when {
        navigation == NavigationPlacement.Bottom -> MaFeiNavigationMode.PhoneBottom
        navigation == NavigationPlacement.Drawer -> MaFeiNavigationMode.TabletDrawer
        navigation == NavigationPlacement.Rail -> MaFeiNavigationMode.CompactRail
        family == MaFeiLayoutFamily.Tablet -> MaFeiNavigationMode.TabletSidebar
        else -> MaFeiNavigationMode.DesktopSidebar
    }

val MaFeiLayout.isPhoneNavigation: Boolean
    get() = navigationMode == MaFeiNavigationMode.PhoneBottom

val MaFeiLayout.hasPersistentNavigation: Boolean
    get() = navigationMode == MaFeiNavigationMode.TabletSidebar ||
        navigationMode == MaFeiNavigationMode.DesktopSidebar ||
        navigationMode == MaFeiNavigationMode.CompactRail

/** Recompute width-driven metrics after the shell resolves runtime navigation chrome. */
fun MaFeiLayout.withUsableWidth(usableWidthDp: Float): MaFeiLayout {
    val width = usableWidthDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val scale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    // Desktop windows are not capped at an arbitrary 1440dp. The poster grid
    // is width-driven, so a wider Mac/OHOS PC window gains columns instead of
    // stretching the same six cards across a larger surface.
    val contentMaxWidth = width
    val gridWidth = (contentMaxWidth - pagePaddingDp * 2).coerceAtLeast(1f)
    // Tablet/desktop posters use the legacy ~130dp visual rhythm. The
    // sidebar is already removed from [gridWidth], so keeping 150dp here
    // leaves a landscape tablet at only four or five columns.
    val minPoster = (if (family == MaFeiLayoutFamily.Phone) 100f else 130f) * scale.coerceAtMost(2f)
    val columns = floor((gridWidth + gapDp) / (minPoster + gapDp)).toInt().coerceIn(1, 12)
    return copy(
        contentMaxWidthDp = contentMaxWidth,
        gridWidthDp = gridWidth,
        posterColumns = columns,
        horizontalHero = gridWidth >= 720f * scale,
    )
}

/** Pure window policy. No OS, device category or input-mode argument by design.
 * The host supplies usable bounds; physical hinge/posture bridging is separate.
 */
fun mafeiLayout(widthDp: Float, heightDp: Float, fontScale: Float = 1f): MaFeiLayout {
    val width = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val height = heightDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val scale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val family = when {
        width >= 1440f -> MaFeiLayoutFamily.Desktop
        width >= 600f -> MaFeiLayoutFamily.Tablet
        else -> MaFeiLayoutFamily.Phone
    }
    // This mirrors the legacy HomeShellPolicy: MD and portrait LG use an
    // overlay drawer; landscape LG and larger use the embedded sidebar.
    val navigation = when {
        width < 600f -> NavigationPlacement.Bottom
        width < 840f || width < height -> NavigationPlacement.Drawer
        else -> NavigationPlacement.Sidebar
    }
    val navWidth = when (navigation) {
        NavigationPlacement.Bottom -> 0f
        NavigationPlacement.Drawer -> 0f
        NavigationPlacement.Rail -> 80f
        // 面板贴窗口边缘通栏、没有悬浮边距，所以"导航宽度"就是侧栏材质本身的宽度。
        // 加 12dp 的悬浮边距会让内容与侧栏之间空出一条缝。
        NavigationPlacement.Sidebar -> 240f
    }
    val padding = when (family) {
        // Keep the dense rhythm of the original HarmonyOS phone client.
        // 16dp still leaves a comfortable gesture edge while avoiding the
        // oversized gutters that made shelves and three-column grids cramped.
        MaFeiLayoutFamily.Phone -> MaFeiPhonePageHorizontalInsetDp.value
        // Legacy HORIZONTAL_CARD_SPACE is 8dp on phone and 16dp on MD+.
        MaFeiLayoutFamily.Tablet -> MaFeiLargePageHorizontalInsetDp.value
        MaFeiLayoutFamily.Desktop -> MaFeiLargePageHorizontalInsetDp.value
    }
    val availableContentWidth = (width - navWidth).coerceAtLeast(1f)
    // Keep the full usable width on desktop. Density is controlled by
    // [posterColumns], not by shrinking the content pane to a fixed maximum.
    val contentMaxWidth = availableContentWidth
    val gridWidth = (min(availableContentWidth, contentMaxWidth) - padding * 2).coerceAtLeast(1f)
    val gap = if (family == MaFeiLayoutFamily.Phone) 10f else 20f
    // The original phone client deliberately used a dense three-poster rhythm.
    // Wider windows progressively move through the legacy 4/6/8/12-column
    // densities instead of making each poster wider.
    val minPoster = (if (family == MaFeiLayoutFamily.Phone) 100f else 130f) * scale.coerceAtMost(2f)
    val columns = floor((gridWidth + gap) / (minPoster + gap)).toInt().coerceIn(1, 12)
    return MaFeiLayout(
        family = family,
        navigation = navigation,
        navigationWidthDp = navWidth,
        pagePaddingDp = padding,
        contentMaxWidthDp = contentMaxWidth,
        gridWidthDp = gridWidth,
        posterColumns = columns,
        gapDp = gap,
        heroHeightDp = when {
            height < 480f -> (height * .58f).coerceIn(210f, 280f)
            // Keep the phone hero aligned with the original HarmonyOS client.
            family == MaFeiLayoutFamily.Phone -> 430f
            else -> {
                // 大屏：一屏预算（顶距 + hero + 16 + 标题行 + 12 + 海报 210）
                // 内取最大 —— 海报完整显示在一屏内，hero 尽量高让清晰图更大。
                min(420f, max(300f, height * .60f))
            }
        },
        horizontalHero = gridWidth >= 720f * scale,
        fontScale = scale,
    )
}

/** Preferred entry point once a platform host can provide fold and occlusion data. */
fun mafeiLayout(window: MaFeiAdaptiveWindow): MaFeiLayout =
    mafeiLayout(
        widthDp = window.contentBounds.width,
        heightDp = window.contentBounds.height,
        fontScale = window.fontScale,
    ).let { base ->
        val broadFoldingInnerScreen = window.displayRole == MaFeiDisplayRole.Inner &&
            window.foldPosture == MaFeiFoldPosture.Flat &&
            window.paneCount == 1 &&
            window.aspect == MaFeiWindowAspect.Balanced &&
            window.contentBounds.width >= 600f
        if (base.navigation == NavigationPlacement.Drawer && broadFoldingInnerScreen) {
            // 12dp trailing margin + the compact sidebar's 56dp material width
            // + 12dp content gutter.
            base.copy(
                navigation = NavigationPlacement.Rail,
                navigationEdge = NavigationEdge.Trailing,
                navigationWidthDp = 80f,
            )
        } else {
            base
        }
    }.copy(
        paneCount = window.paneCount,
        occlusionAxis = window.occlusionAxis,
        displayRole = window.displayRole,
    )
