package cn.mafei.cmp.common.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Retained pages use this to suspend page-owned work while another page is visible. */
val LocalMaFeiPageActive = compositionLocalOf { true }

/**
 * Keeps composition and remembered state, but does not measure, place or draw hidden children.
 * This is a layout boundary, not an alpha animation. Effects must observe [LocalMaFeiPageActive];
 * retaining composition intentionally does not cancel arbitrary effects by itself.
 */
@Composable
fun MaFeiRetainedContent(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val effectiveActive = LocalMaFeiPageActive.current && active
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    SideEffect {
        if (!effectiveActive && hasFocus) focusManager.clearFocus(force = true)
    }
    CompositionLocalProvider(LocalMaFeiPageActive provides effectiveActive) {
        Layout(
            content = content,
            modifier = modifier
                .onFocusChanged { hasFocus = it.hasFocus }
                .focusProperties { onEnter = { if (!effectiveActive) cancelFocusChange() } }
                .focusGroup()
                .then(if (effectiveActive) Modifier else Modifier.clearAndSetSemantics {}),
        ) { measurables, constraints ->
            if (!effectiveActive) {
                layout(constraints.minWidth, constraints.minHeight) {}
            } else {
                val placeables = measurables.map { it.measure(constraints) }
                val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
                val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
                layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
            }
        }
    }
}
