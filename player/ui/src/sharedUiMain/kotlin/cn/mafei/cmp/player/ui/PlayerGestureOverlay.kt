package cn.mafei.cmp.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.player.api.PlayerSeekPreviewFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

private val PlayerGestureSeekSurface = Color(0x85000000)

/** 旧版 PLAYER_GESTURE_FEEDBACK_SURFACE_COLOR：亮度/音量/倍速胶囊比 seek 卡更透。 */
private val PlayerGestureSurface = Color(0x66000000)

private val PlayerGestureTrack = Color(0x66FFFFFF)

@Composable
internal fun PlayerSeekFeedback(
    target: Long,
    duration: Long,
    preview: PlayerSeekPreviewFrame?,
    modifier: Modifier = Modifier,
) {
    val progress = (target.toDouble() / duration.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(PlayerGestureSeekSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        preview?.let { PlayerSeekPreviewImage(it) }
        // 旧版写的是「当前 / 总时长」，只报当前值看不出还剩多少。
        Text(
            "${formatPlayerDuration(target)} / ${formatPlayerDuration(duration)}",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
        )
        Box(Modifier.width(146.dp).height(3.dp).clip(CircleShape).background(PlayerGestureTrack)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(PlayerAccent))
        }
    }
}

@Composable
private fun PlayerSeekPreviewImage(frame: PlayerSeekPreviewFrame) {
    val encodedKey = frame.encodedTile.contentHashCode()
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = encodedKey,
        key2 = frame.frameWidthPx,
        key3 = frame.frameHeightPx,
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                frame.encodedTile.decodeToImageBitmap()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
    }
    Box(
        Modifier.width(220.dp).aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = .36f)),
    ) {
        bitmap?.let { image ->
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(
                        frame.tileColumn * frame.frameWidthPx,
                        frame.tileRow * frame.frameHeightPx,
                    ),
                    srcSize = IntSize(frame.frameWidthPx, frame.frameHeightPx),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            }
        }
    }
}

/**
 * 旧版 `player_chrome_gesture_level_feedback`：顶部居中的胶囊，图标 + 128×4 的级别条。
 * 亮度和音量共用这一格，只有图标不同。
 */
@Composable
internal fun PlayerLevelFeedback(
    level: PlayerGestureLevel,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(21.dp)).background(PlayerGestureSurface)
            .padding(start = 14.dp, end = 16.dp).height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChromeGlyph(
            if (level == PlayerGestureLevel.Brightness) PlayerChromeGlyph.Brightness else PlayerChromeGlyph.Volume,
            Modifier.size(20.dp),
            Color.White,
        )
        Box(Modifier.width(128.dp).height(4.dp).clip(CircleShape).background(PlayerGestureTrack)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
    }
}

/** 旧版 `player_chrome_gesture_speed_feedback`：两颗播放箭头 + 文案。 */
@Composable
internal fun PlayerSpeedFeedback(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(17.dp)).background(PlayerGestureSurface)
            .padding(start = 12.dp, end = 14.dp).height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            ChromeGlyph(PlayerChromeGlyph.Play, Modifier.size(14.dp), Color.White)
            ChromeGlyph(PlayerChromeGlyph.Play, Modifier.size(14.dp), Color.White)
        }
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun PlayerError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xC9000000)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChromeGlyph(PlayerChromeGlyph.Retry, Modifier.size(34.dp), Color.White)
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.height(40.dp).clip(CircleShape).background(PlayerAccent)
                .mafeiInteractive(onClick = onRetry, radiusDp = 20f, label = "重试")
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("重试", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
