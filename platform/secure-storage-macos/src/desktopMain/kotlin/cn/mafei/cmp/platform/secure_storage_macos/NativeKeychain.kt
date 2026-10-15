package cn.mafei.cmp.platform.secure_storage_macos

import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import java.nio.file.Files

/** No process/security CLI invocation and no FFM/JDK preview dependency. */
internal object JniKeychainBridge : KeychainBridge {
    override fun add(service: String, account: String, data: ByteArray): Int { KeychainLibrary.ensureLoaded(); return NativeKeychain.add(service, account, data) }
    override fun update(service: String, account: String, data: ByteArray): Int { KeychainLibrary.ensureLoaded(); return NativeKeychain.update(service, account, data) }
    override fun read(service: String, account: String): KeychainRead { KeychainLibrary.ensureLoaded(); return NativeKeychain.read(service, account) }
    override fun aliases(service: String): KeychainAliases { KeychainLibrary.ensureLoaded(); return NativeKeychain.aliases(service) }
    override fun delete(service: String, account: String): Int { KeychainLibrary.ensureLoaded(); return NativeKeychain.delete(service, account) }
}

internal object NativeKeychain {
    external fun add(service: String, account: String, data: ByteArray): Int
    external fun update(service: String, account: String, data: ByteArray): Int
    external fun read(service: String, account: String): KeychainRead
    external fun aliases(service: String): KeychainAliases
    external fun delete(service: String, account: String): Int
}

private object KeychainLibrary {
    private val loaded: Unit by lazy {
        if (!System.getProperty("os.name").startsWith("Mac") || System.getProperty("os.arch") !in setOf("aarch64", "arm64")) {
            throw CredentialAccessException(CredentialFailureKind.Unsupported, "Keychain JNI supports macOS Apple Silicon only")
        }
        val resource = javaClass.getResourceAsStream("/native/macos-arm64/libmafei_keychain.dylib")
            ?: throw CredentialAccessException(CredentialFailureKind.BackendUnavailable, "Bundled Keychain JNI library missing")
        // Only a trusted bundled binary is extracted; no credential material is
        // written here. Do not search cwd/java.library.path or fall back to CLI.
        resource.use {
            val root = Files.createTempDirectory("mafei-keychain-native-")
            root.toFile().deleteOnExit()
            val library = root.resolve("libmafei_keychain.dylib")
            library.toFile().deleteOnExit()
            Files.copy(it, library)
            System.load(library.toAbsolutePath().toString())
        }
    }
    fun ensureLoaded() { loaded }
}
