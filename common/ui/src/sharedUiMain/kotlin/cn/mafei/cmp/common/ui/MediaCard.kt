package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
    poster: (@Composable (Modifier) -> Unit)? = null,
    progress: Float? = null,
    onPlay: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isWatched: Boolean = false,
    isFavorite: Boolean = false,
    liftArtwork: Boolean = false,
) {
    val interaction = rememberMediaCardInteraction(liftArtwork)
    Column(
        modifier.mafeiMediaCardInteraction(
            interaction,
            onClick,
            label = "查看$title",
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .mafeiArtworkFeedback(interaction, RoundedCornerShape(10.dp))
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
            progress?.takeIf { it > 0f }?.let {
                MaFeiProgressLine(
                    progress = it,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    trackColor = Color.Black.copy(alpha = .5f),
                )
            }
            MediaCardHoverOverlay(
                liftArtwork = liftArtwork,
                visible = interaction.showActions,
                title = title,
                onPlay = onPlay ?: onClick,
                onToggleWatched = onToggleWatched,
                onToggleFavorite = onToggleFavorite,
                isWatched = isWatched,
                isFavorite = isFavorite,
            )
        }
        Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Pointer/keyboard affordance shared by every poster card. It intentionally
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
    visible: Boolean = true,
    liftArtwork: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(100)),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().then(if (liftArtwork) Modifier else Modifier.background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .34f), Color.Black.copy(alpha = .65f)),
            ))),
        ) {
            val landscape = maxWidth > maxHeight
            val compact = maxWidth < 140.dp || maxHeight < 120.dp
            val playSize = if (compact) 40.dp else 52.dp
            val actionSize = if (compact || liftArtwork) 32.dp else 40.dp
            val hasActions = onToggleWatched != null || onToggleFavorite != null
            val playAtStart = landscape && hasActions && (!liftArtwork || maxWidth < 200.dp)
            val playSource = remember { MutableInteractionSource() }
            val playHovered by playSource.collectIsHoveredAsState()
            val playFocused by playSource.collectIsFocusedAsState()
            val playPressed by playSource.collectIsPressedAsState()
            val playScale = animateFloatAsState(if (playPressed) .94f else if (playHovered || playFocused) 1.08f else 1f,
                tween(120), label = "quick play scale")
            val playBackgroundAlpha = animateFloatAsState(
                if (playPressed) .52f else if (playHovered || playFocused) .46f else .34f,
                tween(120), label = "quick play background",
            )
            Box(
                Modifier.align(if (playAtStart) Alignment.CenterStart else Alignment.Center)
                    .padding(start = if (playAtStart) 12.dp else 0.dp,
                        bottom = if (!landscape && hasActions) 12.dp else 0.dp)
                    .size(playSize)
                    .graphicsLayer { scaleX = playScale.value; scaleY = playScale.value }
                    .clip(CircleShape)
                    .drawWithContent {
                        drawCircle(if (liftArtwork) Color.Black.copy(alpha = playBackgroundAlpha.value)
                            else Color.White.copy(alpha = .96f))
                        drawContent()
                    }
                    .mafeiInteractive(
                        onClick = onPlay,
                        enabled = visible,
                        label = "播放$title",
                        visualFeedback = false,
                        interactionSource = playSource,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                MaFeiIcon(
                    MaFeiGlyph.Play,
                    Modifier.size(if (liftArtwork) { if (compact) 26.dp else 32.dp } else if (compact) 20.dp else 26.dp),
                    if (liftArtwork) Color.White else Color(0xFF1A1A1A),
                )
            }
            if (hasActions) {
                Row(
                    Modifier.align(if (liftArtwork) Alignment.BottomEnd else if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                        .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onToggleWatched != null) CardHoverAction(
                        glyph = MaFeiGlyph.Watched,
                        label = if (isWatched) "标记为未观看" else "设置已观看",
                        onClick = onToggleWatched,
                        selected = isWatched,
                        size = actionSize,
                        enabled = visible,
                        localScrim = liftArtwork,
                    )
                    if (onToggleFavorite != null) CardHoverAction(
                        glyph = MaFeiGlyph.Favorite,
                        label = if (isFavorite) "取消收藏" else "收藏",
                        onClick = onToggleFavorite,
                        selected = isFavorite,
                        size = actionSize,
                        enabled = visible,
                        localScrim = liftArtwork,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHoverAction(
    glyph: MaFeiGlyph,
    label: String,
    onClick: () -> Unit,
    selected: Boolean,
    size: Dp,
    enabled: Boolean,
    localScrim: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val highlighted = hovered || focused
    val highlight = animateFloatAsState(if (pressed) .35f else if (highlighted) .24f else .10f,
        tween(100), label = "quick action highlight")
    val scale = animateFloatAsState(if (pressed) .92f else 1f, tween(100), label = "quick action press")

    Box(
        Modifier.size(size).mafeiInteractive(
            onClick = onClick,
            enabled = enabled,
            label = label,
            visualFeedback = false,
            interactionSource = interactionSource,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(CircleShape)
                .drawWithContent {
                    if (localScrim) drawCircle(Color.Black.copy(alpha = .44f))
                    drawCircle(Color.White.copy(alpha = highlight.value))
                    drawContent()
                },
            contentAlignment = Alignment.Center,
        ) {
            MaFeiIcon(
                glyph,
                Modifier.size(if (size < 40.dp) 20.dp else 24.dp),
                when {
                    glyph == MaFeiGlyph.Favorite && selected -> CardFavoriteColor
                    selected -> MaterialTheme.colorScheme.primary
                    else -> Color.White
                },
                filled = glyph == MaFeiGlyph.Favorite && selected,
            )
        }
    }
}

private val CardFavoriteColor = Color(0xFFE9210B)

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
