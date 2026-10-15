package cn.mafei.cmp.core.http_contract

import kotlin.test.*

class HttpExceptionPrivacyTest {
    @Test fun builtInErrorMessagesNeverEmbedSignedUrls() {
        val url = HttpUrl.parse("https://private-host.invalid/signed-path-fixture?api_key=fixture-secret")
        val insecure = HttpUrl.parse("http://private-host.invalid/signed-path-fixture?token=fixture-secret")
        val response = HttpResponse(401, HttpHeaders.EMPTY, ByteArray(0), url, url)
        val errors = listOf(
            HttpTimeoutException(url, 30), HttpStatusCodeException(response),
            HttpRedirectLimitExceededException(url, 4, 302),
            HttpInsecureRedirectException(url, insecure),
            HttpBodyLimitExceededException(url, 4, 5),
        )
        errors.forEach { error ->
            assertFalse(error.toString().contains("fixture-secret"), error::class.simpleName)
            assertFalse(error.toString().contains("signed-path-fixture"), error::class.simpleName)
            assertFalse(error.toString().contains("private-host"), error::class.simpleName)
        }
        // The explicit structured fields remain available; only automatic log text changes.
        assertSame(url, (errors[0] as HttpTimeoutException).url)
        assertSame(response, (errors[1] as HttpStatusCodeException).response)
    }
}
