package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private class HistoryDocument : SettingsDocumentBackend {
    var bytes: ByteArray? = null
    override fun read() = bytes?.copyOf()
    override fun replace(document: ByteArray) { bytes = document.copyOf() }
}

private class HistorySecrets : SecureCredentialStore {
    val values = mutableMapOf<CredentialAlias, SecureSecret>()
    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { values[alias] = secret }
    override suspend fun read(alias: CredentialAlias) = values[alias]
    override suspend fun exists(alias: CredentialAlias) = alias in values
    override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
    override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
    override suspend fun deleteAll(prefix: String?) = error("unscoped deletion")
}

/** Answers every Jellyfin call a login plus a session admission needs. */
private class JellyfinTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val body = when {
            request.url.encodedPath.endsWith("/Users/AuthenticateByName") ->
                """{"User":{"Id":"remote-user","Name":"Demo"},"AccessToken":"fixture-token","ServerId":"abcd"}"""
            request.url.encodedPath.endsWith("/System/Info") ->
                """{"ServerName":"Living Room","Version":"10.10.7","Id":"abcd"}"""
            else -> """{"Items":[],"TotalRecordCount":0}"""
        }
        return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
    }
    override fun close() = Unit
}

/**
 * The account history is one row per remote account. Logging in again used to
 * append a row per login because every login minted a new local identity.
 */
class ApplicationRuntimeAccountHistoryTest {
    private val settings = DocumentSettingsStorage(HistoryDocument())
    private val secrets = HistorySecrets()
    private val transport = JellyfinTransport()
    private val app = ApplicationRuntime(settings, secrets, transport, Dispatchers.Unconfined)

    @AfterTest fun tearDown() = runBlocking { app.close() }

    /** DeviceId of every request that carried one, in order. */
    private fun deviceIds(): List<String> = transport.requests.mapNotNull { request ->
        request.headers.first("Authorization")
            ?.let { Regex("""DeviceId="([^"]+)"""").find(it)?.groupValues?.get(1) }
    }

    @Test fun repeatedLoginsToTheSameAccountKeepASingleHistoryRow() = runBlocking<Unit> {
        val first = app.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")
        val second = app.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")

        val history = app.savedConnections()
        assertEquals(1, history.size)
        assertEquals(first.selection, second.selection)
        assertEquals(first.connection.credentialKey, second.connection.credentialKey)
        assertEquals(ServerId("jellyfin-server-abcd"), history.single().server.id)
        // One device on the server, not one per login: the second request renewed
        // the namespace the first login established.
        // Each login now also reads authenticated system info so the stored
        // server title/version come from Jellyfin instead of the typed host.
        assertEquals(4, deviceIds().size)
        assertEquals(setOf(first.connection.credentialKey), deviceIds().toSet())
        assertEquals("Living Room", history.single().server.name)
    }

    @Test fun bothAccountListsReceiveOnlyKeysOfRememberedPasswordsIncludingEmptyPasswords() = runBlocking<Unit> {
        val withPassword = legacyRecord("s1", "a1", "a".repeat(32), null)
        val emptyPassword = legacyRecord("s2", "a2", "b".repeat(32), null)
        val noPassword = legacyRecord("s3", "a3", "c".repeat(32), null)
        val wrongSecret = legacyRecord("s4", "a4", "d".repeat(32), null)
        secrets.save(withPassword.passwordAlias, SecureSecret.Password("private"))
        secrets.save(emptyPassword.passwordAlias, SecureSecret.Password(""))
        secrets.save(wrongSecret.passwordAlias, SecureSecret.AuthToken("fixture", "token"))

        assertEquals(
            setOf(withPassword.credentialKey, emptyPassword.credentialKey),
            app.savedPasswordKeys(listOf(withPassword, emptyPassword, noPassword, wrongSecret)),
        )
    }

    @Test fun anotherAddressOfTheSameJellyfinServerReusesTheSavedAccount() = runBlocking<Unit> {
        val first = app.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")
        val second = app.connect(ProviderKind.Jellyfin, "https://lan.media.test:8920", "Demo", "pw")

        val history = app.savedConnections()
        assertEquals(1, history.size)
        assertEquals(first.selection, second.selection)
        assertEquals(first.connection.credentialKey, second.connection.credentialKey)
        assertEquals("https://lan.media.test:8920/", history.single().server.baseUrl)
        assertEquals("Living Room", history.single().server.name)
    }

    @Test fun loggingInAgainCollapsesTheDuplicateRowsEarlierLoginsLeftBehind() = runBlocking<Unit> {
        val catalog = ConnectionCatalog(settings)
        // Exactly what the old per-login random identity produced: same account,
        // same address, three different namespaces and three history rows.
        val unchanged = legacyRecord("server-a", "account-a", "a".repeat(32), lastActiveEpochMs = null)
        val kept = legacyRecord("server-b", "account-b", "b".repeat(32), lastActiveEpochMs = 1_000L)
        catalog.save(unchanged)
        catalog.save(kept)
        secrets.save(unchanged.tokenAlias, SecureSecret.AuthToken("MediaBrowser", "stale-token"), overwrite = true)
        secrets.save(unchanged.passwordAlias, SecureSecret.Password("stale-password"), overwrite = true)
        assertEquals(2, app.savedConnections().size)

        val session = app.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")

        val history = app.savedConnections()
        assertEquals(1, history.size)
        // The most recently used row is the account, so it survives with a fresh token.
        assertEquals(kept.selection, history.single().selection)
        assertEquals(kept.credentialKey, session.connection.credentialKey)
        assertEquals(SecureSecret.AuthToken("MediaBrowser", "fixture-token"), secrets.read(kept.tokenAlias))
        // The duplicate row and the dead secrets behind it are gone.
        assertNull(secrets.read(unchanged.tokenAlias))
        assertNull(secrets.read(unchanged.passwordAlias))
    }

    /**
     * Records stored before device identity became explicit carry no device
     * field, and records whose device is their namespace must not grow one:
     * identity equals the namespace in both cases.
     */
    @Test fun aStoredRecordWithoutADeviceFieldStillRestoresItsDevice() {
        val connection = legacyRecord("server-a", "account-a", "a".repeat(32), lastActiveEpochMs = null)
        val encoded = Json.encodeToString(connection)
        assertFalse(encoded.contains("deviceId"))
        assertEquals(connection, Json.decodeFromString<SavedConnection>(encoded))
        assertEquals(connection.credentialKey, connection.deviceId)
    }

    private fun legacyRecord(
        serverId: String,
        accountId: String,
        credentialKey: String,
        lastActiveEpochMs: Long?,
    ): SavedConnection {
        val server = Server(ServerId(serverId), ProviderKind.Jellyfin, "media.test", "https://media.test")
        return SavedConnection(
            server = server,
            account = Account(
                AccountId(accountId), server.id, ProviderKind.Jellyfin, "Demo",
                lastActiveEpochMs = lastActiveEpochMs,
            ),
            remoteUserId = "remote-user",
            credentialKey = credentialKey,
        )
    }
}
