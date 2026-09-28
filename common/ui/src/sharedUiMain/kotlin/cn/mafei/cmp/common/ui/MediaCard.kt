package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.MaFeiShape
import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.placeholder_video
import org.jetbrains.compose.resources.painterResource

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
    onPlay: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isWatched: Boolean = false,
    isFavorite: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val showHoverActions = hovered || focused
    Column(
        modifier.mafeiInteractive(
            onClick,
            radiusDp = 10f,
            label = "查看$title",
            interactionSource = interactionSource,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (poster != null) poster(Modifier.fillMaxSize()) else TextPoster(title, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = .30f), Color.Transparent),
                    ),
                ),
            )
            status?.let {
                Box(
                    Modifier.align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xDD151921)),
                ) {
                    Text(
                        it,
                        Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            progress?.takeIf { it > 0f }?.let {
                MaFeiProgressLine(
                    progress = it,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    trackColor = Color.Black.copy(alpha = .5f),
                )
            }
            if (showHoverActions) {
                MediaCardHoverOverlay(
                    title = title,
                    onPlay = onPlay ?: onClick,
                    onToggleWatched = onToggleWatched,
                    onToggleFavorite = onToggleFavorite,
                    isWatched = isWatched,
                    isFavorite = isFavorite,
                )
            }
        }
        Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (hovered) MaterialTheme.colorScheme.primary else Color.White,
                fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = Color.White.copy(alpha = .72f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Desktop pointer affordance shared by every poster card. It intentionally
 * uses plain darkening and product glyphs rather than a glass surface: the
 * poster remains the visual material and the actions stay legible above it.
 */
@Composable
fun MediaCardHoverOverlay(
    title: String,
    onPlay: () -> Unit,
    onToggleWatched: (() -> Unit)?,
    onToggleFavorite: (() -> Unit)?,
    isWatched: Boolean = false,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = .54f)),
    ) {
        Box(
            Modifier.align(Alignment.Center)
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .20f))
                .border(2.dp, Color.White.copy(alpha = .92f), CircleShape)
                .mafeiInteractive(
                    onClick = onPlay,
                    radiusDp = 29f,
                    label = "播放$title",
                    visualFeedback = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            MaFeiIcon(MaFeiGlyph.Play, Modifier.size(28.dp), Color.White)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardHoverAction(
                glyph = MaFeiGlyph.Watched,
                label = if (isWatched) "标记为未观看" else "设置已观看",
                onClick = onToggleWatched,
                selected = isWatched,
            )
            CardHoverAction(
                glyph = MaFeiGlyph.Favorite,
                label = if (isFavorite) "取消收藏" else "收藏",
                onClick = onToggleFavorite,
                selected = isFavorite,
            )
        }
    }
}

@Composable
private fun CardHoverAction(
    glyph: MaFeiGlyph,
    label: String,
    onClick: (() -> Unit)?,
    selected: Boolean,
) {
    Box(
        Modifier.size(34.dp).mafeiInteractive(
            onClick = { onClick?.invoke() },
            enabled = onClick != null,
            radiusDp = 17f,
            label = label,
            visualFeedback = false,
        ),
        contentAlignment = Alignment.Center,
    ) {
        MaFeiIcon(
            glyph,
            Modifier.size(27.dp),
            when {
                selected -> MaterialTheme.colorScheme.primary
                onClick != null -> Color.White
                else -> Color.White.copy(alpha = .72f)
            },
        )
    }
}

@Composable
fun TextPoster(title: String, modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.background.luminance() >= .5f
    Box(
        modifier.clip(MaFeiShape.poster).background(
            if (light) Color(0xFFCBD2D3)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.placeholder_video),
            contentDescription = null,
            // ArkUI's MediaNetworkImage used ImageFit.CENTER for both the
            // loading and error source. Keep the original 128px reel at its
            // natural, centred size instead of shrinking it to a tiny icon;
            // otherwise a valid fallback reads as an empty/broken poster.
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Inside,
            alpha = 1f,
        )
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
    MaFeiInlineNotice(
        message = message,
        actionLabel = onRetry?.let { "重试" },
        onAction = onRetry,
    )
}
