package cn.mafei.cmp.common.glass.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.LocalGlassIntensity
import cn.mafei.cmp.common.glass.currentGlassStyle
import cn.mafei.cmp.common.glass.surfaceAlpha
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import cn.mafei.cmp.common.glass.utils.InteractiveHighlight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import cn.mafei.cmp.common.glass.drawStyledBackdrop as drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

enum class LiquidBottomTabsStyle {
    Standard,
    Compact,
    Bar,
}

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: GlassBackdrop,
    tabsCount: Int,
    /**
     * Number of slots used to calculate the expanded dock width. Keeping this
     * independent from [tabsCount] lets a destination set lose an item without
     * making the glass dock jump in width; the visible tabs still divide the
     * dock equally.
     */
    widthBaselineTabCount: Int = tabsCount,
    modifier: Modifier = Modifier,
    style: LiquidBottomTabsStyle = LiquidBottomTabsStyle.Standard,
    compactProgress: () -> Float = { 0f },
    bottomInset: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    // A navigation surface with no destinations has no meaningful geometry.
    // Keep the caller's layout intact instead of creating a zero-width tab
    // and dividing by zero below.
    if (tabsCount <= 0) return

    val vendorBackdrop = backdrop.layer
    // MaFei supports an in-app theme override, so the system appearance is not
    // a reliable source of truth for glass tint and selection contrast.
    val isLightTheme by rememberUpdatedState(MaterialTheme.colorScheme.background.luminance() >= .5f)
    val glassIntensity = LocalGlassIntensity.current
    val docked = style == LiquidBottomTabsStyle.Bar
    val resolvedBottomInset = if (docked) bottomInset.coerceAtLeast(0.dp) else 0.dp
    val glassStyle by rememberUpdatedState(currentGlassStyle())
    val containerAlpha = glassStyle.surfaceAlpha(.4f)
    val containerColor by rememberUpdatedState(
        if (isLightTheme) Color(0xFFFAFAFA).copy(containerAlpha)
        else Color(0xFF121212).copy(containerAlpha)
    )

    val tabsBackdrop = rememberLayerBackdrop()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val collapseProgress = if (docked) 0f else compactProgress().fastCoerceIn(0f, 1f)
        // Keep a stable per-destination rhythm and only compress when the
        // available width cannot fit it. The app passes its former four-slot
        // baseline while rendering three destinations, so removing 收藏 does
        // not resize the dock and each visible tab remains an equal third.
        val compactEffectInset = if (style == LiquidBottomTabsStyle.Compact) 24f else 0f
        val availableDockWidth = (maxWidth.value - compactEffectInset * 2f).coerceAtLeast(1f)
        val itemRhythm = if (style == LiquidBottomTabsStyle.Compact) 52f else 72f
        val widthTabCount = widthBaselineTabCount.coerceAtLeast(tabsCount)
        val expandedDockWidth = (if (docked) availableDockWidth
            else min(availableDockWidth, 8f + widthTabCount * itemRhythm)).dp
        val dockWidth = expandedDockWidth + (58.dp - expandedDockWidth) * collapseProgress
        val expandedDockHeight = if (style == LiquidBottomTabsStyle.Compact) 52.dp else 64.dp
        val dockHeight = expandedDockHeight + (58.dp - expandedDockHeight) * collapseProgress
        val dockWidthPx = with(density) { dockWidth.toPx() }
        val compactPanelPressedScale = 1.025f
        val panelEffectScale = if (style == LiquidBottomTabsStyle.Compact) 52f / 64f else 1f
        val selectionEffectScale = if (style == LiquidBottomTabsStyle.Compact) 44f / 56f else 1f
        val tabWidth = with(density) {
            ((dockWidthPx - 8f.dp.toPx()) / tabsCount).coerceAtLeast(1f)
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, docked, dockWidthPx) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / dockWidthPx).fastCoerceIn(-1f, 1f)
                if (docked) 0f else with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val requestedIndex = selectedTabIndex().fastCoerceIn(0, tabsCount - 1)
        val latestRequestedIndex by rememberUpdatedState(requestedIndex)
        val latestOnTabSelected by rememberUpdatedState(onTabSelected)
        val latestTabWidth by rememberUpdatedState(tabWidth)
        val latestIsLtr by rememberUpdatedState(isLtr)
        var currentIndex by remember(tabsCount) {
            mutableIntStateOf(requestedIndex)
        }
        val dampedDragAnimation = remember(animationScope, tabsCount, style) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = requestedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = if (style == LiquidBottomTabsStyle.Compact) 81f / 56f else 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / latestTabWidth * if (latestIsLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        // Read the resolved value as a Compose state, not as a remembered
        // callback key. Callers commonly pass `{ selectedIndex }`, which is a
        // new lambda on every recomposition; keying the effect by that lambda
        // can either restart too often or capture an obsolete index.
        LaunchedEffect(requestedIndex, dampedDragAnimation) {
            currentIndex = requestedIndex
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    // External selection already delivered its navigation event.
                    // Only a new drag selection needs to notify the caller.
                    if (index != latestRequestedIndex) latestOnTabSelected(index)
                }
        }

        val highlightPosition by rememberUpdatedState<(Size, Offset) -> Offset>({ size, _ ->
            Offset(
                if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                (size.height - with(density) { resolvedBottomInset.toPx() }) / 2f,
            )
        })
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset -> highlightPosition(size, offset) },
            )
        }

        Box(
            Modifier.width(dockWidth).height(dockHeight + resolvedBottomInset).testTag("liquid-bottom-tabs"),
            contentAlignment = if (docked) Alignment.TopStart else Alignment.CenterStart,
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = vendorBackdrop,
                        shape = { if (docked) RoundedCornerShape(0.dp) else Capsule() },
                        effects = {
                            vibrancy()
                            blur((8f * panelEffectScale).dp.toPx() * glassIntensity)
                            lens(
                                (24f * panelEffectScale).dp.toPx(),
                                (24f * panelEffectScale).dp.toPx(),
                            )
                        },
                        highlight = if (docked) null else { { Highlight.Default } },
                        shadow = if (docked) null else { { Shadow.Default } },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = if (docked) 1f else if (style == LiquidBottomTabsStyle.Compact) {
                                lerp(1f, compactPanelPressedScale, progress)
                            } else {
                                lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            }
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(dockHeight + resolvedBottomInset)
                    .width(dockWidth)
                    .padding(bottom = resolvedBottomInset)
                    .padding(4f.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )

            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .padding(top = if (docked) 4.dp else 0.dp)
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = vendorBackdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur((8f * panelEffectScale).dp.toPx() * glassIntensity)
                                lens(
                                    (24f * panelEffectScale).dp.toPx() * progress,
                                    (24f * panelEffectScale).dp.toPx() * progress
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(dockHeight - 8.dp)
                        .width(dockWidth)
                        .padding(horizontal = 4f.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            Box(
                Modifier
                    .padding(start = 4.dp, end = 4.dp, top = if (docked) 4.dp else 0.dp)
                    .graphicsLayer {
                        translationX =
                            if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .graphicsLayer {
                        alpha = 1f - (collapseProgress * 2f).fastCoerceIn(0f, 1f)
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(vendorBackdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            val refractionProgress = progress.coerceAtLeast(0.001f)
                            lens(
                                (10f * selectionEffectScale).dp.toPx() * refractionProgress,
                                (14f * selectionEffectScale).dp.toPx() * refractionProgress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress)
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = (8f * selectionEffectScale).dp * progress,
                                alpha = progress
                            )
                        },
                        layerBlock = {
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawBackdrop = { drawBackdrop ->
                            // A Gaussian selection must not blur the tab's own
                            // label. Non-liquid modes highlight the visible row.
                            if (glassStyle == GlassStyle.Liquid) drawBackdrop()
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.1f)
                                else Color.White.copy(0.1f),
                                alpha = if (glassStyle == GlassStyle.Liquid) 1f - progress else 1f
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    )
                    .height(dockHeight - 8.dp)
                    .width((dockWidth - 8.dp) / tabsCount)
            )
        }
    }
}
