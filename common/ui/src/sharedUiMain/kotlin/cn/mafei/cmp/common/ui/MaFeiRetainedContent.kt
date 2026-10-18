package cn.mafei.cmp.common.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Retained pages use this to suspend page-owned work while another page is visible. */
val LocalMaFeiPageActive = compositionLocalOf { true }

/**
 * Keeps both composition and placed render layers across tab switches. Not placing a child
 * makes Compose release its GraphicsLayers, even when its composition is retained.
 * Hidden children are placed beyond a stationary clipped host and omitted from its drawing.
 * Unchanged measurement is reused by Compose. Page-owned effects
 * must observe [LocalMaFeiPageActive] so hidden pages do not keep animating or requesting data.
 */
@Composable
fun MaFeiRetainedContent(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val effectiveActive = LocalMaFeiPageActive.current && active
    val currentActive = rememberUpdatedState(effectiveActive)
    val focusManager = LocalFocusManager.current
    val minimumTouchTarget = LocalViewConfiguration.current.minimumTouchTargetSize
    val hiddenTouchGap = with(LocalDensity.current) { minimumTouchTarget.width.roundToPx() * 2 + 1 }
    var hasFocus by remember { mutableStateOf(false) }
    SideEffect {
        if (!effectiveActive && hasFocus) focusManager.clearFocus(force = true)
    }
    CompositionLocalProvider(LocalMaFeiPageActive provides effectiveActive) {
        Layout(
            content = { RetainedPageInputGate(currentActive, content) },
            modifier = modifier
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusProperties { onEnter = { if (!effectiveActive) cancelFocusChange() } }
                .focusGroup()
                .graphicsLayer {
                    // Keep the clip at the host's normal position. Moving the clip together
                    // with the child would leave a small hidden page hittable inside its parent.
                    clip = !effectiveActive
                }
                .drawWithContent { if (effectiveActive) drawContent() }
                .then(if (effectiveActive) Modifier else Modifier.clearAndSetSemantics {}),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
            val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
            layout(width, height) {
                val childX = if (effectiveActive) 0 else width + hiddenTouchGap
                // Place, rather than omit, the subtree: its native layers stay owned.
                // The stationary host clip gives hidden children an empty visible/hit region.
                placeables.forEach { it.place(childX, 0) }
            }
        }
    }
}

/** Keep this input node inside the moved child, so a hidden host cannot cover visible siblings. */
@Composable
private fun RetainedPageInputGate(active: State<Boolean>, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = Modifier.pointerInput(active) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // A press can start while visible and finish after a tab switch.
                    // Placement alone does not remove that existing hit path.
                    if (!active.value) event.changes.forEach { it.consume() }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}
