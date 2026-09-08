package cn.mafei.cmp.platform.secure_storage_android

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlin.test.*

class EncryptedCredentialFilesTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val alias = CredentialAlias("account/a/token")
    private val secret = SecureSecret.AuthToken("Bearer", "private-test-token-should-not-be-on-disk")
    private suspend fun fixture(block: suspend (File) -> Unit) {
        val root = Files.createTempDirectory("mafei-secure-android-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun store(root: File, namespace: String = "test", keySource: CredentialKeySource = CredentialKeySource { key }, beforeCommit: suspend () -> Unit = {}) =
        EncryptedCredentialFiles(root, namespace, keySource, AtomicCredentialReplace { source, target ->
            if (!source.renameTo(target)) throw IOException("test rename failed")
        }, beforeCommit)
    private fun record(root: File, alias: CredentialAlias = this.alias) = File(root, credentialRecordName(alias))

    @Test fun roundTripsEveryVariantAndNeverWritesPlaintextSecret() = runBlocking {
        fixture { root ->
            val s = store(root)
            val variants = listOf(secret, SecureSecret.Password("private-password"), SecureSecret.CookieText("cookie=私密"), SecureSecret.RawBytes(byteArrayOf(0, 1, -1)))
            for ((index, value) in variants.withIndex()) {
                val target = CredentialAlias("account/$index"); s.save(target, value)
                assertEquals(value, s.read(target)); assertTrue(s.exists(target))
                assertFalse(record(root, target).readBytes().decodeToString().contains("private-"))
            }
            assertNull(s.read(CredentialAlias("missing"))); assertFalse(s.exists(CredentialAlias("missing")))
        }
    }

    @Test fun nonceChangesOnOverwriteAndReopeningReadsExistingRecord() = runBlocking {
        fixture { root ->
            val s = store(root); s.save(alias, secret); val first = record(root).readBytes()
            s.save(alias, secret); assertFalse(first.contentEquals(record(root).readBytes()))
            assertEquals(secret, store(root).read(alias))
        }
    }

    @Test fun overwriteFalseIsAtomicAcrossTwoInstances() = runBlocking {
        withTimeout(5000) { fixture { root ->
            val results = (1..12).map { index -> async(Dispatchers.Default) {
                try { store(root).save(alias, SecureSecret.Password("$index"), overwrite = false); true }
                catch (failure: CredentialAccessException) { assertEquals(CredentialFailureKind.AlreadyExists, failure.kind); false }
            } }.awaitAll()
            assertEquals(1, results.count { it }); assertNotNull(store(root).read(alias))
        } }
    }

    @Test fun tamperTruncationAndWrongKeyAreErrorsNotMissing() = runBlocking {
        fixture { root ->
            val s = store(root); s.save(alias, secret); val original = record(root).readBytes()
            val tampered = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            record(root).writeBytes(tampered)
            assertEquals(CredentialFailureKind.StorageFailure, assertFailsWith<CredentialAccessException> { s.read(alias) }.kind)
            assertFailsWith<CredentialAccessException> { s.exists(alias) }
            record(root).writeBytes(original.take(8).toByteArray())
            assertFailsWith<CredentialAccessException> { s.read(alias) }
            record(root).writeBytes(original)
            val wrongKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            assertFailsWith<CredentialAccessException> { store(root, keySource = CredentialKeySource { wrongKey }).read(alias) }
        }
    }

    @Test fun namespaceAndAliasSwapsFailAuthentication() = runBlocking {
        fixture { root ->
            val s = store(root); s.save(alias, secret)
            val other = CredentialAlias("account/b/token")
            record(root, other).writeBytes(record(root).readBytes())
            assertFailsWith<CredentialAccessException> { s.read(other) }
            // Rewrite the plaintext alias header as well: the file-name check
            // now passes but GCM's alias-bound AAD must still reject the swap.
            val forged = record(root).readBytes()
            other.value.encodeToByteArray().copyInto(forged, 8)
            record(root, other).writeBytes(forged)
            assertFailsWith<CredentialAccessException> { s.read(other) }
            assertFailsWith<CredentialAccessException> { store(root, namespace = "different").read(alias) }
        }
    }

    @Test fun stablePrefixListingDeletionAndMissingDelete() = runBlocking {
        fixture { root ->
            val s = store(root)
            listOf("b/2", "a/2", "a/1").forEach { s.save(CredentialAlias(it), secret) }
            assertEquals(listOf("a/1", "a/2", "b/2"), s.listAliases().map { it.value })
            assertEquals(listOf("a/1", "a/2"), s.listAliases("a/").map { it.value })
            assertEquals(2, s.deleteAll("a/")); assertFalse(s.delete(CredentialAlias("a/1")))
            assertTrue(s.delete(CredentialAlias("b/2"))); assertEquals(0, s.deleteAll())
        }
    }

    @Test fun failedAtomicReplacementKeepsOldRecordAndRemovesTemporaryCiphertext() = runBlocking {
        fixture { root ->
            val s = store(root); s.save(alias, secret); val before = record(root).readBytes()
            val broken = EncryptedCredentialFiles(root, "test", CredentialKeySource { key }, AtomicCredentialReplace { _, _ -> throw IOException("failure") })
            assertEquals(CredentialFailureKind.StorageFailure, assertFailsWith<CredentialAccessException> { broken.save(alias, SecureSecret.Password("new")) }.kind)
            assertTrue(before.contentEquals(record(root).readBytes()))
            assertEquals(secret, s.read(alias)); assertTrue(root.listFiles()!!.none { it.name.endsWith(".tmp") })
        }
    }

    @Test fun cancellationBeforeCommitKeepsOldRecordAndQueuedCancelledWriterDoesNotRun() = runBlocking {
        withTimeout(5000) { fixture { root ->
            val s = store(root); s.save(alias, secret)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val paused = store(root, beforeCommit = { entered.complete(Unit); release.await() })
            val pending = launch { paused.save(alias, SecureSecret.Password("cancelled")) }; entered.await()
            val queued = launch(start = CoroutineStart.UNDISPATCHED) { s.save(CredentialAlias("b"), secret) }
            queued.cancelAndJoin(); pending.cancelAndJoin()
            assertEquals(secret, s.read(alias)); assertNull(s.read(CredentialAlias("b")))
            assertTrue(root.listFiles()!!.none { it.name.endsWith(".tmp") })
        } }
    }

    @Test fun missingKeystoreKeyNeverRegeneratesOverExistingCiphertext() = runBlocking {
        fixture { root ->
            store(root).save(alias, secret)
            val calls = mutableListOf<Boolean>()
            val unavailable = store(root, keySource = CredentialKeySource { allowCreate ->
                calls.add(allowCreate); throw CredentialAccessException(CredentialFailureKind.BackendUnavailable)
            })
            assertEquals(CredentialFailureKind.BackendUnavailable, assertFailsWith<CredentialAccessException> { unavailable.read(alias) }.kind)
            assertFailsWith<CredentialAccessException> { unavailable.save(CredentialAlias("new"), secret) }
            assertEquals(listOf(false, false), calls)
            assertEquals(secret, store(root).read(alias))
        }
    }

    @Test fun securityFailuresAreAccessDeniedAndCancellationIsUnwrapped() = runBlocking {
        fixture { root ->
            val denied = store(root, keySource = CredentialKeySource { throw SecurityException("private") })
            val error = assertFailsWith<CredentialAccessException> { denied.save(alias, secret) }
            assertEquals(CredentialFailureKind.AccessDenied, error.kind)
            assertFalse(error.message!!.contains("private"))
            val cancellation = CancellationException("cancelled by caller")
            val cancelled = store(root, keySource = CredentialKeySource { throw cancellation })
            val observed = assertFailsWith<CancellationException> { cancelled.save(alias, secret) }
            // kotlinx.coroutines may copy the exception for stack-trace recovery
            // at withContext; no CredentialAccessException wrapper is introduced.
            assertTrue(observed === cancellation || observed.cause === cancellation)
        }
    }

    @Test fun corruptListingDoesNotPretendToBeEmptyAndKnownAliasCanStillBeDeleted() = runBlocking {
        fixture { root ->
            val s = store(root); s.save(alias, secret); record(root).writeBytes(byteArrayOf(1, 2))
            assertFailsWith<CredentialAccessException> { s.listAliases() }
            assertFailsWith<CredentialAccessException> { s.deleteAll() }
            assertTrue(s.delete(alias)); assertTrue(s.listAliases().isEmpty())
        }
    }

    @Test fun symbolicLinkRecordCannotReadOrDeleteOutsideTheStore() = runBlocking {
        fixture { root ->
            val outside = File(root, "unrelated-file").apply { writeText("unrelated") }
            Files.createSymbolicLink(record(root).toPath(), outside.toPath())
            val s = store(root)
            assertEquals(CredentialFailureKind.AccessDenied, assertFailsWith<CredentialAccessException> { s.read(alias) }.kind)
            assertFailsWith<CredentialAccessException> { s.delete(alias) }
            assertEquals("unrelated", outside.readText())
        }
    }

    @Test fun oversizedRecordAndAliasAreRejectedWithoutUnboundedAllocation() = runBlocking {
        fixture { root ->
            val s = store(root)
            RandomAccessFile(record(root), "rw").use { it.setLength(2L * 1024 * 1024) }
            assertEquals(CredentialFailureKind.StorageFailure, assertFailsWith<CredentialAccessException> { s.read(alias) }.kind)
            assertFailsWith<CredentialAccessException> { s.save(CredentialAlias("a".repeat(20_000)), secret) }
        }
    }

    @Test fun deletingOneRecordDoesNotPermitKeyRegenerationWhileAnotherRemains() = runBlocking {
        fixture { root ->
            val s = store(root); s.save(alias, secret); s.save(CredentialAlias("other"), secret)
            s.delete(alias)
            var allowCreate: Boolean? = null
            store(root, keySource = CredentialKeySource { allow -> allowCreate = allow; key }).save(alias, secret)
            assertEquals(false, allowCreate)
        }
    }
}
