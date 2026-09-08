package cn.mafei.cmp.platform.secure_storage_macos

import cn.mafei.cmp.core.secure_credential_api.*
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.*

/** Opt-in only; all operations target this invocation's unique synthetic service. */
class MacOSKeychainNativeIntegrationTest {
    @Test fun syntheticNamespaceRoundTripAndCleanup() = runBlocking {
        val namespace = "integration.${UUID.randomUUID()}"
        println("Synthetic Keychain namespace (no other service queried): $namespace")
        val store = MacOSSecureCredentialStore(namespace)
        val alias = CredentialAlias("synthetic/token")
        var created = false
        try {
            store.save(alias, SecureSecret.AuthToken("Synthetic", "non-production-fixture"), overwrite = false)
            created = true
            assertEquals(SecureSecret.AuthToken("Synthetic", "non-production-fixture"), store.read(alias))
            assertEquals(CredentialFailureKind.AlreadyExists, assertFailsWith<CredentialAccessException> {
                store.save(alias, SecureSecret.Password("must-not-replace"), overwrite = false)
            }.kind)
            store.save(alias, SecureSecret.CookieText("synthetic=updated"))
            assertEquals(SecureSecret.CookieText("synthetic=updated"), store.read(alias))
            assertEquals(listOf(alias), store.listAliases())
            assertTrue(store.exists(alias)); assertTrue(store.delete(alias)); created = false
            assertNull(store.read(alias)); assertEquals(emptyList(), store.listAliases())
        } finally {
            // Exact alias in this fresh namespace, never a global/prefix sweep.
            if (created) store.delete(alias)
        }
    }
}
