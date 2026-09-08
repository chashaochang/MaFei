package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.test.*

/**
 * Real AssetStore fixtures, requiring a Harmony test host with application
 * identity. A shell runner may be denied by the service; that is not a pass.
 * Only fresh synthetic namespaces are touched and exact test aliases cleaned.
 */
class AssetStoreNativeIntegrationTest {
    private fun namespace() = "cn.mafei.assettests." + Random.nextBytes(16).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }

    @Test fun realStorePersistsAndKeepsNamespacesSeparate() = runBlocking {
        val namespace = namespace()
        val left = OhosAssetCredentialStore(namespace)
        val right = OhosAssetCredentialStore(namespace())
        val alias = CredentialAlias("synthetic/中文/token")
        try {
            assertNull(left.read(alias))
            left.save(alias, SecureSecret.AuthToken("Synthetic", "fixture"))
            assertEquals(SecureSecret.AuthToken("Synthetic", "fixture"), OhosAssetCredentialStore(namespace).read(alias))
            assertNull(right.read(alias))
            assertEquals(listOf(alias), left.listAliases())
            assertTrue(left.exists(alias))
            assertTrue(left.delete(alias)); assertFalse(left.delete(alias))
        } finally { left.delete(alias); right.delete(alias) }
    }

    @Test fun realStoreConflictAndCapacityPreserveOriginal() = runBlocking {
        val store = OhosAssetCredentialStore(namespace())
        val alias = CredentialAlias("synthetic/capacity")
        try {
            store.save(alias, SecureSecret.RawBytes(ByteArray(1015) { 0x55 }), false)
            assertEquals(CredentialFailureKind.AlreadyExists, assertFailsWith<CredentialAccessException> {
                store.save(alias, SecureSecret.Password("must-not-overwrite"), false)
            }.kind)
            assertEquals(CredentialFailureKind.Unsupported, assertFailsWith<CredentialAccessException> {
                store.save(alias, SecureSecret.RawBytes(ByteArray(1016)))
            }.kind)
            assertEquals(SecureSecret.RawBytes(ByteArray(1015) { 0x55 }), store.read(alias))
        } finally { store.delete(alias) }
    }

    @Test fun realStoreConcurrentAddHasOneWinner() = runBlocking {
        val namespace = namespace()
        val store = OhosAssetCredentialStore(namespace)
        val alias = CredentialAlias("synthetic/concurrent")
        try {
            val results = (0 until 8).map { index -> async(Dispatchers.Default) {
                try { OhosAssetCredentialStore(namespace).save(alias, SecureSecret.Password("synthetic-$index"), false); true }
                catch (failure: CredentialAccessException) { assertEquals(CredentialFailureKind.AlreadyExists, failure.kind); false }
            } }.awaitAll()
            assertEquals(1, results.count { it })
        } finally { store.delete(alias) }
    }
}
