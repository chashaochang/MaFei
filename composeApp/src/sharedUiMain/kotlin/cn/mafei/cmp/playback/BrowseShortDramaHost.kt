package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.session.SessionPlaybackRuntime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun BrowseShortDramaHost(
    request: ShortDramaRequest,
    platform: ShortDramaPlatform,
    browser: MediaBrowser,
    createPlayback: (PlayerRuntime) -> SessionPlaybackRuntime,
    onClose: () -> Unit,
    onOpenRegular: (MediaItem, PlaybackQueue, Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drama = remember(request, platform) { ShortDramaSession(request, scope, platform, createPlayback) }
    val foreground = LocalShortDramaForeground.current
    var queue by remember(request) { mutableStateOf<PlaybackQueue?>(null) }
    LaunchedEffect(request) { queue = browser.playbackQueueFor(request.item) }
    LaunchedEffect(drama, foreground) { drama.setForeground(foreground) }
    DisposableEffect(drama) { onDispose(drama::close) }
    MaFeiBackHandler(enabled = true, onBack = onClose)

    val insets = currentMaFeiSafeArea()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val loaded = queue
        if (loaded == null) {
            ShortDramaProgress(Modifier.align(Alignment.Center))
        } else {
            val pager = rememberPagerState(initialPage = loaded.index) { loaded.size }
            LaunchedEffect(drama, pager, loaded) {
                snapshotFlow { pager.settledPage }.collect { drama.select(loaded.items, it) }
            }
            val settled = pager.settledPage
            val settledSlot = drama.slot(loaded.items[settled])
            val settledPhase = settledSlot?.playback?.controller?.state?.collectAsState()?.value?.phase
            LaunchedEffect(pager, settled, settledPhase) {
                if (settledPhase == PlayerPhase.Ended && settled < loaded.items.lastIndex) {
                    snapshotFlow { !pager.isScrollInProgress }.first { it }
                    if (pager.settledPage == settled) pager.animateScrollToPage(settled + 1)
                }
            }
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                beyondViewportPageCount = 1,
                key = { loaded.items[it].id.value },
            ) { index ->
                val episode = loaded.items[index]
                val slot = drama.slot(episode)
                val current = index == pager.settledPage && !pager.isScrollInProgress
                val status = slot?.playback?.controller?.state?.collectAsState()?.value ?: PlayerState()
                val failed = slot?.preparationFailed?.value == true || status.phase == PlayerPhase.Error
                val toggle = { scope.launch { drama.toggle() }; Unit }
                val backdrop = rememberGlassBackdrop()
                var feedbackDismissed by remember(failed) { mutableStateOf(false) }
                val showFailure = current && failed && !feedbackDismissed
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (showFailure) Modifier.captureGlassBackdrop(backdrop) else Modifier)) {
                        if (slot != null) platform.surface(slot.runtime, Modifier.fillMaxSize())
                        if (current && !failed) {
                            Box(Modifier.fillMaxSize().clickable(
                                interactionSource = null,
                                indication = null,
                                onClickLabel = if (status.phase == PlayerPhase.Playing) "暂停" else "播放",
                                role = Role.Button,
                                onClick = toggle,
                            ))
                            if (status.phase in setOf(PlayerPhase.Idle, PlayerPhase.Loading, PlayerPhase.Buffering)) {
                                ShortDramaProgress(Modifier.align(Alignment.Center))
                            } else if (status.phase == PlayerPhase.Paused || status.phase == PlayerPhase.Ended) {
                                ShortDramaIcon(
                                    if (status.phase == PlayerPhase.Ended) MaFeiGlyph.Refresh else MaFeiGlyph.Play,
                                    if (status.phase == PlayerPhase.Ended) "重播" else "播放",
                                    Modifier.align(Alignment.Center),
                                    toggle,
                                )
                            }
                        }
                        if (current && failed && feedbackDismissed) {
                            ShortDramaIcon(MaFeiGlyph.Refresh, "播放选项", Modifier.align(Alignment.Center)) {
                                feedbackDismissed = false
                            }
                        }
                        if (current) {
                            ShortDramaCaption(
                                episode, index, loaded.size, status,
                                Modifier.align(Alignment.BottomCenter),
                                start = insets.start + 20.dp,
                                end = insets.end + 20.dp,
                                bottom = insets.bottom + 16.dp,
                            )
                        }
                    }
                    if (showFailure) {
                        val unsupported = status.failure == PlayerFailure.UnsupportedMedia
                        val dismiss = { feedbackDismissed = true }
                        MaFeiBackHandler(enabled = true, onBack = dismiss)
                        MaFeiGlassConfirmDialog(
                            title = "无法播放",
                            content = if (unsupported) "此播放源需要普通播放器。" else "可重试，或用普通播放器打开。",
                            confirmText = if (unsupported) "打开" else "重试",
                            dismissText = "取消",
                            onConfirm = {
                                if (unsupported) {
                                    drama.close()
                                    onOpenRegular(episode, loaded.copy(index = index), status.positionMs)
                                } else toggle()
                            },
                            onDismiss = dismiss,
                            backdrop = backdrop,
                            bodyContent = {
                                if (!unsupported) {
                                    ShortDramaCommand("用普通播放器打开") {
                                        drama.close()
                                        onOpenRegular(episode, loaded.copy(index = index), status.positionMs)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        ShortDramaIcon(MaFeiGlyph.Back, "返回", Modifier.align(Alignment.TopStart)
            .padding(start = insets.start + 12.dp, top = insets.top + 8.dp), onClose)
    }
}

@Composable
private fun ShortDramaCaption(
    episode: MediaItem,
    index: Int,
    count: Int,
    status: PlayerState,
    modifier: Modifier,
    start: androidx.compose.ui.unit.Dp,
    end: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
            .padding(start = start, top = 30.dp, end = end, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(episode.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("第 ${index + 1} 集 · 共 $count 集", color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp)
        val fraction = status.durationMs?.takeIf { it > 0 }
            ?.let { (status.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.24f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
        }
    }
}

@Composable
private fun ShortDramaProgress(modifier: Modifier) {
    CircularProgressIndicator(modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
}

@Composable
private fun ShortDramaIcon(glyph: MaFeiGlyph, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.48f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { MaFeiIcon(glyph, color = Color.White) }
}

@Composable
private fun ShortDramaCommand(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}
