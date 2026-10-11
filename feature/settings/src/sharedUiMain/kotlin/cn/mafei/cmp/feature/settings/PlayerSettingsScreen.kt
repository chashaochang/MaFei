package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.player.api.PlayerPlaybackPreferences

/** Uses legacy decode order and cache choices; engine selection is added only when supported. */
@Composable
fun PlayerSettingsScreen(
    preferences: PlayerPlaybackPreferences,
    loading: Boolean,
    loadFailed: Boolean,
    saving: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSelect: (PlayerPlaybackPreferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    engineSelection: Boolean = false,
) {
    val safeArea = LocalMaFeiSafeArea.current
    Box(modifier.fillMaxSize()) {
        MaFeiActionBarScaffold(title = "播放器设置", onBack = onBack, modifier = Modifier.fillMaxSize()) {
            when {
                loading -> LegacyLoadingView()
                loadFailed -> LegacyFailedView(onRetry = onRetry)
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier.widthIn(max = 680.dp).fillMaxWidth()
                            .padding(horizontal = 16.dp).padding(top = 12.dp)
                            .padding(bottom = safeArea.bottom.coerceAtLeast(32.dp)),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (engineSelection) {
                            Text("播放器", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface)
                            val engines = cn.mafei.cmp.player.api.PlayerEngineType.entries
                            LegacyListGroup(items = engines.map { engine -> LegacyListGroupItem(
                                when (engine) {
                                    cn.mafei.cmp.player.api.PlayerEngineType.Auto -> "自动"
                                    cn.mafei.cmp.player.api.PlayerEngineType.System -> "系统播放器"
                                    cn.mafei.cmp.player.api.PlayerEngineType.Mpv -> "MPV"
                                }, isSelected = preferences.engine == engine,
                            ) }, enabled = !saving, onItemClick = { onSelect(preferences.copy(engine = engines[it])) })
                        }
                        Text("MPV 解码方式", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(false, true).forEach { hardware ->
                                val isSelected = preferences.useHardwareDecode == hardware
                                val label = if (hardware) "硬解" else "软解"
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 108.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .mafeiInteractive(
                                            onClick = { onSelect(preferences.copy(useHardwareDecode = hardware)) },
                                            enabled = !saving, radiusDp = 12f, label = label,
                                        )
                                        .semantics { selected = isSelected }
                                        .padding(horizontal = 14.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(label, fontSize = 15.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        if (hardware) "调用手机硬件解码，更省电，HDR 片源更适合，但少数机型可能有兼容问题。"
                                        else "由播放器自己解码，兼容性更好，遇到黑屏或花屏先试它。",
                                        fontSize = 11.sp, lineHeight = 16.sp,
                                        color = if (isSelected) Color.White.copy(alpha = .9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("MPV 缓存大小", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        LegacyListGroup(
                            items = PlayerPlaybackPreferences.CacheSizesMb.map {
                                LegacyListGroupItem("$it MB", isSelected = preferences.cacheSizeMb == it, subtitle = when (it) {
                                    512 -> "更省内存，适合局域网或短视频"
                                    1024 -> "默认推荐，兼顾稳定性和内存占用"
                                    1536 -> "弱网更稳，拖动和切集缓冲更从容"
                                    else -> "缓存拉满，长视频和弱网更稳，但内存占用最高"
                                })
                            },
                            enabled = !saving,
                            onItemClick = { onSelect(preferences.copy(cacheSizeMb = PlayerPlaybackPreferences.CacheSizesMb[it])) },
                        )
                    }
                }
            }
        }
        LegacyTransientToast(message = error, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
