package cn.mafei.cmp.platform.http

import cn.mafei.cmp.core.http_contract.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OhosHttpTransportTest {
    @Test fun rejectsInvalidTimeoutBeforeCreatingEngine() {
        assertFailsWith<IllegalArgumentException> { createPlatformHttpTransport(0) }
    }

    @Test fun closeIsIdempotentAndRejectsNewRequests() = runBlocking<Unit> {
        val transport = createPlatformHttpTransport()
        transport.close()
        transport.close()
        assertFailsWith<HttpTransportException> {
            transport.execute(HttpRequest.builder(HttpMethod.Get, HttpUrl.parse("http://127.0.0.1:1/")).build())
        }
    }
}
