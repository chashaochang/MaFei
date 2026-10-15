package cn.mafei.cmp.common.glass.utils

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class DampedDragAnimationTest {
    @Test
    fun defaultPressKeepsBothAxesEqual() = runBlocking(TestFrameClock()) {
        val animation = DampedDragAnimation(
            animationScope = this,
            initialValue = 0f,
            valueRange = 0f..2f,
            visibilityThreshold = .001f,
            initialScale = 1f,
            pressedScale = 81f / 56f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
        animation.press()
        coroutineContext[Job]!!.children.toList().joinAll()

        assertEquals(81f / 56f, animation.scaleX, .001f)
        assertEquals(81f / 56f, animation.scaleY, .001f)
    }

    @Test
    fun tallerPressDoesNotWidenTabsAndReleaseRestoresBothAxes() = runBlocking(TestFrameClock()) {
        val animation = animation(this, pressedScaleY = 56f / 34f)
        animation.press()
        coroutineContext[Job]!!.children.toList().joinAll()

        assertEquals(81f / 56f, animation.scaleX, .001f)
        assertEquals(56f, 34f * animation.scaleY, .01f)

        animation.release()
        coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(1f, animation.scaleX, .001f)
        assertEquals(1f, animation.scaleY, .001f)
        assertEquals(0f, animation.pressProgress, .001f)
    }

    private fun animation(scope: CoroutineScope, pressedScaleY: Float) =
        DampedDragAnimation(
            animationScope = scope,
            initialValue = 0f,
            valueRange = 0f..2f,
            visibilityThreshold = .001f,
            initialScale = 1f,
            pressedScale = 81f / 56f,
            pressedScaleY = pressedScaleY,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )

    private class TestFrameClock : MonotonicFrameClock {
        private var timeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            timeNanos += 16_666_667L
            return onFrame(timeNanos)
        }
    }
}
