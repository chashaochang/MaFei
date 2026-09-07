package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiPanel
import cn.mafei.cmp.common.glass.GlassLevel

/** Stage 1 development entry. Requested glass levels currently resolve to opaque. */
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
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(12) { ProbeSample(it, level) }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { ProbeControls(level, widthLabel, { level = it }, { dialog = true }) }
                            items(12) { ProbeSample(it, level) }
                        }
                    }
                    Surface(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Stage 1 · 当前材质：实体回退", Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge)
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
            Text("玻璃引擎尚未接入，三档均使用实体背景。切换时保留布局和操作区域。",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onDialog, modifier = Modifier.fillMaxWidth()) { Text("检查弹层") }
        }
    }
}

@Composable
private fun ProbeSample(index: Int, level: String) {
    MaFeiPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${index + 1} / 材质样本", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("让内容保持清晰", style = MaterialTheme.typography.headlineSmall)
            Text("滚动查看卡片边界、文本层级和底部区域。窄窗口使用单栏，宽窗口将控制区放在侧边。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("请求：$level · 实际：Opaque", style = MaterialTheme.typography.labelMedium)
        }
    }
}
