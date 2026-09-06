package cn.mafei.cmp.providers.jellyfin.facade

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Cancellation contract: cancelling a caller mid-request propagates
 * CancellationException through the facade — never a JellyfinError.
 */
class JellyfinCancellationTest {

    private class SlowTransport : cn.mafei.cmp.core.http_contract.HttpTransport {
        override suspend fun execute(request: cn.mafei.cmp.core.http_contract.HttpRequest): cn.mafei.cmp.core.http_contract.HttpResponse {
            delay(60_000) // model a remote server that never answers
            error("unreachable")
        }
    }

    @Test
    fun cancellationPropagatesThroughFacade() = runBlocking {
        val facade = DefaultJellyfinFacade(
            config = JellyfinServerConfig(HttpUrl.parse(JellyfinFixtures.BASE_URL), "MaFei", "Test", "device-1", "2.0.0"),
            transport = SlowTransport(),
        )
        val outcome = CompletableDeferred<Throwable>()
        val supervisor = Job()

        val worker = launch(supervisor) {
            try {
                facade.auth.publicUsers()
                outcome.complete(IllegalStateException("expected cancellation"))
            } catch (t: Throwable) {
                outcome.complete(t)
            }
        }

        yield() // let the worker reach its suspension point
        worker.cancel()

        val thrown = withTimeout(10_000) { outcome.await() }
        assertIs<CancellationException>(thrown)
        assertTrue(thrown !is cn.mafei.cmp.providers.jellyfin.core.JellyfinError)

        supervisor.cancel()
    }
}
