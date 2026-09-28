package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.LocalGlassIntensity
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.max

/**
 * Full-width progressive navigation material from the Liquid Glass catalog.
 * Insets are supplied by the product shell because CPF does not expose a
 * reliable HarmonyOS safe-drawing inset through Compose WindowInsets.
 */
@Composable
fun LiquidActionBar(
    backdrop: GlassBackdrop,
    topInset: Dp,
    startInset: Dp,
    endInset: Dp,
    modifier: Modifier = Modifier,
    actionCount: Int = 0,
    materialAlpha: Float = 1f,
    /**
     * Bar height excluding [topInset]. The phone uses 64dp so the 48dp controls
     * keep breathing room (the hit area itself must stay 48dp); wider chrome
     * passes a taller bar so the back button is not pinned to the top edge of a
     * desktop window.
     */
    barHeight: Dp = 64.dp,
    verticalInset: Dp = 8.dp,
    /**
     * Ordinary page titles stay centred against the whole window. Controls
     * such as the search field instead consume the space between the actual
     * leading/trailing buttons, matching the legacy action-bar row.
     */
    centerTitleOnScreen: Boolean = true,
    /**
     * Tab（中屏）形态下的二级页面头部：**双倍高**，第一行放返回/操作（与顶部 Tab 条
     * 同一行的高度与缩进），第二行放居中标题。规格见导航重构：中屏二级页导航栏高度
     * = 2 × 顶部导航栏高度，返回与 Tab 一行、标题在下一行。
     */
    stackedTitle: Boolean = false,
    /** Align a first-level title with the content leading edge in the stacked header. */
    stackedTitleAlignStart: Boolean = false,
    navigation: (@Composable BoxScope.(buttonBackdrop: GlassBackdrop) -> Unit)? = null,
    title: @Composable BoxScope.(buttonBackdrop: GlassBackdrop) -> Unit = {},
    actions: @Composable RowScope.(buttonBackdrop: GlassBackdrop) -> Unit = {},
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    val glassIntensity = LocalGlassIntensity.current
    // The material spans the safe area and the action row as one continuous
    // gradient. The backdrop is still sampled underneath, so the status-bar
    // content remains visible through the material; the tint follows the page
    // theme instead of introducing a separate gray strip.
    val tintColor = if (isLightTheme) Color.White else MaterialTheme.colorScheme.background
    val progressiveBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop.layer, progressiveBackdrop)
    val buttonBackdrop = remember(combinedBackdrop) { GlassBackdrop(combinedBackdrop, null) }
    val resolvedActionCount = actionCount.coerceIn(0, 3)
    val navigationWidth = if (navigation == null) 0f else 44f
    val actionsWidth = if (resolvedActionCount == 0) 0f
    else resolvedActionCount * 44f + (resolvedActionCount - 1) * 8f
    // Reserve the larger side on both edges so the title remains screen-centred
    // without colliding with two or three trailing actions.
    val titleReservation = (max(navigationWidth, actionsWidth) + 16f).dp
    // 双倍高时两行等分：每行就是顶部 Tab 条那一行的高度（[barHeight] 的一半由调用方
    // 通过 barHeight 传入双倍值，这里只负责把内容分成两行等高的块）。
    val rowHeight = barHeight / 2

    Box(
        modifier.fillMaxWidth().height(topInset + barHeight),
    ) {
        // 材质层：**只有材质、没有内容**。它把自己画出的材质导出到 `progressiveBackdrop`，
        // 好让上面的按钮折射"页面 + 这条栏的材质"。
        //
        // 内容必须留在这一层**外面**：导出层记录的是这一层的全部绘制，若采样它的按钮也在
        // 这一层里，"记录"就会包含"读取自己"，skiko 的 drawable 图会因此递归到爆栈
        // （`GlassSurface` 文档：never record a consumer into its own source）。
        Box(
            Modifier.fillMaxSize().progressiveBlur(
                backdrop = backdrop.layer,
                tint = tintColor,
                blurRadius = 4.dp * glassIntensity,
                tintIntensity = if (isLightTheme) .72f else .68f,
                gradientStartFraction = 1f / 3f,
                opacity = materialAlpha,
                exportedBackdrop = progressiveBackdrop,
            ),
        )
        if (stackedTitle) {
            Box(
                Modifier.fillMaxSize().padding(
                    start = startInset + 16.dp,
                    end = endInset + 16.dp,
                ),
            ) {
                // 第一行：返回与操作。外层 Box 只让开安全区，行内再加 [verticalInset]，
                // 这样返回按钮的位置与顶部 Tab 条里的展开按钮在同一水平线上。
                if (navigation != null) {
                    Box(Modifier.align(Alignment.TopStart)) {
                        Box(Modifier.padding(top = topInset + verticalInset)) {
                            navigation(buttonBackdrop)
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(top = topInset + verticalInset),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions(buttonBackdrop)
                }
                // 第二行：居中标题，独占整行，所以不再让开两侧按钮。
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(rowHeight),
                    contentAlignment = if (stackedTitleAlignStart) {
                        Alignment.CenterStart
                    } else {
                        Alignment.Center
                    },
                ) {
                    title(buttonBackdrop)
                }
            }
        } else {
            Box(
                Modifier.fillMaxSize().padding(
                    start = startInset + 16.dp,
                    top = topInset + verticalInset,
                    end = endInset + 16.dp,
                ),
            ) {
                if (navigation != null) {
                    Box(Modifier.align(Alignment.TopStart)) { navigation(buttonBackdrop) }
                }
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .height(if (centerTitleOnScreen) 44.dp else 48.dp)
                        .padding(
                            start = if (centerTitleOnScreen) titleReservation
                            else (navigationWidth + if (navigation == null) 0f else 12f).dp,
                            end = if (centerTitleOnScreen) titleReservation
                            else (actionsWidth + if (resolvedActionCount == 0) 0f else 12f).dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    title(buttonBackdrop)
                }
                Row(
                    Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions(buttonBackdrop)
                }
            }
        }
    }
}

@Composable
fun LiquidActionBarButton(
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() >= .5f
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(44.dp),
        horizontalPadding = 0.dp,
        surfaceColor = if (isLightTheme) Color.White.copy(alpha = .12f)
        else Color.Black.copy(alpha = .12f),
        content = content,
    )
}
