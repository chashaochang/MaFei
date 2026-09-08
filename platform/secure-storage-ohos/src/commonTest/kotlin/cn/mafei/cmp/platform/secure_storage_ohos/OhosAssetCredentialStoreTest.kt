package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.coroutines.*
import kotlin.test.*

class OhosAssetCredentialStoreTest {
    private val alias = CredentialAlias("account/a/token")
    private fun store(backend: FakeAsset) = OhosAssetCredentialStore("cn.mafei.test", backend, Dispatchers.Default)

    @Test fun shortSecretsRoundTripAndAbsenceIsDistinct() = runBlocking {
        val store = store(FakeAsset())
        assertNull(store.read(alias)); assertFalse(store.exists(alias)); assertFalse(store.delete(alias))
        for (secret in listOf(SecureSecret.Password("合成"), SecureSecret.AuthToken("Bearer", "synthetic"),
            SecureSecret.CookieText("synthetic=cookie"), SecureSecret.RawBytes(byteArrayOf(0, -1, 42)))) {
            store.save(alias, secret); assertEquals(secret, store.read(alias))
        }
        assertTrue(store.exists(alias)); assertTrue(store.delete(alias))
    }

    @Test fun exactEncodedLimitAcceptedButOversizeRejectedBeforeMutation() = runBlocking {
        val backend = FakeAsset(); val store = store(backend)
        store.save(alias, SecureSecret.RawBytes(ByteArray(1015)))
        val before = backend.additions
        val failure = assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.RawBytes(ByteArray(1016))) }
        assertEquals(CredentialFailureKind.Unsupported, failure.kind)
        assertEquals(before, backend.additions)
        assertEquals(SecureSecret.RawBytes(ByteArray(1015)), store.read(alias))
    }

    @Test fun duplicateNeverOverwritesWhenForbidden() = runBlocking {
        val backend = FakeAsset(); val store = store(backend)
        store.save(alias, SecureSecret.Password("original"), false)
        val failure = assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.Password("new"), false) }
        assertEquals(CredentialFailureKind.AlreadyExists, failure.kind)
        assertEquals(SecureSecret.Password("original"), store.read(alias))
    }

    @Test fun overwriteRetriesAfterCompetingDelete() = runBlocking {
        val backend = FakeAsset().apply { addStatuses += listOf(AssetStatus.DUPLICATED, AssetStatus.SUCCESS); updateStatuses += AssetStatus.NOT_FOUND }
        val store = store(backend)
        store.save(alias, SecureSecret.Password("new"))
        assertEquals(2, backend.additions)
        assertEquals(SecureSecret.Password("new"), store.read(alias))
    }

    @Test fun persistentContentionHasExplicitBoundedFailure() = runBlocking {
        val backend = FakeAsset().apply {
            addStatuses += List(16) { AssetStatus.DUPLICATED }; updateStatuses += List(16) { AssetStatus.NOT_FOUND }
        }
        val failure = assertFailsWith<CredentialAccessException> { store(backend).save(alias, SecureSecret.Password("new")) }
        assertEquals(CredentialFailureKind.StorageFailure, failure.kind); assertEquals(16, backend.additions)
    }

    @Test fun listingTraversesPagesAndSortsLiteralPrefix() = runBlocking {
        val store = store(FakeAsset())
        for (index in 130 downTo 0) store.save(CredentialAlias("a/${index.toString().padStart(3, '0')}"), SecureSecret.Password("test"))
        store.save(CredentialAlias("aa/other"), SecureSecret.Password("test"))
        assertEquals((0..130).map { "a/${it.toString().padStart(3, '0')}" }, store.listAliases("a/").map { it.value })
        assertEquals(131, store.deleteAll("a/"))
        assertEquals(listOf("aa/other"), store.listAliases().map { it.value })
    }

    @Test fun lockedDeniedAndServiceFailureAreNotAbsence() = runBlocking {
        val backend = FakeAsset(); val store = store(backend)
        for ((code, kind) in listOf(24000005 to CredentialFailureKind.BackendUnavailable,
            201 to CredentialFailureKind.AccessDenied, 24000007 to CredentialFailureKind.StorageFailure,
            24000017 to CredentialFailureKind.Unsupported)) {
            backend.failure = code
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.read(alias) }.kind)
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.exists(alias) }.kind)
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.delete(alias) }.kind)
            assertEquals(kind, assertFailsWith<CredentialAccessException> { store.listAliases() }.kind)
        }
    }

    @Test fun corruptionIsNotMissingAndReadScratchIsCleared() = runBlocking {
        val backend = FakeAsset()
        backend.values[AssetKeys("cn.mafei.test").encode(alias).toList()] = byteArrayOf(1, 2, 3)
        val failure = assertFailsWith<CredentialAccessException> { store(backend).read(alias) }
        assertEquals(CredentialFailureKind.StorageFailure, failure.kind)
        assertTrue(backend.lastRead!!.all { it == 0.toByte() })
    }

    @Test fun successAndFailedWriteBothClearScratch() = runBlocking {
        val backend = FakeAsset(); val store = store(backend)
        store.save(alias, SecureSecret.Password("synthetic"))
        assertTrue(backend.lastWrite!!.all { it == 0.toByte() })
        assertFailsWith<CredentialAccessException> { store.save(alias, SecureSecret.Password("other"), false) }
        assertTrue(backend.lastWrite!!.all { it == 0.toByte() })
    }

    @Test fun rawSecretSurvivesReadScratchErasure() = runBlocking {
        val backend = FakeAsset(); val store = store(backend)
        store.save(alias, SecureSecret.RawBytes(byteArrayOf(1, 2, 3)))
        val value = store.read(alias) as SecureSecret.RawBytes
        assertTrue(backend.lastRead!!.all { it == 0.toByte() }); assertContentEquals(byteArrayOf(1, 2, 3), value.value)
    }

    @Test fun invalidAliasRejectedBeforeNativeWrite() = runBlocking {
        val backend = FakeAsset(); val store = store(backend)
        for (value in listOf("a\u0000b", "x".repeat(256), "bad/\uD800")) {
            assertFailsWith<IllegalArgumentException> { store.save(CredentialAlias(value), SecureSecret.Password("synthetic")) }
        }
        assertEquals(0, backend.additions)
    }

    @Test fun foreignKeyFailsBeforeBulkDeletionEvenOutsidePrefix() = runBlocking {
        val backend = FakeAsset().apply { values[AssetKeys("foreign").encode(alias).toList()] = byteArrayOf(1) }
        assertFailsWith<CredentialAccessException> { store(backend).deleteAll("unrelated/") }
        assertEquals(0, backend.deletions)
    }

    @Test fun keyFramingCannotAliasNamespacesOrUnicode() {
        val first = AssetKeys("a"); val second = AssetKeys("a-b")
        val key = first.encode(CredentialAlias("b-中文/\uD83C\uDFAC"))
        assertEquals(CredentialAlias("b-中文/\uD83C\uDFAC"), first.decode(key))
        assertFailsWith<CredentialAccessException> { second.decode(key) }
        assertFailsWith<CredentialAccessException> { first.decode(key.copyOf(5)) }
    }

    @Test fun malformedNativeFrameIsRejected() {
        val keys = AssetKeys("cn.mafei.test")
        val key = keys.encode(alias)
        for (index in listOf(0, 3, 4, 5, key.lastIndex)) {
            val corrupt = key.copyOf(); corrupt[index] = 0xff.toByte()
            assertFailsWith<CredentialAccessException> { keys.decode(corrupt) }
        }
    }

    @Test fun cancelledBeforeAdmissionDoesNotMutate() = runBlocking {
        val backend = FakeAsset(); val cancelled = Job().apply { cancel() }
        assertFailsWith<CancellationException> { withContext(cancelled) { store(backend).save(alias, SecureSecret.Password("test")) } }
        assertEquals(0, backend.additions)
    }
}

