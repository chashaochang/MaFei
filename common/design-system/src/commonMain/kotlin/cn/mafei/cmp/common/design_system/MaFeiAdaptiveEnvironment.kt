package cn.mafei.cmp.common.design_system

import kotlin.math.max

enum class MaFeiWidthClass { Compact, Medium, Expanded, Large }

enum class MaFeiHeightClass { Short, Medium, Tall }

enum class MaFeiWindowAspect { Tall, Balanced, Wide, UltraWide }

enum class MaFeiFoldPosture { Flat, HalfOpened, Closed, Unknown }

enum class MaFeiDisplayRole { Default, Outer, Inner, Intermediate }

enum class MaFeiOcclusionAxis { Vertical, Horizontal, Mixed, None }

data class MaFeiInsetsDp(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

data class MaFeiRectDp(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
}

/**
 * Platform-neutral input for responsive layout.
 *
 * Hosts publish current usable window geometry and zero or more physical
 * occlusion regions. Product code must never branch on a device model or OS.
 */
data class MaFeiWindowEnvironment(
    val widthDp: Float,
    val heightDp: Float,
    val fontScale: Float = 1f,
    val safeInsets: MaFeiInsetsDp = MaFeiInsetsDp(),
    val occlusions: List<MaFeiRectDp> = emptyList(),
    val foldPosture: MaFeiFoldPosture = MaFeiFoldPosture.Unknown,
    val displayRole: MaFeiDisplayRole = MaFeiDisplayRole.Default,
)

data class MaFeiAdaptiveWindow(
    val contentBounds: MaFeiRectDp,
    val panes: List<MaFeiRectDp>,
    val occlusions: List<MaFeiRectDp>,
    val widthClass: MaFeiWidthClass,
    val heightClass: MaFeiHeightClass,
    val aspect: MaFeiWindowAspect,
    val occlusionAxis: MaFeiOcclusionAxis,
    val foldPosture: MaFeiFoldPosture,
    val displayRole: MaFeiDisplayRole,
    val fontScale: Float,
) {
    val paneCount: Int get() = panes.size
    val largestPane: MaFeiRectDp get() = panes.maxBy { it.width * it.height }
    val hasPhysicalSeparation: Boolean get() = occlusions.isNotEmpty()
}

/** Pure geometry reducer shared by HarmonyOS, Android, iOS/iPadOS and Mac. */
fun mafeiAdaptiveWindow(environment: MaFeiWindowEnvironment): MaFeiAdaptiveWindow {
    val width = environment.widthDp.validDimension()
    val height = environment.heightDp.validDimension()
    val insets = environment.safeInsets.sanitized(width, height)
    val content = MaFeiRectDp(
        left = insets.left,
        top = insets.top,
        right = (width - insets.right).coerceAtLeast(insets.left + 1f),
        bottom = (height - insets.bottom).coerceAtLeast(insets.top + 1f),
    )
    val clippedOcclusions = environment.occlusions
        .mapNotNull { it.clippedTo(content) }
        .filterNot { it.isEmpty }
        .sortedWith(compareBy<MaFeiRectDp> { it.left }.thenBy { it.top })
    val vertical = clippedOcclusions.filter { it.isVerticalSeparator(content) }
    val horizontal = clippedOcclusions.filter { it.isHorizontalSeparator(content) }
    val axis = when {
        vertical.isNotEmpty() && horizontal.isNotEmpty() -> MaFeiOcclusionAxis.Mixed
        vertical.isNotEmpty() -> MaFeiOcclusionAxis.Vertical
        horizontal.isNotEmpty() -> MaFeiOcclusionAxis.Horizontal
        else -> MaFeiOcclusionAxis.None
    }
    val panes = when (axis) {
        MaFeiOcclusionAxis.Vertical -> splitVertically(content, vertical)
        MaFeiOcclusionAxis.Horizontal -> splitHorizontally(content, horizontal)
        else -> listOf(content)
    }.filterNot { it.isEmpty }.ifEmpty { listOf(content) }
    val ratio = content.width / content.height
    return MaFeiAdaptiveWindow(
        contentBounds = content,
        panes = panes,
        occlusions = clippedOcclusions,
        widthClass = when {
            content.width < 600f -> MaFeiWidthClass.Compact
            content.width < 840f -> MaFeiWidthClass.Medium
            content.width < 1200f -> MaFeiWidthClass.Expanded
            else -> MaFeiWidthClass.Large
        },
        heightClass = when {
            content.height < 480f -> MaFeiHeightClass.Short
            content.height < 720f -> MaFeiHeightClass.Medium
            else -> MaFeiHeightClass.Tall
        },
        aspect = when {
            ratio < .78f -> MaFeiWindowAspect.Tall
            ratio < 1.25f -> MaFeiWindowAspect.Balanced
            ratio < 1.9f -> MaFeiWindowAspect.Wide
            else -> MaFeiWindowAspect.UltraWide
        },
        occlusionAxis = axis,
        foldPosture = environment.foldPosture,
        displayRole = environment.displayRole,
        fontScale = environment.fontScale.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f,
    )
}

private fun Float.validDimension(): Float = takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f

private fun MaFeiInsetsDp.sanitized(width: Float, height: Float): MaFeiInsetsDp {
    val left = this.left.finiteNonNegative().coerceAtMost(width - 1f)
    val top = this.top.finiteNonNegative().coerceAtMost(height - 1f)
    return MaFeiInsetsDp(
        left = left,
        top = top,
        right = right.finiteNonNegative().coerceAtMost(width - left - 1f),
        bottom = bottom.finiteNonNegative().coerceAtMost(height - top - 1f),
    )
}

private fun Float.finiteNonNegative(): Float = takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f

private fun MaFeiRectDp.clippedTo(bounds: MaFeiRectDp): MaFeiRectDp? {
    val clipped = MaFeiRectDp(
        left = max(left, bounds.left),
        top = max(top, bounds.top),
        right = right.coerceAtMost(bounds.right),
        bottom = bottom.coerceAtMost(bounds.bottom),
    )
    return clipped.takeUnless { it.isEmpty }
}

private fun MaFeiRectDp.isVerticalSeparator(bounds: MaFeiRectDp): Boolean =
    height >= bounds.height * .5f && width <= bounds.width * .35f

private fun MaFeiRectDp.isHorizontalSeparator(bounds: MaFeiRectDp): Boolean =
    width >= bounds.width * .5f && height <= bounds.height * .35f

private fun splitVertically(bounds: MaFeiRectDp, separators: List<MaFeiRectDp>): List<MaFeiRectDp> {
    val result = mutableListOf<MaFeiRectDp>()
    var start = bounds.left
    separators.sortedBy { it.left }.forEach { separator ->
        if (separator.left > start) result += MaFeiRectDp(start, bounds.top, separator.left, bounds.bottom)
        start = max(start, separator.right)
    }
    if (start < bounds.right) result += MaFeiRectDp(start, bounds.top, bounds.right, bounds.bottom)
    return result
}

private fun splitHorizontally(bounds: MaFeiRectDp, separators: List<MaFeiRectDp>): List<MaFeiRectDp> {
    val result = mutableListOf<MaFeiRectDp>()
    var start = bounds.top
    separators.sortedBy { it.top }.forEach { separator ->
        if (separator.top > start) result += MaFeiRectDp(bounds.left, start, bounds.right, separator.top)
        start = max(start, separator.bottom)
    }
    if (start < bounds.bottom) result += MaFeiRectDp(bounds.left, start, bounds.right, bounds.bottom)
    return result
}
