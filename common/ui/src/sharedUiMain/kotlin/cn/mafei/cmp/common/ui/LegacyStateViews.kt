package cn.mafei.cmp.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun LegacyEmptyView(
    title: String = "暂无数据",
    modifier: Modifier = Modifier,
) {
    val reveal = rememberLegacyStateReveal()
    Column(
        modifier.fillMaxSize().graphicsLayer { alpha = reveal.value }.padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Image(
            painter = painterResource(MaFeiLegacyDrawables.empty),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
        )
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun LegacyFailedView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberLegacyStateReveal()
    Column(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = reveal.value }
            .mafeiInteractive(onRetry, radiusDp = 0f, label = "加载失败，点击重试")
            .padding(bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Image(
            painter = painterResource(MaFeiLegacyDrawables.failed),
            contentDescription = null,
            modifier = Modifier.size(240.dp),
        )
        Text(
            "加载失败,点击重试",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun LegacyLoadingView(modifier: Modifier = Modifier) {
    val reveal = rememberLegacyStateReveal(delayMs = 140L)
    Box(
        modifier.fillMaxSize().graphicsLayer { alpha = reveal.value },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(42.dp))
    }
}

/** Mechanical counterpart of ArkUI `SkeletonGridView` used by media lists. */
@Composable
fun LegacyMediaGridSkeleton(
    columns: Int,
    startPadding: Dp,
    endPadding: Dp,
    topPadding: Dp = 12.dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val reveal = rememberLegacyStateReveal(delayMs = 100L)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = reveal.value },
        userScrollEnabled = false,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = startPadding,
            top = topPadding,
            end = endPadding,
            bottom = bottomPadding,
        ),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        items((0 until maxOf(12, columns * 4)).toList()) {
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
            )
        }
    }
}

@Composable
private fun rememberLegacyStateReveal(delayMs: Long = 0L): State<Float> {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(delayMs) {
        if (delayMs > 0L) delay(delayMs)
        visible = true
    }
    return animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "legacy-state-reveal",
    )
}
