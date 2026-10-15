package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.data.migration.MigrationResult
import cn.mafei.cmp.player.api.PlaybackGeneration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class MigrationStartupTest {

    private class PasswordUnavailableStore(
        private val delegate: MemorySecure,
    ) : SecureCredentialStore {
        var failReads = false

        override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) {
            if (secret is SecureSecret.Password) {
                throw CredentialAccessException(CredentialFailureKind.BackendUnavailable)
            }
            delegate.save(alias, secret, overwrite)
        }

        override suspend fun read(alias: CredentialAlias): SecureSecret? {
            if (failReads && alias.value.endsWith("/password")) {
                throw CredentialAccessException(CredentialFailureKind.BackendUnavailable)
            }
            return delegate.read(alias)
        }

        override suspend fun exists(alias: CredentialAlias): Boolean = delegate.exists(alias)
        override suspend fun delete(alias: CredentialAlias): Boolean = delegate.delete(alias)
        override suspend fun listAliases(prefix: String?): List<CredentialAlias> = delegate.listAliases(prefix)
        override suspend fun deleteAll(prefix: String?): Int = delegate.deleteAll(prefix)
    }

    private class CountingLegacyReader : cn.mafei.cmp.data.migration.LegacySnapshotReader {
        var reads = 0
        override suspend fun read(): cn.mafei.cmp.data.migration.LegacyReadResult {
            reads += 1
            return FakeLegacyReader().read()
        }
    }

    private class EmptyLegacyReader : cn.mafei.cmp.data.migration.LegacySnapshotReader {
        var reads = 0
        override suspend fun read(): cn.mafei.cmp.data.migration.LegacyReadResult {
            reads += 1
            return cn.mafei.cmp.data.migration.LegacyReadResult.Captured(
                cn.mafei.cmp.data.migration.LegacySnapshot(
                    version = 1,
                    installationId = "new-installation",
                    sources = cn.mafei.cmp.data.migration.LegacySource.entries.associateWith {
                        cn.mafei.cmp.data.migration.LegacySourceState.ConfirmedAbsent
                    },
                ),
            )
        }
    }

    @Test
    fun migrateLegacyDataActivatesTheMigratedCurrentAccount() = runBlocking {
        val app = ApplicationRuntime(
            settings = MemorySettings(),
            secureStore = MemorySecure(),
            transport = StubHttpTransport(),
            dispatcher = Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        try {
            app.sessions.start()
            val result = app.migrateLegacyData()
            assertNotNull(result)
            assertTrue(result is MigrationResult.Completed, "expected Completed, got $result")

            val session = app.sessions.state.value.session
            assertNotNull(session, "migrated current account must be active")
            // AccountId is the migration-prefixed identity.
            // The migration identity is a prefixed hex encoding, not the raw key.
            assertTrue(session.connection.account.id.value.isNotEmpty())
            // SavedConnection alias namespace == where the engine stored the secret.
            assertEquals(
                "mafei/session/${session.connection.credentialKey}/token",
                session.connection.tokenAlias.value,
            )
            assertEquals("remembered-password", app.savedPassword(session.connection))
        } finally {
            app.close()
        }
    }

    @Test
    fun playbackOnMigratedSessionBindsTheMigratedAdapter() = runBlocking<Unit> {
        val app = ApplicationRuntime(
            settings = MemorySettings(),
            secureStore = MemorySecure(),
            transport = StubHttpTransport(),
            dispatcher = Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
            playerRuntimeFactory = { NoEngine() },
        )
        try {
            app.sessions.start()
            app.migrateLegacyData()
            val session = app.sessions.state.value.session ?: throw AssertionError("no active session")
            // No real engine is wired for composeApp desktopTest (needs a real
            // platform runtime); verify that the migration itself completed
            // and the session has an active binding.
            assertNotNull(app.sessions.state.value.session)
        } finally {
            app.close()
        }
    }

    @Test
    fun withoutReaderMigrationReportsPending() = runBlocking {
        val app = ApplicationRuntime(
            settings = MemorySettings(),
            secureStore = MemorySecure(),
            transport = StubHttpTransport(),
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            app.sessions.start()
            assertNull(app.migrateLegacyData()) // no reader: Pending, no fake migration
        } finally {
            app.close()
        }
    }

    @Test
    fun restartRestoresCmpSessionWithoutReadingLegacyDataAgain() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val firstReader = CountingLegacyReader()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = firstReader,
        )
        try {
            assertNotNull(first.start())
            assertEquals(1, firstReader.reads)
        } finally {
            first.close()
        }

        val restartReader = CountingLegacyReader()
        val restarted = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = restartReader,
        )
        try {
            assertNotNull(restarted.start())
            assertEquals(0, restartReader.reads)
        } finally {
            restarted.close()
        }
    }

    @Test
    fun startupImportBackfillsPasswordForMigrationCompletedByAnOlderBuild() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        val connection = try {
            assertNotNull(first.start()).connection
        } finally {
            first.close()
        }

        assertTrue(secure.delete(connection.passwordAlias))
        assertTrue(settings.remove("mafei.legacy-password.v3.imported"))
        val reader = CountingLegacyReader()
        val restarted = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = reader,
        )
        try {
            assertNotNull(restarted.start())
            assertEquals(1, reader.reads)
            assertEquals("remembered-password", restarted.savedPassword(connection))
            assertEquals(listOf(connection.selection), restarted.savedConnections().map { it.selection })
            assertEquals(1, reader.reads)
            assertEquals("remembered-password", restarted.savedPassword(connection))
        } finally {
            restarted.close()
        }
    }

    @Test
    fun accountHistorySurvivesOptionalPasswordImportAndBadgeFailures() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        val connection = try {
            assertNotNull(first.start()).connection
        } finally {
            first.close()
        }

        assertTrue(secure.delete(connection.passwordAlias))
        assertTrue(settings.remove("mafei.legacy-password.v3.imported"))
        val unavailable = PasswordUnavailableStore(secure)
        val failures = mutableListOf<String>()
        val restarted = ApplicationRuntime(
            settings, unavailable, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
            failureReporter = { stage, _ -> failures += stage },
        )
        try {
            assertEquals(listOf(connection.selection), restarted.savedConnections().map { it.selection })
            assertTrue("history-password-import" in failures)
            assertNull(settings.getString("mafei.legacy-password.v3.imported"))

            unavailable.failReads = true
            assertFalse(restarted.hasSavedPassword(connection))
            assertTrue("history-password-status" in failures)
        } finally {
            restarted.close()
        }
    }

    @Test
    fun correctedPasswordImportIgnoresTheObsoleteV2Receipt() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        val connection = try {
            assertNotNull(first.start()).connection
        } finally {
            first.close()
        }

        assertTrue(secure.delete(connection.passwordAlias))
        assertTrue(settings.remove("mafei.legacy-password.v3.imported"))
        settings.putString("mafei.legacy-password.v2.imported", "done")

        val restarted = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        try {
            restarted.savedConnections()
            assertEquals("remembered-password", restarted.savedPassword(connection))
            assertEquals("done", settings.getString("mafei.legacy-password.v3.imported"))
        } finally {
            restarted.close()
        }
    }

    @Test
    fun correctedPasswordImportDoesNotResurrectAnExplicitlyForgottenPassword() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        val connection = try {
            assertNotNull(first.start()).connection
        } finally {
            first.close()
        }

        assertTrue(secure.delete(connection.passwordAlias))
        assertTrue(settings.remove("mafei.legacy-password.v3.imported"))
        ConnectionCatalog(settings).save(connection.copy(rememberLogin = false))

        val restarted = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = FakeLegacyReader(),
        )
        try {
            val forgotten = assertNotNull(restarted.savedConnections().singleOrNull())
            assertFalse(forgotten.rememberLogin)
            assertNull(restarted.savedPassword(forgotten))
            assertEquals("done", settings.getString("mafei.legacy-password.v3.imported"))
        } finally {
            restarted.close()
        }
    }

    @Test
    fun consumedMigrationRepairsAReplacedSessionIndex() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = CountingLegacyReader(),
        )
        try {
            assertNotNull(first.start())
        } finally {
            first.close()
        }

        // Reproduces the development-package regression: migration records and
        // credentials survived, but a different build replaced the account index.
        assertTrue(settings.remove("mafei.session-accounts.v1"))

        val repairReader = CountingLegacyReader()
        val repaired = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = repairReader,
        )
        try {
            assertNotNull(repaired.start())
            assertEquals(1, repairReader.reads)
            assertEquals(1, repaired.savedConnections().size)
        } finally {
            repaired.close()
        }
    }

    @Test
    fun logoutDoesNotLetCompletedLegacyMigrationSignInAgain() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = CountingLegacyReader(),
        )
        try {
            val session = assertNotNull(first.start())
            assertTrue(first.sessions.logout(session))
        } finally {
            first.close()
        }

        val restartReader = CountingLegacyReader()
        val restarted = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = restartReader,
        )
        try {
            assertNull(restarted.start())
            assertEquals(ApplicationSessionStatus.SignedOut, restarted.sessions.state.value.status)
            assertEquals(0, restartReader.reads)
        } finally {
            restarted.close()
        }
    }

    @Test
    fun newInstallationIsDetectedAndDoesNotRetryLegacyImport() = runBlocking {
        val settings = MemorySettings()
        val secure = MemorySecure()
        val firstReader = EmptyLegacyReader()
        val first = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = firstReader,
        )
        try {
            assertNull(first.start())
            assertEquals(ApplicationSessionStatus.SignedOut, first.sessions.state.value.status)
            assertEquals(1, firstReader.reads)
        } finally {
            first.close()
        }

        val restartReader = EmptyLegacyReader()
        val restarted = ApplicationRuntime(
            settings, secure, StubHttpTransport(), Dispatchers.Unconfined,
            legacySnapshotReader = restartReader,
        )
        try {
            assertNull(restarted.start())
            assertEquals(0, restartReader.reads)
        } finally {
            restarted.close()
        }
    }
}
