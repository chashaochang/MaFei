package cn.mafei.cmp.common.glass.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop
import kotlinx.coroutines.launch

@Composable
fun LiquidFloatingTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val animationScope = rememberCoroutineScope()

    LazyRow(
        modifier = modifier,
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            val selected = index == selectedIndex
            val contentColor = when {
                selected -> Color.Black
                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = .86f)
            }
            val surfaceColor = when {
                selected -> Color.White
                else -> Color.Black.copy(alpha = .24f)
            }
            LiquidButton(
                onClick = {
                    onSelected(index)
                    animationScope.launch {
                        var item = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }
                        if (item == null) {
                            listState.animateScrollToItem(index)
                            item = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                        }
                        item?.let {
                            val layoutInfo = listState.layoutInfo
                            val viewportWidth =
                                layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            val distanceToCenter =
                                it.offset - (viewportWidth - it.size) / 2
                            listState.animateScrollBy(distanceToCenter.toFloat())
                        }
                    }
                },
                backdrop = backdrop,
                surfaceColor = surfaceColor,
                height = 40.dp,
                horizontalPadding = 14.dp,
            ) {
                BasicText(
                    label,
                    style = TextStyle(
                        color = contentColor,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                )
            }
        }
    }
}
