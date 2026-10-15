@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.secure_storage_ios

import cn.mafei.cmp.platform.secure_storage_core.SecureSecretCodec
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*

/** Owns every CF Create/Copy result explicitly. No Objective-C bridging or implicit retain. */
internal class SecurityKeychainBackend(private val service: String) : KeychainBackend {
    override fun add(alias: String, encoded: ByteArray): Int = query(alias, interaction = false) { query ->
        CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        putData(query, encoded)
        SecItemAdd(query, null)
    }

    override fun update(alias: String, encoded: ByteArray): Int = query(alias) { query ->
        dictionary { attributes ->
            CFDictionarySetValue(attributes, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            putData(attributes, encoded)
            SecItemUpdate(query, attributes)
        }
    }

    override fun read(alias: String): KeychainRead = query(alias) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        copyMatching(query) { status, value ->
            if (status != errSecSuccess) return@copyMatching KeychainRead(status, null)
            if (value == null || CFGetTypeID(value) != CFDataGetTypeID()) invalidKeychainResult()
            val data: CFDataRef = value.reinterpret()
            val length = CFDataGetLength(data)
            if (length !in 9..SecureSecretCodec.MAX_ENCODED_BYTES.toLong()) invalidKeychainResult()
            val bytes = CFDataGetBytePtr(data)?.readBytes(length.toInt()) ?: invalidKeychainResult()
            KeychainRead(status, bytes)
        }
    }

    override fun contains(alias: String): Int = query(alias) { query ->
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        SecItemCopyMatching(query, null)
    }

    override fun delete(alias: String): Int = query(alias) { query -> SecItemDelete(query) }

    override fun aliases(): KeychainAliases = query(null) { query ->
        CFDictionarySetValue(query, kSecReturnAttributes, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitAll)
        copyMatching(query) { status, value ->
            if (status != errSecSuccess) return@copyMatching KeychainAliases(status, emptyList())
            if (value == null || CFGetTypeID(value) != CFArrayGetTypeID()) invalidKeychainResult()
            val array: CFArrayRef = value.reinterpret()
            val count = CFArrayGetCount(array)
            if (count !in 0..MAX_ALIASES.toLong()) invalidKeychainResult()
            val aliases = (0 until count).map { index ->
                val item = CFArrayGetValueAtIndex(array, index) ?: invalidKeychainResult()
                if (CFGetTypeID(item) != CFDictionaryGetTypeID()) invalidKeychainResult()
                val account = CFDictionaryGetValue(item.reinterpret(), kSecAttrAccount) ?: invalidKeychainResult()
                if (CFGetTypeID(account) != CFStringGetTypeID()) invalidKeychainResult()
                decodeAlias(account.reinterpret())
            }
            KeychainAliases(status, aliases)
        }
    }

    private fun <T> query(alias: String?, interaction: Boolean = true, block: (CFMutableDictionaryRef) -> T): T = dictionary { query ->
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrSynchronizable, kCFBooleanFalse)
        putString(query, kSecAttrService, service)
        if (alias != null) putString(query, kSecAttrAccount, alias)
        if (interaction) CFDictionarySetValue(query, kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
        block(query)
    }

    private fun <T> dictionary(block: (CFMutableDictionaryRef) -> T): T {
        val dictionary = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr) ?: invalidKeychainResult()
        try { return block(dictionary) } finally { CFRelease(dictionary) }
    }

    private fun putString(dictionary: CFMutableDictionaryRef, key: CFStringRef?, value: String) {
        val bytes = value.encodeToByteArray(throwOnInvalidSequence = true)
        val string = bytes.usePinned { CFStringCreateWithBytes(null, it.addressOf(0).reinterpret(), bytes.size.toLong(), kCFStringEncodingUTF8, false) } ?: invalidKeychainResult()
        try { CFDictionarySetValue(dictionary, key, string) } finally { CFRelease(string) }
    }

    private fun putData(dictionary: CFMutableDictionaryRef, bytes: ByteArray) {
        if (bytes.size !in 9..SecureSecretCodec.MAX_ENCODED_BYTES) invalidKeychainResult()
        val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) } ?: invalidKeychainResult()
        try { CFDictionarySetValue(dictionary, kSecValueData, data) } finally { CFRelease(data) }
    }

    private fun <T> copyMatching(query: CFDictionaryRef, block: (Int, CFTypeRef?) -> T): T = memScoped {
        val result = alloc<CFTypeRefVar>(); result.value = null
        val status = SecItemCopyMatching(query, result.ptr)
        val owned = result.value
        try { block(status, owned) } finally { if (owned != null) CFRelease(owned) }
    }

    internal fun decodeAlias(value: CFStringRef): String {
        val utf16Length = CFStringGetLength(value)
        if (utf16Length !in 1..MAX_ALIAS_BYTES.toLong()) invalidKeychainResult()
        val capacity = CFStringGetMaximumSizeForEncoding(utf16Length, kCFStringEncodingUTF8) + 1
        if (capacity !in 2..(MAX_ALIAS_BYTES * 4L + 1)) invalidKeychainResult()
        val bytes = ByteArray(capacity.toInt())
        if (!bytes.usePinned { CFStringGetCString(value, it.addressOf(0), capacity, kCFStringEncodingUTF8) }) invalidKeychainResult()
        val length = bytes.indexOf(0)
        if (length !in 1..MAX_ALIAS_BYTES) invalidKeychainResult()
        val decoded = try { bytes.decodeToString(0, length, throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { invalidKeychainResult() }
        // CFStringGetCString preserves embedded NULs, whereas the C-string length
        // above stops at the first one. Never return a truncated account: a later
        // delete could otherwise target a different, valid entry in this service.
        if (decoded.length.toLong() != utf16Length) invalidKeychainResult()
        return decoded
    }
}
