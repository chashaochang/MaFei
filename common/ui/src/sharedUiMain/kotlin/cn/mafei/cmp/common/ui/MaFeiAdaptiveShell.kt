package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.*
import cn.mafei.cmp.common.glass.*

data class MaFeiNavItem(val key: String, val label: String, val glyph: MaFeiGlyph)

/** Geometry-only shell; session, navigation state and scroll state belong to caller.
 * Background is recorded once, separately from all its glass consumers.
 */
@Composable
fun MaFeiAdaptiveShell(
    layout: MaFeiLayout,
    destinations: List<MaFeiNavItem>,
    selectedDestination: String,
    onDestination: (String) -> Unit,
    libraries: List<MaFeiNavItem>,
    selectedLibrary: String?,
    onLibrary: (String) -> Unit,
    accountLabel: String,
    showBottomNavigation: Boolean = true,
    background: @Composable () -> Unit,
    accountActions: @Composable () -> Unit,
    content: @Composable (GlassBackdrop) -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val sideScroll = rememberScrollState()
    val sidebar = layout.navigation == NavigationPlacement.Sidebar
    val bottomNavigationVisible = layout.navigation == NavigationPlacement.Bottom && showBottomNavigation
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) { background() }
        Row(Modifier.fillMaxSize()) {
            if (layout.navigation != NavigationPlacement.Bottom) {
                MaFeiSurface(
                    GlassSurfaceSpec(GlassRole.Navigation, GlassLevel.Reduced, tintAlpha = .54f, cornerRadiusDp = 0f),
                    Modifier.width(layout.navigationWidthDp.dp).fillMaxHeight(), backdrop,
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (sidebar) "马飞" else "马飞", Modifier.padding(horizontal = if (sidebar) 8.dp else 0.dp),
                            style = if (sidebar) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                        Column(Modifier.weight(1f).verticalScroll(sideScroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            destinations.forEach { item ->
                                NavigationEntry(item, selectedDestination == item.key, !sidebar) { onDestination(item.key) }
                            }
                            if (sidebar && libraries.isNotEmpty()) {
                                Text("我的媒体库", Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                libraries.forEach { item ->
                                    NavigationEntry(item, selectedDestination == "library" && selectedLibrary == item.key, false) { onLibrary(item.key) }
                                }
                            }
                        }
                        if (sidebar) Text(accountLabel, Modifier.padding(horizontal = 8.dp), maxLines = 2,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (sidebar) Alignment.CenterStart else Alignment.Center) { accountActions() }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(bottom = if (bottomNavigationVisible) 88.dp else 0.dp)) {
                content(backdrop)
            }
        }
        if (bottomNavigationVisible) {
            MaFeiSurface(
                MaFeiGlassDefaults.spec(GlassRole.Navigation).copy(tintAlpha = .54f),
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp)
                    .widthIn(max = 460.dp).fillMaxWidth(), backdrop,
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    destinations.forEach { item ->
                        Box(Modifier.weight(1f)) { NavigationEntry(item, selectedDestination == item.key, true) { onDestination(item.key) } }
                    }
                    Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { accountActions() }
                }
            }
        }
    }
}

@Composable
private fun NavigationEntry(item: MaFeiNavItem, isSelected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaFeiShape.field)
        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent)
        .semantics { selected = isSelected }.mafeiInteractive(onClick, radiusDp = 16f, label = item.label)
        .padding(horizontal = if (compact) 2.dp else 10.dp, vertical = 8.dp)
    if (compact) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MaFeiIcon(item.glyph, color = color)
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MaFeiIcon(item.glyph, color = color)
        Text(item.label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
