package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec

/** Ciphertext-only immutable files. [locked] also coordinates other cooperating app processes. */
internal interface LongCredentialVault {
    fun <T> locked(block: () -> T): T
    fun random(size: Int): ByteArray
    /** AES-256-GCM, 12-byte nonce, 16-byte tag appended to ciphertext. */
    fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray
    fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
    /** Exclusive create, complete write and file+directory fsync before returning. */
    fun writeNew(id: String, ciphertext: ByteArray)
    /** Missing referenced data is corruption, never an absent credential. */
    fun read(id: String): ByteArray
    fun delete(id: String)
}

internal expect fun createLongCredentialVault(privateDirectory: String, namespace: String): LongCredentialVault

/** MFL1 Asset record: magic + random file ID (16) + per-record key (32) + nonce (12). */
internal class LongCredentialReference(val bytes: ByteArray) {
    init { if (bytes.size != SIZE || !isReference(bytes)) invalidAssetResult() }
    val id: String get() = bytes.copyOfRange(4, 20).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    fun <T> usingKey(block: (ByteArray, ByteArray) -> T): T {
        val key = bytes.copyOfRange(20, 52)
        val nonce = bytes.copyOfRange(52, 64)
        try { return block(key, nonce) } finally { key.fill(0); nonce.fill(0) }
    }
    companion object {
        const val SIZE = 64
        val MAGIC: ByteArray get() = byteArrayOf(77, 70, 76, 49)
        fun isReference(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(MAGIC)
        fun create(vault: LongCredentialVault): LongCredentialReference {
            val random = vault.random(SIZE - 4)
            try {
                if (random.size != SIZE - 4) invalidAssetResult()
                return LongCredentialReference(MAGIC + random)
            } finally { random.fill(0) }
        }
    }
}

internal const val MAX_CIPHERTEXT_BYTES = SecureSecretCodec.MAX_ENCODED_BYTES + 16
