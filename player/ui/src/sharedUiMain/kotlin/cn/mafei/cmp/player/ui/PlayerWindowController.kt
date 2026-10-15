package cn.mafei.cmp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

/** Window capability selects this arrangement; pointer/touch share the same playback commands. */
@Composable
internal fun PlayerWindowController(
    safeStart: Dp,
    safeEnd: Dp,
    safeBottom: Dp,
    timeline: @Composable (Modifier) -> Unit,
    transport: @Composable RowScope.() -> Unit,
    options: @Composable RowScope.() -> Unit,
    volume: @Composable RowScope.() -> Unit,
    screenModes: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().testTag("playerWindowController")
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCB000000)))),
    ) {
        val singleRow = maxWidth >= 840.dp
        val inlineVolume = maxWidth >= 600.dp
        Column(
            Modifier.fillMaxWidth().padding(
                start = safeStart + 20.dp, end = safeEnd + 20.dp,
                top = 28.dp, bottom = maxOf(safeBottom, 12.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            timeline(Modifier.fillMaxWidth().height(32.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                transport()
                if (inlineVolume) volume()
                Spacer(Modifier.weight(1f))
                if (singleRow) options()
                screenModes()
            }
            if (!singleRow) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (!inlineVolume) volume()
                    options()
                }
            }
        }
    }
}

/** Pointer-only labels never capture focus or join the menu/back stack. */
@Composable
internal fun PlayerPointerTooltip(label: String, hovered: Boolean) {
    val visible by produceState(false, hovered) {
        value = false
        if (hovered) { delay(550); value = true }
    }
    if (!visible) return
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val above = anchorBounds.top - popupContentSize.height - gap
                val y = if (above >= 0) above else anchorBounds.bottom + gap
                return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
            }
        }
    }
    Popup(popupPositionProvider = position) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.background(Color(0xF02A2A2A), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
