package cn.mafei.cmp.feature.detail

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Child scroll consumes first; only direct downward drag left at its top moves the sheet. */
@Composable
internal fun rememberDetailSheetDrag(contentScrollState: ScrollState, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val distance = with(density) { 96.dp.toPx() }
    val velocityThreshold = with(density) { 900.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(1f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val drag: (Float) -> Float = remember {
        { delta ->
            settling?.cancel()
            val old = offset
            offset = (offset + delta).coerceIn(0f, height)
            offset - old
        }
    }
    val settle: (Float) -> Unit = { velocity ->
        settling?.cancel()
        if (offset > 0f) {
            settling = scope.launch {
                val close = offset >= minOf(distance, height * .25f) || velocity >= velocityThreshold
                if (close) {
                    animate(offset, height, animationSpec = tween(180)) { value, _ -> offset = value }
                    dismiss()
                } else {
                    animate(offset, 0f, animationSpec = spring(dampingRatio = 1f)) { value, _ -> offset = value }
                }
            }
        }
    }
    val currentSettle by rememberUpdatedState(settle)
    val connection = remember(contentScrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && offset > 0f && !contentScrollState.canScrollBackward) {
                    Offset(0f, drag(available.y))
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y > 0f && !contentScrollState.canScrollBackward) {
                    Offset(0f, drag(available.y))
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset <= 0f) return Velocity.Zero
                currentSettle(available.y)
                return Velocity(0f, available.y)
            }
        }
    }
    return Modifier
        .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
        .graphicsLayer { translationY = offset }
        .nestedScroll(connection)
        // A slow release may not dispatch a fling on every Compose target. Observe
        // the final pointer-up without consuming it so the child keeps owning scroll.
        .pointerInput(contentScrollState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val pointerId = down.id
                var pressed = true
                var pendingDrag = 0f
                var directDrag = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        pressed = false
                    } else {
                        val deltaY = change.position.y - change.previousPosition.y
                        // The header/short body may not dispatch nested scroll. Wait
                        // for touch slop AND the body's top before adopting that drag.
                        // Unconsumed pre-slop motion must never steal a scrolled body.
                        if (change.isConsumed || contentScrollState.canScrollBackward) {
                            pendingDrag = 0f
                            directDrag = false
                        } else if (change.pressed) {
                            if (directDrag) {
                                drag(deltaY)
                                change.consume()
                            } else {
                                pendingDrag = (pendingDrag + deltaY).coerceAtLeast(0f)
                                if (pendingDrag > viewConfiguration.touchSlop) {
                                    directDrag = true
                                    drag(pendingDrag - viewConfiguration.touchSlop)
                                    change.consume()
                                }
                            }
                        }
                        pressed = change.pressed
                    }
                } while (pressed)
                currentSettle(0f)
            }
        }
        .semantics { dismiss { dismiss(); true } }
}

@Composable
internal fun DetailSheetHeader(
    title: String,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    contentColor: Color = LocalContentColor.current,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .36f)),
        )
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title, Modifier.fillMaxWidth().padding(horizontal = 52.dp).semantics { heading() },
                color = contentColor,
                textAlign = TextAlign.Center,
                fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
            )
            LiquidButton(
                onClick = onDismiss,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.CenterEnd).size(44.dp)
                    .semantics { contentDescription = "关闭$title" },
                height = 44.dp,
                horizontalPadding = 0.dp,
            ) {
                MaFeiIcon(MaFeiGlyph.Close, Modifier.size(20.dp), color = contentColor)
            }
        }
    }
}
