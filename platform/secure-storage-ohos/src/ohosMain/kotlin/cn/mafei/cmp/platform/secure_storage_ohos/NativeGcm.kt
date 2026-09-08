@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.secure_storage_ohos

import cn.mafei.cmp.core.secure_credential_api.*
import cnames.structs.OH_CryptoSymCipher
import cnames.structs.OH_CryptoSymCipherParams
import cnames.structs.OH_CryptoSymKey
import cnames.structs.OH_CryptoSymKeyGenerator
import kotlinx.cinterop.*
import platform.CryptoArchitectureKit.CryptoCommonApi.*
import platform.CryptoArchitectureKit.CryptoSymCipherApi.*
import platform.CryptoArchitectureKit.CryptoSymKeyApi.*
import platform.posix.memcpy
import platform.posix.memset

/** Existing API 12+ CryptoArchitectureKit AES-GCM implementation; no custom cipher. */
internal object NativeGcm {
    fun crypt(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, bytes: ByteArray): ByteArray {
        if (key.size != 32 || nonce.size != 12 || bytes.isEmpty() ||
            !encrypt && bytes.size <= 16) invalidAssetResult()
        val tag = if (encrypt) ByteArray(16) else bytes.copyOfRange(bytes.size - 16, bytes.size)
        val input = if (encrypt) bytes else bytes.copyOfRange(0, bytes.size - 16)
        try {
            return memScoped {
                val generator = alloc<CPointerVar<OH_CryptoSymKeyGenerator>>().apply { value = null }
                val nativeKey = alloc<CPointerVar<OH_CryptoSymKey>>().apply { value = null }
                val cipher = alloc<CPointerVar<OH_CryptoSymCipher>>().apply { value = null }
                val params = alloc<CPointerVar<OH_CryptoSymCipherParams>>().apply { value = null }
                val output = alloc<Crypto_DataBlob>().apply { data = null; len = 0u }
                val scratch = mutableListOf<Pair<CPointer<UByteVar>, Int>>()
                fun blob(value: ByteArray): CPointer<Crypto_DataBlob> {
                    val pointer = allocArray<UByteVar>(maxOf(1, value.size))
                    scratch += pointer to value.size
                    if (value.isNotEmpty()) value.usePinned { memcpy(pointer, it.addressOf(0), value.size.toULong()) }
                    return alloc<Crypto_DataBlob>().apply { data = pointer; len = value.size.toULong() }.ptr
                }
                try {
                    cryptoCheck(OH_CryptoSymKeyGenerator_Create("AES256", generator.ptr))
                    cryptoCheck(OH_CryptoSymKeyGenerator_Convert(generator.value, blob(key), nativeKey.ptr))
                    cryptoCheck(OH_CryptoSymCipher_Create("AES256|GCM|NoPadding", cipher.ptr))
                    cryptoCheck(OH_CryptoSymCipherParams_Create(params.ptr))
                    cryptoCheck(OH_CryptoSymCipherParams_SetParam(params.value, CRYPTO_IV_DATABLOB, blob(nonce)))
                    cryptoCheck(OH_CryptoSymCipherParams_SetParam(params.value, CRYPTO_AAD_DATABLOB, blob(aad)))
                    cryptoCheck(OH_CryptoSymCipherParams_SetParam(params.value, CRYPTO_TAG_DATABLOB, blob(tag)))
                    cryptoCheck(OH_CryptoSymCipher_Init(cipher.value, if (encrypt) CRYPTO_ENCRYPT_MODE else CRYPTO_DECRYPT_MODE,
                        nativeKey.value, params.value))
                    // Encrypt returns ciphertext || tag; decrypt receives tag through params.
                    // No plaintext is returned until Final successfully verifies the tag.
                    cryptoCheck(OH_CryptoSymCipher_Final(cipher.value, blob(input), output.ptr))
                    val expected = if (encrypt) bytes.size + 16 else bytes.size - 16
                    if (output.len != expected.toULong()) invalidAssetResult()
                    (output.data ?: invalidAssetResult()).reinterpret<ByteVar>().readBytes(expected)
                } finally {
                    output.data?.let { memset(it, 0, output.len) }
                    OH_Crypto_FreeDataBlob(output.ptr)
                    cipher.value?.let { OH_CryptoSymCipher_Destroy(it) }
                    params.value?.let { OH_CryptoSymCipherParams_Destroy(it) }
                    nativeKey.value?.let { OH_CryptoSymKey_Destroy(it) }
                    generator.value?.let { OH_CryptoSymKeyGenerator_Destroy(it) }
                    scratch.forEach { (pointer, size) -> memset(pointer, 0, size.toULong()) }
                }
            }
        } finally { tag.fill(0); if (!encrypt) input.fill(0) }
    }
}

private fun cryptoCheck(status: UInt) {
    if (status == CRYPTO_SUCCESS) return
    throw CredentialAccessException(
        if (status == CRYPTO_NOT_SUPPORTED) CredentialFailureKind.Unsupported else CredentialFailureKind.StorageFailure,
        "Authenticated credential encryption failed",
    )
}
