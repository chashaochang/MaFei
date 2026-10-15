package cn.mafei.cmp.platform.secure_storage_core

import cn.mafei.cmp.core.secure_credential_api.*
import kotlin.test.*

class SecureSecretCodecTest {
    @Test fun allVariantsRoundTripWithoutJsonOrDelimiterAmbiguity() {
        listOf(
            SecureSecret.AuthToken("MediaBrowser", "fixture:\u0000/秘密"),
            SecureSecret.Password("\u0000|\n密码"),
            SecureSecret.CookieText("a=fixture; b=two"),
            SecureSecret.RawBytes(byteArrayOf(0, -1, 42)),
        ).forEach { assertEquals(it, SecureSecretCodec.decode(SecureSecretCodec.encode(it))) }
    }

    @Test fun emptyFieldsAndEmptyRawBytesArePreserved() {
        listOf(SecureSecret.AuthToken("", ""), SecureSecret.Password(""),
            SecureSecret.CookieText(""), SecureSecret.RawBytes(byteArrayOf())).forEach {
            assertEquals(it, SecureSecretCodec.decode(SecureSecretCodec.encode(it)))
        }
    }

    @Test fun stableVersionOneFixture() {
        // MFS1, Password=2, big-endian byte length=1, 'x'.
        val fixture = byteArrayOf(77, 70, 83, 49, 2, 0, 0, 0, 1, 120)
        assertContentEquals(fixture, SecureSecretCodec.encode(SecureSecret.Password("x")))
        assertEquals(SecureSecret.Password("x"), SecureSecretCodec.decode(fixture))
    }

    @Test fun truncatedInputsAllRejectWithSanitizedError() {
        val encoded = SecureSecretCodec.encode(SecureSecret.AuthToken("s", "fixture-value"))
        for (size in encoded.indices) rejects(encoded.copyOf(size))
    }

    @Test fun unknownMagicVersionAndTypeReject() {
        val valid = SecureSecretCodec.encode(SecureSecret.Password("x"))
        for (index in listOf(0, 3, 4)) {
            val bytes = valid.copyOf(); bytes[index] = 99; rejects(bytes)
        }
    }

    @Test fun trailingBytesRejectInsteadOfPartialDecode() {
        rejects(SecureSecretCodec.encode(SecureSecret.CookieText("x")) + byteArrayOf(0))
    }

    @Test fun negativeAndOverflowingLengthRejectBeforeAllocation() {
        for (length in listOf(byteArrayOf(-1, -1, -1, -1), byteArrayOf(127, -1, -1, -1))) {
            rejects(byteArrayOf(77, 70, 83, 49, 4) + length)
        }
    }

    @Test fun invalidUtf8IsNotSilentlyReplaced() {
        rejects(byteArrayOf(77, 70, 83, 49, 2, 0, 0, 0, 2, -64, -81))
    }

    @Test fun invalidSurrogateCannotSilentlyChangeSecret() {
        val error = assertFailsWith<CredentialAccessException> {
            SecureSecretCodec.encode(SecureSecret.Password("\uD800"))
        }
        assertEquals(CredentialFailureKind.StorageFailure, error.kind)
    }

    @Test fun globalBoundIncludesHeaderAndAllFields() {
        val exact = SecureSecret.RawBytes(ByteArray(SecureSecretCodec.MAX_ENCODED_BYTES - 9))
        assertEquals(SecureSecretCodec.MAX_ENCODED_BYTES, SecureSecretCodec.encode(exact).size)
        assertEquals(exact, SecureSecretCodec.decode(SecureSecretCodec.encode(exact)))
        assertFailsWith<CredentialAccessException> {
            SecureSecretCodec.encode(SecureSecret.RawBytes(ByteArray(SecureSecretCodec.MAX_ENCODED_BYTES - 8)))
        }
        rejects(ByteArray(SecureSecretCodec.MAX_ENCODED_BYTES + 1))
        assertFailsWith<CredentialAccessException> {
            SecureSecretCodec.encode(SecureSecret.AuthToken("x".repeat(600_000), "y".repeat(600_000)))
        }
    }

    @Test fun decodedRawBytesNeverAliasEncodedInput() {
        val encoded = SecureSecretCodec.encode(SecureSecret.RawBytes(byteArrayOf(42)))
        val decoded = SecureSecretCodec.decode(encoded) as SecureSecret.RawBytes
        encoded.fill(0)
        assertContentEquals(byteArrayOf(42), decoded.value)
        decoded.value.fill(0)
        assertContentEquals(byteArrayOf(42), decoded.value)
    }

    private fun rejects(bytes: ByteArray) {
        val error = assertFailsWith<CredentialAccessException> { SecureSecretCodec.decode(bytes) }
        assertEquals(CredentialFailureKind.StorageFailure, error.kind)
        assertEquals("Invalid secure credential encoding", error.message)
        assertNull(error.cause)
    }
}
