package cn.mafei.cmp.platform.secure_storage_ios

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import platform.Security.*
import kotlin.test.*

private class FakeKeychain : KeychainBackend {
    val values = mutableMapOf<String, ByteArray>()
    var addStatuses = mutableListOf<Int>()
    var updateStatuses = mutableListOf<Int>()
    var status: Int? = null
    var additions = 0
    var lastWrite: ByteArray? = null
    var lastRead: ByteArray? = null
    override fun add(alias: String, encoded: ByteArray): Int {
        additions++
        lastWrite = encoded
        val result = status ?: addStatuses.removeFirstOrNull() ?: if (values.containsKey(alias)) errSecDuplicateItem else errSecSuccess
        if (result == errSecSuccess) values[alias] = encoded.copyOf()
        return result
    }
    override fun update(alias: String, encoded: ByteArray): Int {
        val result = status ?: updateStatuses.removeFirstOrNull() ?: if (values.containsKey(alias)) errSecSuccess else errSecItemNotFound
        if (result == errSecSuccess) values[alias] = encoded.copyOf()
        return result
    }
    override fun read(alias: String) = KeychainRead(status ?: if (values.containsKey(alias)) errSecSuccess else errSecItemNotFound, values[alias]?.copyOf()?.also { lastRead = it })
    override fun delete(alias: String): Int = status ?: if (values.remove(alias) != null) errSecSuccess else errSecItemNotFound
    override fun aliases() = KeychainAliases(status ?: if (values.isEmpty()) errSecItemNotFound else errSecSuccess, values.keys.toList())
}

class IosKeychainCredentialStoreTest {
    private val alias = CredentialAlias("account/a/token")

    @Test fun typedSecretsRoundTripAndMissingAreDistinct() = runBlocking {
        val backend = FakeKeychain(); val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        assertNull(store.read(alias)); assertFalse(store.exists(alias))
        for (secret in listOf(SecureSecret.AuthToken("Bearer", "token"), SecureSecret.Password("密码"), SecureSecret.CookieText("a=b"), SecureSecret.RawBytes(byteArrayOf(0, -1, 1)))) {
            store.save(alias, secret); assertEquals(secret, store.read(alias))
        }
        assertTrue(store.delete(alias)); assertFalse(store.delete(alias))
    }

