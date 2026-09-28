package cn.mafei.cmp.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import cn.mafei.cmp.common.design_system.LocalMaFeiPopupColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Mechanical Compose translation of the ArkUI `ListGroup`/`ListGroupContent`.
 * It is an ordinary content group, not a glass surface.
 */
data class LegacyListGroupItem(
    val title: String,
    val icon: DrawableResource? = null,
    val trailing: String = "",
    val showArrow: Boolean = true,
    val accessibilityLabel: String = title,
)

@Composable
fun LegacyListGroup(
    items: List<LegacyListGroupItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val popupColors = LocalMaFeiPopupColors.current
    val groupColor = popupColors?.item ?: MaterialTheme.colorScheme.surface.copy(alpha = .82f)
    val primaryText = popupColors?.primaryText ?: MaterialTheme.colorScheme.onSurface
    val secondaryText = popupColors?.secondaryText ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(groupColor),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .mafeiInteractive(
                        onClick = { onItemClick(index) },
                        radiusDp = 0f,
                        label = item.accessibilityLabel,
                    )
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = item.accessibilityLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.icon != null) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(item.icon),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        colorFilter = ColorFilter.tint(secondaryText),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    item.title,
                    Modifier.weight(1f),
                    color = primaryText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.trailing.isNotBlank()) {
                    Text(
                        item.trailing,
                        color = secondaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.showArrow) {
                    Spacer(Modifier.width(8.dp))
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.arrowRight),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(secondaryText.copy(alpha = .72f)),
                    )
                }
            }
            if (index < items.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = if (item.icon == null) 16.dp else 42.dp, end = 12.dp)
                        .height(.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .42f)),
                )
            }
        }
    }
}
