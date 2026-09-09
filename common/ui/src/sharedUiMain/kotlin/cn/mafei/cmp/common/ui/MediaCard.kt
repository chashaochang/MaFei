package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiShape

/** Provider-neutral card. The caller owns authenticated artwork fetching and lifecycle. */
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    poster: (@Composable (Modifier) -> Unit)? = null,
    progress: Float? = null,
) {
    Column(modifier.clip(MaFeiShape.poster).mafeiInteractive(onClick, label = "查看$title"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaFeiShape.poster)) {
            if (poster != null) poster(Modifier.fillMaxSize()) else TextPoster(title, Modifier.fillMaxSize())
            status?.let {
                Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = Color(0xDD151921),
                    contentColor = Color.White, shape = RoundedCornerShape(6.dp)) {
                    Text(it, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            progress?.takeIf { it > 0f }?.let {
                LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().height(3.dp), color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = .5f))
            }
        }
        Column(Modifier.padding(horizontal = 2.dp).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TextPoster(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(MaFeiShape.poster).background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
        ))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title.take(1).ifEmpty { "影" }, style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("暂无封面", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScreenHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** [message] must be user-safe text, never a raw provider exception or URL. */
@Composable
fun InlineProblem(message: String, onRetry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            onRetry?.let { TextButton(onClick = it) { Text("重试") } }
        }
    }
}
