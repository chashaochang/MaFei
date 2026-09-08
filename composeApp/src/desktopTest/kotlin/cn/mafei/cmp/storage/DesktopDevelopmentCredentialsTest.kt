package cn.mafei.cmp.storage

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.session.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*

class DesktopDevelopmentCredentialsTest {
    private val token = CredentialAlias("account/fixture/token")
    private val cookie = CredentialAlias("account/fixture/cookie")

    private fun temporary(block: suspend (File) -> Unit) = runBlocking<Unit> {
        val root = Files.createTempDirectory("mafei-development-credentials-test-").toFile().canonicalFile
        try { block(root) } finally { root.deleteRecursively() }
    }

    @Test fun missingRestartOverwriteDeleteAndPermissions() = temporary { root ->
        val directory = File(root, "credentials")
        val first = DesktopDevelopmentCredentials(directory)
        assertNull(first.read(token)); assertFalse(first.exists(token)); assertFalse(first.delete(token))
        first.save(token, SecureSecret.AuthToken("Bearer", "synthetic-first"))
        first.save(cookie, SecureSecret.CookieText("synthetic-cookie"))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(directory.toPath()))
        assertEquals(PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(File(directory, "sessions.json").toPath()))
        val restarted = DesktopDevelopmentCredentials(directory)
        assertEquals(SecureSecret.AuthToken("Bearer", "synthetic-first"), restarted.read(token))
        assertEquals(SecureSecret.CookieText("synthetic-cookie"), restarted.read(cookie))
        assertEquals(listOf(cookie, token), restarted.listAliases())
        assertEquals(CredentialFailureKind.AlreadyExists,
            assertFailsWith<CredentialAccessException> { restarted.save(token, SecureSecret.AuthToken("Bearer", "other"), false) }.kind)
        restarted.save(token, SecureSecret.AuthToken("Bearer", "synthetic-next"))
        assertEquals(SecureSecret.AuthToken("Bearer", "synthetic-next"), first.read(token))
        assertTrue(restarted.delete(token)); assertNull(first.read(token))
        assertEquals(1, restarted.deleteAll("account/fixture/")); assertTrue(first.listAliases().isEmpty())
        assertEquals(listOf("sessions.json"), directory.list()!!.toList())
    }

    @Test fun passwordsAndArbitraryBytesAreNeverWritten() = temporary { root ->
        val store = DesktopDevelopmentCredentials(root)
        for (secret in listOf(SecureSecret.Password("synthetic"), SecureSecret.RawBytes(byteArrayOf(1)))) {
            assertEquals(CredentialFailureKind.Unsupported,
                assertFailsWith<CredentialAccessException> { store.save(token, secret) }.kind)
        }
        assertTrue(root.list()!!.isEmpty())
    }

    @Test fun malformedDocumentFailsClosedAndCannotBeOverwritten() = temporary { root ->
        val file = File(root, "sessions.json")
        val store = DesktopDevelopmentCredentials(root)
        store.save(token, SecureSecret.AuthToken("Bearer", "synthetic"))
        val corruptions = listOf("broken", "{\"version\":2,\"entries\":[]}",
            "{\"version\":1,\"entries\":[{\"alias\":\"a\",\"kind\":\"password\",\"scheme\":\"\",\"value\":\"bad\"}]}")
        for (corruption in corruptions) {
            file.writeText(corruption)
            assertFailsWith<CredentialAccessException> { store.read(token) }
            assertFailsWith<CredentialAccessException> { store.save(token, SecureSecret.AuthToken("Bearer", "new")) }
            assertEquals(corruption, file.readText())
        }
    }

    @Test fun symlinksAndRelaxedFilePermissionsAreRejected() = temporary { root ->
        val directory = File(root, "credentials")
        val store = DesktopDevelopmentCredentials(directory)
        store.save(token, SecureSecret.AuthToken("Bearer", "synthetic"))
        val file = File(directory, "sessions.json")
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        assertFailsWith<CredentialAccessException> { store.read(token) }
        assertFailsWith<CredentialAccessException> { store.delete(token) }
        Files.delete(file.toPath())
        val untouched = File(root, "untouched.txt").apply { writeText("keep") }
        Files.createSymbolicLink(file.toPath(), untouched.toPath())
        assertFailsWith<CredentialAccessException> { store.save(token, SecureSecret.AuthToken("Bearer", "new")) }
        assertEquals("keep", untouched.readText())
    }

    @Test fun actualFileBackendsAdmitRestoreReadLibrariesAndLogout() = temporary { root ->
        class Transport : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                val body = if (request.url.toEncodedString().contains("AuthenticateByName"))
                    """{"User":{"Id":"fixture-user","Name":"Fixture"},"AccessToken":"synthetic-auth-token","ServerId":"fixture-server"}"""
                else """{"Items":[],"TotalRecordCount":0}"""
                return HttpResponse(200, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
            }
            override fun close() = Unit
        }
        fun runtime() = ApplicationRuntime(
            DocumentSettingsStorage(DesktopSettingsDocument(File(root, "settings"))),
            DesktopDevelopmentCredentials(File(root, "credentials")), Transport(), Dispatchers.Default)
        val first = runtime()
        val selection = try {
            assertNull(first.sessions.start())
            first.connect(ProviderKind.Jellyfin, "https://fixture.test", "Fixture", "synthetic-password").selection
        } finally { first.close() }
        assertFalse(File(root, "settings/settings.json").readText().contains("synthetic-auth-token"))
        assertFalse(File(root, "credentials/sessions.json").readText().contains("synthetic-password"))
        val reopened = runtime()
        try {
            val session = assertNotNull(reopened.sessions.start())
            assertEquals(selection, session.selection)
            reopened.sessions.media.collections(session.media)
            assertEquals(ApplicationSessionStatus.Ready, reopened.sessions.state.value.status)
            assertTrue(reopened.sessions.logout(session))
        } finally { reopened.close() }
        val signedOut = runtime()
        try { assertNull(signedOut.sessions.start()) } finally { signedOut.close() }
    }
}
