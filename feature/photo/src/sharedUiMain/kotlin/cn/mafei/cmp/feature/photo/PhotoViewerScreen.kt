package cn.mafei.cmp.feature.photo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.player.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun PhotoViewerScreen(photos: List<MediaItem>, initialId: String, onBack: () -> Unit,
    onFavorite: (MediaItem) -> Unit, favoriteBusy: Boolean, onSave: ((MediaItem) -> Unit)?, saving: Boolean,
    info: MediaItem?, infoLoading: Boolean, infoFailed: Boolean, onInfo: (MediaItem) -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit) {
    if (photos.isEmpty()) { LegacyFailedView(onBack, Modifier.fillMaxSize()); return }
    val pager = rememberPagerState(initialPage = photos.indexOfFirst { it.id.value == initialId }.coerceAtLeast(0), pageCount = { photos.size })
    val current = photos.getOrNull(pager.currentPage) ?: photos.first()
    val safe = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    var controls by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    var showInfo by remember(current.id) { mutableStateOf(false) }
    var zoom by remember(current.id) { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()
    fun interact() { slideshow = false }
    LaunchedEffect(slideshow, pager.currentPage) {
        if (slideshow) {
            controls = false
            delay(5000)
            if (pager.currentPage < photos.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
            if (pager.currentPage >= photos.lastIndex) slideshow = false
        }
    }
    MaFeiBackHandler(true) { if (showInfo) showInfo = false else onBack() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, userScrollEnabled = zoom <= 1f,
            modifier = Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) { index ->
            ZoomablePhoto(photos[index], index == pager.currentPage,
                onTap = { interact(); controls = !controls }, onInteraction = ::interact,
                onZoom = { if (index == pager.currentPage) zoom = it }, artwork = artwork)
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().padding(start = safe.start + 14.dp, end = safe.end + 14.dp,
                top = safe.top + 8.dp, bottom = safe.bottom + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MaFeiActionBarButton(onBack, backdrop, "返回") { Image(painterResource(MaFeiLegacyDrawables.back), null,
                        Modifier.size(22.dp), colorFilter = ColorFilter.tint(Color.White)) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.title, color = Color.White, fontSize = 16.sp, maxLines = 1)
                        Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.size(44.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiquidButton(if (favoriteBusy) null else {{ interact(); onFavorite(current) }}, backdrop,
                        Modifier.semantics { contentDescription = if (current.userData.isFavorite) "取消收藏" else "收藏" }, height = 48.dp, horizontalPadding = 12.dp) {
                        Image(painterResource(if (current.userData.isFavorite) MaFeiLegacyDrawables.favoriteSelected else MaFeiLegacyDrawables.favoriteUnselected),
                            null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
                    }
                    LiquidButton({ slideshow = !slideshow && pager.currentPage < photos.lastIndex }, backdrop,
                        Modifier.semantics { contentDescription = if (slideshow) "停止幻灯片" else "播放幻灯片" }, height = 48.dp, horizontalPadding = 12.dp) {
                        ChromeGlyph(if (slideshow) PlayerChromeGlyph.Pause else PlayerChromeGlyph.Play, Modifier.size(24.dp))
                    }
                    LiquidButton({ interact(); showInfo = true; onInfo(current) }, backdrop,
                        Modifier.semantics { contentDescription = "照片信息" }, height = 48.dp, horizontalPadding = 12.dp) {
                        ChromeGlyph(PlayerChromeGlyph.Info, Modifier.size(24.dp))
                    }
                    if (onSave != null) LiquidButton(if (saving || current.photo?.canDownload != true) null else {{ interact(); onSave(current) }},
                        backdrop, height = 48.dp, horizontalPadding = 16.dp) { Text(if (saving) "保存中" else "保存", color = Color.White) }
                }
            }
        }
        if (showInfo) LegacyModalSheet("照片信息", { showInfo = false }, largeWindow = maxWidth >= 840.dp) {
            when {
                infoLoading -> LegacyLoadingView(Modifier.fillMaxWidth().height(160.dp))
                infoFailed -> LegacyFailedView({ onInfo(current) }, Modifier.fillMaxWidth().height(160.dp))
                else -> PhotoInfoContent((info?.takeIf { it.id == current.id } ?: current))
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(item: MediaItem, active: Boolean, onTap: () -> Unit, onInteraction: () -> Unit,
    onZoom: (Float) -> Unit, artwork: @Composable (MediaItem, Modifier) -> Unit) {
    var transform by remember(item.id) { mutableStateOf(PhotoTransform()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun change(scale: Float, x: Float, y: Float) {
        transform = clampPhotoTransform(scale, x, y, size.width.toFloat(), size.height.toFloat(),
            item.photo?.width?.toFloat() ?: 0f, item.photo?.height?.toFloat() ?: 0f)
        onZoom(transform.scale)
    }
    LaunchedEffect(active, size) {
        if (!active) transform = PhotoTransform()
        else change(transform.scale, transform.x, transform.y)
    }
    Box(Modifier.fillMaxSize().onSizeChanged { size = it }
        .pointerInput(item.id) { detectTapGestures(onTap = { onTap() }, onDoubleTap = {
            onInteraction(); change(if (transform.scale > 1f) 1f else 2.5f, 0f, 0f)
        }) }
        .pointerInput(item.id, size) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || (transform.scale > 1f && pan != androidx.compose.ui.geometry.Offset.Zero)) {
                        onInteraction()
                        change(transform.scale * zoom, transform.x + pan.x, transform.y + pan.y)
                        event.changes.forEach { if (!it.isConsumed && it.pressed) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        artwork(item, Modifier.fillMaxSize().graphicsLayer {
            scaleX = transform.scale; scaleY = transform.scale; translationX = transform.x; translationY = transform.y
        })
    }
}

@Composable
private fun PhotoInfoContent(item: MediaItem) {
    val photo = item.photo ?: return
    val rows = photoExifRows(photo)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        rows.forEach { (label, value) -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = .65f), fontSize = 13.sp, modifier = Modifier.width(88.dp))
            Text(value.orEmpty(), color = Color.White, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        } }
    }
}
