package cn.mafei.cmp.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

@Composable
internal fun DetailAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(tween(DetailMotionEnterMillis)),
    exit: ExitTransition = fadeOut(tween(DetailMotionExitMillis)),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content,
    )
}

/**
 * Avoid flashing progress chrome for requests that complete within a frame or two.
 * The job launched by [LaunchedEffect] is cancelled as soon as loading finishes.
 */
@Composable
internal fun rememberDelayedDetailLoading(
    loading: Boolean,
    delayMillis: Long = DetailLoadingIndicatorDelayMillis,
): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading, delayMillis) {
        if (!loading) {
            visible = false
            return@LaunchedEffect
        }
        delay(delayMillis)
        visible = true
    }
    return visible
}

internal const val DetailLoadingIndicatorDelayMillis = 140L
internal const val DetailMotionEnterMillis = 200
internal const val DetailMotionExitMillis = 120
internal const val DetailMotionFastMillis = 140
