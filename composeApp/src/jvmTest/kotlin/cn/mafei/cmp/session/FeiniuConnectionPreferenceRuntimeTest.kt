package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeiniuConnectionPreferenceRuntimeTest {
    private class Document : SettingsDocumentBackend {
        var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun replace(document: ByteArray) {
            bytes = document.copyOf()
        }
    }

    private class Secrets : SecureCredentialStore {
        private val values = mutableMapOf<CredentialAlias, SecureSecret>()

        override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) {
            values[alias] = secret
        }

        override suspend fun read(alias: CredentialAlias): SecureSecret? = values[alias]

        override suspend fun exists(alias: CredentialAlias): Boolean = alias in values

        override suspend fun delete(alias: CredentialAlias): Boolean = values.remove(alias) != null

        override suspend fun listAliases(prefix: String?): List<CredentialAlias> =
            values.keys.filter { prefix == null || it.value.startsWith(prefix) }

        override suspend fun deleteAll(prefix: String?): Int = error("Unscoped deletion is not used")
    }

    private class Transport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            error("A connection preference save without an active Feiniu session must not use the network")
        }

        override fun close() = Unit
    }

    @Test
    fun missingAndUnknownValuesUseLegacySpeedDefault() = runBlocking {
        val settings = DocumentSettingsStorage(Document())
        val transport = Transport()
        val runtime = ApplicationRuntime(settings, Secrets(), transport, Dispatchers.Unconfined)
        try {
            assertEquals(FeiniuRoutePreference.Speed, runtime.loadFeiniuRoutePreference())

            settings.putString(PREFERENCE_KEY, "unknown")

            assertEquals(FeiniuRoutePreference.Speed, runtime.loadFeiniuRoutePreference())
            assertTrue(transport.requests.isEmpty())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun publicAndRelayValuesPersistAcrossRuntimeRecreationWithoutSessionNetwork() = runBlocking {
        val document = Document()
        val settings = DocumentSettingsStorage(document)
        val firstTransport = Transport()
        val first = ApplicationRuntime(settings, Secrets(), firstTransport, Dispatchers.Unconfined)
        try {
            first.saveFeiniuRoutePreference(FeiniuRoutePreference.Public)
            assertEquals(FeiniuRoutePreference.Public, first.loadFeiniuRoutePreference())
            assertTrue(firstTransport.requests.isEmpty())
        } finally {
            first.close()
        }

        val secondTransport = Transport()
        val second = ApplicationRuntime(
            DocumentSettingsStorage(document),
            Secrets(),
            secondTransport,
            Dispatchers.Unconfined,
        )
        try {
            assertEquals(FeiniuRoutePreference.Public, second.loadFeiniuRoutePreference())

            second.saveFeiniuRoutePreference(FeiniuRoutePreference.Relay)

            assertEquals(FeiniuRoutePreference.Relay, second.loadFeiniuRoutePreference())
            assertTrue(secondTransport.requests.isEmpty())
        } finally {
            second.close()
        }
    }

    private companion object {
        const val PREFERENCE_KEY = "pref_feiniu_connection_preference"
    }
}
