package cn.mafei.cmp.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val PlayerInfoCardSurface = Color(0x7A101010)

private val PlayerInfoCardBorder = Color(0x1AFFFFFF)

private val PlayerInfoSectionTitle = Color(0xA3FFFFFF)

private val PlayerInfoEntryLabel = Color(0xADFFFFFF)

/**
 * 旧版 `PlaybackInfoCard`：盖住画面的一张技术信息卡（左对齐、72% 宽/76% 高，
 * 250~360dp 宽 clamp）。整层点击即关；卡片自身拦截点击，内部滚动照常。
 */
@Composable
internal fun PlayerPlaybackInfoCard(
    sections: List<PlayerInfoSection>,
    onDismiss: () -> Unit,
    largeWindow: Boolean,
    modifier: Modifier = Modifier,
) {
    val dismissSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Clamp the viewport-relative width before applying a fixed constraint.
        // fillMaxWidth followed by widthIn would lock in 72% and ignore the cap.
        val cardWidth = if (largeWindow) {
            (maxWidth * .56f).coerceIn(360.dp, 560.dp)
        } else {
            (maxWidth * .72f).coerceIn(250.dp, 360.dp)
        }
        Box(
            Modifier.fillMaxSize()
                .clickable(
                    interactionSource = dismissSource,
                    indication = null,
                    onClickLabel = "关闭播放信息",
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier.width(cardWidth)
                .fillMaxHeight(if (largeWindow) .72f else .76f)
                .then(
                    if (largeWindow) Modifier.align(Alignment.Center)
                    else Modifier.padding(start = 32.dp, top = 16.dp, bottom = 16.dp),
                )
                .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x52000000), spotColor = Color(0x52000000))
                .clip(RoundedCornerShape(20.dp))
                .background(PlayerInfoCardSurface)
                .border(1.dp, PlayerInfoCardBorder, RoundedCornerShape(20.dp))
                .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
                // 卡片内部自行拦截点击，避免点在卡片上被当成关闭。
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChromeGlyph(PlayerChromeGlyph.Info, Modifier.size(16.dp), Color.White)
                Text(
                    "视频信息",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            section.title,
                            color = PlayerInfoSectionTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        section.entries.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(
                                    "${entry.label}：",
                                    Modifier.width(82.dp),
                                    color = PlayerInfoEntryLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    entry.value,
                                    Modifier.weight(1f),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = if (entry.label == "标题") 3 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
