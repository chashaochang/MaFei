package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class ProviderSessionsTest {
    private class Transport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var setCookie: String? = null
        var closes = 0
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val text = if (request.url.encodedPath.contains("mediadb")) """{"code":0,"data":[]}""" else """{"Items":[],"TotalRecordCount":0}"""
            val headers = HttpHeaders.Builder().add("Content-Type", "application/json")
            setCookie?.let { headers.add("Set-Cookie", it) }
            return HttpResponse(200, headers.build(), text.encodeToByteArray(), request.url, request.url)
        }
        override fun close() { closes++ }
    }

    private fun connection(provider: ProviderKind = ProviderKind.Jellyfin, key: String = "a".repeat(32), user: String = "remote-a", route: String = "Local"): SavedConnection {
        val server = Server(ServerId("server"), provider, "Fixture", "https://media.test/base")
        return SavedConnection(server, Account(AccountId(user), server.id, provider, user), user, key,
            key,
            if (provider == ProviderKind.Feiniu) listOf(SavedFeiniuRoute(server.baseUrl, route)) else emptyList())
    }

    private fun secrets(connection: SavedConnection, value: String = "fixture-token"): Map<CredentialAlias, SecureSecret> = buildMap {
        put(connection.tokenAlias, SecureSecret.AuthToken(if (connection.server.provider == ProviderKind.Jellyfin) "MediaBrowser" else "Bearer", value))
        if (connection.server.provider == ProviderKind.Feiniu) put(connection.cookieAlias, SecureSecret.CookieText("session=original; Trim-MC-token=$value"))
    }

    @Test fun jellyfinRestoreInstallsTokenAndStableDeviceBeforeFirstRealAdapterRequest() = runBlocking {
        val connection = connection(); val transport = Transport(); val factory = DefaultProviderSessionFactory(transport)
        val first = factory.restore(connection, secrets(connection))
        first.adapter.collections(PageRequest())
        val header = assertNotNull(transport.requests.single().headers.first("Authorization"))
        assertTrue(header.contains("Token=\"fixture-token\""))
        assertTrue(header.contains("DeviceId=\"${connection.credentialKey}\""))
        assertTrue(transport.requests.single().url.toEncodedString().contains("remote-a"))
        val restarted = factory.restore(connection, secrets(connection))
        restarted.adapter.collections(PageRequest())
        assertEquals(header, transport.requests.last().headers.first("Authorization"))
        assertEquals(connection.aliases, first.aliases)
    }

    @Test fun feiniuResponseCookieUpdateProjectsAuthoritativeFlowNotInitialCredentials() = runBlocking {
        val connection = connection(ProviderKind.Feiniu); val transport = Transport()
        val binding = DefaultProviderSessionFactory(transport).restore(connection, secrets(connection))
        val before = binding.source.current()
        transport.setCookie = "session=rotated; Path=/; HttpOnly"
        binding.adapter.collections(PageRequest())
        assertEquals("fixture-token", transport.requests.single().headers.first("Authorization"))
        assertTrue(transport.requests.single().headers.first("Cookie")!!.contains("session=original"))
        val after = binding.source.current()
        assertEquals(before.identityGeneration, after.identityGeneration)
        assertTrue(after.revision > before.revision)
        assertTrue((after.secrets[connection.cookieAlias] as SecureSecret.CookieText).value.contains("session=rotated"))
        transport.setCookie = null
        binding.adapter.collections(PageRequest())
        assertTrue(transport.requests.last().headers.first("Cookie")!!.contains("session=rotated"))
    }

    @Test fun clearInvalidatesBothSourcesAndAdaptersWithoutClosingRootTransport() = runBlocking {
        for (provider in ProviderKind.entries) {
            val connection = connection(provider); val transport = Transport()
            val binding = DefaultProviderSessionFactory(transport).restore(connection, secrets(connection))
            val before = binding.source.current()
            binding.clear()
            val after = binding.source.current()
            assertNotEquals(before.identityGeneration, after.identityGeneration)
            assertTrue(after.secrets.isEmpty())
            assertFailsWith<CancellationException> { binding.adapter.collections(PageRequest()) }
            binding.clear()
            assertEquals(0, transport.closes)
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test fun twoAccountsOwnIndependentSdkInstancesAndCredentialHeaders() = runBlocking {
        for (provider in ProviderKind.entries) {
            val a = connection(provider); val b = connection(provider, "b".repeat(32), "remote-b")
            val transport = Transport(); val factory = DefaultProviderSessionFactory(transport)
            val first = factory.restore(a, secrets(a, "token-a")); val second = factory.restore(b, secrets(b, "token-b"))
            first.adapter.collections(PageRequest()); second.adapter.collections(PageRequest())
            assertTrue(transport.requests[0].headers.first("Authorization")!!.contains("token-a"))
            assertTrue(transport.requests[1].headers.first("Authorization")!!.contains("token-b"))
            first.clear()
            second.adapter.collections(PageRequest())
            assertTrue(transport.requests.last().headers.first("Authorization")!!.contains("token-b"))
            assertEquals(b.account.id, second.adapter.accountId)
            assertTrue(a.aliases.intersect(b.aliases).isEmpty())
        }
    }

    @Test fun invalidCredentialTypesSchemesAndForeignAliasesAreRejectedBeforeNetwork() {
        val transport = Transport(); val factory = DefaultProviderSessionFactory(transport)
        for (provider in ProviderKind.entries) {
            val connection = connection(provider)
            for (invalid in listOf(SecureSecret.Password("fixture-password"), SecureSecret.CookieText("cookie=value"), SecureSecret.AuthToken("Basic", "token"), SecureSecret.AuthToken("Bearer", "\r\ntoken"))) {
                val error = assertFailsWith<ClientConfigError> { factory.restore(connection, mapOf(connection.tokenAlias to invalid)) }
                assertFalse(error.toString().contains("fixture-password"))
            }
            assertFailsWith<ClientConfigError> { factory.restore(connection, secrets(connection) + (CredentialAlias("foreign") to SecureSecret.Password("fixture"))) }
            assertFailsWith<ClientConfigError> { factory.restore(connection, emptyMap()) }
        }
        val feiniu = connection(ProviderKind.Feiniu)
        assertFailsWith<ClientConfigError> { factory.restore(feiniu, secrets(feiniu) + (feiniu.cookieAlias to SecureSecret.Password("fixture"))) }
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun feiniuRoutesAreExplicitAndRelayAloneAddsModeCookie() = runBlocking<Unit> {
        for (route in listOf("Local", "Public", "Relay", "Direct")) {
            val connection = connection(ProviderKind.Feiniu, route = route); val transport = Transport()
            DefaultProviderSessionFactory(transport).restore(connection, secrets(connection)).adapter.collections(PageRequest())
            assertEquals(route == "Relay", transport.requests.single().headers.first("Cookie")!!.contains("mode=relay"))
        }
        assertFailsWith<ClientConfigError> { connection(ProviderKind.Feiniu).copy(feiniuRoutes = emptyList()) }
        assertFailsWith<ClientConfigError> { SavedFeiniuRoute("https://media.test", "auto") }
    }

    @Test fun metadataValidationRejectsUnsafeUrlsMismatchedAccountsAndInvalidKeysWithoutEchoingValues() {
        val original = connection()
        for (url in listOf("https://user:fixture-password@media.test", "https://media.test?token=fixture-secret", "https://media.test#fragment", "https://media.test?", "https://media.test\n")) {
            val error = assertFailsWith<ClientConfigError> { original.copy(server = original.server.copy(baseUrl = url)) }
            assertFalse(error.toString().contains("fixture-password")); assertFalse(error.toString().contains("fixture-secret"))
            assertFailsWith<ClientConfigError> { SavedFeiniuRoute(url, "Local") }
        }
        assertFailsWith<ClientConfigError> { original.copy(account = original.account.copy(serverId = ServerId("other"))) }
        assertFailsWith<ClientConfigError> { original.copy(account = original.account.copy(provider = ProviderKind.Feiniu)) }
        assertFailsWith<ClientConfigError> { original.copy(credentialKey = "A".repeat(32)) }
        assertFailsWith<ClientConfigError> { original.copy(remoteUserId = "") }
    }

    @Test fun metadataSerializationAliasesAndToStringContainNoCredentialMaterialOrRawUrls() {
        val original = connection(ProviderKind.Feiniu)
        val encoded = Json.encodeToString(original)
        assertEquals(original, Json.decodeFromString<SavedConnection>(encoded))
        assertFalse(original.toString().contains("https://"))
        assertFalse(original.feiniuRoutes.first().toString().contains("https://"))
        repeat(100) {
            val key = newCredentialKey()
            assertTrue(Regex("[0-9a-f]{32}").matches(key))
            assertTrue(original.copy(credentialKey = key).aliases.all { it.value.encodeToByteArray().size < 256 })
        }
    }

    @Test fun jellyfinReplacementCannotRebindTheSingleInstalledIdentityEvenWhenEqual() {
        val initial = JellyfinSession("remote", "token")
        val state = SingleIdentityJellyfinState(initial)
        assertSame(initial, state.current.value)
        state.set(initial.copy())
        assertNull(state.current.value)
        state.set(initial)
        assertNull(state.current.value)
    }
}
