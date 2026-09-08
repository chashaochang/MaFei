package cn.mafei.cmp.platform.secure_storage_macos

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Independent mainline acceptance of the public error/privacy boundary. */
class MacOSKeychainMainlineReviewTest {
    @Test fun untrustedEngineCausesAreNotExportedInCredentialErrors() = runBlocking {
        val marker = "synthetic-private-token-MUST-NOT-LOG"
        for ((cause, kind) in listOf(
            IllegalStateException(marker) to CredentialFailureKind.Unknown,
            SecurityException(marker) to CredentialFailureKind.AccessDenied,
            UnsatisfiedLinkError(marker) to CredentialFailureKind.BackendUnavailable,
        )) {
            val backend = object : KeychainBridge {
                override fun add(service: String, account: String, data: ByteArray): Int = throw cause
                override fun update(service: String, account: String, data: ByteArray) = KeychainStatus.NotFound
                override fun read(service: String, account: String) = KeychainRead(KeychainStatus.NotFound, null)
                override fun aliases(service: String) = KeychainAliases(KeychainStatus.NotFound, null)
                override fun delete(service: String, account: String) = KeychainStatus.NotFound
            }
            val failure = assertFailsWith<CredentialAccessException> {
                MacOSSecureCredentialStore("mainline-review", backend).save(CredentialAlias("synthetic/token"), SecureSecret.Password("synthetic"))
            }
            assertEquals(kind, failure.kind)
            assertNull(failure.cause)
            assertFalse(failure.stackTraceToString().contains(marker))
        }
    }
}
