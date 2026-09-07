package cn.mafei.cmp.providers.feiniu.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FeiniuSignerTest {

    @Test
    fun connectFnSignMatchesFrozenFormula() {
        assertEquals(
            "61f6c0c757218310e68f96cf86f39dd237735f75bc1ee8a0304019c3dab70718",
            FeiniuSigner.connectFnSign("testfnid", "1700000000000"),
        )
    }

    @Test
    fun mediaAuthxMatchesFrozenFormula() {
        val body = """{"username":"demo","password":"pw","app_name":"trimemedia-web"}"""
        assertEquals(
            "nonce=123456&timestamp=1700000000000&sign=1043f311a5e576d85a3de6bfcb2ec8b4",
            FeiniuSigner.authx(
                path = "/v/api/v1/login",
                nonce = "123456",
                timestamp = "1700000000000",
                bodyText = body,
                variant = FeiniuSigner.Variant.Media,
            ),
        )
    }

    @Test
    fun connectAuthxUsesTheConnectSecret() {
        assertEquals(
            "nonce=123456&timestamp=1700000000000&sign=a4baa8ec738f344c82a0e44743f08888",
            FeiniuSigner.authx(
                path = "/api/v1/fn/con",
                nonce = "123456",
                timestamp = "1700000000000",
                bodyText = """{"fnId":"testfnid"}""",
                variant = FeiniuSigner.Variant.Connect,
            ),
        )
    }

    @Test
    fun queryAuthxSortsParametersAndHashesThem() {
        assertEquals(
            "nonce=123456&timestamp=1700000000000&sign=876fadb02dd6ce974a236e1d77428913",
            FeiniuSigner.authx(
                path = "/v/api/v1/search/list",
                nonce = "123456",
                timestamp = "1700000000000",
                queryParameters = listOf("q" to "ab", "lan" to "zh"),
                variant = FeiniuSigner.Variant.Media,
            ),
        )
    }

    @Test
    fun nonceFromRandomMatchesLegacyFormula() {
        assertEquals("450000", FeiniuSigner.nonceFrom(0.5))
        assertEquals("0", FeiniuSigner.nonceFrom(0.0))
    }
}

class FeiniuCookieJarTest {

    @Test
    fun mergesCookiesLastWriterWins() {
        val merged = FeiniuCookieJar.merge("a=1; b=2", "b=3; c=4")
        assertEquals("a=1; b=3; c=4", merged)
    }

    @Test
    fun tokenMirrorsTrimMcTokenCookie() {
        val merged = FeiniuCookieJar.mergeToken("sid=abc", "tok-1")
        assertEquals("sid=abc; Trim-MC-token=tok-1", merged)
        assertEquals("tok-1", FeiniuCookieJar.extractToken(merged))
    }

    @Test
    fun flattensSetCookieValuesWithAttributes() {
        val flattened = FeiniuCookieJar.fromSetCookieValues(
            listOf(
                "Trim-MC-token=tok-2; Path=/; HttpOnly; Expires=Wed, 21 Oct 2099 07:28:00 GMT",
                "sid=xyz; Path=/",
            ),
        )
        assertEquals("Trim-MC-token=tok-2; sid=xyz", flattened)
        assertEquals("tok-2", FeiniuCookieJar.extractToken(flattened))
    }

    @Test
    fun credentialsNormalizeFromCookie() {
        val credentials = FeiniuCredentials(cookie = "Trim-MC-token=from-cookie; sid=1").normalized()
        assertEquals("from-cookie", credentials.token)
        assertEquals(true, credentials.hasToken)
        assertEquals(true, credentials.cookie.contains("Trim-MC-token=from-cookie"))
    }
}

class FeiniuAddressParserTest

class FeiniuSessionTest {

    @Test
    fun sessionStateTransitions() {
        val state = InMemoryFeiniuSessionState()
        assertEquals(false, state.current.value.hasToken)
        state.set(FeiniuCredentials(token = "t1"))
        assertEquals("t1", state.current.value.token)
        state.update { FeiniuCookieJar.mergeToken(it.cookie, "t2").let { c -> FeiniuCredentials("t2", c) } }
        assertEquals("t2", state.current.value.token)
        state.clear()
        assertEquals(false, state.current.value.hasToken)
    }
}
