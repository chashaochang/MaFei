package cn.mafei.cmp.core.secure_credential_api

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureCredentialStoreTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private val store = InMemorySecureCredentialStore()

    @Test
    fun aliasRejectsBlankOrPaddedValues() {
        assertFailsWith<IllegalArgumentException> { CredentialAlias("") }
        assertFailsWith<IllegalArgumentException> { CredentialAlias(" padded") }
        assertEquals("ok/1", CredentialAlias("ok/1").toString())
    }

    @Test
    fun saveAndReadRoundTripAllSecretTypes() = runTest {
        val token = SecureSecret.AuthToken(scheme = "MediaBrowser", token = "token-123")
        val password = SecureSecret.Password(value = "hunter2")
        val cookies = SecureSecret.CookieText(value = "mc=abc; sid=def")
        val raw = SecureSecret.RawBytes(value = byteArrayOf(9, 8, 7))

        store.save(CredentialAlias("account/1/token"), token)
        store.save(CredentialAlias("account/1/password"), password)
        store.save(CredentialAlias("account/2/cookies"), cookies)
        store.save(CredentialAlias("account/2/key"), raw)

        assertEquals(token, store.read(CredentialAlias("account/1/token")))
        assertEquals(password, store.read(CredentialAlias("account/1/password")))
        assertEquals(cookies, store.read(CredentialAlias("account/2/cookies")))
        assertEquals(raw, store.read(CredentialAlias("account/2/key")))
        assertNull(store.read(CredentialAlias("account/3/token")))
    }

    @Test
    fun secretsRedactInToString() {
        val token = SecureSecret.AuthToken(scheme = "Bearer", token = "super-secret-token")
        val password = SecureSecret.Password(value = "super-secret-password")
        val cookies = SecureSecret.CookieText(value = "mc=super-secret-cookie")
        val raw = SecureSecret.RawBytes(value = "super-secret-bytes".encodeToByteArray())

        for (secret in listOf(token, password, cookies, raw)) {
            val rendered = secret.toString()
            assertFalse(rendered.contains("super-secret"), "leaked: $rendered")
        }
        // defensive: sanity-check the test itself can detect leaks
        assertTrue(token.toString().contains("Bearer"))
        assertNotEquals("super-secret-bytes".encodeToByteArray().decodeToString(), raw.toString())
    }

    @Test
    fun overwritePolicy() = runTest {
        val alias = CredentialAlias("account/1/token")
        store.save(alias, SecureSecret.AuthToken(scheme = "A", token = "first"))

        assertFailsWith<CredentialAccessException> {
            store.save(alias, SecureSecret.AuthToken(scheme = "A", token = "second"), overwrite = false)
        }.let { failure ->
            assertEquals(CredentialFailureKind.AlreadyExists, failure.kind)
        }
        assertEquals("first", (store.read(alias) as SecureSecret.AuthToken).token)

        store.save(alias, SecureSecret.AuthToken(scheme = "A", token = "third"))
        assertEquals("third", (store.read(alias) as SecureSecret.AuthToken).token)
    }

    @Test
    fun deleteAndExists() = runTest {
        val alias = CredentialAlias("account/1/token")
        store.save(alias, SecureSecret.AuthToken(scheme = "A", token = "x"))
        assertTrue(store.exists(alias))

        assertTrue(store.delete(alias))
        assertFalse(store.exists(alias))
        assertFalse(store.delete(alias))
        assertNull(store.read(alias))
    }

    @Test
    fun listAliasesFiltersByPrefixInStableOrder() = runTest {
        store.save(CredentialAlias("account/2/token"), SecureSecret.Password("x"))
        store.save(CredentialAlias("account/10/token"), SecureSecret.Password("x"))
        store.save(CredentialAlias("account/10/password"), SecureSecret.Password("x"))
        store.save(CredentialAlias("server/1/token"), SecureSecret.Password("x"))

        val aliases = store.listAliases("account/").map { it.value }
        assertEquals(listOf("account/10/password", "account/10/token", "account/2/token"), aliases)
        assertEquals(4, store.listAliases().size)
    }

    @Test
    fun deleteAllRemovesMatchingPrefix() = runTest {
        store.save(CredentialAlias("account/1/token"), SecureSecret.Password("x"))
        store.save(CredentialAlias("account/2/token"), SecureSecret.Password("x"))
        store.save(CredentialAlias("server/1/token"), SecureSecret.Password("x"))

        assertEquals(2, store.deleteAll("account/"))
        assertNull(store.read(CredentialAlias("account/1/token")))
        assertEquals(1, store.listAliases().size)
        assertEquals(0, store.deleteAll("account/"))
    }

    @Test
    fun rawBytesEqualityIsByContent() = runTest {
        val alias = CredentialAlias("k")
        store.save(alias, SecureSecret.RawBytes(byteArrayOf(1, 2)))
        assertEquals(SecureSecret.RawBytes(byteArrayOf(1, 2)), store.read(alias))
        assertTrue(store.read(alias) != SecureSecret.RawBytes(byteArrayOf(1, 3)))
    }

    @Test
    fun rawBytesAreDefensivelyCopiedAtEveryBoundary() = runTest {
        val alias = CredentialAlias("k")
        val source = byteArrayOf(1, 2, 3)
        val secret = SecureSecret.RawBytes(source)

        // mutating the constructor input must not affect the secret
        source[0] = 99
        assertEquals(SecureSecret.RawBytes(byteArrayOf(1, 2, 3)), secret)

        // mutating the value read back must not affect the secret
        val leaked = secret.value
        leaked[0] = 42
        assertContentEquals(byteArrayOf(1, 2, 3), secret.value)

        // mutating the array returned by the store must not affect the stored copy
        store.save(alias, secret)
        val fromStore = store.read(alias) as SecureSecret.RawBytes
        fromStore.value[0] = 77
        assertEquals(SecureSecret.RawBytes(byteArrayOf(1, 2, 3)), store.read(alias) as SecureSecret.RawBytes)
    }
}
