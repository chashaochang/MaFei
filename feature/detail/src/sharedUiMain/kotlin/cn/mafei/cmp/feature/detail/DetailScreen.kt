package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.AppGlassCard
import cn.mafei.cmp.common.ui.InlineProblem
import cn.mafei.cmp.common.ui.ScreenHeading
import cn.mafei.cmp.common.ui.TextPoster
import cn.mafei.cmp.core.domain.MediaItem

/** Read-only detail; never offers a playback action before an actual player is wired. */
@Composable
fun DetailScreen(
    item: MediaItem?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    stale: Boolean = false,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1000.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(onClick = onBack) { Text("返回媒体库") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (stale) Text("当前显示缓存详情。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { InlineProblem(it, if (loading) null else onRetry) }
            if (item != null) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 640.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            if (artwork != null) artwork(item, Modifier.width(220.dp).aspectRatio(2f / 3f))
                            else TextPoster(item.title, Modifier.width(220.dp).aspectRatio(2f / 3f))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { Summary(item) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (artwork != null) artwork(item, Modifier.width(156.dp).aspectRatio(2f / 3f))
                            else TextPoster(item.title, Modifier.width(156.dp).aspectRatio(2f / 3f))
                            Summary(item)
                        }
                    }
                }
                AppGlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                    Text("剧情简介", style = MaterialTheme.typography.titleMedium)
                    Text(item.overview?.takeIf { it.isNotBlank() } ?: "暂无简介", style = MaterialTheme.typography.bodyLarge)
                }
                if (item.people.isNotEmpty()) {
                    AppGlassCard(backdrop = null, modifier = Modifier.fillMaxWidth()) {
                        Text("演职人员", style = MaterialTheme.typography.titleMedium)
                        item.people.take(12).forEach { person ->
                            Text(listOfNotNull(person.name, person.role?.takeIf { it.isNotBlank() }).joinToString(" · "))
                        }
                    }
                }
            } else if (!loading && error == null) {
                Text("暂时无法显示该媒体的详情。")
                TextButton(onClick = onRetry) { Text("重新加载") }
            }
        }
    }
}

@Composable
private fun Summary(item: MediaItem) {
    ScreenHeading(item.title, item.originalTitle?.takeIf { it != item.title })
    val metadata = listOfNotNull(
        item.year?.toString(),
        item.runtimeMs?.takeIf { it > 0 }?.let { "${it / 60_000} 分钟" },
        item.officialRating,
    ).joinToString(" · ")
    if (metadata.isNotEmpty()) Text(metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (item.genres.isNotEmpty()) Text(item.genres.joinToString(" / "), style = MaterialTheme.typography.bodyMedium)
    if (item.userData.isFavorite) Text("已收藏", color = MaterialTheme.colorScheme.primary)
    if (item.userData.isPlayed) Text("已看", color = MaterialTheme.colorScheme.primary)
    Text("播放器尚未接入，本轮可浏览媒体信息。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
