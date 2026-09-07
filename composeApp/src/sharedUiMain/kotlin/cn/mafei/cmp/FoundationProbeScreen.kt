package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiPanel
import cn.mafei.cmp.common.design_system.MaFeiSurface
import cn.mafei.cmp.common.glass.*

/** Stage 1 development entry, not a production Home or runtime acceptance claim. */
@Composable
fun FoundationProbeScreen() {
    var dark by rememberSaveable { mutableStateOf(true) }
    var level by rememberSaveable { mutableStateOf(GlassLevel.Full.name) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    MaFeiTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                val widthLabel = "${maxWidth.value.toInt()} dp · ${if (wide) "双栏" else "单栏"}"
                Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 32.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("MaFei", style = MaterialTheme.typography.headlineMedium)
                            Text("2.0 · 界面实验室", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(onClick = { dark = !dark }) { Text(if (dark) "浅色" else "深色") }
                    }
                    if (wide) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            LazyColumn(Modifier.width(280.dp)) {
                                item { ProbeControls(level, widthLabel, { level = it }, { dialog = true }) }
                            }
                            ProbeGlassViewport(level, Modifier.weight(1f)) {
                                items(12) { ProbeSample(it) }
                            }
                        }
                    } else {
                        ProbeGlassViewport(level, Modifier.weight(1f)) {
                            item { ProbeControls(level, widthLabel, { level = it }, { dialog = true }) }
                            items(12) { ProbeSample(it) }
                        }
                    }
                }
            }
        }
        if (dialog) AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("弹层可读性检查") },
            text = { Text("检查文字、背景、焦点与关闭操作。调整窗口后，弹层应保持可读、可操作。") },
            confirmButton = { TextButton(onClick = { dialog = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun ProbeControls(level: String, width: String, onLevel: (String) -> Unit, onDialog: () -> Unit) {
    MaFeiPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("窗口与材质", style = MaterialTheme.typography.titleLarge)
            Text(width, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GlassLevel.entries.forEach { option ->
                FilterChip(selected = level == option.name, onClick = { onLevel(option.name) },
                    label = { Text(when (option) {
                        GlassLevel.Full -> "完整玻璃"
                        GlassLevel.Reduced -> "简化玻璃"
                        GlassLevel.Opaque -> "实体背景"
                    }) }, modifier = Modifier.fillMaxWidth())
            }
            Text("滚动彩色样本，观察底部浮层的模糊与折射。简化档关闭折射，实体档完全不透明；能力不足时自动降级。",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onDialog, modifier = Modifier.fillMaxWidth()) { Text("检查弹层") }
        }
    }
}

@Composable
private fun ProbeSample(index: Int) {
    MaFeiPanel(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val colors = if (index % 2 == 0) listOf(Color(0xFF4357AD), Color(0xFFDA8C63))
                else listOf(Color(0xFF247D83), Color(0xFFA074C4))
            drawRect(Brush.linearGradient(colors))
            repeat(12) { stripe ->
                val x = size.width * stripe / 11f
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, 0f),
                    Offset(x + 48.dp.toPx(), size.height), strokeWidth = 8.dp.toPx())
            }
        }
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${index + 1} / 材质样本", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("让内容保持清晰", style = MaterialTheme.typography.headlineSmall)
            Text("滚动查看卡片边界、文本层级和底部区域。窄窗口使用单栏，宽窗口将控制区放在侧边。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProbeGlassViewport(level: String, modifier: Modifier, content: LazyListScope.() -> Unit) {
    val backdrop = rememberGlassBackdrop()
    val requested = GlassLevel.valueOf(level)
    val resolved = resolveGlassLevel(requested, currentGlassCapability(), hasBackdrop = true)
    var taps by rememberSaveable { mutableStateOf(0) }
    Box(modifier.fillMaxSize()) {
        // Only the scrolling source is recorded, never the consuming overlay.
        LazyColumn(
            Modifier.fillMaxSize().captureGlassBackdrop(backdrop),
            contentPadding = PaddingValues(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        MaFeiSurface(
            spec = GlassSurfaceSpec(GlassRole.Overlay, requested, tintAlpha = 0.45f),
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(vertical = 12.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("液态玻璃 · 滚动探针", style = MaterialTheme.typography.titleMedium)
                Text("请求 $requested → 能力档位 $resolved", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { taps++ }) { Text("命中检查 · $taps") }
            }
        }
    }
}
