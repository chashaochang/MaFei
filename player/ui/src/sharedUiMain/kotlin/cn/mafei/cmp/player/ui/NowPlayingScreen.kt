package cn.mafei.cmp.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.components.LiquidSlider
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import com.kyant.shapes.RoundedRectangle
import kotlin.math.min

/**
 * Adaptive player controls over one page-level live capture. The media scene is
 * recorded separately; every optical control consumes it as a sibling, so no
 * surface samples itself.
 */
@Composable
fun NowPlayingScreen(
    media: NowPlayingMedia,
    state: PlayerState,
    capabilities: PlayerCapabilities,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    onSeek: (Long) -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    backdropArtwork: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberGlassBackdrop()
    val presentation = state.toNowPlayingPresentation(capabilities)
    var sliderProgress by remember(media.id, state.generation) {
        mutableFloatStateOf(presentation.progress)
    }
    LaunchedEffect(presentation.progress) {
        sliderProgress = presentation.progress
    }

    BoxWithConstraints(modifier.background(Color(0xFF070A0E))) {
        val wide = useWideNowPlayingLayout(maxWidth.value, maxHeight.value)

        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
            NowPlayingScene(
                media = media,
                wide = wide,
                artwork = artwork,
                backdropArtwork = backdropArtwork,
            )
        }

        LiquidButton(
            onClick = onBack,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            surfaceColor = Color.White.copy(alpha = .08f),
        ) {
            MaFeiIcon(MaFeiGlyph.Back, Modifier.size(20.dp), Color.White)
            Text("返回", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }

        NowPlayingConsole(
            media = media,
            presentation = presentation,
            wide = wide,
            sliderProgress = sliderProgress,
            backdrop = backdrop,
            onSliderProgress = { value ->
                sliderProgress = value
                state.durationMs?.takeIf { it > 0L }?.let { duration ->
                    onSeek((duration * value.coerceIn(0f, 1f)).toLong())
                }
            },
            onTogglePlay = onTogglePlay,
            onRetry = onRetry,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = if (wide) 36.dp else 16.dp, vertical = 18.dp)
                .widthIn(max = if (wide) 980.dp else 560.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun NowPlayingScene(
    media: NowPlayingMedia,
    wide: Boolean,
    artwork: @Composable (Modifier) -> Unit,
    backdropArtwork: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        backdropArtwork(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.18f
                scaleY = 1.18f
                alpha = .72f
            }.blur(54.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .16f),
                    .48f to Color(0xFF071018).copy(alpha = .4f),
                    1f to Color(0xFF05070A).copy(alpha = .7f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = if (wide) .2f else .06f),
                        Color.Transparent,
                        Color(0xFF06111A).copy(alpha = if (wide) .66f else .12f),
                    ),
                ),
            ),
        )

        if (wide) {
            WideMediaComposition(media, artwork)
        } else {
            CompactMediaComposition(media, artwork)
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.CompactMediaComposition(
    media: NowPlayingMedia,
    artwork: @Composable (Modifier) -> Unit,
) {
    val availableByHeight = ((maxHeight - 330.dp).coerceAtLeast(180.dp)) * (2f / 3f)
    val artworkWidth = minOf(360.dp, (maxWidth - 48.dp).coerceAtLeast(180.dp), availableByHeight)
    Column(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 82.dp, bottom = 194.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        artwork(
            Modifier
                .width(artworkWidth)
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedRectangle(30.dp),
                    ambientColor = Color.Black.copy(alpha = .48f),
                    spotColor = Color.Black.copy(alpha = .62f),
                )
                .clip(RoundedRectangle(30.dp)),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            media.title,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (media.subtitle.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                media.subtitle,
                color = Color.White.copy(alpha = .68f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.WideMediaComposition(
    media: NowPlayingMedia,
    artwork: @Composable (Modifier) -> Unit,
) {
    val short = maxHeight < 620.dp
    val topPadding = if (short) 60.dp else 72.dp
    val bottomPadding = if (short) 118.dp else 150.dp
    val horizontalPadding = if (maxWidth < 900.dp) 42.dp else 82.dp
    val contentHeight = (maxHeight - topPadding - bottomPadding - 12.dp).coerceAtLeast(150.dp)
    Row(
        Modifier.fillMaxSize().padding(
            start = horizontalPadding,
            end = if (short) 36.dp else 74.dp,
            top = topPadding,
            bottom = bottomPadding,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (maxWidth < 900.dp) 32.dp else 72.dp),
    ) {
        Box(Modifier.weight(.54f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val artworkWidth = min(420f, contentHeight.value * (2f / 3f)).dp
            artwork(
                Modifier
                    .width(artworkWidth)
                    .aspectRatio(2f / 3f)
                    .shadow(
                        elevation = 38.dp,
                        shape = RoundedRectangle(36.dp),
                        ambientColor = Color.Black.copy(alpha = .5f),
                        spotColor = Color.Black.copy(alpha = .68f),
                    )
                    .clip(RoundedRectangle(36.dp)),
            )
        }
        Column(
            Modifier.weight(.46f).widthIn(max = 520.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "正在播放",
                color = Color(0xFF8BC8FF),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                media.title,
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.subtitle.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    media.subtitle,
                    color = Color.White.copy(alpha = .7f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            media.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(24.dp))
                Text(
                    description,
                    color = Color.White.copy(alpha = .58f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingConsole(
    media: NowPlayingMedia,
    presentation: NowPlayingPresentation,
    wide: Boolean,
    sliderProgress: Float,
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    onSliderProgress: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        backdrop = backdrop,
        spec = GlassSurfaceSpec(
            role = GlassRole.PlayerOverlay,
            level = GlassLevel.Full,
            tintAlpha = .18f,
            intensity = 1f,
            cornerRadiusDp = if (wide) 34f else 30f,
        ),
        tint = Color(0xFF1C2B36),
        modifier = modifier,
    ) {
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PlaybackButton(presentation, backdrop, onTogglePlay, onRetry)
                Column(Modifier.widthIn(min = 150.dp, max = 220.dp)) {
                    Text(
                        media.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        presentation.status,
                        color = Color.White.copy(alpha = .58f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                PlaybackTimeline(
                    presentation = presentation,
                    sliderProgress = sliderProgress,
                    backdrop = backdrop,
                    onSliderProgress = onSliderProgress,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlaybackTimeline(
                    presentation = presentation,
                    sliderProgress = sliderProgress,
                    backdrop = backdrop,
                    onSliderProgress = onSliderProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        presentation.status,
                        color = Color.White.copy(alpha = .66f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    PlaybackButton(presentation, backdrop, onTogglePlay, onRetry)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaybackTimeline(
    presentation: NowPlayingPresentation,
    sliderProgress: Float,
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    onSliderProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (presentation.canSeek) {
            LiquidSlider(
                value = { sliderProgress },
                onValueChange = onSliderProgress,
                valueRange = 0f..1f,
                visibilityThreshold = .001f,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onSliderProgress((sliderProgress - .01f).coerceAtLeast(0f))
                                true
                            }
                            Key.DirectionRight -> {
                                onSliderProgress((sliderProgress + .01f).coerceAtMost(1f))
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .semantics {
                        contentDescription = "播放进度"
                        progressBarRangeInfo = ProgressBarRangeInfo(sliderProgress, 0f..1f)
                        setProgress { value ->
                            onSliderProgress(value.coerceIn(0f, 1f))
                            true
                        }
                    },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .18f)),
                )
                if (presentation.isBusy) {
                    Box(
                        Modifier.fillMaxWidth(.34f).height(4.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = .72f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(presentation.elapsed, color = Color.White.copy(alpha = .64f), style = MaterialTheme.typography.labelSmall)
            Text(presentation.duration, color = Color.White.copy(alpha = .64f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlaybackButton(
    presentation: NowPlayingPresentation,
    backdrop: cn.mafei.cmp.common.glass.GlassBackdrop,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit,
) {
    val action = if (presentation.showRetry) onRetry else onTogglePlay
    LiquidButton(
        onClick = action,
        backdrop = backdrop,
        surfaceColor = Color(0xFF72B9FF).copy(alpha = .12f),
    ) {
        if (presentation.isBusy) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            PlaybackGlyph(
                phase = if (presentation.showRetry) PlayerPhase.Error
                else if (presentation.isPlaying) PlayerPhase.Playing
                else PlayerPhase.Paused,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            when {
                presentation.showRetry -> "重试"
                presentation.isBusy -> "缓冲"
                presentation.isPlaying -> "暂停"
                else -> "播放"
            },
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PlaybackGlyph(
    phase: PlayerPhase,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        when (phase) {
            PlayerPhase.Playing, PlayerPhase.Buffering -> {
                val barWidth = size.width * .2f
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(size.width * .22f, size.height * .16f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height * .68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * .3f),
                )
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(size.width * .58f, size.height * .16f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height * .68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * .3f),
                )
            }
            PlayerPhase.Error -> {
                drawArc(
                    Color.White,
                    startAngle = -55f,
                    sweepAngle = 285f,
                    useCenter = false,
                    style = Stroke(width = size.width * .11f, cap = StrokeCap.Round),
                )
                val arrow = Path().apply {
                    moveTo(size.width * .72f, size.height * .08f)
                    lineTo(size.width * .92f, size.height * .18f)
                    lineTo(size.width * .7f, size.height * .3f)
                    close()
                }
                drawPath(arrow, Color.White)
            }
            else -> {
                val play = Path().apply {
                    moveTo(size.width * .3f, size.height * .16f)
                    lineTo(size.width * .84f, size.height * .5f)
                    lineTo(size.width * .3f, size.height * .84f)
                    close()
                }
                drawPath(play, Color.White)
            }
        }
    }
}
