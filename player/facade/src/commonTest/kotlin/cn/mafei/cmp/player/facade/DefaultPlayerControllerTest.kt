package cn.mafei.cmp.player.facade

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.player.testkit.FakePlayerRuntime
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

private fun immediateTest(block: suspend () -> Unit) {
    var completed = false; var failure: Throwable? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { completed = true; failure = result.exceptionOrNull() }
    })
    check(completed) { "Test must complete synchronously" }; failure?.let { throw it }
}

class DefaultPlayerControllerTest {
    private fun resource(id: String = "one") = PlayableResource(id, "https://media.invalid/$id")
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun runtimeOwnsPlaybackConfirmationNotCommands() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope()
        val c = DefaultPlayerController(runtime, owner, { 0 })
        val id = c.load(listOf(resource()))
        assertEquals(PlayerPhase.Loading, c.state.value.phase)
        runtime.emit(RuntimeEvent.Ready(id, 1000))
        assertEquals(PlayerPhase.Ready, c.state.value.phase)
        runtime.emit(RuntimeEvent.Playing(id))
        assertEquals(PlayerPhase.Playing, c.state.value.phase)
        c.pause(); assertEquals(PlayerPhase.Playing, c.state.value.phase)
        runtime.emit(RuntimeEvent.Paused(id)); assertEquals(PlayerPhase.Paused, c.state.value.phase)
        c.release(); owner.cancel()
    }

    @Test fun synchronousNativeCallbackDoesNotDeadlock() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope()
        runtime.beforeExecute = { if (it is RuntimeCommand.Load) runtime.emit(RuntimeEvent.Ready(it.generation, null)) }
        val c = DefaultPlayerController(runtime, owner, { 0 })
        c.load(listOf(resource()))
        assertEquals(PlayerPhase.Ready, c.state.value.phase)
        c.release(); owner.cancel()
    }

    @Test fun rapidReplacementAndCancelledOldLoadIgnoreStaleNativeEvents() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope(); val c = DefaultPlayerController(runtime, owner, { 0 })
        val old = c.load(listOf(resource("old")))
        val fresh = c.load(listOf(resource("fresh")))
        c.cancelLoad(old)
        runtime.emit(RuntimeEvent.Failed(old, PlayerFailure.Network))
        assertSame(fresh, c.state.value.generation)
        assertEquals(PlayerPhase.Loading, c.state.value.phase)
        c.cancelLoad(fresh); assertEquals(PlayerPhase.Idle, c.state.value.phase)
        c.release(); owner.cancel()
    }

    @Test fun ownerCancellationReleasesEngineExactlyOnce() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope(); val c = DefaultPlayerController(runtime, owner, { 0 })
        val id = c.load(listOf(resource()))
        owner.cancel()
        assertEquals(PlayerPhase.Released, c.state.value.phase)
        c.release()
        assertEquals(1, runtime.commands.count { it is RuntimeCommand.Release })
        runtime.emit(RuntimeEvent.Playing(id))
        assertEquals(PlayerPhase.Released, c.state.value.phase)
    }

    @Test fun engineFailureStopsAutoplayAndStillAllowsRelease() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope(); val c = DefaultPlayerController(runtime, owner, { 0 })
        val id = c.load(listOf(resource()), resumePositionMs = 500)
        runtime.beforeExecute = { if (it is RuntimeCommand.Seek || it is RuntimeCommand.Stop) throw IllegalStateException("engine") }
        runtime.emit(RuntimeEvent.Ready(id, 1000))
        assertEquals(PlayerPhase.Error, c.state.value.phase)
        assertTrue(runtime.commands.none { it is RuntimeCommand.Play })
        c.release()
        assertEquals(1, runtime.commands.count { it is RuntimeCommand.Release })
        owner.cancel()
    }

    @Test fun reportSinkFailureIsObservableAndDoesNotBreakPlayback() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope()
        val c = DefaultPlayerController(runtime, owner, { 0 }, { throw IllegalStateException("sink offline") })
        val id = c.load(listOf(resource())); runtime.emit(RuntimeEvent.Ready(id, null)); runtime.emit(RuntimeEvent.Playing(id))
        assertEquals(1, c.reportDeliveryFailures.value)
        assertEquals(PlayerPhase.Playing, c.state.value.phase)
        c.release(); owner.cancel()
    }

    @Test fun cancellationWhileWaitingForAdmissionNeverExecutesTheCommand() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope(); val c = DefaultPlayerController(runtime, owner, { 0 })
        runtime.beforeExecute = { command ->
            if (command is RuntimeCommand.Load) {
                val queued = owner.launch(start = CoroutineStart.UNDISPATCHED) { c.pause() }
                queued.cancel()
            }
        }
        val id = c.load(listOf(resource()))
        runtime.emit(RuntimeEvent.Ready(id, null))
        assertEquals(1, runtime.commands.count { it is RuntimeCommand.Play })
        assertTrue(runtime.commands.none { it is RuntimeCommand.Pause })
        c.release(); owner.cancel()
    }

    @Test fun cancelledOwnerCannotLoadAnythingAndRuntimeStillReleased() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope(); owner.cancel()
        val c = DefaultPlayerController(runtime, owner, { 0 })
        assertFailsWith<IllegalStateException> { c.load(listOf(resource())) }
        assertEquals(1, runtime.commands.count { it is RuntimeCommand.Release })
        c.release()
    }

    @Test fun releaseFailureIsRecordedEvenThoughControllerCloses() = immediateTest {
        val runtime = FakePlayerRuntime(); val owner = scope(); val c = DefaultPlayerController(runtime, owner, { 0 })
        runtime.beforeExecute = { if (it is RuntimeCommand.Release) throw IllegalStateException("native disposal failed") }
        c.release()
        assertEquals(PlayerPhase.Released, c.state.value.phase)
        assertEquals(1, c.runtimeCommandFailures.value)
        owner.cancel()
    }
}
