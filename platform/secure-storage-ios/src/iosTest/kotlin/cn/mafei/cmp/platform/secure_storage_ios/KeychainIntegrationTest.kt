package cn.mafei.cmp.platform.secure_storage_ios

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.*
import platform.Foundation.NSUUID
import kotlin.test.*

/**
 * Real Security.framework tests. Creates only unique cn.mafei.keychain-tests.*
 * services in the test device's Keychain; finally removes only those entries.
 * Requires an entitled test host. A missing entitlement is a failure, not a skip.
 */
class KeychainIntegrationTest {
    private fun service() = "cn.mafei.keychain-tests." + NSUUID().UUIDString

    @Test fun realKeychainPersistsAllTypesAcrossStoreInstances() = runBlocking {
        val service = service()
        val store = IosKeychainCredentialStore(service)
        val alias = CredentialAlias("account/中文/\uD83C\uDFAC/token")
        try {
            assertNull(store.read(alias))
            for (secret in listOf(SecureSecret.AuthToken("Bearer", "synthetic"),
                SecureSecret.Password("synthetic"), SecureSecret.CookieText("synthetic=cookie"),
                SecureSecret.RawBytes(byteArrayOf(0, -1, 42)))) {
                store.save(alias, secret)
                assertEquals(secret, IosKeychainCredentialStore(service).read(alias))
                assertTrue(store.exists(alias))
            }
            assertEquals(listOf(alias), store.listAliases())
            assertTrue(store.delete(alias))
            assertFalse(store.delete(alias))
        } finally { store.deleteAll() }
    }

    @Test fun realKeychainNamespaceAndPrefixDeletionAreIsolated() = runBlocking {
        val left = IosKeychainCredentialStore(service())
        val right = IosKeychainCredentialStore(service())
        val alias = CredentialAlias("a/token")
        try {
            left.save(alias, SecureSecret.Password("left"))
            left.save(CredentialAlias("aa/token"), SecureSecret.Password("left-other"))
            right.save(alias, SecureSecret.Password("right"))
            assertEquals(1, left.deleteAll("a/"))
            assertEquals(listOf("aa/token"), left.listAliases().map { it.value })
            assertEquals(SecureSecret.Password("right"), right.read(alias))
        } finally { left.deleteAll(); right.deleteAll() }
    }

    @Test fun realKeychainConcurrentNoOverwriteHasOneWinner() = runBlocking {
        val service = service()
        val store = IosKeychainCredentialStore(service)
        val alias = CredentialAlias("concurrent/token")
        try {
            val results = (0 until 8).map { index -> async(Dispatchers.Default) {
                try {
                    IosKeychainCredentialStore(service).save(alias, SecureSecret.Password("synthetic-$index"), false)
                    true
                } catch (failure: CredentialAccessException) {
                    assertEquals(CredentialFailureKind.AlreadyExists, failure.kind)
                    false
                }
            } }.awaitAll()
            assertEquals(1, results.count { it })
            assertTrue(store.exists(alias))
        } finally { store.deleteAll() }
    }

    @Test fun realKeychainCorruptRecordIsNotMissing() = runBlocking {
        val service = service()
        val store = IosKeychainCredentialStore(service)
        val alias = CredentialAlias("corrupt/token")
        try {
            assertEquals(0, SecurityKeychainBackend(service).add(alias.value, ByteArray(9) { 0x55 }))
            assertTrue(store.exists(alias))
            val failure = assertFailsWith<CredentialAccessException> { store.read(alias) }
            assertEquals(CredentialFailureKind.StorageFailure, failure.kind)
        } finally { store.deleteAll() }
    }
}
