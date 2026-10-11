package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
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
            Response("""{"ServerName":"Living Room","Version":"10.10.7","Id":"remote-server"}"""),
            Response("""{"Items":[],"TotalRecordCount":0}"""),
        )
        val result = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test/jellyfin", " Demo ", " pw ")
        val login = transport.requests.first()
        assertEquals("/jellyfin/Users/AuthenticateByName", login.url.encodedPath)
        assertEquals("""{"Username":"Demo","Pw":" pw "}""", (login.body as HttpBody.Text).text)
        assertEquals("remote-user", result.connection.remoteUserId)
        assertEquals("Demo", result.connection.account.displayName)
        assertEquals("Living Room", result.connection.server.name)
        assertEquals("10.10.7", result.connection.server.serverVersion)
        val loginHeader = login.headers.first("Authorization")!!
        assertTrue(loginHeader.contains("Client=\"MaFei\""))
        assertTrue(loginHeader.contains("Device=\"MaFei\""))
        assertFalse(login.headers.first("Authorization")!!.contains("Token="))
        assertEquals(SecureSecret.AuthToken("MediaBrowser", "fixture-token"), result.binding.source.current().secrets[result.connection.tokenAlias])
        result.binding.adapter.collections(PageRequest())
        val authenticatedHeader = transport.requests.last().headers.first("Authorization")!!
        assertTrue(authenticatedHeader.contains("Client=\"MaFei\""))
        assertTrue(authenticatedHeader.contains("Device=\"MaFei\""))
        assertTrue(authenticatedHeader.contains("Token=\"fixture-token\""))
        assertTrue(authenticatedHeader.contains("DeviceId=\"${result.connection.credentialKey}\""))
        assertTrue(login.headers.first("Authorization")!!.contains("DeviceId=\"${result.connection.credentialKey}\""))
        result.binding.clear()
        assertEquals(0, transport.closes)
    }

    @Test fun jellyfinLoginAcceptsBareLanAddressAndEmptyPassword() = runBlocking<Unit> {
        val requests = mutableListOf<HttpRequest>()
        val transport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                requests += request
                if (request.url.encodedPath.endsWith("/System/Info/Public") && request.url.isHttps) {
                    throw HttpTransportException(request.url, TransportFailureKind.Tls, "fixture TLS mismatch")
                }
                val body = when {
                    request.url.encodedPath.endsWith("/System/Info/Public") ->
                        """{"ProductName":"Jellyfin Server","ServerName":"Living Room","Version":"10.10.7","Id":"server-id"}"""
                    request.url.encodedPath.endsWith("/Users/AuthenticateByName") ->
                        """{"User":{"Id":"remote-user","Name":"Demo"},"AccessToken":"fixture-token"}"""
                    else -> error("Unexpected request")
                }
                return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
            }

            override fun close() = Unit
        }

        val result = ProviderLogin(transport).authenticate(
            ProviderKind.Jellyfin,
            "192.168.31.96:8096",
            "xiaobai",
            "",
        )

        assertEquals(listOf("https", "http", "http"), requests.map { it.url.scheme })
        val request = requests.last()
        assertEquals("http", request.url.scheme)
        assertEquals("192.168.31.96", request.url.host)
        assertEquals(8096, request.url.port)
        assertEquals("Living Room", result.connection.server.name)
        assertEquals("""{"Username":"xiaobai","Pw":""}""", (request.body as HttpBody.Text).text)
        result.binding.clear()
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
        assertEquals("http://media.test:5667/base", saved.feiniuOriginalAddress)
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
            val invalid = listOf(
                "ftp://media.test",
                "https://user:secret@media.test",
                "https://media.test?token=secret",
                "https://media.test#secret",
            ) + if (provider == ProviderKind.Jellyfin) listOf("fnid-only") else emptyList()
            for (url in invalid) {
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

    @Test fun reloginKeepsSavedIdentityAndPresentsItsNamespaceAsTheDeviceId() = runBlocking<Unit> {
        val typed = "https://media.test"
        val existing = SavedConnection(
            server = Server(ServerId("jellyfin-server-abcd"), ProviderKind.Jellyfin, "media.test", typed),
            account = Account(AccountId("jellyfin-server-abcd-account-remote-user"),
                ServerId("jellyfin-server-abcd"), ProviderKind.Jellyfin, "Demo"),
            remoteUserId = "remote-user",
            credentialKey = "0123456789abcdef0123456789abcdef",
        )
        val lookups = mutableListOf<String>()
        val transport = Transport(
            Response("""{"User":{"Id":"remote-user","Name":"Demo"},"AccessToken":"fixture-token","ServerId":"abcd"}"""),
            Response("""{"ServerName":"Living Room","Version":"10.10.7","Id":"abcd"}"""),
            Response("""{"Items":[],"TotalRecordCount":0}"""),
        )
        val login = ProviderLogin(transport) { provider, address, _, _, remoteUserId, username ->
            lookups += "${provider.name}|$address|${remoteUserId ?: "typed"}|$username"
            existing.takeIf {
                provider == ProviderKind.Jellyfin &&
                    (remoteUserId == existing.remoteUserId || (remoteUserId == null && username == "Demo"))
            }
        }
        val result = login.authenticate(ProviderKind.Jellyfin, typed, "Demo", "pw")
        // The device identity has to be known BEFORE the request, and the
        // authoritative lookup afterwards must agree with that first answer. Both
        // see the normalized address (a scheme's default port is made explicit).
        assertEquals(listOf(
            "Jellyfin|https://media.test:443|typed|Demo",
            "Jellyfin|https://media.test:443|remote-user|Demo",
        ), lookups)
        assertEquals(existing.credentialKey, result.connection.credentialKey)
        assertEquals(existing.deviceId, result.connection.deviceId)
        assertEquals(existing.server.id, result.connection.server.id)
        assertEquals(existing.account.id, result.connection.account.id)
        assertEquals("remote-user", result.connection.remoteUserId)
        val loginHeader = transport.requests.first().headers.first("Authorization")!!
        assertTrue(loginHeader.contains("DeviceId=\"${existing.deviceId}\""))
        result.binding.adapter.collections(PageRequest())
        // The admitted session keeps presenting the very same device.
        assertTrue(transport.requests.last().headers.first("Authorization")!!.contains("DeviceId=\"${result.connection.deviceId}\""))
        result.binding.clear()
        assertEquals(0, transport.closes)
    }

    /**
     * The same typed user name can authenticate as a different remote account (a
     * rebuilt account, or any provider that accepts an alias). That account must
     * not be forced into the saved row's namespace, while the token it just got
     * still belongs to the device that requested it.
     */
    @Test fun aLoginThatLandsOnAnotherRemoteUserTakesAFreshNamespaceButKeepsTheDevice() = runBlocking<Unit> {
        val knownDevice = "0123456789abcdef0123456789abcdef"
        val existing = SavedConnection(
            server = Server(ServerId("jellyfin-server-abcd"), ProviderKind.Jellyfin, "media.test", "https://media.test"),
            account = Account(AccountId("jellyfin-server-abcd-account-remote-user"),
                ServerId("jellyfin-server-abcd"), ProviderKind.Jellyfin, "Demo"),
            remoteUserId = "remote-user",
            credentialKey = knownDevice,
        )
        val transport = Transport(
            Response("""{"User":{"Id":"other-user","Name":"Demo"},"AccessToken":"fixture-token","ServerId":"abcd"}"""),
            Response("""{"ServerName":"Living Room","Version":"10.10.7","Id":"abcd"}"""),
        )
        val login = ProviderLogin(transport) { provider, _, _, _, remoteUserId, username ->
            existing.takeIf {
                provider == ProviderKind.Jellyfin &&
                    (remoteUserId == existing.remoteUserId || (remoteUserId == null && username == "Demo"))
            }
        }
        val result = login.authenticate(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")
        assertNotEquals(knownDevice, result.connection.credentialKey)
        assertEquals(knownDevice, result.connection.deviceId)
        assertEquals("other-user", result.connection.remoteUserId)
        assertNotEquals(existing.selection, result.connection.selection)
        // The borrowed device is what the request and the session both present.
        assertTrue(transport.requests.first().headers.first("Authorization")!!.contains("DeviceId=\"$knownDevice\""))
        result.binding.clear()
    }

    @Test fun firstLoginDerivesTheSameIdsForThatServerEveryTime() = runBlocking<Unit> {
        val loginBody = """{"User":{"Id":"remote-user","Name":"Demo"},"AccessToken":"fixture-token","ServerId":"abcd"}"""
        val infoBody = """{"ServerName":"Living Room","Version":"10.10.7","Id":"abcd"}"""
        val transport = Transport(Response(loginBody), Response(infoBody), Response(loginBody), Response(infoBody))
        // No hook wired: the identity still has to be the server's own, so that a
        // caller which forgets the reuse lookup still cannot fork the account.
        val first = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test/", "Demo", "pw")
        val second = ProviderLogin(transport).authenticate(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")
        assertEquals(ServerId("jellyfin-server-abcd"), first.connection.server.id)
        assertEquals(AccountId("jellyfin-server-abcd-account-remote-user"), first.connection.account.id)
        assertEquals(first.connection.selection, second.connection.selection)
        first.binding.clear()
        second.binding.clear()
        assertEquals(0, transport.closes)
    }

    @Test fun feiniuReloginRebuildsTheSavedRecordVerbatimIncludingRoutes() = runBlocking<Unit> {
        val existing = SavedConnection(
            server = Server(ServerId("feiniu-server-media"), ProviderKind.Feiniu, "media.test", "http://media.test:5667/base"),
            account = Account(AccountId("feiniu-server-media-account-user-7"),
                ServerId("feiniu-server-media"), ProviderKind.Feiniu, "demo", displayName = "Cinema"),
            remoteUserId = "user-7",
            credentialKey = "fedcba9876543210fedcba9876543210",
            feiniuRoutes = listOf(SavedFeiniuRoute("http://media.test:5667/base", "Direct")),
        )
        val transport = Transport(
            Response("""{"code":0,"data":{"token":"fixture-token"}}""", cookie = "session=login; Path=/"),
            Response("""{"code":0,"data":{"user_id":"user-7","username":"demo","nickname":"Cinema"}}""", cookie = "session=rotated; Path=/"),
        )
        val login = ProviderLogin(transport) { provider, _, _, _, remoteUserId, _ ->
            existing.takeIf { provider == ProviderKind.Feiniu && remoteUserId == "user-7" }
        }
        val result = login.authenticate(ProviderKind.Feiniu, "http://media.test:5667/base", " demo ", " pw ")
        assertEquals(existing.credentialKey, result.connection.credentialKey)
        assertEquals(existing.selection, result.connection.selection)
        assertEquals(existing.feiniuRoutes, result.connection.feiniuRoutes)
        assertEquals("http://media.test:5667/base", result.connection.feiniuOriginalAddress)
        result.binding.clear()
        assertEquals(0, transport.closes)
    }

    @Test fun feiniuBareHostGetsLegacyDefaultPort() = runBlocking<Unit> {
        val transport = Transport(
            Response("""{"code":0,"data":{"token":"fixture-token"}}"""),
            Response("""{"code":0,"data":{"user_id":"user-7","username":"demo"}}"""),
        )

        val result = ProviderLogin(transport).authenticate(
            ProviderKind.Feiniu,
            "192.168.31.20",
            "demo",
            "pw",
        )

        assertEquals("http://192.168.31.20/", result.connection.server.baseUrl)
        assertEquals("192.168.31.20", result.connection.feiniuOriginalAddress)
        assertEquals(
            listOf(
                "http://192.168.31.20/",
                "https://192.168.31.20/",
                "http://192.168.31.20:5666/",
                "https://192.168.31.20:5667/",
            ),
            result.connection.feiniuRoutes.map { it.baseUrl },
        )
        result.binding.clear()
    }

    @Test fun feiniuFnIdResolvesAndPersistsAllRoutesInPreferenceOrder() = runBlocking<Unit> {
        val transport = Transport(
            Response(
                """{"code":0,"data":{"ipv4":["192.168.31.20"],"publicIpv4":["203.0.113.8"],"fn":["fnid-only.5ddd.com"],"port":{"httpPort":5666,"httpsPort":5667}}}""",
            ),
            Response("{}"),
            Response("{}"),
            Response("{}"),
            Response("{}"),
            Response("""{"code":0,"data":{"token":"fixture-token"}}"""),
            Response("""{"code":0,"data":{"user_id":"user-7","username":"demo"}}"""),
        )

        val result = ProviderLogin(transport).authenticate(
            ProviderKind.Feiniu,
            "fnid-only",
            "demo",
            "pw",
            FeiniuRoutePreference.Public,
        )

        assertEquals("fnid-only", result.connection.feiniuOriginalAddress)
        assertEquals("Public", result.connection.feiniuRoutes.first().route)
        assertEquals(result.connection.feiniuRoutes.first().baseUrl, result.connection.server.baseUrl)
        assertEquals(setOf("Local", "Public", "Relay"), result.connection.feiniuRoutes.map { it.route }.toSet())
        assertEquals("/api/v1/fn/con", transport.requests.first().url.encodedPath)
        assertEquals(4, transport.requests.count { it.url.encodedPath.endsWith("/license/v1/device/baseInfo") })
        result.binding.clear()
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
