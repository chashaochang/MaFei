package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.placeholder_folder
import cn.mafei.cmp.common.ui.generated.resources.placeholder_video
import cn.mafei.cmp.common.ui.generated.resources.tag_4k
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

data class LegacySortChoice<T>(val label: String, val value: T)

/** Mechanical Compose translation of ArkUI `VideoCard`. */
@Composable
fun LegacyVideoCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = item.title,
    subtitle: String = legacyListSubtitle(item),
    textAlign: TextAlign = TextAlign.Start,
    poster: (@Composable (Modifier) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier.mafeiInteractive(onClick, radiusDp = 6f, label = "查看$title", onLongClick = onLongClick)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        val posterShape = RoundedCornerShape(6.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .clip(posterShape)
                .background(Color(0xFFCBD2D3))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, posterShape),
        ) {
            if (poster == null) {
                LegacyPosterPlaceholder(item.mediaType, Modifier.fillMaxSize().clip(posterShape))
            } else {
                poster(Modifier.fillMaxSize().clip(posterShape))
            }
            item.communityRating?.takeIf { it > 0.0 }?.let { score ->
                Text(
                    text = formatLegacyRating(score),
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x60000000))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            LegacyMediaTagBadges(
                tags = item.tags,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
        Text(
            title,
            Modifier.fillMaxWidth().padding(top = 8.dp),
            // The card can be rendered directly in the adaptive shell scene,
            // outside a MaFeiSurface. Do not depend on a parent content-color
            // provider here; Material's fallback is black on the dark canvas.
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
        Text(
            subtitle,
            Modifier.fillMaxWidth().padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

/**
 * Mechanical Compose translation of ArkUI `BaseItemCard`.
 *
 * This is intentionally separate from [LegacyVideoCard]: the actor-detail
 * page used a fixed 100vp card, 10vp media radius, every provider tag and a
 * different episode-subtitle rule. Reusing the library grid card silently
 * changed both its data and its geometry.
 */
@Composable
fun LegacyBaseItemCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    poster: (@Composable (Modifier) -> Unit)? = null,
) {
    Column(
        modifier.width(100.dp).mafeiInteractive(onClick, radiusDp = 10f, label = "查看${item.title}")
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        val posterShape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, posterShape)
                .clip(posterShape)
                .background(Color(0xFFCBD2D3)),
        ) {
            if (poster == null) {
                LegacyPosterPlaceholder(item.mediaType, Modifier.fillMaxSize())
            } else {
                poster(Modifier.fillMaxSize())
            }
            item.communityRating?.takeIf { it > 0.0 }?.let { score ->
                Text(
                    text = formatLegacyRating(score),
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x60000000))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            LegacyMediaTagBadges(
                tags = item.tags,
                maxTags = null,
                filterOther = false,
                deduplicate = false,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
        Text(
            item.title,
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
        Text(
            legacyBaseItemSubtitle(item),
            Modifier.fillMaxWidth().padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

/** Exact two-badge treatment shared by ArkUI `VideoCard` and season episodes. */
@Composable
fun LegacyMediaTagBadges(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxTags: Int? = 2,
    filterOther: Boolean = true,
    deduplicate: Boolean = true,
) {
    val normalizedTags = tags.map(String::trim)
        .filter { it.isNotEmpty() && (!filterOther || !it.uppercase().startsWith("OTH")) }
    val uniqueTags = if (deduplicate) normalizedTags.distinctBy(String::uppercase) else normalizedTags
    val visibleTags = maxTags?.let(uniqueTags::take) ?: uniqueTags
    if (visibleTags.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleTags.forEach { tag ->
            if (tag == "4K") {
                Image(
                    painter = painterResource(Res.drawable.tag_4k),
                    contentDescription = "4K",
                    modifier = Modifier.width(28.dp).height(20.dp),
                    contentScale = ContentScale.FillBounds,
                    alpha = 0.7f,
                )
            } else {
                Text(
                    text = tag,
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
fun LegacyPosterPlaceholder(
    mediaType: MediaType,
    modifier: Modifier = Modifier,
) {
    val image = if (mediaType == MediaType.Season || mediaType == MediaType.Folder) {
        Res.drawable.placeholder_folder
    } else {
        Res.drawable.placeholder_video
    }
    Box(modifier.background(Color(0xFFCBD2D3)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Inside,
        )
    }
}

/** Mechanical Compose translation of the 46vp sort row shared by VideoList/Favorite. */
@Composable
fun LegacySortRow(
    label: String,
    totalCount: String,
    descending: Boolean,
    onOpenSort: () -> Unit,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.height(46.dp).clickable(onClick = onOpenSort).padding(vertical = 13.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Icon(
            painter = painterResource(
                if (descending) MaFeiLegacyDrawables.sortDescending else MaFeiLegacyDrawables.sortAscending,
            ),
            contentDescription = if (descending) "降序" else "升序",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp).size(16.dp).clickable(onClick = onToggleDirection),
        )
        Text(
            totalCount,
            Modifier.padding(start = 12.dp).clip(RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Bottom-aligned list dialog translated from ArkUI `ListDialog`. */
@Composable
fun <T> LegacySortSheet(
    choices: List<LegacySortChoice<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = maxHeight * .4f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    "排序",
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    itemsIndexed(choices) { _, choice ->
                        Text(
                            choice.label,
                            Modifier.fillMaxWidth().clickable {
                                onSelected(choice.value)
                                onDismiss()
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = if (choice.value == selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

fun legacyListSubtitle(item: MediaItem): String {
    // VideoList/Search/Favorite first overwrite the mapper subtitle with the
    // production year whenever it exists, including episode rows.
    item.year?.let { return it.toString() }
    if (item.mediaType != MediaType.Episode) return ""
    val title = item.title.trim()
    val prefix = when {
        (item.seasonNumber ?: 0) > 0 && (item.episodeNumber ?: 0) > 0 ->
            "S${item.seasonNumber}:E${item.episodeNumber}"
        (item.episodeNumber ?: 0) > 0 -> "第 ${item.episodeNumber} 集"
        else -> return title
    }
    return if (title.isEmpty()) prefix else "$prefix - $title"
}

/** Exact `BaseItemCard.subtitle()` used only by actor-detail work shelves. */
fun legacyBaseItemSubtitle(item: MediaItem): String {
    if (item.mediaType == MediaType.Episode) {
        return "S${item.seasonNumber ?: 0}:E${item.episodeNumber ?: 0} - ${item.title}"
    }
    return item.year?.toString().orEmpty()
}

/**
 * Content-width grid density shared by media, favorites, search and home.
 * The input is the width remaining after navigation and page padding, so the
 * thresholds are deliberately lower than the legacy full-window breakpoints.
 * This keeps poster cards around 130–190dp instead of stretching them on wide
 * windows, while retaining the legacy 2/3/4/5/6/8/12 progression.
 */
fun legacyMediaColumns(contentWidthDp: Float): Int = when {
    contentWidthDp < 320f -> 2
    contentWidthDp < 600f -> 3
    contentWidthDp < 720f -> 4
    contentWidthDp < 840f -> 5
    contentWidthDp < 1200f -> 6
    contentWidthDp < 1800f -> 8
    else -> 12
}

fun legacyMediaGridGap(contentWidthDp: Float): Dp = if (contentWidthDp < 600f) 8.dp else 16.dp

internal fun formatLegacyRating(score: Double): String {
    val tenths = (score * 10.0).roundToInt()
    return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}
