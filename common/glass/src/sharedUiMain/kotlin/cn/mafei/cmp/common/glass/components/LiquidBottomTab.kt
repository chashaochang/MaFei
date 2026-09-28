package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = -1,
    isSelected: Boolean = false,
    forceCollapse: Boolean = false,
    collapseProgress: Float = 0f,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { selected = isSelected }
            .fillMaxHeight()
            .weight(
                if (index >= 0 && (!isSelected || forceCollapse)) {
                    (1f - collapseProgress).coerceAtLeast(0.001f)
                } else {
                    1f
                }
            )
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            }
            // Keep the selected droplet as the primary feedback. Hover only
            // adds a restrained gloss so the desktop pointer state does not
            // compete with the tab's existing liquid selection animation.
            .drawWithContent {
                drawContent()
                if (hovered) drawRect(Color.White.copy(alpha = if (isSelected) .035f else .055f))
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
