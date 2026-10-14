package cn.mafei.cmp.feature.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.rememberMediaCardInteraction
import cn.mafei.cmp.common.ui.mafeiMediaCardInteraction
import cn.mafei.cmp.common.ui.mafeiArtworkFeedback
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import org.jetbrains.compose.resources.painterResource

/** Shared by the library, album, artist, full player and queue. */
@Composable
fun MusicTrackRow(
    item: MediaItem, index: Int, current: Boolean, preparing: Boolean,
    playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val track = item.toMusicTrackOrNull() ?: return
    val color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when {
                preparing -> cn.mafei.cmp.common.ui.LegacyLoadingView(Modifier.size(22.dp))
                current && playing -> Image(painterResource(MaFeiLegacyDrawables.libraryMusic), null,
                    Modifier.size(18.dp), colorFilter = ColorFilter.tint(color))
                else -> Text((track.trackNumber.takeIf { it > 0 } ?: index + 1).toString(),
                    color = color, fontSize = 12.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = listOf(track.artist, track.album).filter(String::isNotBlank).joinToString(" · ")
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(musicDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
fun MusicArtworkCard(item: MediaItem, onClick: () -> Unit,
    artwork: @Composable (MediaItem, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val artist = item.mediaType == MediaType.MusicArtist
    val interaction = rememberMediaCardInteraction()
    Column(modifier.fillMaxWidth().mafeiMediaCardInteraction(interaction, onClick, label = item.title),
        verticalArrangement = Arrangement.spacedBy(if (artist) 8.dp else 7.dp)) {
        artwork(item, Modifier.fillMaxWidth().aspectRatio(1f)
            .mafeiArtworkFeedback(interaction, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant))
        Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 17.sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val subtitle = if (artist) null else item.albumArtist
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun musicDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Old XS/SM/MD/LG/XL/XXL music grid density, resolved against the actual content pane. */
internal fun musicColumns(width: Float): Int = when {
    width >= 1920 -> 8
    width >= 1440 -> 6
    width >= 840 -> 5
    width >= 600 -> 4
    width >= 320 -> 3
    else -> 2
}
