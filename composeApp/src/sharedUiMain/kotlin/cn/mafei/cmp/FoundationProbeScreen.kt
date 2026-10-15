package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import org.jetbrains.compose.resources.stringResource
import mafei.composeapp.generated.resources.Res
import mafei.composeapp.generated.resources.probe_resource_status

/** Stage 1 development entry, not a production Home or runtime acceptance claim. */
@Composable
fun FoundationProbeScreen(onThemeChanged: (Boolean) -> Unit = {}, sessionStatus: String = "") {
    var level by rememberSaveable { mutableStateOf(GlassLevel.Full.name) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var reduceTransparency by rememberSaveable { mutableStateOf(false) }
    var taps by rememberSaveable { mutableStateOf(0) }
    var barExpanded by rememberSaveable { mutableStateOf(true) }
    // 只有深色一套配色（浅色主题已砍），系统 chrome 也照深色画。
    SideEffect { onThemeChanged(true) }
    val renderHealth = rememberGlassRenderHealth()
    // Layout branches must not own separate reading positions. Stable item keys
    // also preserve the sample anchor when controls move out into the side pane.
    val contentScrollState = rememberLazyListState()
    MaFeiTheme {
      GlassRenderingEnvironment(renderHealth) {
      MaFeiGlassPreferences(reduceTransparency) {
        Surface(color = MaterialTheme.colorScheme.background) {
            // One shared inset owner: UIKit/Activity fill the window; the
            // probe protects controls without asking each child to pad again.
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= 840.dp
                val widthLabel = "${maxWidth.value.toInt()} dp · ${if (wide) "双栏" else "单栏"}"
                Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 32.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("MaFei", style = MaterialTheme.typography.headlineMedium)
                            Text("2.0 · 界面实验室", style = MaterialTheme.typography.labelLarge)
                            if (sessionStatus.isNotEmpty()) Text(sessionStatus, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (wide) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            LazyColumn(Modifier.width(280.dp)) {
                                item { ProbeControls(level, widthLabel, { level = it }, { dialog = true },
                                    reduceTransparency, { reduceTransparency = it }, renderHealth) }
                            }
                            ProbeGlassViewport(level, reduceTransparency, taps, { taps++ }, Modifier.weight(1f), contentScrollState,
                                barExpanded, { barExpanded = !barExpanded }) {
                                item(key = "material-comparison") { ProbeGlassComparison(reduceTransparency) }
                                items(12, key = { "material-sample-$it" }) { ProbeSample(it) }
                            }
                        }
                    } else {
                        ProbeGlassViewport(level, reduceTransparency, taps, { taps++ }, Modifier.weight(1f), contentScrollState,
                            barExpanded, { barExpanded = !barExpanded }) {
                            item(key = "material-controls") { ProbeControls(level, widthLabel, { level = it }, { dialog = true },
                                reduceTransparency, { reduceTransparency = it }, renderHealth) }
                            item(key = "material-comparison") { ProbeGlassComparison(reduceTransparency) }
                            items(12, key = { "material-sample-$it" }) { ProbeSample(it) }
                        }
                    }
                }
            }
        }
        if (dialog) AppGlassDialog(
            onDismissRequest = { dialog = false },
            title = "弹层可读性检查",
            level = GlassLevel.valueOf(level),
            confirmButton = { TextButton(onClick = { dialog = false }) { Text("关闭") } },
        ) {
            Text("检查文字、焦点、返回与关闭操作。弹层是独立窗口，未共享页面背景，当前实际材质为实体回退。")
        }
      }
      }
    }
}

