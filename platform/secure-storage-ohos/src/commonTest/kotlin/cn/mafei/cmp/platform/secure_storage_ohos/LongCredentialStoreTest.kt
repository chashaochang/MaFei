package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import kotlin.test.*

/** Transaction/format tests only; fake authentication is NOT a cryptographic implementation. */
class LongCredentialStoreTest {
    private val namespace = "cn.mafei.longtests"
    private val alias = CredentialAlias("account/token-cookie")
    private val long = SecureSecret.CookieText("synthetic=" + "x".repeat(16000))
    private fun store(asset: FakeAsset, vault: FakeVault) = OhosAssetCredentialStore(namespace, asset, Dispatchers.Default, vault)

    @Test fun longValuesRoundTripWithoutPlaintextFilesOrAssetOverflow() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        for (secret in listOf(long, SecureSecret.AuthToken("Bearer", "t".repeat(32000)),
            SecureSecret.RawBytes(ByteArray(64000) { 42 }))) {
            store.save(alias, secret)
            assertEquals(secret, store.read(alias))
            assertTrue(store.exists(alias))
            assertEquals(1, vault.files.size)
            assertEquals(64, asset.values.values.single().size)
            assertEquals(listOf(alias), store.listAliases())
        }
        assertTrue(asset.lastWrite!!.all { it == 0.toByte() })
        assertTrue(vault.lastPlaintext!!.all { it == 0.toByte() })
        assertTrue(vault.lastKey!!.all { it == 0.toByte() })
    }

    @Test fun readsOldShortAssetAndHandlesBothLengthTransitions() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault()
        val old = SecureSecret.Password("old-short")
        val key = AssetKeys(namespace).encode(alias).toList()
        asset.values[key] = SecureSecretCodec.encode(old)
        val store = store(asset, vault)
        assertEquals(old, store.read(alias))
        store.save(alias, long); assertEquals(long, store.read(alias)); assertEquals(1, vault.files.size)
        store.save(alias, old); assertEquals(old, store.read(alias)); assertTrue(vault.files.isEmpty())
        assertEquals("MFS1", asset.values.getValue(key).copyOfRange(0, 4).decodeToString())
    }

    @Test fun forbiddenOverwriteNeitherEncryptsNorTouchesOldShortOrLong() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        for (existing in listOf(SecureSecret.Password("short"), long)) {
            store.save(alias, existing)
            val calls = vault.encryptions
            for (replacement in listOf(SecureSecret.Password("new"), long)) {
                assertEquals(CredentialFailureKind.AlreadyExists, assertFailsWith<CredentialAccessException> {
                    store.save(alias, replacement, false)
                }.kind)
                assertEquals(existing, store.read(alias))
                assertEquals(calls, vault.encryptions)
            }
        }
    }

    @Test fun encryptionAndFileFailuresPreserveCommittedCredential() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long)
        val before = asset.values.values.single().copyOf()
        vault.failEncrypt = true
        assertFailsWith<CredentialAccessException> { store.save(alias, long) }
        vault.failEncrypt = false; vault.failWrite = true
        assertFailsWith<CredentialAccessException> { store.save(alias, long) }
        vault.failWrite = false
        assertContentEquals(before, asset.values.values.single())
        assertEquals(long, store.read(alias)); assertEquals(1, vault.files.size)
    }

    @Test fun assetCommitFailureRemovesOnlyUncommittedCiphertext() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long)
        asset.updateStatuses += 24000007
        assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.CookieText("new".repeat(10000))) }
        assertEquals(long, store.read(alias)); assertEquals(1, vault.files.size)
    }

    @Test fun fileIdCollisionNeverDeletesExistingCiphertext() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long); vault.repeatRandom = true
        assertFailsWith<CredentialAccessException> { store.save(alias, long) }
        assertEquals(long, store.read(alias)); assertEquals(1, vault.files.size)
    }

    @Test fun cancellationAfterFileWriteDoesNotInstallItsReference() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long)
        val job = Job()
        vault.afterWrite = { job.cancel() }
        assertFailsWith<CancellationException> { withContext(job) { store.save(alias, long) } }
        vault.afterWrite = {}
        assertEquals(long, store.read(alias)); assertEquals(1, vault.files.size)
    }

    @Test fun cleanupFailureAfterCommitIsExplicitButNewCredentialRemainsReadable() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long); vault.failDelete = true
        val replacement = SecureSecret.Password("committed-short")
        assertFailsWith<CredentialAccessException> { store.save(alias, replacement) }
        assertEquals(replacement, store.read(alias))
        vault.failDelete = false
    }

    @Test fun missingAndModifiedCiphertextAreErrorsNotAbsence() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long)
        val id = vault.files.keys.single(); val bytes = vault.files.getValue(id)
        vault.files[id] = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFailsWith<CredentialAccessException> { store.read(alias) }
        assertFailsWith<CredentialAccessException> { store.exists(alias) }
        vault.files.remove(id)
        assertFailsWith<CredentialAccessException> { store.read(alias) }
        assertTrue(store.delete(alias)) // Known-alias removal recovers a missing/corrupt ciphertext.
    }

    @Test fun changedKeyNonceAliasOrNamespaceFailsAuthentication() = runBlocking<Unit> {
        for (offset in listOf(20, 52)) {
            val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
            store.save(alias, long)
            asset.values.values.single()[offset] = 0x7f
            assertFailsWith<CredentialAccessException> { store.read(alias) }
        }
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long)
        val marker = asset.values.values.single().copyOf()
        val another = CredentialAlias("account/other")
        asset.values[AssetKeys(namespace).encode(another).toList()] = marker.copyOf()
        assertFailsWith<CredentialAccessException> { store.read(another) }
        asset.values[AssetKeys("other.namespace").encode(alias).toList()] = marker.copyOf()
        val other = OhosAssetCredentialStore("other.namespace", asset, Dispatchers.Default, vault)
        assertFailsWith<CredentialAccessException> { other.read(alias) }
    }

    @Test fun malformedReferenceIsExplicitUntilKnownAliasDeletion() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        asset.values[AssetKeys(namespace).encode(alias).toList()] = LongCredentialReference.MAGIC + ByteArray(3)
        assertFailsWith<CredentialAccessException> { store.read(alias) }
        assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.Password("replace")) }
        assertTrue(store.delete(alias)); assertNull(store.read(alias))
    }

    @Test fun deleteAndBulkDeleteReleaseReferencedFiles() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        store.save(alias, long)
        store.save(CredentialAlias("account/second"), long)
        store.save(CredentialAlias("keep/one"), long)
        assertEquals(2, store.deleteAll("account/"))
        assertEquals(1, vault.files.size)
        assertTrue(store.delete(CredentialAlias("keep/one")))
        assertTrue(vault.files.isEmpty()); assertFalse(store.delete(alias))
    }

    @Test fun upperCodecBoundIsKeptAndOversizeNeverMutates() = runBlocking {
        val asset = FakeAsset(); val vault = FakeVault(); val store = store(asset, vault)
        val exact = SecureSecret.RawBytes(ByteArray(SecureSecretCodec.MAX_ENCODED_BYTES - 9) { 7 })
        store.save(alias, exact); assertEquals(exact, store.read(alias))
        val calls = vault.encryptions
        assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.RawBytes(ByteArray(SecureSecretCodec.MAX_ENCODED_BYTES))) }
        assertEquals(calls, vault.encryptions); assertEquals(exact, store.read(alias))
    }
}

