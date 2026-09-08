@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.secure_storage_ios

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import kotlin.test.*

/** Real CoreFoundation conversion, without accessing any Keychain entries. */
class KeychainNativeValueTest {
    @Test fun embeddedNulCannotTruncateAliasAndTargetAnotherEntry() {
        withString("account/a\u0000suffix") { value ->
            val failure = assertFailsWith<CredentialAccessException> {
                SecurityKeychainBackend("cn.mafei.tests.synthetic").decodeAlias(value)
            }
            assertEquals(CredentialFailureKind.StorageFailure, failure.kind)
        }
    }

    @Test fun supplementaryUnicodeAliasIsNotTruncated() {
        val alias = "account/中文/\uD83C\uDFAC/token"
        withString(alias) { value ->
            assertEquals(alias, SecurityKeychainBackend("cn.mafei.tests.synthetic").decodeAlias(value))
        }
    }

    private fun withString(value: String, block: (CFStringRef) -> Unit) {
        val bytes = value.encodeToByteArray()
        val string = bytes.usePinned {
            CFStringCreateWithBytes(null, it.addressOf(0).reinterpret(), bytes.size.toLong(), kCFStringEncodingUTF8, false)
        }!!
        try { block(string) } finally { CFRelease(string) }
    }
}
