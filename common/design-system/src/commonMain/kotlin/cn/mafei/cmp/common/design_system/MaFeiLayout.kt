package cn.mafei.cmp.common.design_system

import kotlin.math.floor
import kotlin.math.min

enum class NavigationPlacement { Bottom, Rail, Sidebar }

/** P0 product structure selected from the current window, never from a device model. */
enum class MaFeiLayoutFamily { Phone, Tablet, Desktop }

data class MaFeiLayout(
    val family: MaFeiLayoutFamily,
    val navigation: NavigationPlacement,
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
)

/** Pure window policy. No OS, device category or input-mode argument by design.
 * The host supplies usable bounds; physical hinge/posture bridging is separate.
 */
fun mafeiLayout(widthDp: Float, heightDp: Float, fontScale: Float = 1f): MaFeiLayout {
    val width = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val height = heightDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val scale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val family = when {
        width >= 1000f -> MaFeiLayoutFamily.Desktop
        width >= 600f -> MaFeiLayoutFamily.Tablet
        else -> MaFeiLayoutFamily.Phone
    }
    val navigation = when (family) {
        MaFeiLayoutFamily.Phone -> NavigationPlacement.Bottom
        MaFeiLayoutFamily.Tablet -> NavigationPlacement.Rail
        MaFeiLayoutFamily.Desktop -> NavigationPlacement.Sidebar
    }
    val navWidth = when (navigation) {
        NavigationPlacement.Bottom -> 0f
        NavigationPlacement.Rail -> 72f
        NavigationPlacement.Sidebar -> 208f
    }
    val padding = when (family) {
        MaFeiLayoutFamily.Phone -> 20f
        MaFeiLayoutFamily.Tablet -> 28f
        MaFeiLayoutFamily.Desktop -> 36f
    }
    val availableContentWidth = (width - navWidth).coerceAtLeast(1f)
    val contentMaxWidth = when (family) {
        MaFeiLayoutFamily.Phone, MaFeiLayoutFamily.Tablet -> availableContentWidth
        MaFeiLayoutFamily.Desktop -> 1440f
    }
    val gridWidth = (min(availableContentWidth, contentMaxWidth) - padding * 2).coerceAtLeast(1f)
    val gap = if (family == MaFeiLayoutFamily.Phone) 12f else 20f
    val minPoster = (if (family == MaFeiLayoutFamily.Phone) 140f else 164f) * scale.coerceAtMost(2f)
    val columns = floor((gridWidth + gap) / (minPoster + gap)).toInt().coerceIn(1, 6)
    return MaFeiLayout(
        family, navigation, navWidth, padding, contentMaxWidth, gridWidth,
        columns, gap,
        heroHeightDp = when {
            height < 480f -> (height * .58f).coerceIn(210f, 280f)
            family == MaFeiLayoutFamily.Phone -> (height * .51f).coerceIn(360f, 460f)
            else -> (height * .46f).coerceIn(320f, 440f)
        },
        horizontalHero = gridWidth >= 720f * scale,
    )
}

/** Preferred entry point once a platform host can provide fold and occlusion data. */
fun mafeiLayout(window: MaFeiAdaptiveWindow): MaFeiLayout =
    mafeiLayout(
        widthDp = window.contentBounds.width,
        heightDp = window.contentBounds.height,
        fontScale = window.fontScale,
    ).copy(
        paneCount = window.paneCount,
        occlusionAxis = window.occlusionAxis,
        displayRole = window.displayRole,
    )