    @Test fun noOverwriteUsesAtomicAddAndNeverUpdatesTheWinner() = runBlocking {
        val backend = FakeKeychain(); val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        store.save(alias, SecureSecret.Password("winner"), false)
        val failure = assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.Password("loser"), false) }
        assertEquals(CredentialFailureKind.AlreadyExists, failure.kind)
        assertEquals(SecureSecret.Password("winner"), store.read(alias))
    }

    @Test fun overwriteRecoversWhenItemDisappearsBetweenAddAndUpdate() = runBlocking {
        val backend = FakeKeychain().apply { addStatuses += listOf(errSecDuplicateItem, errSecSuccess); updateStatuses += errSecItemNotFound }
        val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        store.save(alias, SecureSecret.Password("value"))
        assertEquals(2, backend.additions); assertEquals(SecureSecret.Password("value"), store.read(alias))
    }

    @Test fun prefixDeletionIsLiteralAndAliasesAreStable() = runBlocking {
        val backend = FakeKeychain(); val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        for (key in listOf("a/z", "a/a", "aa/b", "b/x")) store.save(CredentialAlias(key), SecureSecret.Password("value"))
        assertEquals(listOf("a/a", "a/z"), store.listAliases("a/").map { it.value })
        assertEquals(2, store.deleteAll("a/")); assertEquals(listOf("aa/b", "b/x"), store.listAliases().map { it.value })
    }

    @Test fun lockedAndDeniedAreNotMissing() = runBlocking {
        val backend = FakeKeychain(); val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        for ((status, kind) in listOf(errSecInteractionNotAllowed to CredentialFailureKind.BackendUnavailable, errSecAuthFailed to CredentialFailureKind.AccessDenied)) {
            backend.status = status
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.read(alias) }.kind)
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.exists(alias) }.kind)
        }
    }

    @Test fun corruptBytesFailClosedAndErrorsDoNotEchoTheSecret() = runBlocking {
        val backend = FakeKeychain(); backend.values[alias.value] = "DO-NOT-LOG".encodeToByteArray()
        val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        val failure = assertFailsWith<CredentialAccessException> { store.read(alias) }
        assertEquals(CredentialFailureKind.StorageFailure, failure.kind)
        assertFalse(failure.toString().contains("DO-NOT-LOG"))
    }

    @Test fun cancellationBeforeAdmissionPerformsNoNativeWrite() = runBlocking {
        val backend = FakeKeychain(); val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        val job = Job().apply { cancel() }
        assertFailsWith<CancellationException> { withContext(job) { store.save(alias, SecureSecret.Password("value")) } }
        assertEquals(0, backend.additions)
    }

    @Test fun continuingDeleteContentionFailsExplicitlyAfterBoundedRetries() = runBlocking {
        val backend = FakeKeychain().apply {
            addStatuses.addAll(List(16) { errSecDuplicateItem })
            updateStatuses.addAll(List(16) { errSecItemNotFound })
        }
        val failure = assertFailsWith<CredentialAccessException> {
            IosKeychainCredentialStore(backend, Dispatchers.Default).save(alias, SecureSecret.Password("synthetic"))
        }
        assertEquals(CredentialFailureKind.StorageFailure, failure.kind)
        assertEquals(16, backend.additions)
    }

    @Test fun writeScratchIsClearedAfterBothSuccessAndDuplicate() = runBlocking {
        val backend = FakeKeychain()
        val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        store.save(alias, SecureSecret.Password("synthetic"))
        assertTrue(backend.lastWrite!!.all { it == 0.toByte() })
        assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.Password("another"), false) }
        assertTrue(backend.lastWrite!!.all { it == 0.toByte() })
    }

    @Test fun readScratchIsClearedAndRawSecretKeepsItsOwnCopy() = runBlocking {
        val backend = FakeKeychain().apply {
            values[alias.value] = SecureSecretCodec.encode(SecureSecret.RawBytes(byteArrayOf(1, 2, 3)))
        }
        val secret = IosKeychainCredentialStore(backend, Dispatchers.Default).read(alias) as SecureSecret.RawBytes
        assertTrue(backend.lastRead!!.all { it == 0.toByte() })
        assertContentEquals(byteArrayOf(1, 2, 3), secret.value)
    }

    @Test fun corruptReadScratchIsClearedWhenDecodingFails() = runBlocking {
        val backend = FakeKeychain().apply { values[alias.value] = byteArrayOf(1, 2, 3) }
        assertFailsWith<CredentialAccessException> { IosKeychainCredentialStore(backend, Dispatchers.Default).read(alias) }
        assertTrue(backend.lastRead!!.all { it == 0.toByte() })
    }

    @Test fun malformedStoredAliasFailsClosedWithoutEcho() = runBlocking {
        val backend = FakeKeychain().apply { values[" padded-private-alias "] = byteArrayOf() }
        val failure = assertFailsWith<CredentialAccessException> { IosKeychainCredentialStore(backend, Dispatchers.Default).listAliases() }
        assertEquals(CredentialFailureKind.StorageFailure, failure.kind)
        assertFalse(failure.toString().contains("padded-private-alias"))
    }

    @Test fun invalidPublicAliasIsRejectedBeforeCallingNative() = runBlocking {
        val backend = FakeKeychain()
        val store = IosKeychainCredentialStore(backend, Dispatchers.Default)
        for (value in listOf("account/a\u0000suffix", "a".repeat(1025), "account/\uD800")) {
            assertFailsWith<IllegalArgumentException> { store.save(CredentialAlias(value), SecureSecret.Password("synthetic")) }
        }
        assertEquals(0, backend.additions)
    }
}
