package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ApplicationRuntimePlexTest {
    private class Document : SettingsDocumentBackend {
        var bytes: ByteArray? = null
        override fun read() = bytes?.copyOf()
        override fun replace(document: ByteArray) { bytes = document.copyOf() }
    }
    private class Secrets : SecureCredentialStore {
        val values = mutableMapOf<CredentialAlias, SecureSecret>()
        override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { values[alias] = secret }
        override suspend fun read(alias: CredentialAlias) = values[alias]
        override suspend fun exists(alias: CredentialAlias) = alias in values
        override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
        override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
        override suspend fun deleteAll(prefix: String?) = error("Unscoped deletion")
    }

    @Test fun selectedServerTokenIsTheOnlyPersistedSecretAndRestartRestoresWithoutCloudRequests() = runBlocking<Unit> {
        val document = Document()
        val storage = DocumentSettingsStorage(document)
        val secrets = Secrets()
        val transport = PlexTestTransport()
        val app = ApplicationRuntime(storage, secrets, transport, Dispatchers.Unconfined)
        val session = try {
            val challenge = app.beginPlexLogin()
            val servers = app.awaitPlexServers(challenge)
            assertEquals(listOf("server-1"), servers.choices.map { it.id })
            app.connectPlex(servers, "server-1").also { active ->
                assertEquals(ProviderKind.Plex, active.selection.provider)
                assertEquals(mapOf<CredentialAlias, SecureSecret>(active.connection.tokenAlias to SecureSecret.AuthToken("Plex", "server-fixture")), secrets.values)
                assertEquals(1, active.binding.adapter.collections(PageRequest()).items.size)
                assertNull(app.savedPassword(active.connection))
                for (secret in listOf("cloud-fixture", "server-fixture", "pin-fixture")) {
                    assertFalse(assertNotNull(document.bytes).decodeToString().contains(secret))
                }
            }
        } finally { app.close() }
        val restarted = ApplicationRuntime(storage, secrets, transport, Dispatchers.Unconfined)
        transport.requests.clear()
        try {
            val restored = assertNotNull(restarted.sessions.start())
            assertEquals(session.selection, restored.selection)
            assertTrue(transport.requests.isEmpty())
            restored.binding.adapter.collections(PageRequest())
            val request = transport.requests.single()
            assertEquals("media.test", request.url.host)
            assertEquals("server-fixture", request.headers.first("X-Plex-Token"))
            assertEquals(session.connection.deviceId, request.headers.first("X-Plex-Client-Identifier"))
        } finally { restarted.close() }
    }

    @Test fun repeatedPlexAuthorizationAndAnotherServerAddressKeepOneSavedAccount() = runBlocking<Unit> {
        val secrets = Secrets()
        val transport = PlexTestTransport()
        val app = ApplicationRuntime(DocumentSettingsStorage(Document()), secrets, transport, Dispatchers.Unconfined)
        try {
            val first = app.connectPlex(app.awaitPlexServers(app.beginPlexLogin()), "server-1")
            transport.serverHost = "other.media.test"
            transport.serverToken = "renewed-server-fixture"
            val second = app.connectPlex(app.awaitPlexServers(app.beginPlexLogin()), "server-1")
            assertEquals(first.selection, second.selection)
            assertEquals(first.connection.credentialKey, second.connection.credentialKey)
            assertEquals(1, app.savedConnections().size)
            assertEquals("https://other.media.test/plex", app.savedConnections().single().server.baseUrl)
            assertEquals(mapOf<CredentialAlias, SecureSecret>(second.connection.tokenAlias to SecureSecret.AuthToken("Plex", "renewed-server-fixture")), secrets.values)
        } finally { app.close() }
    }

    @Test fun serverIdentityMismatchNeverPersistsCredentialsOrChangesSession() = runBlocking<Unit> {
        val secrets = Secrets()
        val transport = PlexTestTransport()
        val app = ApplicationRuntime(DocumentSettingsStorage(Document()), secrets, transport, Dispatchers.Unconfined)
        try {
            val servers = app.awaitPlexServers(app.beginPlexLogin())
            transport.requests.clear()
            transport.respond = { 200 to """{"MediaContainer":{"machineIdentifier":"other-server"}}""" }
            assertFailsWith<ProtocolDecodingError> { app.connectPlex(servers, "server-1") }
            assertTrue(secrets.values.isEmpty())
            assertTrue(app.savedConnections().isEmpty())
            assertNull(app.sessions.state.value.session)
            assertTrue(transport.requests.all { it.headers.first("X-Plex-Token") == null })
        } finally { app.close() }
    }
}
