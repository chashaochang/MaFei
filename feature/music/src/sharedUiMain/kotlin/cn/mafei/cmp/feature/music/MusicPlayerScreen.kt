package cn.mafei.cmp.feature.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidSlider
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.ui.ChromeGlyph
import cn.mafei.cmp.player.ui.PlayerChromeGlyph
import org.jetbrains.compose.resources.painterResource

@Composable
fun MusicPlayerScreen(item: MediaItem, state: PlayerState, preparing: Boolean, error: String?,
    queue: List<MediaItem>, queueIndex: Int, repeatMode: MusicRepeatMode,
    onBack: () -> Unit, onTogglePlay: () -> Unit, onRetry: () -> Unit, onSeek: (Long) -> Unit,
    onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, onSelect: (Int) -> Unit,
    onRepeat: () -> Unit, onFavorite: (() -> Unit)?, favoriteBusy: Boolean,
    onRemove: (Int) -> Unit, onClear: () -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit,
    ambientArtwork: @Composable (MediaItem, Modifier) -> Unit) {
    val safe = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    var queueVisible by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var pendingPosition by remember(item.id) { mutableStateOf<Float?>(null) }
    var seeking by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(state.positionMs, seeking) {
        if (!seeking && pendingPosition?.let { kotlin.math.abs(it - state.positionMs) < 1000f } == true) pendingPosition = null
    }
    LaunchedEffect(seeking, pendingPosition) {
        if (!seeking && pendingPosition != null) { kotlinx.coroutines.delay(2000); pendingPosition = null }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val cover = if (wide) (maxWidth * .34f).coerceIn(320.dp, 420.dp)
            else (maxWidth - 64.dp).coerceIn(180.dp, 360.dp)
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            ambientArtwork(item, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        }
        Column(Modifier.fillMaxSize().padding(start = safe.start + 24.dp, end = safe.end + 24.dp,
            top = safe.top + 76.dp, bottom = safe.bottom + 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val controls: @Composable () -> Unit = {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.toMusicTrackOrNull()?.artist.orEmpty().ifBlank { item.albumTitle.orEmpty() },
                            color = Color.White.copy(alpha = .72f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val duration = (state.durationMs?.takeIf { it > 0 } ?: item.runtimeMs ?: 0L).coerceAtLeast(0L)
                    val range = duration.toFloat().coerceAtLeast(1f)
                    LiquidSlider(value = { pendingPosition ?: state.positionMs.coerceIn(0L, duration).toFloat() },
                        onValueChange = { position -> if (duration > 0) {
                            pendingPosition = position
                            seeking = true
                        } }, valueRange = 0f..range, visibilityThreshold = 100f, backdrop = backdrop,
                        onValueChangeFinished = { position ->
                            seeking = false
                            if (duration > 0) onSeek(position.toLong().coerceIn(0, duration))
                        })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(musicDuration(pendingPosition?.toLong() ?: state.positionMs), color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                        Text(musicDuration(duration), color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        MusicTransportButton("上一首", PlayerChromeGlyph.Previous, onPrevious, backdrop, 52)
                        MusicTransportButton(if (state.phase == PlayerPhase.Playing || state.phase == PlayerPhase.Buffering) "暂停" else "播放",
                            if (state.phase == PlayerPhase.Playing || state.phase == PlayerPhase.Buffering) PlayerChromeGlyph.Pause else PlayerChromeGlyph.Play,
                            if (preparing) null else onTogglePlay, backdrop, 76)
                        MusicTransportButton("下一首", PlayerChromeGlyph.Next, onNext, backdrop, 52)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        LiquidButton(onRepeat, backdrop, Modifier.semantics { contentDescription = when(repeatMode) {
                            MusicRepeatMode.Off -> "循环关闭"
                            MusicRepeatMode.All -> "列表循环"
                            MusicRepeatMode.One -> "单曲循环"
                        } }, height = 52.dp, horizontalPadding = 14.dp) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(painterResource(MaFeiLegacyDrawables.replay), null, Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(if (repeatMode == MusicRepeatMode.Off) Color.White else MaterialTheme.colorScheme.primary))
                                if (repeatMode == MusicRepeatMode.One) Text("1", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                        if (onFavorite != null) LiquidButton(if (favoriteBusy) null else onFavorite, backdrop,
                            Modifier.semantics { contentDescription = if (item.userData.isFavorite) "取消收藏" else "收藏" },
                            height = 52.dp, horizontalPadding = 14.dp) {
                            Image(painterResource(if (item.userData.isFavorite) MaFeiLegacyDrawables.favoriteSelected else MaFeiLegacyDrawables.favoriteUnselected),
                                null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }
                    if (preparing) LegacyLoadingView(Modifier.fillMaxWidth().height(48.dp))
                    if (error != null || state.phase == PlayerPhase.Error) {
                        Text(error ?: "播放失败，请重试", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                        LiquidButton(onRetry, backdrop) { Text("重试", color = Color.White) }
                    }
                }
            }
            if (wide) Row(Modifier.widthIn(max = 1080.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
                artwork(item, Modifier.size(cover).clip(RoundedCornerShape(16.dp)))
                Box(Modifier.weight(1f)) { controls() }
            } else {
                artwork(item, Modifier.size(cover).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.height(28.dp))
                controls()
            }
        }
        MaFeiActionBar("", onBack, backdrop, actionCount = 1, contentColor = Color.White, actions = { buttonBackdrop ->
            MaFeiActionBarButton({ queueVisible = true }, buttonBackdrop, "播放队列") {
                ChromeGlyph(PlayerChromeGlyph.More, Modifier.size(24.dp))
            }
        })
        if (queueVisible) LegacyModalSheet("播放队列", { queueVisible = false }, largeWindow = wide) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                Text("清空", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = !preparing) { queueVisible = false; clearConfirm = true }.padding(12.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 16.dp)) {
                itemsIndexed(queue, key = { _, track -> track.id.value }) { index, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MusicTrackRow(track, index, index == queueIndex, preparing && index == queueIndex,
                            state.phase == PlayerPhase.Playing, { if (!preparing) onSelect(index) }, Modifier.weight(1f))
                        if (index != queueIndex) Box(Modifier.size(44.dp).semantics { contentDescription = "移除${track.title}" }
                            .clickable(enabled = !preparing) { onRemove(index) }, contentAlignment = Alignment.Center) {
                            Image(painterResource(MaFeiLegacyDrawables.close), null, Modifier.size(16.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        }
        if (clearConfirm) MaFeiGlassConfirmDialog("清空播放队列", "确定清空播放队列吗？", "清空", "取消",
            onConfirm = { clearConfirm = false; queueVisible = false; onClear() },
            onDismiss = { clearConfirm = false; queueVisible = true }, backdrop = backdrop, destructive = true)
    }
}

@Composable
private fun MusicTransportButton(label: String, glyph: PlayerChromeGlyph, onClick: (() -> Unit)?,
    backdrop: GlassBackdrop, size: Int) {
    LiquidButton(onClick, backdrop, Modifier.size(size.dp).semantics { contentDescription = label },
        height = size.dp, horizontalPadding = ((size - 28) / 2).dp) {
        ChromeGlyph(glyph, Modifier.size(28.dp), if (onClick == null) Color.White.copy(alpha = .3f) else Color.White)
    }
}
