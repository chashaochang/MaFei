package cn.mafei.cmp.common.design_system

import kotlin.math.floor

enum class NavigationPlacement { Bottom, Rail, Sidebar }

data class MaFeiLayout(
    val navigation: NavigationPlacement,
    val navigationWidthDp: Float,
    val pagePaddingDp: Float,
    val gridWidthDp: Float,
    val posterColumns: Int,
    val gapDp: Float,
    val heroHeightDp: Float,
    val horizontalHero: Boolean,
)

/** Pure window policy. No OS, device category or input-mode argument by design.
 * The host supplies usable bounds; physical hinge/posture bridging is separate.
 */
fun mafeiLayout(widthDp: Float, heightDp: Float, fontScale: Float = 1f): MaFeiLayout {
    val width = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val height = heightDp.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val scale = fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val navigation = when {
        width >= 1000f -> NavigationPlacement.Sidebar
        width >= 600f -> NavigationPlacement.Rail
        else -> NavigationPlacement.Bottom
    }
    val navWidth = when (navigation) {
        NavigationPlacement.Bottom -> 0f
        NavigationPlacement.Rail -> 72f
        NavigationPlacement.Sidebar -> 208f
    }
    val padding = if (width < 600f) 20f else 32f
    val gridWidth = (width - navWidth - padding * 2).coerceAtLeast(1f)
    val gap = if (width < 600f) 12f else 20f
    val minPoster = (if (width < 600f) 140f else 164f) * scale.coerceAtMost(2f)
    return MaFeiLayout(
        navigation, navWidth, padding, gridWidth,
        floor((gridWidth + gap) / (minPoster + gap)).toInt().coerceAtLeast(1), gap,
        heroHeightDp = (height * .42f).coerceIn(180f, 380f),
        horizontalHero = gridWidth >= 720f * scale,
    )
}
