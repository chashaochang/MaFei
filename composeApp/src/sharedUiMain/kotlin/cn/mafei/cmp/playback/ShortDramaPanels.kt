package cn.mafei.cmp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.currentMaFeiSafeArea
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

private const val EpisodesPerGroup = 30

internal fun shortDramaEpisodeGroups(size: Int): List<IntRange> =
    (0 until size).step(EpisodesPerGroup).map { it until minOf(it + EpisodesPerGroup, size) }

@Composable
internal fun ShortDramaEpisodePanel(
    work: MediaItem,
    title: String,
    items: List<MediaItem>,
    currentIndex: Int,
    height: Dp,
    complete: Boolean,
    loadingFailed: Boolean,
    onRetry: () -> Unit,
    loadArtwork: suspend (MediaImage, Int) -> ByteArray?,
    onFavorite: (() -> Unit)?,
    favorite: Boolean,
    onDetails: () -> Unit,
    onClose: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val insets = currentMaFeiSafeArea()
    val poster = shortDramaPoster(work, loadArtwork)
    val seasons = remember(items) { items.indices.groupBy { items[it].seasonId?.value ?: items[it].seasonNumber } }
    val seasonKeys = seasons.keys.toList()
    val initialSeason = seasons.keys.indexOfFirst { currentIndex in seasons.getValue(it) }.coerceAtLeast(0)
    var season by remember { mutableIntStateOf(initialSeason) }
    val seasonItems = seasons[seasonKeys.getOrNull(season)] ?: items.indices.toList()
    val groups = shortDramaEpisodeGroups(seasonItems.size)
    val firstPage = (seasonItems.indexOf(currentIndex).coerceAtLeast(0) / EpisodesPerGroup)
    var group by remember { mutableIntStateOf(firstPage) }
    val visible = groups.getOrNull(group) ?: groups.firstOrNull() ?: IntRange.EMPTY
    val listState = rememberLazyGridState()
    val selectedPosition = seasonItems.indexOf(currentIndex) - visible.first
    LaunchedEffect(season, group) {
        listState.scrollToItem(selectedPosition.takeIf { it in 0 until visible.count() } ?: 0)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 480.dp
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClickLabel = "关闭选集", onClick = onClose))
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 660.dp).fillMaxWidth()
                .height(height).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color(0xFF222224))
                .padding(start = insets.start + 16.dp, end = insets.end + 16.dp, bottom = insets.bottom),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 7.dp)
                .size(36.dp, 4.dp).clip(CircleShape).background(Color(0xFF636366)))
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                ShortDramaPoster(poster, title, Modifier.width(if (compact) 32.dp else 44.dp))
                Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onDetails).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f, fill = false), color = Color.White, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MaFeiIcon(MaFeiGlyph.Chevron, color = Color.White)
                    }
                    if (complete && items.size > 1) Text("共 ${items.size} 集", color = Color(0xFFAEAEB2), fontSize = 13.sp)
                }
                onFavorite?.let { ShortDramaIcon(MaFeiGlyph.Favorite, if (favorite) "取消收藏" else "收藏", Modifier, favorite, it) }
            }
            if (!compact || loadingFailed) {
                Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("选集", Modifier.weight(1f), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (loadingFailed) ShortDramaCommand("重试", onClick = onRetry)
                }
            }
            if (seasonKeys.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    seasonKeys.forEachIndexed { index, key ->
                        val first = items[seasons.getValue(key).first()]
                        ShortDramaSegment(first.seasonTitle ?: "第 ${first.seasonNumber ?: index + 1} 季", index == season) {
                            season = index
                            group = if (index == initialSeason) firstPage else 0
                        }
                    }
                }
            }
            if (groups.size > 1) {
                val groupScroll = rememberScrollState()
                val density = androidx.compose.ui.platform.LocalDensity.current
                LaunchedEffect(season) { groupScroll.scrollTo(with(density) { (group * 76).dp.roundToPx() }) }
                Row(Modifier.horizontalScroll(groupScroll)) {
                    groups.forEachIndexed { index, range ->
                        val from = items[seasonItems[range.first]].episodeNumber ?: range.first + 1
                        val to = items[seasonItems[range.last]].episodeNumber ?: range.last + 1
                        ShortDramaSegment("$from–$to", index == group) { group = index }
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp), state = listState,
                modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp),
            ) {
                items(visible.map { seasonItems[it] }, key = { items[it].id.value }) { index ->
                    val chosen = index == currentIndex
                    val number = items[index].episodeNumber ?: seasonItems.indexOf(index) + 1
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (chosen) Color(0xFF46362C) else Color(0xFF343436))
                            .semantics { selected = chosen }.clickable(role = Role.Button, onClickLabel = "第 $number 集") { onPick(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$number", color = if (chosen) Color(0xFFFFC58B) else Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortDramaSegment(label: String, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.widthIn(min = 76.dp).height(38.dp).semantics { selected = active }
        .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (active) Color.White else Color(0xFFAEAEB2),
            fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
internal fun ShortDramaDetail(
    item: MediaItem,
    title: String,
    episodeCount: Int?,
    loadArtwork: suspend (MediaImage, Int) -> ByteArray?,
    favorite: Boolean,
    onFavorite: (() -> Unit)?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val insets = currentMaFeiSafeArea()
    val poster = shortDramaPoster(item, loadArtwork)
    val tint = remember(poster) {
        try {
            poster?.let {
                val color = it.toPixelMap(it.width / 2, it.height / 3, 1, 1)[0, 0]
                Color(color.red * 0.27f, color.green * 0.27f, color.blue * 0.27f)
            } ?: Color(0xFF2E2E32)
        } catch (_: Exception) {
            Color(0xFF2E2E32)
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Color(0xFF171719), Color(0xFF111113))))) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxSize()
            .padding(start = insets.start + 18.dp, end = insets.end + 18.dp, top = insets.top + 5.dp, bottom = insets.bottom + 12.dp)) {
            ShortDramaIcon(MaFeiGlyph.Back, "返回", Modifier, onClick = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShortDramaPoster(poster, title, Modifier.width(108.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        val metadata = listOfNotNull(episodeCount?.let { "共 $it 集" }, item.year?.toString())
                        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), color = Color(0xFFCBCBD0), fontSize = 14.sp)
                        if (item.genres.isNotEmpty()) Text(item.genres.take(4).joinToString(" · "), color = Color(0xFFCBCBD0), fontSize = 14.sp)
                    }
                }
                item.overview?.takeIf(String::isNotBlank)?.let { overview ->
                    var expanded by remember(item.id) { mutableStateOf(false) }
                    var truncated by remember(item.id) { mutableStateOf(false) }
                    Spacer(Modifier.height(25.dp))
                    Text("剧情简介", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(overview, Modifier.padding(top = 9.dp), color = Color(0xFFD5D5D9), fontSize = 15.sp,
                        lineHeight = 23.sp, maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow })
                    if (truncated || expanded) {
                        Text(if (expanded) "收起" else "展开",
                            Modifier.clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 11.dp),
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onFavorite?.let {
                    ShortDramaDetailAction(if (favorite) "已收藏" else "收藏", MaFeiGlyph.Favorite, false, Modifier.weight(1f), it)
                }
                ShortDramaDetailAction("继续播放", MaFeiGlyph.Play, true, Modifier.weight(1.35f), onContinue)
            }
        }
    }
}

@Composable
private fun ShortDramaDetailAction(label: String, glyph: MaFeiGlyph, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (primary) Color(0xFF1C1C1E) else Color.White
    Row(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
        .background(if (primary) Color(0xFFF0F0F3) else Color(0xFF343436))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        MaFeiIcon(glyph, Modifier.size(19.dp), color)
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ShortDramaPoster(bitmap: ImageBitmap?, title: String, modifier: Modifier) {
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) {
        if (bitmap == null) TextPoster(title, Modifier.fillMaxSize())
        else Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun shortDramaPoster(item: MediaItem, loadArtwork: suspend (MediaImage, Int) -> ByteArray?): ImageBitmap? {
    val images = item.preferredPoster()
    val bitmap by produceState<ImageBitmap?>(null, images) {
        value = try {
            val bytes = images.firstNotNullOfOrNull { loadArtwork(it, 360) }
            bytes?.let { withContext(Dispatchers.Default) { it.decodeToImageBitmap() } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    return bitmap
}
