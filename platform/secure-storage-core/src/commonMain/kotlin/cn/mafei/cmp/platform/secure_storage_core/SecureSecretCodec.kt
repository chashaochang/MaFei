package cn.mafei.cmp.platform.secure_storage_core

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import cn.mafei.cmp.core.secure_credential_api.SecureSecret

/**
 * Private-at-rest platform wire format, NOT encryption or general JSON serialization.
 * Only feed its bytes to a platform secure store/encryption boundary; never ordinary
 * settings, logs, backups or migration manifests. Format MFS1 is versioned and strict:
 * magic(4), variant(1), then length-prefixed fields (unsigned BE32, UTF-8 or raw).
 * No normalization, unknown-type fallback, trailing bytes or UTF-8 replacement.
 * Callers own returned bytes and should clear temporary plaintext buffers after use.
 */
object SecureSecretCodec {
    const val MAX_ENCODED_BYTES: Int = 1024 * 1024

    fun encode(secret: SecureSecret): ByteArray {
        val fields = mutableListOf<ByteArray>()
        try {
            val kind = when (secret) {
                is SecureSecret.AuthToken -> { fields += utf8(secret.scheme); fields += utf8(secret.token); 1 }
                is SecureSecret.Password -> { fields += utf8(secret.value); 2 }
                is SecureSecret.CookieText -> { fields += utf8(secret.value); 3 }
                is SecureSecret.RawBytes -> { fields += secret.value; 4 }
            }
            val size = fields.fold(5L) { total, field -> total + 4 + field.size }
            if (size > MAX_ENCODED_BYTES) invalid()
            val result = ByteArray(size.toInt())
            result[0] = 77; result[1] = 70; result[2] = 83; result[3] = 49; result[4] = kind.toByte()
            var offset = 5
            for (field in fields) {
                for (shift in listOf(24, 16, 8, 0)) result[offset++] = (field.size ushr shift).toByte()
                field.copyInto(result, offset)
                offset += field.size
            }
            return result
        } finally {
            fields.forEach { it.fill(0) }
        }
    }

    fun decode(bytes: ByteArray): SecureSecret {
        if (bytes.size !in 9..MAX_ENCODED_BYTES || bytes[0] != 77.toByte() ||
            bytes[1] != 70.toByte() || bytes[2] != 83.toByte() || bytes[3] != 49.toByte()) invalid()
        var offset = 5
        fun field(): ByteArray {
            if (bytes.size - offset < 4) invalid()
            var size = 0L
            repeat(4) { size = (size shl 8) or (bytes[offset++].toLong() and 255) }
            if (size > bytes.size - offset) invalid()
            val end = offset + size.toInt()
            return bytes.copyOfRange(offset, end).also { offset = end }
        }
        fun string(): String {
            val field = field()
            try {
                return try { field.decodeToString(throwOnInvalidSequence = true) }
                catch (_: CharacterCodingException) { invalid() }
            } finally { field.fill(0) }
        }
        val secret = when (bytes[4].toInt()) {
            1 -> SecureSecret.AuthToken(string(), string())
            2 -> SecureSecret.Password(string())
            3 -> SecureSecret.CookieText(string())
            4 -> {
                val field = field()
                try { SecureSecret.RawBytes(field) } finally { field.fill(0) }
            }
            else -> invalid()
        }
        if (offset != bytes.size) invalid()
        return secret
    }

    private fun utf8(value: String): ByteArray {
        if (value.length > MAX_ENCODED_BYTES) invalid()
        return try { value.encodeToByteArray(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { invalid() }
    }

    private fun invalid(): Nothing = throw CredentialAccessException(
        CredentialFailureKind.StorageFailure, "Invalid secure credential encoding",
    )
}
