package cn.mafei.cmp.platform.secure_storage_macos

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class MacOSSecureCredentialStoreTest {
    private class FakeKeychain : KeychainBridge {
        val entries = ConcurrentHashMap<Pair<String, String>, ByteArray>()
        var forcedStatus: Int? = null
        var beforeAdd: () -> Unit = {}
        var createDuringUpdate = false
        override fun add(service: String, account: String, data: ByteArray): Int {
            beforeAdd(); forcedStatus?.let { return it }
            return if (entries.putIfAbsent(service to account, data.copyOf()) == null) 0 else KeychainStatus.Duplicate
        }
        override fun update(service: String, account: String, data: ByteArray): Int {
            forcedStatus?.let { return it }
            if (createDuringUpdate) { createDuringUpdate = false; entries[service to account] = byteArrayOf(9); return KeychainStatus.NotFound }
            return if (entries.replace(service to account, data.copyOf()) != null) 0 else KeychainStatus.NotFound
        }
        override fun read(service: String, account: String) = forcedStatus?.let { KeychainRead(it, null) }
            ?: entries[service to account]?.let { KeychainRead(0, it.copyOf()) } ?: KeychainRead(KeychainStatus.NotFound, null)
        override fun aliases(service: String) = forcedStatus?.let { KeychainAliases(it, null) }
            ?: KeychainAliases(0, entries.keys.filter { it.first == service }.map { it.second }.toTypedArray())
        override fun delete(service: String, account: String): Int = forcedStatus ?: if (entries.remove(service to account) == null) KeychainStatus.NotFound else 0
    }
    private val alias = CredentialAlias("account/a/token")
    private val secret = SecureSecret.AuthToken("Bearer", "synthetic-token")

    @Test fun allSecretVariantsRoundTripAndNamespaceIsolationIsExact() = runBlocking {
        val backend = FakeKeychain(); val a = MacOSSecureCredentialStore("test-a", backend); val b = MacOSSecureCredentialStore("test-ab", backend)
        for (value in listOf(secret, SecureSecret.Password("p"), SecureSecret.CookieText("c=值"), SecureSecret.RawBytes(byteArrayOf(0, -1)))) {
            a.save(alias, value); assertEquals(value, a.read(alias)); assertTrue(a.exists(alias))
        }
        b.save(alias, secret); a.deleteAll()
        assertNull(a.read(alias)); assertEquals(secret, b.read(alias)); assertFalse(a.delete(alias))
    }

    @Test fun overwriteFalseAcrossInstancesHasExactlyOneWinner() = runBlocking {
        val backend = FakeKeychain()
        val results = (1..20).map { async(Dispatchers.Default) {
            try { MacOSSecureCredentialStore("test", backend).save(alias, secret, overwrite = false); true }
            catch (error: CredentialAccessException) { assertEquals(CredentialFailureKind.AlreadyExists, error.kind); false }
        } }.awaitAll()
        assertEquals(1, results.count { it })
    }

    @Test fun upsertHandlesConcurrentInsertWithoutDeleteGap() = runBlocking {
        val backend = FakeKeychain().apply { createDuringUpdate = true }
        val store = MacOSSecureCredentialStore("test", backend); store.save(alias, secret)
        assertEquals(secret, store.read(alias))
    }

    @Test fun prefixListingIsStableAndDeleteAllTouchesOnlyMatchingNamespaceAndAliases() = runBlocking {
        val backend = FakeKeychain(); val store = MacOSSecureCredentialStore("test", backend)
        listOf("b", "a/2", "a/1").forEach { store.save(CredentialAlias(it), secret) }
        assertEquals(listOf("a/1", "a/2", "b"), store.listAliases().map { it.value })
        assertEquals(listOf("a/1", "a/2"), store.listAliases("a/").map { it.value })
        assertEquals(2, store.deleteAll("a/")); assertEquals(listOf("b"), store.listAliases().map { it.value })
    }

    @Test fun missingLockedDeniedAndCorruptAreNotConflated() = runBlocking {
        val backend = FakeKeychain(); val store = MacOSSecureCredentialStore("test", backend)
        assertNull(store.read(alias))
        val cases = mapOf(KeychainStatus.InteractionNotAllowed to CredentialFailureKind.BackendUnavailable,
            KeychainStatus.AuthFailed to CredentialFailureKind.AccessDenied,
            KeychainStatus.MissingEntitlement to CredentialFailureKind.AccessDenied,
            KeychainStatus.Decode to CredentialFailureKind.StorageFailure,
            KeychainStatus.Unimplemented to CredentialFailureKind.Unsupported)
        for ((status, kind) in cases) {
            backend.forcedStatus = status
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.read(alias) }.kind)
            assertFailsWith<CredentialAccessException> { store.exists(alias) }
        }
    }

    @Test fun malformedCodecAndSuccessfulNullDataAreStorageFailures() = runBlocking {
        val backend = FakeKeychain(); val store = MacOSSecureCredentialStore("test", backend)
        store.save(alias, secret)
        backend.entries.keys.forEach { backend.entries[it] = byteArrayOf(1, 2) }
        assertEquals(CredentialFailureKind.StorageFailure, assertFailsWith<CredentialAccessException> { store.read(alias) }.kind)
        backend.forcedStatus = 0
        assertFailsWith<CredentialAccessException> { store.read(alias) }
        Unit
    }

    @Test fun queuedCancellationDoesNotCallKeychainAndNativeCallsRunOffCallerThread() = runBlocking {
        withTimeout(5000) {
            val backend = FakeKeychain(); val store = MacOSSecureCredentialStore("test-cancel", backend)
            val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
            val caller = Thread.currentThread(); var nativeThread: Thread? = null
            backend.beforeAdd = { nativeThread = Thread.currentThread(); entered.complete(Unit); check(release.await(4, TimeUnit.SECONDS)) }
            val first = launch { store.save(alias, secret, overwrite = false) }; entered.await()
            val queued = launch(start = CoroutineStart.UNDISPATCHED) { store.save(CredentialAlias("cancelled"), secret) }
            queued.cancelAndJoin(); release.countDown(); first.join()
            assertNotSame(caller, nativeThread); assertEquals(1, backend.entries.size)
        }
    }

    @Test fun inputEncodingIsClearedAfterNativeCall() = runBlocking {
        var received: ByteArray? = null
        val backend = object : KeychainBridge {
            override fun add(service: String, account: String, data: ByteArray): Int { received = data; return 0 }
            override fun update(service: String, account: String, data: ByteArray) = KeychainStatus.NotFound
            override fun read(service: String, account: String) = KeychainRead(KeychainStatus.NotFound, null)
            override fun aliases(service: String) = KeychainAliases(KeychainStatus.NotFound, null)
            override fun delete(service: String, account: String) = KeychainStatus.NotFound
        }
        MacOSSecureCredentialStore("test-clear", backend).save(alias, secret)
        assertTrue(received!!.all { it == 0.toByte() })
    }
}
