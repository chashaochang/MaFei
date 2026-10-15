package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.providers.feiniu.auth.FeiniuAuthApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuApiCall
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionSnapshot
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionState
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * K1 regression: under persistent same-identity CAS loss the retry loops
 * must YIELD the thread, so a cancellation queued to the SAME single-thread
 * dispatcher as the SDK call can execute. ensureActive alone would starve
 * that task. Reflection is used only to inject the deterministic CAS-loss
 * scheduling seam into the real state flow; no production code changes.
 *
 * Desktop-only: requires a real single-thread dispatcher and java.util.concurrent.
 */
class FeiniuCancellationDeliveryTest {

    private fun exercise(login: Boolean) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val watchdog = Executors.newSingleThreadScheduledExecutor()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val state = InMemoryFeiniuSessionState(FeiniuCredentials("original"))
        val localCancelRan = AtomicBoolean(false)
        val cancelledLocallyAtExit = AtomicBoolean(false)
        val watchdogFired = AtomicBoolean(false)
        var armed = false
        var nested = false
        var attempts = 0
        lateinit var operation: kotlinx.coroutines.Deferred<Unit>

        // Deterministic seam: on EVERY attempt, one REAL same-identity
        // revision update lands between the operation's read and its
        // original CAS, so the CAS loses each time (identity unchanged).
        val stateFlow = state.snapshots
        @Suppress("UNCHECKED_CAST")
        val field = InMemoryFeiniuSessionState::class.java.getDeclaredField("state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val original = field.get(state) as kotlinx.coroutines.flow.MutableStateFlow<FeiniuSessionSnapshot>
        field.set(state, object : kotlinx.coroutines.flow.MutableStateFlow<FeiniuSessionSnapshot> by original {
            override fun compareAndSet(expect: FeiniuSessionSnapshot, update: FeiniuSessionSnapshot): Boolean {
                if (armed && !nested) {
                    nested = true
                    try {
                        attempts += 1
                        original.update { snap -> FeiniuSessionSnapshot(snap.credentials, snap.identityGeneration, snap.revision + 1) }
                    } finally { }
                    if (attempts == 1) {
                        // Model a UI cancellation task queued to the SAME
                        // single-thread dispatcher as the suspend SDK call.
                        scope.launch {
                            localCancelRan.set(true)
                            operation.cancel()
                        }
                        // Safety net only; NOT the cancellation path under test.
                        watchdog.schedule({
                            watchdogFired.set(true)
                            operation.cancel()
                        }, 1, TimeUnit.SECONDS)
                    }
                }
                return original.compareAndSet(expect, update)
            }
        })

        val resolution = FeiniuHostResolution(FeiniuHostCandidate("https://feiniu.test", FeiniuRoute.Direct))
        val client = FeiniuClient(sessionState = state, transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                armed = true
                val body = if (login) """{"code":0,"token":"new-login"}""" else """{"code":0}"""
                return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
            }
        })
        try {
            operation = scope.async(start = CoroutineStart.LAZY) {
                try {
                    if (login) {
                        FeiniuAuthApi(client).login(resolution, "user", "password")
                    } else {
                        client.execute(
                            resolution,
                            FeiniuApiCall(
                                "/v/api/v1/item/list",
                                FeiniuApiCall.FeiniuMethod.Get,
                                authRequirement = FeiniuApiCall.AuthRequirement.Token,
                            ),
                        ) { _, _ -> true }
                    }
                } finally {
                    cancelledLocallyAtExit.set(localCancelRan.get())
                }
                Unit
            }
            operation.start()
            runBlocking { withTimeout(5_000) { operation.join() } }
            assertTrue(attempts > 0, "Real CAS loss must execute")
            assertTrue(operation.isCancelled)
            assertTrue(cancelledLocallyAtExit.get(),
                "Retry monopolized dispatcher: queued cancellation could not run; external watchdog=$watchdogFired")
            assertEquals("original", state.snapshots.value.credentials.token)
        } finally {
            scope.cancel()
            watchdog.shutdownNow()
            dispatcher.close()
        }
    }

    @Test
    fun loginRetryMustAllowSameDispatcherCancellation() = exercise(login = true)

    @Test
    fun cookieRetryMustAllowSameDispatcherCancellation() = exercise(login = false)
}
