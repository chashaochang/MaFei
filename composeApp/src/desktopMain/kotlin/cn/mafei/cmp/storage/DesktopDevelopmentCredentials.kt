package cn.mafei.cmp.storage

import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.platform.secure_storage_macos.MacOSSecureCredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

const val DESKTOP_DEVELOPMENT_STORAGE_PROPERTY = "mafei.desktop.developmentStorage"

fun usesDesktopDevelopmentStorage(): Boolean =
    System.getProperty(DESKTOP_DEVELOPMENT_STORAGE_PROPERTY) == "true"

fun createDesktopCredentialStore(): SecureCredentialStore = if (usesDesktopDevelopmentStorage()) {
    val home = System.getProperty("user.home")?.takeIf(String::isNotBlank) ?: credentialFailure()
    DesktopDevelopmentCredentials(File(home,
        "Library/Application Support/cn.mafei.cmp/credentials-development-v1").canonicalFile)
} else MacOSSecureCredentialStore()

/**
 * Explicit local-development exception, NOT a secure storage implementation for distribution.
 * The user requested a simple Mac test store to unblock login/home acceptance. Tokens/cookies
 * are stored UNENCRYPTED with owner-only permissions; passwords and arbitrary raw secrets are
 * rejected. This never activates as a fallback from a Keychain error. Shipping builds retain
 * the platform store; packaging requires an explicit development flag and forbids DMG/release.
 *
 * One app process owns this document. Mutex serialization is not cross-process locking.
 * Only this isolated document is read/replaced/deleted; no Keychain or other app files are swept.
 */
internal class DesktopDevelopmentCredentials(private val directory: File) : SecureCredentialStore {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }

    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) = locked {
        val entry = when (secret) {
            is SecureSecret.AuthToken -> Entry(alias.value, "token", secret.scheme, secret.token)
            is SecureSecret.CookieText -> Entry(alias.value, "cookie", "", secret.value)
            else -> throw CredentialAccessException(CredentialFailureKind.Unsupported,
                "Development storage accepts only session tokens and cookies")
        }
        val entries = load().toMutableMap()
        if (!overwrite && alias.value in entries) throw CredentialAccessException(CredentialFailureKind.AlreadyExists)
        entries[alias.value] = entry
        replace(entries)
    }

    override suspend fun read(alias: CredentialAlias): SecureSecret? = locked {
        load()[alias.value]?.let { entry -> when (entry.kind) {
            "token" -> SecureSecret.AuthToken(entry.scheme, entry.value)
            "cookie" -> SecureSecret.CookieText(entry.value)
            else -> credentialFailure()
        } }
    }
    override suspend fun exists(alias: CredentialAlias): Boolean = locked { alias.value in load() }
    override suspend fun delete(alias: CredentialAlias): Boolean = locked {
        val entries = load().toMutableMap()
        if (entries.remove(alias.value) == null) false else { replace(entries); true }
    }
    override suspend fun listAliases(prefix: String?): List<CredentialAlias> = locked {
        load().keys.filter { prefix == null || it.startsWith(prefix) }.sorted().map(::CredentialAlias)
    }
    override suspend fun deleteAll(prefix: String?): Int = locked {
        val entries = load()
        val retained = entries.filterKeys { prefix != null && !it.startsWith(prefix) }
        (entries.size - retained.size).also { if (it != 0) replace(retained) }
    }

    private fun target(): File {
        // Resolve the trusted app parent before construction, but never accept a linked store.
        if (directory.absoluteFile != directory.canonicalFile) credentialFailure()
        Files.createDirectories(directory.toPath(), PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS))
        if (!Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS)) credentialFailure()
        Files.setPosixFilePermissions(directory.toPath(), DIRECTORY_PERMISSIONS)
        return File(directory, "sessions.json").also {
            if (it.absoluteFile != it.canonicalFile) credentialFailure()
        }
    }

    private fun load(): Map<String, Entry> {
        val target = target().toPath()
        val attributes = try { Files.readAttributes(target, BasicFileAttributes::class.java, NOFOLLOW_LINKS) }
        catch (_: NoSuchFileException) { return emptyMap() }
        if (!attributes.isRegularFile || attributes.size() !in 1..MAX_BYTES.toLong()) credentialFailure()
        // Refuse a permissions-regressed credential file instead of quietly reading it.
        if (Files.getPosixFilePermissions(target, NOFOLLOW_LINKS) != FILE_PERMISSIONS) credentialFailure()
        val bytes = Files.newInputStream(target, NOFOLLOW_LINKS).use { input ->
            input.readNBytes(MAX_BYTES + 1).also { if (it.isEmpty() || it.size > MAX_BYTES) credentialFailure() }
        }
        try {
            val document = json.decodeFromString<Document>(bytes.decodeToString(throwOnInvalidSequence = true))
            if (document.version != 1 || document.entries.size > 4096) credentialFailure()
            val entries = linkedMapOf<String, Entry>()
            document.entries.forEach {
                CredentialAlias(it.alias)
                if (it.alias.length > 1024 || it.alias in entries ||
                    it.kind !in setOf("token", "cookie") || it.kind == "cookie" && it.scheme.isNotEmpty()) credentialFailure()
                entries[it.alias] = it
            }
            return entries
        } finally { bytes.fill(0) }
    }

    private fun replace(entries: Map<String, Entry>) {
        if (entries.size > 4096 || entries.keys.any { it.length > 1024 }) credentialFailure()
        val target = target().toPath()
        val bytes = json.encodeToString(Document(1, entries.toSortedMap().values.toList()))
            .encodeToByteArray(throwOnInvalidSequence = true)
        try {
            if (bytes.size > MAX_BYTES) credentialFailure()
            val temporary = Files.createTempFile(directory.toPath(), ".sessions-", ".tmp",
                PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
            try {
                FileOutputStream(temporary.toFile()).use { it.write(bytes); it.fd.sync() }
                // No fallible checks after the atomic commit point; no non-atomic fallback.
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { try { Files.deleteIfExists(temporary) } catch (_: Exception) { } }
        } finally { bytes.fill(0) }
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: CredentialAccessException) { throw failure }
            catch (_: Exception) { credentialFailure() } // Never expose file paths or credential document.
        }
    }

    @Serializable private data class Document(val version: Int, val entries: List<Entry>)
    @Serializable private data class Entry(val alias: String, val kind: String, val scheme: String, val value: String)

    companion object {
        private const val MAX_BYTES = 1024 * 1024
        private val DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------")
        private val FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------")
    }
}

private fun credentialFailure(): Nothing = throw CredentialAccessException(
    CredentialFailureKind.StorageFailure, "Development session storage unavailable or invalid")