private class FakeAsset : AssetBackend {
    val values = linkedMapOf<List<Byte>, ByteArray>()
    val addStatuses = mutableListOf<Int>()
    val updateStatuses = mutableListOf<Int>()
    var failure: Int? = null
    var additions = 0
    var deletions = 0
    var lastRead: ByteArray? = null
    var lastWrite: ByteArray? = null
    override fun add(key: ByteArray, bytes: ByteArray): Int {
        additions++; lastWrite = bytes
        val status = failure ?: addStatuses.removeFirstOrNull() ?: if (values.containsKey(key.toList())) AssetStatus.DUPLICATED else AssetStatus.SUCCESS
        if (status == AssetStatus.SUCCESS) values[key.toList()] = bytes.copyOf()
        return status
    }
    override fun update(key: ByteArray, bytes: ByteArray): Int {
        val status = failure ?: updateStatuses.removeFirstOrNull() ?: if (values.containsKey(key.toList())) AssetStatus.SUCCESS else AssetStatus.NOT_FOUND
        if (status == AssetStatus.SUCCESS) values[key.toList()] = bytes.copyOf()
        return status
    }
    override fun read(key: ByteArray): AssetRead = AssetRead(failure ?: if (values.containsKey(key.toList())) 0 else AssetStatus.NOT_FOUND,
        values[key.toList()]?.copyOf()?.also { lastRead = it })
    override fun exists(key: ByteArray): Int = failure ?: if (values.containsKey(key.toList())) 0 else AssetStatus.NOT_FOUND
    override fun delete(key: ByteArray): Int {
        deletions++
        return failure ?: if (values.remove(key.toList()) != null) 0 else AssetStatus.NOT_FOUND
    }
    override fun aliases(offset: Int, limit: Int) = AssetPage(failure ?: 0,
        values.keys.drop(offset).take(limit).map { it.toByteArray() })
}
