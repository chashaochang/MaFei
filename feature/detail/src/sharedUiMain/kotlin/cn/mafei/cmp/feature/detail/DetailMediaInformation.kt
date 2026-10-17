package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.ui.LocalMaFeiNavigationChrome
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.LocalShellBackdrop
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.common.ui.mafeiDismissOnOutsidePointer

@Composable
internal fun MediaInfoSection(
    source: DetailPlaybackSource,
    onOpenDetails: () -> Unit,
) {
    if (!source.hasFileInformation && !source.hasVideoInformation) return
    val largeTypography = usesLargeDetailTypography()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (source.hasFileInformation) {
            Text(
                "文件信息",
                Modifier.semantics { heading() },
                color = LocalContentColor.current,
                fontSize = if (largeTypography) 18.sp else 15.sp,
                lineHeight = if (largeTypography) 24.sp else 21.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DetailInformationBackground)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailInformationValue("文件位置", source.filePath, largeTypography = largeTypography)
                DetailInformationValue("文件大小", source.fileSize, largeTypography = largeTypography)
                DetailInformationValue("文件创建日期", source.fileCreatedDate, largeTypography = largeTypography)
                DetailInformationValue("添加日期", source.addedDate, largeTypography = largeTypography)
            }
        }
        if (source.hasVideoInformation) {
            if (source.hasFileInformation) Spacer(Modifier.height(DetailMediaSubsectionSpacing))
            Text(
                "视频信息",
                Modifier.semantics { heading() },
                color = LocalContentColor.current,
                fontSize = if (largeTypography) 18.sp else 15.sp,
                lineHeight = if (largeTypography) 24.sp else 21.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DetailInformationBackground)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DetailInformationValue("视频", source.videoSummary, multiline = true, largeTypography = largeTypography)
                DetailInformationValue("音频", source.audioSummary, multiline = true, largeTypography = largeTypography)
                DetailInformationValue("字幕", source.subtitleSummary, multiline = true, largeTypography = largeTypography)
                if (source.infoSections.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .18f)))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .mafeiInteractive(onClick = onOpenDetails, radiusDp = 8f, label = "查看全部媒体信息")
                            .padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "查看全部",
                            Modifier.weight(1f),
                            color = Color.White.copy(alpha = .88f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        MaFeiIcon(
                            glyph = MaFeiGlyph.Chevron,
                            modifier = Modifier.size(16.dp),
                            color = Color.White.copy(alpha = .88f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInformationValue(
    label: String,
    value: String,
    multiline: Boolean = false,
    largeTypography: Boolean = false,
) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            color = Color.White.copy(alpha = .60f),
            fontSize = if (largeTypography) 13.sp else 12.sp,
            lineHeight = if (largeTypography) 19.sp else 18.sp,
        )
        Text(
            value,
            color = Color.White.copy(alpha = .88f),
            fontSize = if (largeTypography) 14.sp else 12.sp,
            lineHeight = when {
                multiline && largeTypography -> 22.sp
                multiline -> 20.sp
                largeTypography -> 20.sp
                else -> 18.sp
            },
        )
    }
}

@Composable
internal fun BoxScope.MediaInfoSheetOverlay(
    sections: List<DetailInfoSection>,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    surfaceMotion: Modifier = Modifier,
) {
    if (sections.isEmpty()) return
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
    GlassSurface(
            backdrop = LocalShellBackdrop.current ?: backdrop,
            spec = OverviewSheetSurfaceSpec,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.align(sheetAlignment)
                .padding(horizontal = if (largeWindow) 24.dp else 0.dp)
                .widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(.70f)
                .then(surfaceMotion)
                .then(if (largeWindow) Modifier else sheetDrag)
                .pointerInput(Unit) { detectTapGestures { } },
            exportedBackdrop = sheetMaterialBackdrop,
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(
                    Modifier.fillMaxSize().padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = safeArea.bottom + 10.dp,
                    ),
                ) {
                    DetailSheetHeader(
                        "文件媒体信息",
                        closeButtonBackdrop,
                        onDismiss,
                        contentColor = Color.White,
                    )
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(contentScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        sections.forEach { section ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    section.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                section.cards.forEach { card -> DetailInfoCard(card) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
    }
}

@Composable
private fun DetailInfoCard(card: DetailInfoCard) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(DetailInformationBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            card.title,
            color = Color.White.copy(alpha = .92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        card.rows.forEach { row ->
            if (row.dividerBefore) {
                Box(Modifier.fillMaxWidth().height(.5.dp).background(Color.White.copy(alpha = .18f)))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    row.title,
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    row.value,
                    Modifier.widthIn(max = 440.dp),
                    color = Color.White.copy(alpha = .60f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

internal val DetailInformationBackground = Color(0x24FFFFFF)
