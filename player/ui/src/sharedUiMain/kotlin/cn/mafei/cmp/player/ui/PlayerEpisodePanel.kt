package cn.mafei.cmp.player.ui

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal const val PlayerEpisodeEntranceDurationMs = 180

private const val PlayerEpisodeRowStaggerMs = 50

/**
 * 选集面板（旧版 `PlayerChromePanel.Episode`）：超过 12 集用 5 列网格快速跳集，
 * 否则用带缩略信息的行式列表，并定位到当前集。
 */
@Composable
internal fun PlayerEpisodePanel(
    episodes: List<cn.mafei.cmp.core.domain.MediaItem>,
    episodeIndex: Int,
    entranceVisible: Boolean,
    closing: Boolean,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    largeType: Boolean = false,
) {
    if (episodes.isEmpty()) return
    if (episodes.size > PlayerEpisodeListMaxCount) {
        val gridState = rememberLazyGridState(
            initialFirstVisibleItemIndex = (episodeIndex - PlayerEpisodeGridColumns)
                .coerceAtLeast(0),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(PlayerEpisodeGridColumns),
            state = gridState,
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItemsIndexed(episodes, key = { _, episode -> episode.id.value }) { index, episode ->
                val selected = index == episodeIndex
                Box(
                    Modifier.height(PlayerEpisodeGridCellHeight)
                        .playerStaggeredEntrance(
                            visible = entranceVisible && !closing,
                            index = (index / PlayerEpisodeGridColumns).coerceAtMost(5),
                            count = 6,
                            durationMs = PlayerEpisodeEntranceDurationMs,
                            staggerMs = PlayerEpisodeRowStaggerMs,
                            offsetDp = 14,
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) PlayerAccent else PlayerEpisodeCellSurface)
                        .clickable { onSelectEpisode(index) }
                        .semantics { contentDescription = episodeLabel(episode, index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        episode.episodeNumber?.toString() ?: "${index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = if (largeType) 16.sp else MaterialTheme.typography.labelMedium.fontSize,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    } else {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = episodeIndex.coerceAtLeast(0))
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(episodes, key = { _, episode -> episode.id.value }) { index, episode ->
                val selected = index == episodeIndex
                Row(
                    Modifier.fillMaxWidth().height(PlayerEpisodeRowHeight)
                        .playerStaggeredEntrance(
                            visible = entranceVisible && !closing,
                            index = abs(index - episodeIndex).coerceAtMost(5),
                            count = 6,
                            durationMs = PlayerEpisodeEntranceDurationMs,
                            staggerMs = PlayerEpisodeRowStaggerMs,
                            offsetDp = 14,
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) PlayerEpisodeSelectedSurface else Color.Transparent)
                        .clickable { onSelectEpisode(index) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        episode.episodeNumber?.toString() ?: "${index + 1}",
                        Modifier.width(28.dp),
                        color = if (selected) PlayerAccent else PlayerMenuText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            episode.title,
                            color = if (selected) PlayerAccent else PlayerMenuText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = if (largeType) 16.sp else MaterialTheme.typography.bodyMedium.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        episode.runtimeMs?.takeIf { it > 0L }?.let { runtime ->
                            Text(
                                formatPlayerDuration(runtime),
                                color = PlayerMenuText.copy(alpha = .66f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    if (episode.userData.isPlayed) {
                        Text(
                            "已看",
                            color = PlayerMenuText.copy(alpha = .66f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private fun episodeLabel(episode: cn.mafei.cmp.core.domain.MediaItem, index: Int): String =
    episode.episodeNumber?.let { "第 $it 集" } ?: "第 ${index + 1} 集"

private const val PlayerEpisodeListMaxCount = 12

private const val PlayerEpisodeGridColumns = 5

private val PlayerEpisodeGridCellHeight = 42.dp

internal val PlayerEpisodeRowHeight = 56.dp

private val PlayerEpisodeCellSurface = Color(0x33FFFFFF)

private val PlayerEpisodeSelectedSurface = Color(0x2E2173DF)
