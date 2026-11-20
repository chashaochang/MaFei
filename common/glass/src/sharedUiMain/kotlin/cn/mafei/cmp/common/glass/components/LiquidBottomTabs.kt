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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.utils.DampedDragAnimation
import cn.mafei.cmp.common.glass.utils.InteractiveHighlight
import cn.mafei.cmp.common.glass.drawFrostedBackdrop
import cn.mafei.cmp.common.glass.frostedAlpha
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
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
    onAnimationRunningChanged: (Boolean) -> Unit = {},
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
    val docked = style == LiquidBottomTabsStyle.Bar
    val resolvedBottomInset = if (docked) bottomInset.coerceAtLeast(0.dp) else 0.dp
    val containerAlpha = frostedAlpha(.4f)
    val containerColor by rememberUpdatedState(
        if (isLightTheme) Color(0xFFFAFAFA).copy(containerAlpha)
        else Color(0xFF121212).copy(containerAlpha)
    )


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
        val latestAnimationRunningChanged by rememberUpdatedState(onAnimationRunningChanged)
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
        LaunchedEffect(dampedDragAnimation, offsetAnimation) {
            snapshotFlow { dampedDragAnimation.isRunning || offsetAnimation.isRunning }
                .distinctUntilChanged()
                .collect { latestAnimationRunningChanged(it) }
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

        val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }

        Box(
            Modifier.width(dockWidth).height(dockHeight + resolvedBottomInset).testTag("liquid-bottom-tabs"),
            contentAlignment = if (docked) Alignment.TopStart else Alignment.CenterStart,
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawFrostedBackdrop(
                        backdrop = vendorBackdrop,
                        shape = { if (docked) RoundedCornerShape(0.dp) else Capsule() },
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
                    .graphicsLayer {
                        shape = Capsule()
                        clip = true
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    }
                    .drawBehind {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(if (isLightTheme) Color.Black.copy(.1f) else Color.White.copy(.1f))
                        drawRect(Color.Black.copy(alpha = .03f * progress))
                    }
                    .height(dockHeight - 8.dp)
                    .width((dockWidth - 8.dp) / tabsCount)
            )
        }
    }
}