@Composable
private fun ProbeControls(level: String, width: String, onLevel: (String) -> Unit, onDialog: () -> Unit,
                          reduceTransparency: Boolean, onReduceTransparency: (Boolean) -> Unit,
                          renderHealth: GlassRenderHealth) {
    MaFeiPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("窗口与材质", style = MaterialTheme.typography.titleLarge)
            Text(width, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.probe_resource_status), style = MaterialTheme.typography.labelSmall)
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("减少透明度", Modifier.weight(1f))
                Switch(checked = reduceTransparency, onCheckedChange = onReduceTransparency,
                    modifier = Modifier.semantics { contentDescription = "减少透明度" })
            }
            OutlinedButton(onClick = onDialog, modifier = Modifier.fillMaxWidth()) { Text("检查弹层") }
            OutlinedButton(onClick = renderHealth::reportEffectFailure,
                enabled = !renderHealth.effectFailed, modifier = Modifier.fillMaxWidth()) {
                Text(if (renderHealth.effectFailed) "已熔断 · 实体回退" else "模拟特效熔断")
            }
            Text(if (renderHealth.effectFailed)
                "本窗口已停用玻璃特效，重开页面前不重试。请继续检查命中、滚动和弹层；切换主题或档位不会恢复特效。"
                else "模拟按钮只验证故障后的回退策略，不代表已验证真实 GPU 故障恢复。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProbeSample(index: Int) {
    MaFeiPanel(Modifier.fillMaxWidth()) {
        ProbePattern(Modifier.fillMaxWidth().height(120.dp), index)
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
private fun ProbePattern(modifier: Modifier, index: Int = 0) {
        Canvas(modifier) {
            val colors = if (index % 2 == 0) listOf(Color(0xFF4357AD), Color(0xFFDA8C63))
                else listOf(Color(0xFF247D83), Color(0xFFA074C4))
            drawRect(Brush.linearGradient(colors))
            repeat(12) { stripe ->
                val x = size.width * stripe / 11f
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, 0f),
                    Offset(x + 48.dp.toPx(), size.height), strokeWidth = 8.dp.toPx())
            }
        }
}

@Composable
private fun ProbeGlassViewport(level: String, reduceTransparency: Boolean, taps: Int, onTap: () -> Unit,
                               modifier: Modifier, scrollState: LazyListState,
                               barExpanded: Boolean, onToggleBar: () -> Unit, content: LazyListScope.() -> Unit) {
    val backdrop = rememberGlassBackdrop()
    val requested = GlassLevel.valueOf(level)
    val spec = MaFeiGlassDefaults.spec(GlassRole.Navigation, requested, reduceTransparency)
    val resolved = resolveGlassLevel(spec.level, currentGlassCapability(), hasBackdrop = true)
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    Box(modifier.fillMaxSize()) {
        // Only the scrolling source is recorded, never the consuming overlay.
        LazyColumn(
            Modifier.fillMaxSize().captureGlassBackdrop(backdrop),
            state = scrollState,
            contentPadding = PaddingValues(bottom = barHeight + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        AppGlassBar(
            level = requested,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } }.padding(vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f).animateContentSize(animationSpec = tween(220))
                .clickable(role = Role.Button, onClickLabel = if (barExpanded) "收起说明" else "展开说明",
                    onClick = onToggleBar), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (barExpanded) "液态玻璃 · 收起说明" else "液态玻璃 · 展开说明", style = MaterialTheme.typography.titleMedium)
                if (barExpanded) Text("请求 $requested → 能力档位 $resolved", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onTap) { Text("命中 · $taps") }
        }
    }
}

@Composable
private fun ProbeGlassComparison(reduceTransparency: Boolean) {
    val backdrop = rememberGlassBackdrop()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("同屏材质对照", style = MaterialTheme.typography.titleLarge)
        Text("以下卡片独立展示三档；底部浮层由控制区切换。", style = MaterialTheme.typography.bodyMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ProbePattern(Modifier.matchParentSize().captureGlassBackdrop(backdrop))
            if (maxWidth >= 600.dp) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassLevel.entries.forEach { level ->
                        ProbeMaterialCard(backdrop, level, reduceTransparency, Modifier.weight(1f))
                    }
                }
            } else {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassLevel.entries.forEach { level ->
                        ProbeMaterialCard(backdrop, level, reduceTransparency, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbeMaterialCard(backdrop: GlassBackdrop, level: GlassLevel, reduceTransparency: Boolean,
                              modifier: Modifier) {
    val spec = MaFeiGlassDefaults.spec(GlassRole.Card, level, reduceTransparency)
    val resolved = resolveGlassLevel(spec.level, currentGlassCapability(), hasBackdrop = true)
    AppGlassCard(backdrop, modifier, level) {
        Text(level.name, style = MaterialTheme.typography.titleMedium)
        Text("能力档位：$resolved", style = MaterialTheme.typography.labelMedium)
        Text("相同文字与留白，比较背景纹理和边缘。", style = MaterialTheme.typography.bodyMedium)
    }
}
