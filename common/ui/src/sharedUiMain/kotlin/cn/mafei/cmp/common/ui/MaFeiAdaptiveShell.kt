package cn.mafei.cmp.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                    GlassSurfaceSpec(GlassRole.Navigation, GlassLevel.Reduced, tintAlpha = .34f, cornerRadiusDp = 0f),
                    Modifier.width(layout.navigationWidthDp.dp).fillMaxHeight(), backdrop,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(
                            horizontal = if (sidebar) 16.dp else 10.dp,
                            vertical = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(if (sidebar) 58.dp else 50.dp),
                            contentAlignment = if (sidebar) Alignment.CenterStart else Alignment.Center,
                        ) {
                            Text(
                                "马飞",
                                Modifier.padding(horizontal = if (sidebar) 8.dp else 0.dp),
                                style = if (sidebar) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth().height(1.dp).background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.outline.copy(alpha = .28f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                        )
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
                        if (sidebar) {
                            Column(
                                Modifier.fillMaxWidth().clip(MaFeiShape.field)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .22f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = .20f),
                                        MaFeiShape.field,
                                    ).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    accountLabel,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { accountActions() }
                            }
                        } else {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { accountActions() }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(bottom = if (bottomNavigationVisible) 88.dp else 0.dp)) {
                content(backdrop)
            }
        }
        if (bottomNavigationVisible) {
            MaFeiSurface(
                MaFeiGlassDefaults.spec(GlassRole.Navigation).copy(tintAlpha = .34f, cornerRadiusDp = 34f),
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 14.dp)
                    .widthIn(max = 430.dp).fillMaxWidth(), backdrop,
            ) {
                Box(
                    Modifier.fillMaxWidth().border(
                        1.dp,
                        Color.White.copy(alpha = .22f),
                        RoundedCornerShape(34.dp),
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        destinations.forEach { item ->
                            Box(Modifier.weight(1f)) {
                                NavigationEntry(item, selectedDestination == item.key, true) { onDestination(item.key) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationEntry(item: MaFeiNavItem, isSelected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaFeiShape.field)
        .background(if (isSelected && !compact) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent)
        .then(
            if (isSelected && !compact) Modifier.border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = .20f),
                MaFeiShape.field,
            ) else Modifier,
        )
        .semantics { selected = isSelected }.mafeiInteractive(onClick, radiusDp = 16f, label = item.label)
        .padding(horizontal = if (compact) 2.dp else 10.dp, vertical = 8.dp)
    if (compact) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(width = 40.dp, height = 28.dp).clip(MaFeiShape.capsule)
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            MaFeiIcon(item.glyph, Modifier.size(22.dp), color)
        }
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MaFeiIcon(item.glyph, color = color)
        Text(item.label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