internal class FakeVault : LongCredentialVault {
    val files = linkedMapOf<String, ByteArray>()
    private data class Fixture(val key: ByteArray, val nonce: ByteArray, val aad: ByteArray, val plain: ByteArray)
    private val fixtures = mutableMapOf<List<Byte>, Fixture>()
    private var sequence = 0
    private var randomSequence = 0
    var repeatRandom = false
    var failEncrypt = false
    var failWrite = false
    var failDelete = false
    var afterWrite: () -> Unit = {}
    var encryptions = 0
    var lastPlaintext: ByteArray? = null
    var lastKey: ByteArray? = null
    override fun <T> locked(block: () -> T): T = block()
    override fun random(size: Int): ByteArray {
        if (!repeatRandom) randomSequence++
        return ByteArray(size) { (it + randomSequence).toByte() }
    }
    override fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        encryptions++; lastPlaintext = plaintext; lastKey = key
        if (failEncrypt) invalidAssetResult()
        val cipher = ByteArray(plaintext.size + 16) { 0x55 }.also { it[0] = (++sequence).toByte() }
        fixtures[cipher.toList()] = Fixture(key.copyOf(), nonce.copyOf(), aad.copyOf(), plaintext.copyOf())
        return cipher
    }
    override fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val record = fixtures[ciphertext.toList()] ?: invalidAssetResult()
        if (!record.key.contentEquals(key) || !record.nonce.contentEquals(nonce) || !record.aad.contentEquals(aad)) invalidAssetResult()
        return record.plain.copyOf()
    }
    override fun writeNew(id: String, ciphertext: ByteArray) {
        if (failWrite || files.containsKey(id)) invalidAssetResult()
        files[id] = ciphertext.copyOf()
        afterWrite()
    }
    override fun read(id: String): ByteArray = files[id]?.copyOf() ?: invalidAssetResult()
    override fun delete(id: String) { if (failDelete) invalidAssetResult(); files.remove(id) }
}
