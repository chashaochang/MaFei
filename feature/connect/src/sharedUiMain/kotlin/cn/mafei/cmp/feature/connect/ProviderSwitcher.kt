package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.ProviderMenuOptions
import cn.mafei.cmp.common.ui.providerLogo
import cn.mafei.cmp.common.ui.providerName
import cn.mafei.cmp.core.domain.ProviderKind
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ProviderSwitcher(
    provider: ProviderKind,
    enabled: Boolean,
    backdrop: GlassBackdrop,
    onSelected: (ProviderKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positions = remember(gap) { ProviderMenuPosition(gap) }
    val triggerColor = Color.White
    Box(modifier) {
        LiquidButton(
            onClick = if (enabled) ({ expanded = !expanded }) else null,
            backdrop = backdrop,
            height = 44.dp,
            horizontalPadding = 12.dp,
            modifier = Modifier.width(144.dp)
                .alpha(if (enabled) 1f else .55f)
                .testTag("providerSwitcher")
                .semantics { if (!enabled) disabled() },
        ) {
            Image(
                painterResource(providerLogo(provider)),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                providerName(provider),
                modifier = Modifier.weight(1f),
                color = triggerColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Image(
                painterResource(MaFeiLegacyDrawables.arrowRight),
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(if (expanded) -90f else 90f),
                colorFilter = ColorFilter.tint(triggerColor.copy(alpha = .72f)),
            )
        }
        if (expanded && enabled) {
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            Popup(
                popupPositionProvider = positions,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                ProviderMenu(
                    provider = provider,
                    modifier = Modifier.heightIn(max = (windowHeight - 24.dp).coerceIn(56.dp, 280.dp)),
                    onSelected = {
                        expanded = false
                        if (it != provider) onSelected(it)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderMenu(
    provider: ProviderKind,
    modifier: Modifier = Modifier,
    onSelected: (ProviderKind) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    ProviderMenuOptions(
        modifier = modifier.width(204.dp)
            .testTag("providerMenu")
            .shadow(8.dp, shape)
            .clip(shape)
            .background(colors.surfaceContainer)
            .verticalScroll(rememberScrollState()),
        selectedProvider = provider,
        onProvider = onSelected,
    )
}

private class ProviderMenuPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val bottomLimit = (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap)
        val y = if (below > bottomLimit && above >= gap) above else below
        return IntOffset(
            x.coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)),
            y.coerceIn(gap, bottomLimit),
        )
    }
}
