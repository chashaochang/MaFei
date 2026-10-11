package cn.mafei.cmp.feature.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.MediaItem
import org.jetbrains.compose.resources.painterResource

@Composable
fun MusicDetailScreen(
    fallbackTitle: String, artist: Boolean, detail: MusicDetailState, tracks: MusicPageState,
    albums: MusicPageState = MusicPageState(initialized = true), onBack: () -> Unit, onRetry: () -> Unit,
    onMoreTracks: () -> Unit, onMoreAlbums: () -> Unit, onAlbum: (MediaItem) -> Unit,
    onPlay: (List<MediaItem>, Int) -> Unit,
    onFavorite: (() -> Unit)?, favoriteBusy: Boolean = false,
    artwork: @Composable (MediaItem, Modifier) -> Unit, currentId: String? = null,
    preparing: Boolean = false, playing: Boolean = false, bottomInset: Dp = 0.dp,
) {
    val safe = LocalMaFeiSafeArea.current
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, artist, tracks.cursor, albums.cursor, tracks.loadingMore, albums.loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { last ->
            if (last != null && last >= gridState.layoutInfo.totalItemsCount - 4) {
                if (artist && albums.cursor != null && albums.error == null && !albums.loadingMore) onMoreAlbums()
                if (!artist && tracks.cursor != null && tracks.error == null && !tracks.loadingMore) onMoreTracks()
            }
        }
    }
    MaFeiActionBarScaffold(detail.item?.title ?: fallbackTitle, onBack) {
        when {
            detail.loading && detail.item == null -> LegacyLoadingView(Modifier.fillMaxSize())
            detail.error != null && detail.item == null -> LegacyFailedView(onRetry, Modifier.fillMaxSize())
            detail.item != null -> BoxWithConstraints(Modifier.fillMaxSize().padding(start = safe.start, end = safe.end)) {
                val item = detail.item
                val wide = maxWidth >= 840.dp
                val pagePadding = if (maxWidth >= 600.dp) 24.dp else 16.dp
                val summaryWidth = if (maxWidth >= 1100.dp) 320.dp else 280.dp
                val albumCoverSize = (maxHeight - if (maxHeight < 620.dp) 300.dp else 350.dp).coerceIn(176.dp, summaryWidth)
                if (!artist && wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = pagePadding), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(Modifier.width(summaryWidth).fillMaxHeight()
                            .verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = safe.bottom + bottomInset + 20.dp)) {
                            MusicSummary(item, false, false, artwork, onPlay.takeIf { tracks.items.isNotEmpty() }
                                ?.let { { it(tracks.items, 0) } }, onFavorite, favoriteBusy,
                                coverSize = albumCoverSize)
                        }
                        LazyVerticalGrid(GridCells.Fixed(1), Modifier.weight(1f).fillMaxHeight(), state = gridState, contentPadding = PaddingValues(
                            top = 20.dp, bottom = safe.bottom + bottomInset + 20.dp)) {
                            items(tracks.items.size, key = { "track:${tracks.items[it].id.value}" }) { index ->
                                val track = tracks.items[index]
                                MusicTrackRow(track, index, track.id.value == currentId, preparing && track.id.value == currentId,
                                    playing, { onPlay(tracks.items, index) })
                            }
                            item { MusicSectionFeedback(tracks, onMoreTracks, onRetry) }
                        }
                    }
                } else {
                    val columns = musicColumns(maxWidth.value)
                    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(), state = gridState,
                        contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 20.dp,
                            bottom = safe.bottom + bottomInset + 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            MusicSummary(item, artist, !wide, artwork,
                                { onPlay(tracks.items, 0) }.takeIf { tracks.items.isNotEmpty() },
                                onFavorite, favoriteBusy, Modifier.padding(bottom = 26.dp))
                        }
                        if (artist && tracks.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("热门歌曲", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        items(tracks.items.size, key = { "track:${tracks.items[it].id.value}" }, span = { GridItemSpan(maxLineSpan) }) { index ->
                            val track = tracks.items[index]
                            MusicTrackRow(track, index, track.id.value == currentId, preparing && track.id.value == currentId,
                                playing, { onPlay(tracks.items, index) })
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) { MusicSectionFeedback(tracks, onMoreTracks, onRetry) }
                        if (artist && (albums.items.isNotEmpty() || albums.loading || albums.error != null)) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("专辑", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 26.dp, bottom = 18.dp))
                            }
                            items(albums.items, key = { "album:${it.id.value}" }) { album ->
                                MusicArtworkCard(album, { onAlbum(album) }, artwork, Modifier.padding(bottom = 18.dp))
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) { MusicSectionFeedback(albums, onMoreAlbums, onRetry) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSectionFeedback(state: MusicPageState, onMore: () -> Unit, onRetry: () -> Unit) {
    when {
        state.loading -> LegacyLoadingView(Modifier.fillMaxWidth().height(64.dp))
        state.error != null && state.items.isEmpty() -> LegacyFailedView(onRetry, Modifier.fillMaxWidth().height(180.dp))
        state.initialized && state.items.isEmpty() -> LegacyEmptyView(modifier = Modifier.fillMaxWidth().height(200.dp))
        else -> MusicPageFooter(state, onMore)
    }
}

@Composable
private fun MusicSummary(item: MediaItem, artist: Boolean, centered: Boolean,
    artwork: @Composable (MediaItem, Modifier) -> Unit, onPlay: (() -> Unit)?,
    onFavorite: (() -> Unit)?, favoriteBusy: Boolean, modifier: Modifier = Modifier, coverSize: Dp? = null) {
    val description: @Composable ColumnScope.() -> Unit = {
        Text(item.title, fontSize = if (artist) 24.sp else 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (artist && centered) TextAlign.Center else TextAlign.Start)
        if (!artist && !item.albumArtist.isNullOrBlank()) Text(item.albumArtist.orEmpty(), fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!artist) {
            val metadata = listOfNotNull(item.year?.takeIf { it > 0 }?.toString(), item.childCount?.takeIf { it > 0 }?.let { "$it 首歌曲" },
                item.runtimeMs?.takeIf { it > 0 }?.let(::musicDuration)).joinToString(" · ")
            if (metadata.isNotBlank()) Text(metadata, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (!artist && !item.overview.isNullOrBlank()) Text(item.overview.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (centered) 12.sp else 14.sp, lineHeight = if (centered) 17.sp else 21.sp,
            maxLines = if (centered) 2 else 5, overflow = TextOverflow.Ellipsis)
    }
    val favorite: @Composable () -> Unit = {
        if (onFavorite != null) Box(Modifier.size(44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = if (item.userData.isFavorite) "取消收藏" else "收藏" }
            .clickable(enabled = !favoriteBusy, onClick = onFavorite), contentAlignment = Alignment.Center) {
            Image(painterResource(if (item.userData.isFavorite) MaFeiLegacyDrawables.favoriteSelected else MaFeiLegacyDrawables.favoriteUnselected),
                null, Modifier.size(21.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
        }
    }
    val play: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (onPlay == null) .4f else 1f))
                .clickable(enabled = onPlay != null) { onPlay?.invoke() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(MaFeiLegacyDrawables.playRound), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White))
                Spacer(Modifier.width(8.dp))
                Text("播放全部", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            if (!centered) favorite()
        }
    }
    val content: @Composable ColumnScope.() -> Unit = {
        description()
        if (artist && !centered && !item.overview.isNullOrBlank()) Text(item.overview.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp, lineHeight = 21.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        play()
        if (artist && centered && !item.overview.isNullOrBlank()) Text(item.overview.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp, lineHeight = 21.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
    if (!artist && centered) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(136.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            artwork(item, Modifier.size(136.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                description()
                Spacer(Modifier.weight(1f))
                favorite()
            }
        }
        play()
    } else if (artist && !centered) Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically) {
        artwork(item, Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        artwork(item, (if (centered) Modifier.size(180.dp) else if (coverSize != null) Modifier.size(coverSize) else Modifier.fillMaxWidth().aspectRatio(1f))
            .clip(RoundedCornerShape(if (artist) 12.dp else 8.dp)))
        content()
    }
}
