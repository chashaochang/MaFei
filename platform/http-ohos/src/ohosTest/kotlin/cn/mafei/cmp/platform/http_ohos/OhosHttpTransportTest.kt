package cn.mafei.cmp.platform.http_ohos

import cn.mafei.cmp.core.http_contract.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OhosHttpTransportTest {
    @Test fun rejectsInvalidTimeoutBeforeCreatingEngine() {
        assertFailsWith<IllegalArgumentException> { createOhosHttpTransport(0) }
    }

    @Test fun closeIsIdempotentAndRejectsNewRequests() = runBlocking<Unit> {
        val transport = createOhosHttpTransport()
        transport.close()
        transport.close()
        assertFailsWith<HttpTransportException> {
            transport.execute(HttpRequest.builder(HttpMethod.Get, HttpUrl.parse("http://127.0.0.1:1/")).build())
        }
    }
}
