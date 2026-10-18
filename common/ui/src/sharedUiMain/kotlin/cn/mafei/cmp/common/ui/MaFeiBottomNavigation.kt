package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*
import cn.mafei.cmp.common.glass.components.LiquidBottomTab
import cn.mafei.cmp.common.glass.components.LiquidBottomTabs
import cn.mafei.cmp.common.glass.components.LiquidBottomTabsStyle
import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.ic_home_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_home_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_favorite_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_favorite_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_media_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_media_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_mine_sel
import cn.mafei.cmp.common.ui.generated.resources.ic_mine_unsel
import cn.mafei.cmp.common.ui.generated.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Delivers only the latest request; route acknowledgements never own a newer request. */
@Stable
internal class BottomNavigationSelectionScheduler(
    private val scope: CoroutineScope,
    private val interaction: MaFeiTabInteractionState?,
    private val onDestination: (String) -> Unit,
) {
    private class Request(val destination: String) {
        var delivered by mutableStateOf(false)
        var job: Job? = null
    }

    private var pending by mutableStateOf<Request?>(null)
    val pendingDestination: String? get() = pending?.destination
    val deliveredDestination: String? get() = pending?.takeIf { it.delivered }?.destination

    fun select(destination: String) {
        pending?.job?.cancel()
        val request = Request(destination)
        pending = request
        interaction?.selectionPending(true)
        request.job = scope.launch {
            // Draw the local droplet target before rebuilding the destination.
            withFrameNanos { }
            withFrameNanos { }
            if (pending !== request) return@launch
            request.delivered = true
            try {
                onDestination(destination)
            } finally {
                // A destination callback can itself enqueue another selection.
                if (pending === request) interaction?.selectionPending(false)
            }
        }
    }

    fun acknowledge(destination: String) {
        val request = pending ?: return
        if (!request.delivered || request.destination != destination) return
        pending = null
        interaction?.selectionPending(false)
    }

    fun dispose() {
        pending?.job?.cancel()
        pending = null
        interaction?.clear()
    }
}

/** Keep scroll-driven dock morphing out of the shell's scene composition. */
@Composable
internal fun BottomNavigationLayer(
    destinations: List<MaFeiNavItem>,
    selectedDestination: String,
    onDestination: (String) -> Unit,
    backdrop: GlassBackdrop,
    compactProgress: () -> Float,
    type: BottomNavigationType,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = destinations.indexOfFirst { it.key == selectedDestination }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val interaction = LocalMaFeiTabInteractionState.current
    val currentOnDestination by rememberUpdatedState(onDestination)
    val scheduler = remember(scope, interaction) {
        BottomNavigationSelectionScheduler(scope, interaction) { currentOnDestination(it) }
    }
    LaunchedEffect(scheduler, selectedDestination, scheduler.deliveredDestination) {
        scheduler.acknowledge(selectedDestination)
    }
    DisposableEffect(scheduler) { onDispose { scheduler.dispose() } }
    val select: (Int) -> Unit = { index ->
        destinations.getOrNull(index)?.let { scheduler.select(it.key) }
    }
    val visualIndex = destinations.indexOfFirst { it.key == scheduler.pendingDestination }
        .takeIf { it >= 0 } ?: selectedIndex
    val resolvedCompactProgress = if (type == BottomNavigationType.Bar) 0f else compactProgress().coerceIn(0f, 1f)
    LiquidBottomTabs(
        selectedTabIndex = { visualIndex },
        onTabSelected = select,
        backdrop = backdrop,
        tabsCount = destinations.size,
        widthBaselineTabCount = 4,
        compactProgress = { resolvedCompactProgress },
        style = if (type == BottomNavigationType.Bar) LiquidBottomTabsStyle.Bar else LiquidBottomTabsStyle.Standard,
        bottomInset = bottomInset,
        onAnimationRunningChanged = { interaction?.animationRunning(it) },
        modifier = modifier,
    ) {
        destinations.forEachIndexed { index, item ->
            val selected = visualIndex == index
            LiquidBottomTab(
                onClick = { select(index) },
                index = index,
                isSelected = selected,
                collapseProgress = resolvedCompactProgress,
            ) {
                NavigationIcon(
                    glyph = item.glyph,
                    selected = selected,
                    modifier = Modifier.size(22.dp),
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Uses the original HarmonyOS navigation artwork instead of a newly drawn approximation. */
@Composable
internal fun NavigationIcon(
    glyph: MaFeiGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val resource = when (glyph) {
        MaFeiGlyph.Home -> if (selected) Res.drawable.ic_home_sel else Res.drawable.ic_home_unsel
        MaFeiGlyph.Favorite -> if (selected) Res.drawable.ic_favorite_sel else Res.drawable.ic_favorite_unsel
        MaFeiGlyph.Library -> if (selected) Res.drawable.ic_media_sel else Res.drawable.ic_media_unsel
        MaFeiGlyph.Account -> if (selected) Res.drawable.ic_mine_sel else Res.drawable.ic_mine_unsel
        MaFeiGlyph.Search -> Res.drawable.ic_search
        else -> null
    }
    if (resource == null) {
        MaFeiIcon(glyph, modifier, color)
    } else {
        Icon(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = modifier.size(24.dp),
            tint = color,
        )
    }
}
