package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.LocalShellBackdrop
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.mafeiDismissOnOutsidePointer

/**
 * 播放选择区（legacy MediaSourceSelector + AudioSubtitleSelector 的页面内化）：
 * 多版本时先选版本（按账号和当前媒体条目保存），再按需挑音轨/字幕轨；
 * 字幕「无字幕」传 -1。区块只在真的有多可选内容时出现。
 */
@Composable
internal fun PlaybackSelectionSection(
    sources: List<DetailPlaybackSource>,
    selectedMediaSourceId: String?,
    onSelectMediaSource: ((String?) -> Unit)?,
    selectedAudioStreamIndex: Int?,
    onSelectAudioTrack: ((Int?) -> Unit)?,
    selectedSubtitleStreamIndex: Int?,
    onSelectSubtitleTrack: ((Int?) -> Unit)?,
    pickerKind: DetailPickerKind?,
    onOpenPicker: (DetailPickerKind) -> Unit,
    backdrop: GlassBackdrop,
    largeLayout: Boolean,
) {
    val primary = sources.firstOrNull { it.id == selectedMediaSourceId } ?: sources.firstOrNull() ?: return
    val multiSource = sources.size > 1 && onSelectMediaSource != null
    val audioTracks = primary.audioOptions
    val subtitleTracks = primary.subtitleOptions
    val effectiveAudioIndex = selectedAudioStreamIndex ?: primary.defaultAudioStreamIndex
    val effectiveSubtitleIndex = selectedSubtitleStreamIndex ?: primary.defaultSubtitleStreamIndex
    val audioLabel = audioTracks.firstOrNull { it.streamIndex == effectiveAudioIndex }?.compactLabel ?: "无音频"
    val subtitleLabel = subtitleTracks.firstOrNull {
        it.streamIndex == effectiveSubtitleIndex
    }?.compactLabel ?: "无字幕"

    Row(
        Modifier
            .then(if (largeLayout) Modifier.widthIn(max = LargeDetailControlWidth) else Modifier)
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSource) {
            DetailSelectorTrigger(
                label = primary.compactLabel,
                hasOptions = true,
                expanded = pickerKind == DetailPickerKind.Source,
                onClick = { onOpenPicker(DetailPickerKind.Source) },
                backdrop = backdrop,
                largeTypography = largeLayout,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.weight(if (multiSource) 2f else 1f).fillMaxHeight()) {
            DetailSelectorTrigger(
                label = subtitleLabel,
                hasOptions = subtitleTracks.size > 1 && onSelectSubtitleTrack != null,
                expanded = pickerKind == DetailPickerKind.Subtitle,
                onClick = { onOpenPicker(DetailPickerKind.Subtitle) },
                backdrop = backdrop,
                largeTypography = largeLayout,
                modifier = Modifier.weight(1f),
            )
            DetailSelectorTrigger(
                label = audioLabel,
                hasOptions = audioTracks.size > 1 && onSelectAudioTrack != null,
                expanded = pickerKind == DetailPickerKind.Audio,
                onClick = { onOpenPicker(DetailPickerKind.Audio) },
                backdrop = backdrop,
                largeTypography = largeLayout,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailSelectorTrigger(
    label: String,
    hasOptions: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    backdrop: GlassBackdrop,
    largeTypography: Boolean,
    modifier: Modifier = Modifier,
) {
    LiquidButton(
        onClick = if (hasOptions) onClick else ({}),
        backdrop = backdrop,
        isInteractive = hasOptions,
        height = 32.dp,
        horizontalPadding = 8.dp,
        modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            // Keep the label and chevron as one centred group. `fill = true`
            // consumed the whole remaining row and pushed the chevron to the
            // button edge instead of keeping it beside the text.
            Modifier.weight(1f, fill = false),
            color = Color.White,
            fontSize = if (largeTypography) 14.sp else 12.sp,
            lineHeight = if (largeTypography) 18.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hasOptions) {
            MaFeiIcon(
                glyph = MaFeiGlyph.Chevron,
                modifier = Modifier.size(12.dp).graphicsLayer {
                    rotationZ = if (expanded) -90f else 90f
                },
                color = Color.White,
            )
        }
    }
}

internal enum class DetailPickerKind {
    Source,
    Subtitle,
    Audio,
}

internal sealed interface DetailModal {
    data object Overview : DetailModal
    data object MediaInfo : DetailModal
    data class Picker(val kind: DetailPickerKind) : DetailModal
}

/**
 * Same-layer translation of the legacy ArkUI bindSheet selectors. The source
 * list keeps the 68vp checkmark row; audio and subtitle keep the 66vp radio row.
 */
@Composable
internal fun BoxScope.DetailPickerSheetOverlay(
    kind: DetailPickerKind,
    sources: List<DetailPlaybackSource>,
    selectedSource: DetailPlaybackSource?,
    selectedAudioStreamIndex: Int?,
    selectedSubtitleStreamIndex: Int?,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    onSelectSource: (String) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    surfaceMotion: Modifier = Modifier,
) {
    val source = selectedSource ?: return
    val safeArea = LocalMaFeiSafeArea.current
    val sheetMaterialBackdrop = rememberGlassBackdrop()
    val closeButtonBackdrop = sheetMaterialBackdrop
    val contentScrollState = rememberScrollState()
    val sheetDrag = rememberDetailSheetDrag(contentScrollState, onDismiss)
    val largeWindow = LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true
    val sheetAlignment = if (largeWindow) Alignment.Center else Alignment.BottomCenter
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))
            .mafeiDismissOnOutsidePointer(onDismiss),
    )
    BoxWithConstraints(
        Modifier.align(sheetAlignment)
            .padding(horizontal = if (largeWindow) 24.dp else 0.dp)
            .widthIn(max = 720.dp).fillMaxWidth(),
    ) {
            val sheetMaxHeight = maxHeight * .70f
            val listMaxHeight = (sheetMaxHeight - 76.dp).coerceAtLeast(120.dp)
            GlassSurface(
                backdrop = LocalShellBackdrop.current ?: backdrop,
                spec = OverviewSheetSurfaceSpec,
                tint = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight)
                    .then(surfaceMotion)
                    .then(if (largeWindow) Modifier else sheetDrag)
                    .pointerInput(Unit) { detectTapGestures { } },
                exportedBackdrop = sheetMaterialBackdrop,
            ) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Column(
                        Modifier.fillMaxWidth().padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = safeArea.bottom + 10.dp,
                        ),
                    ) {
                        DetailSheetHeader(
                            title = when (kind) {
                                DetailPickerKind.Source -> "选择播放版本"
                                DetailPickerKind.Subtitle -> "字幕"
                                DetailPickerKind.Audio -> "音频"
                            },
                            backdrop = closeButtonBackdrop,
                            onDismiss = onDismiss,
                            contentColor = Color.White,
                        )
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = listMaxHeight).verticalScroll(contentScrollState),
                            verticalArrangement = Arrangement.spacedBy(
                                if (kind == DetailPickerKind.Source) 4.dp else 12.dp,
                            ),
                        ) {
                            when (kind) {
                                DetailPickerKind.Source -> sources.forEach { option ->
                                    DetailSourceOptionRow(
                                        option = option,
                                        selected = option.id == source.id,
                                        onClick = { onSelectSource(option.id) },
                                    )
                                }

                                DetailPickerKind.Subtitle -> {
                                    val selectedIndex = selectedSubtitleStreamIndex ?: source.defaultSubtitleStreamIndex
                                    source.subtitleOptions.forEach { option ->
                                        DetailTrackOptionRow(
                                            option = option,
                                            selected = option.streamIndex == selectedIndex,
                                            onClick = { onSelectSubtitle(option.streamIndex) },
                                        )
                                    }
                                }

                                DetailPickerKind.Audio -> {
                                    val selectedIndex = selectedAudioStreamIndex ?: source.defaultAudioStreamIndex
                                    source.audioOptions.forEach { option ->
                                        DetailTrackOptionRow(
                                            option = option,
                                            selected = option.streamIndex == selectedIndex,
                                            onClick = { onSelectAudio(option.streamIndex) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun DetailSourceOptionRow(
    option: DetailPlaybackSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .04f))
            .mafeiInteractive(onClick = onClick, radiusDp = 12f, label = option.title),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 16.dp).width(20.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                MaFeiIcon(
                    glyph = MaFeiGlyph.Watched,
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                option.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                option.summary,
                color = Color.White.copy(alpha = .72f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailTrackOptionRow(
    option: DetailMediaOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .04f))
            .mafeiInteractive(onClick = onClick, radiusDp = 8f, label = option.title)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val strokeWidth = 1.5.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = if (selected) .95f else .70f),
                radius = size.minDimension / 2f - strokeWidth / 2f,
                style = Stroke(width = strokeWidth),
            )
            if (selected) {
                drawCircle(
                    color = Color.White.copy(alpha = .95f),
                    radius = size.minDimension * .25f,
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                option.title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.summary.isNotEmpty()) {
                Text(
                    option.summary,
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
