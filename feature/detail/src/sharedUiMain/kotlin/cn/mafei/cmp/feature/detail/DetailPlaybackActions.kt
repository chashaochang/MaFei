package cn.mafei.cmp.feature.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.MaFeiShape
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.core.domain.MediaItem
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
internal fun DetailActions(
    item: MediaItem,
    playTarget: MediaItem?,
    onPlay: (() -> Unit)?,
    playbackBusy: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    onToggleChasing: (() -> Unit)?,
    isChasing: Boolean,
    chasingBusy: Boolean,
    actionsBusy: Boolean,
    backdrop: GlassBackdrop,
    largeLayout: Boolean,
) {
    Row(
        Modifier
            .then(if (largeLayout) Modifier.widthIn(max = LargeDetailControlWidth) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailPlayAction(
            item = item,
            target = playTarget,
            onPlay = onPlay,
            busy = playbackBusy,
            backdrop = backdrop,
            modifier = if (largeLayout) {
                Modifier.widthIn(max = LargeDetailPlayWidth).weight(1f)
            } else {
                Modifier.weight(1f)
            },
        )
        DetailIconAction(
            icon = if (item.userData.isFavorite) {
                MaFeiLegacyDrawables.favoriteSelected
            } else {
                MaFeiLegacyDrawables.favoriteUnselected
            },
            selected = item.userData.isFavorite,
            selectedColor = FavoriteColor,
            preserveOriginalColor = item.userData.isFavorite,
            accessibilityLabel = if (item.userData.isFavorite) "取消收藏" else "收藏",
            onAction = onToggleFavorite,
            busy = actionsBusy,
            backdrop = backdrop,
        )
        DetailIconAction(
            icon = MaFeiLegacyDrawables.watched,
            selected = item.userData.isPlayed,
            selectedColor = MaterialTheme.colorScheme.primary,
            accessibilityLabel = if (item.userData.isPlayed) "标记为未播放" else "标记为已播放",
            onAction = onTogglePlayed,
            busy = actionsBusy,
            backdrop = backdrop,
        )
        if (onToggleChasing != null) {
            DetailIconAction(
                icon = MaFeiLegacyDrawables.history,
                selected = isChasing,
                selectedColor = MaterialTheme.colorScheme.primary,
                accessibilityLabel = if (isChasing) "取消追剧" else "追剧",
                onAction = onToggleChasing,
                busy = chasingBusy,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun RowScope.DetailPlayAction(
    item: MediaItem,
    target: MediaItem?,
    onPlay: (() -> Unit)?,
    busy: Boolean,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val progress = target?.playbackProgress() ?: item.playbackProgress()
    val label = legacyDetailPlayLabel(item, target)
    Box(modifier.height(50.dp)) {
        // Keep the progress fill behind the glass surface. The play action
        // must use the same hit target and press/drag surface as the favorite
        // and watched actions; drawing the fill/content as siblings above an
        // empty LiquidButton made its drag feedback look different.
        if (progress != null && progress > 0f && progress < 1f) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight()
                    .clip(MaFeiShape.capsule)
                    .background(Color.White.copy(alpha = .26f)),
            )
        }
        if (onPlay != null) {
            LiquidButton(
                onClick = { if (!busy) onPlay() },
                backdrop = backdrop,
                isInteractive = !busy,
                height = 50.dp,
                horizontalPadding = 0.dp,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = label
                },
            ) {
                DetailPlayContents(label, busy)
            }
        } else {
            GlassSurface(
                backdrop = backdrop,
                spec = DetailActionSurfaceSpec,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = label
                    disabled()
                },
                ) {}
        }
        if (onPlay == null) {
            Row(
                Modifier.fillMaxSize()
                    .alpha(.48f)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailPlayContents(label, busy = false)
            }
        }
    }
}

@Composable
private fun DetailPlayContents(label: String, busy: Boolean) {
    AnimatedContent(
        targetState = Pair(label, busy),
        transitionSpec = {
            fadeIn(tween(DetailMotionFastMillis)) togetherWith
                fadeOut(tween(DetailMotionExitMillis))
        },
        label = "detail-play-state",
    ) { (currentLabel, isBusy) ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (isBusy) {
                    MaFeiLoadingIndicator(
                        modifier = Modifier.size(20.dp),
                        style = MaFeiLoadingStyle.Button,
                        tint = Color.White,
                    )
                } else {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.playRound),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    currentLabel,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailIconAction(
    icon: DrawableResource,
    selected: Boolean,
    selectedColor: Color,
    preserveOriginalColor: Boolean = false,
    accessibilityLabel: String,
    onAction: (() -> Unit)?,
    busy: Boolean,
    backdrop: GlassBackdrop,
) {
    val busyAlpha by animateFloatAsState(
        targetValue = if (busy) .56f else 1f,
        animationSpec = tween(DetailMotionFastMillis),
        label = "detail-action-alpha",
    )
    val content: @Composable RowScope.() -> Unit = {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(tween(DetailMotionFastMillis)) togetherWith
                    fadeOut(tween(DetailMotionExitMillis))
            },
            label = "detail-action-selection",
        ) { isSelected ->
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = if (isSelected && preserveOriginalColor) null else ColorFilter.tint(
                    if (isSelected) selectedColor else Color.White,
                ),
            )
        }
    }
    if (onAction != null) {
        LiquidButton(
            onClick = { if (!busy) onAction() },
            backdrop = backdrop,
            isInteractive = !busy,
            height = 44.dp,
            horizontalPadding = 11.dp,
            modifier = Modifier.width(44.dp).alpha(busyAlpha).semantics {
                contentDescription = accessibilityLabel
                if (busy) disabled()
            },
            content = content,
        )
    } else {
        GlassSurface(
            backdrop = backdrop,
            spec = DetailActionSurfaceSpec,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(44.dp).alpha(.48f).semantics {
                contentDescription = accessibilityLabel
                disabled()
            },
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

internal fun MediaItem.playbackProgress(): Float? {
    val duration = runtimeMs?.takeIf { it > 0L } ?: return null
    val explicit = userData.playedPercentage?.takeIf { it > 0.0 }?.div(100.0)?.toFloat()
    return (explicit ?: (userData.playbackPositionMs.toFloat() / duration)).coerceIn(0f, 1f)
}

private fun MediaItem.remainingTime(): String {
    val duration = runtimeMs?.takeIf { it > 0L } ?: return ""
    val position = when {
        userData.playbackPositionMs > 0L -> userData.playbackPositionMs
        (userData.playedPercentage ?: 0.0) > 0.0 ->
            (duration * (userData.playedPercentage!!.coerceIn(0.0, 100.0) / 100.0)).roundToInt().toLong()
        else -> 0L
    }
    if (position <= 0L || position >= duration || (userData.playedPercentage ?: 0.0) >= 100.0) return ""
    val remainingMinutes = (duration - position).coerceAtLeast(0L) / 60_000L
    val hours = remainingMinutes / 60L
    val minutes = remainingMinutes % 60L
    return if (hours > 0L) "剩余 $hours 小时 $minutes 分钟" else "剩余 $minutes 分钟"
}

private fun compactRemainingTime(value: String): String = value
    .replace("剩余 ", "剩余")
    .replace(" 小时 ", "小时")
    .replace(" 分钟", "分")

internal val LargeDetailControlWidth = 320.dp

internal val LargeDetailPlayWidth = 180.dp

internal val DetailActionSelectorSpacing = 12.dp

internal val DetailActionSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Overlay,
    level = GlassLevel.Full,
    tintAlpha = .18f,
    intensity = .86f,
    cornerRadiusDp = 25f,
)

private val FavoriteColor = Color(0xFFE9210B)
