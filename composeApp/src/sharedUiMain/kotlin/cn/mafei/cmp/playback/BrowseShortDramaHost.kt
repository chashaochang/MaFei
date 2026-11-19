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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.feature.home.MediaBrowser
import cn.mafei.cmp.feature.home.PlaybackQueue
import cn.mafei.cmp.feature.home.userDataFor
import cn.mafei.cmp.player.api.PlayerFailure
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.ui.ChromeGlyph
import cn.mafei.cmp.player.ui.PlayerChromeGlyph
import cn.mafei.cmp.session.SessionPlaybackRuntime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun BrowseShortDramaHost(
    request: ShortDramaRequest,
    platform: ShortDramaPlatform,
    browser: MediaBrowser,
    createPlayback: (PlayerRuntime) -> SessionPlaybackRuntime,
    onClose: () -> Unit,
    onOpenRegular: (MediaItem, PlaybackQueue, Long) -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
    onPlayerVisible: (Boolean) -> Unit,
    loadItem: suspend (MediaId) -> MediaItem,
    loadArtwork: suspend (MediaImage, Int) -> ByteArray?,
    loadPreview: suspend (MediaId, String?, Long) -> PlayerSeekPreviewFrame?,
) {
    val scope = rememberCoroutineScope()
    val drama = remember(request, platform) { ShortDramaSession(request, scope, platform, createPlayback) }
    val foreground = LocalShortDramaForeground.current
    var queue by remember(request) { mutableStateOf(PlaybackQueue.forItem(request.item, emptyList())) }
    var episodesOpen by remember(request) { mutableStateOf(false) }
    var fullscreen by remember(request) { mutableStateOf(false) }
    var showFullscreenControls by remember(request) { mutableStateOf(true) }
    var scrubbing by remember(request) { mutableStateOf(false) }
    var detailsOpen by remember(request) { mutableStateOf(false) }
    var queueFailed by remember(request) { mutableStateOf(false) }
    var queueComplete by remember(request) { mutableStateOf(false) }
    var queueAttempt by remember(request) { mutableIntStateOf(0) }
    val pager = rememberPagerState { queue.size }
    LaunchedEffect(request, queueAttempt) {
        val result = browser.shortDramaQueueFor(request.item, refresh = queueAttempt > 0)
        snapshotFlow { !pager.isScrollInProgress }.first { it }
        val activeId = queue.items.getOrNull(pager.settledPage)?.id ?: request.item.id
        val updated = result.queue.items
        if (result.complete || updated.map { it.id }.containsAll(queue.items.map { it.id })) {
            val anchor = updated.indexOfFirst { it.id == activeId }.takeIf { it >= 0 }
            if (anchor != null) {
                queue = result.queue.copy(index = anchor)
                pager.requestScrollToPage(anchor)
            }
        }
        queueComplete = result.complete
        queueFailed = !result.complete
    }
    val workId = request.item.seriesId ?: request.item.id
    val work by produceState<MediaItem?>(null, workId) {
        value = try {
            loadItem(workId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    val facts by browser.state.collectAsState()
    val favorite = work?.let { (facts.userDataFor(it.id) ?: it.userData).isFavorite } ?: false
    var favoriteBusy by remember(request) { mutableStateOf(false) }
    val toggleFavorite: (() -> Unit)? = work?.takeIf { browser.capabilities.favoritesEdit }?.let { item ->
        {
            if (!favoriteBusy) {
                favoriteBusy = true
                scope.launch {
                    try {
                        browser.toggleFavorite(item.copy(userData = item.userData.copy(isFavorite = favorite)))
                    } finally {
                        favoriteBusy = false
                    }
                }
            }
        }
    }
    val latestVisible by rememberUpdatedState(onPlayerVisible)
    val latestFullscreen by rememberUpdatedState(onFullscreenChanged)
    val windowedHost = LocalMaFeiWindowFeatures.current.windowedHost
    DisposableEffect(Unit) {
        latestVisible(true)
        if (!windowedHost) latestFullscreen(false)
        onDispose {
            latestFullscreen(false)
            latestVisible(false)
        }
    }
    fun setFullscreen(value: Boolean) {
        fullscreen = value
        showFullscreenControls = true
        onFullscreenChanged(value)
    }
    LaunchedEffect(drama, foreground) { drama.setForeground(foreground) }
    DisposableEffect(drama) { onDispose(drama::close) }
    fun closeDetails(play: Boolean) {
        detailsOpen = false
        scope.launch { drama.closeDetails(play) }
    }
    fun openDetails() {
        episodesOpen = false
        detailsOpen = true
        scope.launch { drama.openDetails() }
    }
    val back: () -> Unit = {
        when {
            detailsOpen -> closeDetails(false)
            episodesOpen -> episodesOpen = false
            fullscreen -> setFullscreen(false)
            else -> onClose()
        }
    }
    MaFeiBackHandler(enabled = true, onBack = back)

    val insets = currentMaFeiSafeArea()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val loaded = queue
        LaunchedEffect(drama, pager, loaded) {
            // Align the settled selection before collecting positions in the replacement queue.
            if (pager.currentPage != loaded.index) pager.scrollToPage(loaded.index)
            snapshotFlow { pager.settledPage }.collect { if (queue === loaded) drama.select(loaded.items, it) }
        }
        val settled = pager.settledPage.coerceIn(loaded.items.indices)
        val episode = loaded.items[settled]
        val settledSlot = drama.slot(episode)
        val settledState = settledSlot?.playback?.controller?.state?.collectAsState()?.value ?: PlayerState()
        val settledPhase = settledState.phase
        val workTitle = work?.title ?: episode.seriesTitle ?: episode.title
        val video = episode.mediaSources.firstNotNullOfOrNull { it.videoStream }
        val runtimeInfo = settledState.runtimeInfo
        val rotation = (video?.rotation ?: 0) % 180 != 0
        val wide = when {
            video?.widthPx != null && video.heightPx != null ->
                if (rotation) video.heightPx!! > video.widthPx!! else video.widthPx!! > video.heightPx!!
            runtimeInfo?.videoWidthPx != null && runtimeInfo.videoHeightPx != null ->
                runtimeInfo.videoWidthPx!! > runtimeInfo.videoHeightPx!!
            else -> false
        }
        val autoNext = !detailsOpen && !scrubbing && foreground
        LaunchedEffect(pager, settled, settledPhase, autoNext, loaded.size) {
            if (autoNext && settledPhase == PlayerPhase.Ended && settled < loaded.items.lastIndex) {
                snapshotFlow { !pager.isScrollInProgress }.first { it }
                if (pager.settledPage == settled) pager.animateScrollToPage(settled + 1)
            }
        }
        val episodeLabel = episode.episodeNumber?.let { "第 $it 集" }
            ?: if (episode.seriesId != null) "第 ${settled + 1} 集" else null
        val captionVisible = !detailsOpen && !episodesOpen && (!fullscreen || showFullscreenControls || scrubbing)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val normalBottom = if (loaded.size > 1 || queueFailed) 156.dp else 108.dp
            val panelHeight = (maxHeight * .5f).coerceAtLeast(insets.bottom + 218.dp)
                .coerceAtMost(maxHeight * .82f)
            val videoTop = if (fullscreen) 0.dp else insets.top + if (episodesOpen) 8.dp else 52.dp
            val videoBottom = when {
                fullscreen -> 0.dp
                episodesOpen -> panelHeight
                else -> insets.bottom + normalBottom
            }
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                beyondViewportPageCount = 1,
                // The pager can remeasure before this scope recomposes. Read
                // live keys, just like pageCount, rather than capturing loaded.
                key = { queue.items.getOrNull(it)?.id?.value ?: it },
                userScrollEnabled = loaded.size > 1 && !scrubbing && !fullscreen && !episodesOpen && !detailsOpen,
            ) { index ->
                val pageQueue = queue
                val page = pageQueue.items.getOrNull(index) ?: return@VerticalPager
                val slot = drama.slot(page)
                val current = index == pager.settledPage && !pager.isScrollInProgress
                val status = slot?.playback?.controller?.state?.collectAsState()?.value ?: PlayerState()
                val failed = slot?.preparationFailed?.value == true || status.phase == PlayerPhase.Error
                val toggle = { scope.launch { drama.toggle() }; Unit }
                val backdrop = rememberGlassBackdrop()
                var feedbackDismissed by remember(failed) { mutableStateOf(false) }
                val showFailure = current && failed && !feedbackDismissed
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (showFailure) Modifier.captureGlassBackdrop(backdrop) else Modifier)) {
                        if (slot != null) {
                            Box(Modifier.fillMaxSize().padding(top = videoTop, bottom = videoBottom)) {
                                platform.surface(slot.runtime, Modifier.fillMaxSize())
                            }
                        }
                        if (current && !failed && !detailsOpen && !episodesOpen) {
                            Box(Modifier.fillMaxSize().padding(top = videoTop, bottom = videoBottom).clickable(
                                interactionSource = null,
                                indication = null,
                                onClickLabel = if (status.phase == PlayerPhase.Playing) "暂停" else "播放",
                                role = Role.Button,
                                onClick = { if (fullscreen) showFullscreenControls = !showFullscreenControls else toggle() },
                            ))
                            if (status.phase in setOf(PlayerPhase.Idle, PlayerPhase.Loading, PlayerPhase.Buffering)) {
                                ShortDramaProgress(Modifier.align(Alignment.Center))
                            } else if (status.phase == PlayerPhase.Paused || status.phase == PlayerPhase.Ended ||
                                (fullscreen && showFullscreenControls && status.phase == PlayerPhase.Playing)) {
                                ShortDramaIcon(
                                    when (status.phase) {
                                        PlayerPhase.Ended -> MaFeiGlyph.Refresh
                                        PlayerPhase.Playing -> MaFeiGlyph.Pause
                                        else -> MaFeiGlyph.Play
                                    },
                                    when (status.phase) {
                                        PlayerPhase.Ended -> "重播"
                                        PlayerPhase.Playing -> "暂停"
                                        else -> "播放"
                                    },
                                    Modifier.align(Alignment.Center),
                                    onClick = toggle,
                                )
                            }
                        }
                        if (current && failed && feedbackDismissed) {
                            ShortDramaIcon(MaFeiGlyph.Refresh, "播放选项", Modifier.align(Alignment.Center)) {
                                feedbackDismissed = false
                            }
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
                                    onOpenRegular(page, pageQueue.copy(index = index), status.positionMs)
                                } else toggle()
                            },
                            onDismiss = dismiss,
                            backdrop = backdrop,
                            bodyContent = {
                                if (!unsupported) {
                                    ShortDramaCommand("用普通播放器打开") {
                                        drama.close()
                                        onOpenRegular(page, pageQueue.copy(index = index), status.positionMs)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            if (captionVisible) {
                Row(
                    Modifier.align(Alignment.TopCenter).widthIn(max = 660.dp).fillMaxWidth()
                        .padding(start = insets.start + 10.dp, end = insets.end + 10.dp, top = insets.top + 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fullscreen) {
                        ShortDramaIconButton("退出全屏", Modifier, back) { ChromeGlyph(PlayerChromeGlyph.ExitFullscreen, Modifier.size(20.dp)) }
                    } else {
                        ShortDramaIcon(MaFeiGlyph.Back, "返回", Modifier, onClick = back)
                    }
                    Text(episodeLabel.orEmpty(), Modifier.weight(1f), color = Color.White,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (!fullscreen && wide) {
                        ShortDramaIconButton("全屏观看", Modifier, { setFullscreen(true) }) {
                            ChromeGlyph(PlayerChromeGlyph.Fullscreen, Modifier.size(20.dp))
                        }
                    }
                    if (!fullscreen) toggleFavorite?.let {
                        ShortDramaIcon(MaFeiGlyph.Favorite, if (favorite) "取消收藏" else "收藏", Modifier, favorite, it)
                    }
                }
                key(episode.id) {
                    ShortDramaPlaybackChrome(
                        title = workTitle,
                        synopsis = episode.overview ?: work?.overview,
                        position = settledState.positionMs,
                        duration = settledState.durationMs,
                        selectionLabel = if (!fullscreen && queueComplete && loaded.size > 1) "选集 · 共 ${loaded.size} 集"
                            else if (!fullscreen && (queueFailed || loaded.size > 1)) "选集" else null,
                        onDetails = if (fullscreen) null else ({ openDetails() }),
                        onEpisodes = { episodesOpen = true },
                        onSeek = { time -> scope.launch { drama.seek(episode.id, time) } },
                        onScrubbing = { scrubbing = it },
                        loadPreview = { time ->
                            loadPreview(episode.id, settledSlot?.playback?.prepared?.value?.mediaSourceId, time)
                                ?: settledSlot?.playback?.previewFrame(time)
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(start = insets.start, end = insets.end, bottom = insets.bottom + 12.dp),
                        fullscreen = fullscreen,
                    )
                }
            }
            if (episodesOpen) {
                ShortDramaEpisodePanel(
                    work = work ?: request.item, title = workTitle, items = loaded.items, currentIndex = settled,
                    height = panelHeight, complete = queueComplete, loadingFailed = queueFailed,
                    onRetry = { queueAttempt++ }, loadArtwork = loadArtwork,
                    onFavorite = toggleFavorite, favorite = favorite, onDetails = ::openDetails,
                    onClose = { episodesOpen = false },
                    onPick = { index ->
                        episodesOpen = false
                        if (index != settled) scope.launch { pager.scrollToPage(index) }
                    },
                )
            }
            if (detailsOpen) {
                ShortDramaDetail(
                    item = work ?: request.item, title = workTitle,
                    episodeCount = loaded.size.takeIf { queueComplete && it > 1 }, loadArtwork = loadArtwork,
                    favorite = favorite, onFavorite = toggleFavorite, onBack = { closeDetails(false) },
                    onContinue = { closeDetails(true) },
                )
            }
        }
    }
}

@Composable
private fun ShortDramaProgress(modifier: Modifier) {
    CircularProgressIndicator(modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
}

@Composable
internal fun ShortDramaIcon(glyph: MaFeiGlyph, label: String, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    ShortDramaIconButton(label, modifier, onClick) {
        MaFeiIcon(glyph, color = if (filled) Color(0xFFFFCC66) else Color.White, filled = filled)
    }
}

@Composable
private fun ShortDramaIconButton(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.48f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
internal fun ShortDramaCommand(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clip(CircleShape).background(Color(0xFF242426))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}
