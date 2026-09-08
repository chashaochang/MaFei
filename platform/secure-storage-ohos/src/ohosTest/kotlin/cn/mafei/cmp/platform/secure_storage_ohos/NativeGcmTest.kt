package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import kotlin.test.*

/** Real CryptoArchitectureKit tests. Compilation is not evidence these passed on OHOS. */
class NativeGcmTest {
    @Test fun aes256GcmKnownAnswerAndTagVerification() {
        // Standard all-zero AES-256 GCM vector: 32-byte key, 12-byte IV, 16-byte plaintext.
        val key = ByteArray(32); val nonce = ByteArray(12); val plain = ByteArray(16)
        val expected = hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919")
        assertContentEquals(expected, NativeGcm.crypt(true, key, nonce, ByteArray(0), plain))
        assertContentEquals(plain, NativeGcm.crypt(false, key, nonce, ByteArray(0), expected))
        expected[expected.lastIndex] = (expected.last().toInt() xor 1).toByte()
        assertFailsWith<CredentialAccessException> { NativeGcm.crypt(false, key, nonce, ByteArray(0), expected) }
    }

    @Test fun changedAuthenticatedAliasRejectsLongCredential() {
        val key = ByteArray(32) { it.toByte() }; val nonce = ByteArray(12) { it.toByte() }
        val aad = "MFL1/test-namespace/alias".encodeToByteArray()
        val plain = "synthetic=".repeat(5000).encodeToByteArray()
        val encrypted = NativeGcm.crypt(true, key, nonce, aad, plain)
        assertContentEquals(plain, NativeGcm.crypt(false, key, nonce, aad, encrypted))
        assertFailsWith<CredentialAccessException> {
            NativeGcm.crypt(false, key, nonce, "MFL1/test-namespace/other".encodeToByteArray(), encrypted)
        }
    }
    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
