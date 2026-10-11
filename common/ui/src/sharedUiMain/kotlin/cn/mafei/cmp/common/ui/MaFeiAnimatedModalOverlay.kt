package cn.mafei.cmp.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.glass.GlassBackdrop

/** Keeps the last modal content and its window portal alive until the exit completes. */
@Composable
fun <T : Any> MaFeiAnimatedModalOverlay(
    value: T?,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    useWindowHost: Boolean = false,
    content: @Composable BoxScope.(T, GlassBackdrop, Modifier) -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = value != null
    var retainedValue by remember { mutableStateOf<T?>(null) }
    SideEffect {
        if (value != null) retainedValue = value
        else if (visibility.isIdle && !visibility.currentState) retainedValue = null
    }
    val displayedValue = value ?: retainedValue
    MaFeiBackHandler(
        enabled = visibility.currentState || visibility.targetState || !visibility.isIdle,
        onBack = { if (value != null) onDismiss() },
    )
    val largeWindow = LocalMaFeiNavigationChrome.current.effectiveLayout
        ?.let { it.navigation != NavigationPlacement.Bottom } == true
    val render: @Composable BoxScope.(GlassBackdrop) -> Unit = { source ->
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            val panelMotion = Modifier.animateEnterExit(
                enter = if (largeWindow) scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = .96f)
                else slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it },
                exit = if (largeWindow) scaleOut(tween(180), targetScale = .96f)
                else slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
            )
            Box(Modifier.fillMaxSize()) {
                displayedValue?.let { content(it, source, panelMotion) }
            }
        }
    }
    if (useWindowHost && LocalMaFeiWindowModalHost.current != null) {
        MaFeiWindowModalPortal(
            visible = visibility.currentState || visibility.targetState || !visibility.isIdle,
            content = render,
        )
    } else {
        Box(Modifier.fillMaxSize()) { render(backdrop) }
    }
}
