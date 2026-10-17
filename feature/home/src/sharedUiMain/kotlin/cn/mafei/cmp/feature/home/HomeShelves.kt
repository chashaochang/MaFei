package cn.mafei.cmp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.ui.*
import cn.mafei.cmp.core.domain.*

@Composable
private fun HomeLibraryShelf(
    collections: List<MediaCollection>,
    onBrowseCollection: (MediaCollection) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "媒体库",
                Modifier.weight(1f).semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                Modifier.mafeiInteractive(onOpenLibrary, radiusDp = 12f, label = "查看全部媒体库")
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("查看全部", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                MaFeiIcon(MaFeiGlyph.Arrow, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(collections, key = { it.id.value }) { collection ->
                val palette = when (collection.mediaTypes.firstOrNull()) {
                    MediaType.Series, MediaType.Season, MediaType.Episode -> listOf(Color(0xFF244EA5), Color(0xFF477ED1))
                    MediaType.Movie, MediaType.Video -> listOf(Color(0xFF176B78), Color(0xFF36A7B3))
                    MediaType.Audio, MediaType.MusicAlbum, MediaType.MusicArtist -> listOf(Color(0xFF6B3D92), Color(0xFF9E70C4))
                    else -> listOf(Color(0xFF315A83), Color(0xFF5F89AE))
                }
                Box(
                    Modifier.width(104.dp).height(76.dp).clip(RoundedCornerShape(17.dp))
                        .background(Brush.linearGradient(palette))
                        .mafeiInteractive(
                            onClick = { onBrowseCollection(collection) },
                            radiusDp = 18f,
                            label = "打开${collection.name}",
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        collection.name,
                        Modifier.align(Alignment.TopStart).fillMaxWidth(.82f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MaFeiIcon(
                        MaFeiGlyph.Library,
                        Modifier.align(Alignment.BottomEnd).size(30.dp),
                        Color.White.copy(alpha = .34f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeMediaShelf(
    title: String,
    items: List<MediaItem>,
    onItem: (MediaItem) -> Unit,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)?,
    sharedArtworkModifier: (@Composable (MediaItem, Modifier) -> Modifier)? = null,
    kind: HomeShelfKind,
    layout: MaFeiLayout,
    leadingInset: Dp,
    onPlay: ((MediaItem) -> Unit)? = null,
    onToggleWatched: ((MediaItem) -> Unit)? = null,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
    onOpenMore: (() -> Unit)? = null,
) {
    val width = if (layout.family == MaFeiLayoutFamily.Phone) 160.dp else 240.dp
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = leadingInset),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            if (onOpenMore != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .20f),
                            RoundedCornerShape(18.dp),
                        )
                        .mafeiInteractive(onClick = onOpenMore, radiusDp = 18f, label = "更多")
                        .height(36.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("更多", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    MaFeiIcon(MaFeiGlyph.Chevron, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        LazyRow(
            modifier = Modifier.mafeiArtworkShelfBleed(mediaArtworkHoverSideInset(width)),
            horizontalArrangement = Arrangement.spacedBy(if (layout.family == MaFeiLayoutFamily.Phone) 8.dp else 16.dp),
            contentPadding = PaddingValues(
                start = leadingInset + mediaArtworkHoverSideInset(width),
                end = leadingInset + mediaArtworkHoverSideInset(width),
                top = mediaArtworkHoverTopInset(width * 9f / 16f),
            ),
        ) {
            items(items, key = { it.id.value }) { item ->
                val text = homeShelfText(item, kind)
                val interaction = rememberMediaCardInteraction()
                val cardBounds = Modifier.width(width)
                Column(
                    (sharedArtworkModifier?.invoke(item, cardBounds) ?: cardBounds).mafeiMediaCardInteraction(
                        interaction,
                        onClick = { onItem(item) },
                        label = "查看${text.title}",
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp))) {
                        val posterModifier = Modifier.fillMaxSize()
                        if (artwork != null) {
                            artwork(item, posterModifier)
                        } else {
                            TextPoster(text.title, posterModifier)
                        }
                        if (kind == HomeShelfKind.Resume) progress(item)?.let { value ->
                            MaFeiProgressLine(
                                progress = value,
                                modifier = Modifier.align(Alignment.BottomCenter),
                                progressColor = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Black.copy(alpha = .36f),
                            )
                        }
                        MediaCardHoverOverlay(
                            visible = interaction.showActions,
                            title = text.title,
                            onPlay = { (onPlay ?: onItem)(item) },
                            onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                            onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                            isWatched = item.userData.isPlayed,
                            isFavorite = item.userData.isFavorite,
                        )
                    }
                    Text(
                        text.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text.subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal enum class HomeShelfKind { Resume, NextUp, Recent }

internal data class HomeCardText(val title: String, val subtitle: String)

private fun homeShelfText(item: MediaItem, kind: HomeShelfKind): HomeCardText {
    // 卡片文案风格与所有展示文本一样由 model 提供（见 core:domain/MediaPresentation.kt ），
    // 此处不再判断 provider。
    if (item.displayStyle.cardStyle == CardTextStyle.Plain) {
        return HomeCardText(item.title, item.metaLine)
    }
    return when (kind) {
        HomeShelfKind.Resume -> when (item.mediaType) {
            MediaType.Movie -> HomeCardText(item.title, item.typeLabel ?: item.metaLine)
            MediaType.Episode -> HomeCardText(
                item.cardTitle,
                listOfNotNull(item.seasonTitle?.takeIf { it.isNotBlank() }, item.title.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
            )
            else -> HomeCardText(item.title, item.metaLine)
        }
        HomeShelfKind.NextUp -> if (item.mediaType == MediaType.Episode) {
            HomeCardText(item.cardTitle, item.episodeCardLine ?: item.metaLine)
        } else HomeCardText(item.title, item.metaLine)
        HomeShelfKind.Recent -> homeLatestText(item)
    }
}

internal fun homeLatestText(item: MediaItem): HomeCardText {
    if (item.displayStyle.cardStyle == CardTextStyle.EpisodeAware && item.mediaType == MediaType.Episode) {
        return HomeCardText(item.cardTitle, item.episodeCardLine ?: item.metaLine)
    }
    // Feiniu's legacy latest cards show the TV episode total before the year.
    // The provider maps this to childCount; keeping the rule here means the
    // ordinary movie/folder cards retain their existing year-only subtitle.
    if (item.provider == ProviderKind.Feiniu && item.mediaType == MediaType.Series) {
        val subtitle = buildList {
            item.childCount?.takeIf { it > 0 }?.let { add("共${it}集") }
            item.year?.let { add(it.toString()) }
        }.joinToString(" · ")
        return HomeCardText(item.title, subtitle)
    }
    return HomeCardText(item.title, item.year?.toString() ?: "")
}

internal fun progress(item: MediaItem): Float? = item.runtimeMs?.takeIf { it > 0 && item.userData.hasProgress }?.let {
    (item.userData.playbackPositionMs.toFloat() / it).coerceIn(0f, 1f)
}

private fun progressLabel(item: MediaItem): String {
    val minutes = item.userData.playbackPositionMs / 60_000
    return "看到 $minutes 分钟"
}
