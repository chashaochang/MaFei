package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ProviderLoginTest {
    private data class Response(val body: String, val status: Int = 200, val cookie: String? = null)
    private class Transport(vararg responses: Response) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        private val pending = responses.toMutableList()
        var failure: Exception? = null
        var closes = 0
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            failure?.let { throw it }
            val response = pending.removeAt(0)
            val headers = HttpHeaders.Builder().add("Content-Type", "application/json")
            response.cookie?.let { headers.add("Set-Cookie", it) }
            return HttpResponse(response.status, headers.build(), response.body.encodeToByteArray(), request.url, request.url)
        }
        override fun close() { closes++ }
    }

    @Test fun jellyfinLoginUsesActualResponseAndSameDeviceIdForFreshBinding() = runBlocking<Unit> {
        val transport = Transport(
            Response("""{"User":{"Id":"remote-user","Name":"Demo"},"AccessToken":"fixture-token","ServerId":"remote-server"}"""),
            Response("""{"Items":[],"TotalRecordCount":0}"""),
        )
        val result = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test/jellyfin", " Demo ", " pw ")
        val login = transport.requests.single()
        assertEquals("/jellyfin/Users/AuthenticateByName", login.url.encodedPath)
        assertEquals("""{"Username":"Demo","Pw":" pw "}""", (login.body as HttpBody.Text).text)
        assertEquals("remote-user", result.connection.remoteUserId)
        assertEquals("Demo", result.connection.account.displayName)
        assertFalse(login.headers.first("Authorization")!!.contains("Token="))
        assertEquals(SecureSecret.AuthToken("MediaBrowser", "fixture-token"), result.binding.source.current().secrets[result.connection.tokenAlias])
        result.binding.adapter.collections(PageRequest())
        val authenticatedHeader = transport.requests.last().headers.first("Authorization")!!
        assertTrue(authenticatedHeader.contains("Token=\"fixture-token\""))
        assertTrue(authenticatedHeader.contains("DeviceId=\"${result.connection.credentialKey}\""))
        assertTrue(login.headers.first("Authorization")!!.contains("DeviceId=\"${result.connection.credentialKey}\""))
        result.binding.clear()
        assertEquals(0, transport.closes)
    }

    @Test fun feiniuDirectLoginReadsAuthoritativeCookieAfterUserInfo() = runBlocking<Unit> {
        val transport = Transport(
            Response("""{"code":0,"data":{"token":"fixture-token"}}""", cookie = "session=login; Path=/"),
            Response("""{"code":0,"data":{"user_id":"user-7","username":"demo","nickname":"Cinema","avatar":"https://media.test/avatar?token=avatar-secret"}}""", cookie = "session=rotated; Path=/"),
            Response("""{"code":0,"data":[]}"""),
        )
        val result = ProviderLogin(transport).authenticate(ProviderKind.Feiniu, "http://media.test:5667/base", " demo ", " pw ")
        assertEquals(listOf("/base/v/api/v1/login", "/base/v/api/v1/user/info"), transport.requests.map { it.url.encodedPath })
        val login = transport.requests.first()
        assertEquals("""{"username":"demo","password":" pw ","app_name":"trimemedia-web"}""", (login.body as HttpBody.Text).text)
        assertNull(login.headers.first("Authorization")); assertNull(login.headers.first("Cookie"))
        assertNotNull(login.headers.first("Authx"))
        val saved = result.connection
        assertEquals("user-7", saved.remoteUserId)
        assertEquals("Cinema", saved.account.displayName)
        assertEquals(listOf(SavedFeiniuRoute(saved.server.baseUrl, "Direct")), saved.feiniuRoutes)
        assertNull(saved.account.avatarImage)
        val cookie = (result.binding.source.current().secrets[saved.cookieAlias] as SecureSecret.CookieText).value
        assertTrue(cookie.contains("session=rotated")); assertFalse(cookie.contains("session=login"))
        result.binding.adapter.collections(PageRequest())
        assertEquals("fixture-token", transport.requests.last().headers.first("Authorization"))
        assertFalse(transport.requests.last().headers.first("Cookie")!!.contains("mode=relay"))
        val encoded = Json.encodeToString(saved)
        for (secret in listOf("fixture-token", " pw ", "avatar-secret", "session=rotated")) assertFalse(encoded.contains(secret))
        assertFalse(result.toString().contains("fixture-token"))
        result.binding.clear()
        assertEquals(0, transport.closes)
    }

    @Test fun feiniuIdentityRequiresAuthenticatedUserInfoButSupportsUsernameWithoutId() = runBlocking<Unit> {
        for (info in listOf("""{"username":"demo"}""", "{}")) {
            val transport = Transport(Response("""{"code":0,"token":"fixture-token"}"""), Response("""{"code":0,"data":$info}"""))
            val login = ProviderLogin(transport)
            if (info == "{}") {
                assertFailsWith<ProtocolDecodingError> { login.authenticate(ProviderKind.Feiniu, "https://media.test", "input-name", "pw") }
            } else {
                val result = login.authenticate(ProviderKind.Feiniu, "https://media.test", "input-name", "pw")
                assertEquals("demo", result.connection.remoteUserId)
                assertEquals("demo", result.connection.account.userName)
                result.binding.clear()
            }
            assertEquals(0, transport.closes)
        }
    }

    @Test fun invalidAddressAndUsernameFailBeforeNetworkWithoutEchoingSecrets() = runBlocking<Unit> {
        val transport = Transport()
        for (provider in ProviderKind.entries) {
            for (url in listOf("fnid-only", "ftp://media.test", "https://user:secret@media.test", "https://media.test?token=secret", "https://media.test#secret")) {
                val error = assertFailsWith<ClientConfigError> { ProviderLogin(transport).authenticate(provider, url, "demo", "secret") }
                assertFalse(error.toString().contains("secret"))
            }
            assertFailsWith<ClientConfigError> { ProviderLogin(transport).authenticate(provider, "https://media.test", " ", "secret") }
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun failedAuthenticationNeverReturnsBindingOrLeaksServerMessage() = runBlocking<Unit> {
        for (provider in ProviderKind.entries) {
            val response = if (provider == ProviderKind.Jellyfin) Response("""{"detail":"echo-password-secret"}""", 401)
                else Response("""{"code":100401,"msg":"echo-password-secret"}""")
            val transport = Transport(response)
            val error = assertFailsWith<AuthenticationError> {
                ProviderLogin(transport).authenticate(provider, "https://media.test", "demo", "password-secret")
            }
            assertEquals(AuthFailureKind.InvalidCredentials, error.kind)
            assertSafeChain(error)
            assertEquals(1, transport.requests.size)
            assertEquals(0, transport.closes)
        }
    }

    @Test fun malformedSuccessfulLoginAndUserInfoFailureDoNotAdmitBinding() = runBlocking<Unit> {
        val jellyfin = Transport(Response("""{"User":{"Id":"u"},"AccessToken":""}"""))
        assertFailsWith<ProtocolDecodingError> { ProviderLogin(jellyfin).authenticate(ProviderKind.Jellyfin, "https://media.test", "demo", "pw") }
        val feiniu = Transport(Response("""{"code":0,"token":"fixture-token"}"""), Response("""{"code":100401,"msg":"password-secret"}"""))
        assertFailsWith<AuthenticationError> { ProviderLogin(feiniu).authenticate(ProviderKind.Feiniu, "https://media.test", "demo", "pw") }
        assertEquals(0, jellyfin.closes); assertEquals(0, feiniu.closes)
    }

    @Test fun transportFailureIsSafeAndCancellationPropagates() = runBlocking<Unit> {
        for (provider in ProviderKind.entries) {
            val transport = Transport()
            transport.failure = IllegalStateException("password-secret https://user:secret@media.test")
            val error = assertFailsWith<DomainError> { ProviderLogin(transport).authenticate(provider, "https://media.test", "demo", "pw") }
            assertSafeChain(error)
            transport.failure = CancellationException("cancel-login")
            assertFailsWith<CancellationException> { ProviderLogin(transport).authenticate(provider, "https://media.test", "demo", "pw") }
            assertEquals(0, transport.closes)
        }
    }

    @Test fun cancellationDuringNetworkDoesNotReturnSuccessfulConnection() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        var closed = false
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                entered.complete(Unit)
                awaitCancellation()
            }
            override fun close() { closed = true }
        }
        var admitted = false
        val job = launch {
            ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test", "demo", "pw")
            admitted = true
        }
        entered.await(); job.cancelAndJoin()
        assertFalse(admitted); assertFalse(closed)
    }

    private fun assertSafeChain(error: Throwable) {
        var current: Throwable? = error
        repeat(10) {
            val value = current ?: return
            assertFalse(value.toString().contains("password-secret"))
            assertFalse(value.toString().contains("user:secret"))
            current = value.cause
        }
    }
}
