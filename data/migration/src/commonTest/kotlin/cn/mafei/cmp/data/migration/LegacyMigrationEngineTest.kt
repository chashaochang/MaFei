package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.core.storage_api.*
import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class LegacyMigrationEngineTest {
    @Test fun importsBothProvidersAndSeparatesSecrets() = sync {
        val f = Fixture()
        val result = f.run() as MigrationResult.Completed
        assertFalse(result.alreadyCompleted)
        assertEquals(2, f.sink.records!!.servers.size)
        assertEquals(2, f.sink.records!!.accounts.size)
        assertEquals("remote-j", f.sink.records!!.accounts.single { it.account.provider == ProviderKind.Jellyfin }.remoteUserId)
        assertEquals(3, f.secure.values.size)
        assertTrue(f.journal.values.values.none { "SECRET" in it })
        assertTrue(f.sink.receipts.values.none { "SECRET" in it })
        assertEquals(1, f.sink.records!!.chasing.single().seriesIds.size)
        assertNotNull(f.sink.records!!.home)
    }
    @Test fun duplicateImportIsNoOpIncludingCredentialWrites() = sync {
        val f = Fixture(); f.run(); val calls = f.secure.writes
        assertTrue((f.run() as MigrationResult.Completed).alreadyCompleted)
        assertEquals(calls, f.secure.writes); assertEquals(1, f.sink.commits)
    }
    @Test fun credentialFailureBeforeWriteCanResume() = sync {
        val f = Fixture(); f.secure.failWrite = 2
        assertFailsWith<InjectedFailure> { f.run() }
        assertNull(f.sink.records); assertEquals(1, f.secure.values.size)
        f.secure.failWrite = null; f.run()
        assertEquals(3, f.secure.values.size); assertEquals(1, f.sink.commits)
    }
    @Test fun credentialFailureAfterDurableWriteCanResume() = sync {
        val f = Fixture(); f.secure.failAfterWrite = true
        assertFailsWith<InjectedFailure> { f.run() }
        f.secure.failAfterWrite = false; f.run()
        assertEquals(3, f.secure.values.size)
    }
    @Test fun businessFailureCanResumeWithoutDuplicateSecrets() = sync {
        val f = Fixture(); f.sink.failBefore = true
        assertFailsWith<InjectedFailure> { f.run() }
        val writes = f.secure.writes; f.sink.failBefore = false; f.run()
        assertEquals(writes, f.secure.writes); assertEquals(1, f.sink.commits)
    }
    @Test fun lostBusinessAcknowledgementUsesReceipt() = sync {
        val f = Fixture(); f.sink.failAfter = true
        assertFailsWith<InjectedFailure> { f.run() }
        f.sink.failAfter = false; f.secure.values.clear(); f.run()
        assertTrue(f.secure.values.isEmpty()) // Never resurrect credentials after business commit.
        assertEquals(1, f.sink.commits)
    }
    @Test fun markerFailureCanResumeFromReceiptWithoutCredentials() = sync {
        val f = Fixture(); f.journal.failMarker = true
        assertFailsWith<InjectedFailure> { f.run() }
        f.journal.failMarker = false; f.secure.values.clear(); f.run()
        assertTrue(f.secure.values.isEmpty()); assertEquals(1, f.sink.commits)
    }
    @Test fun incompleteSourcesDoNotMigrate() = sync {
        val f = Fixture(); f.snapshot = snapshot(sources = emptyMap())
        assertEquals(MigrationProblem.IncompleteSources,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
        assertTrue(f.journal.values.isEmpty()); assertTrue(f.secure.values.isEmpty())
    }
    @Test fun pendingReaderIsNotEmptySuccess() = sync {
        val f = Fixture(); f.readFailure = ReadFailure.PendingIntegration
        assertEquals(MigrationResult.Pending(ReadFailure.PendingIntegration), f.run())
        assertTrue(f.journal.values.isEmpty())
    }
    @Test fun confirmedEmptyInstallationCompletes() = sync {
        val f = Fixture(); f.snapshot = LegacySnapshot(1, "empty", absent())
        assertIs<MigrationResult.Completed>(f.run()); assertTrue(f.sink.records!!.accounts.isEmpty())
    }
    @Test fun unsupportedSnapshotVersionIsRejected() = sync {
        val f = Fixture(); f.snapshot = LegacySnapshot(2, "old", absent())
        assertEquals(MigrationProblem.UnsupportedVersion,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun danglingAccountRejectsWholeSnapshotBeforeWrites() = sync {
        val f = Fixture(); f.snapshot = LegacySnapshot(1, "old", read(), accounts = listOf(
            LegacyAccount("x", "missing", "remote", "Name")))
        assertEquals(MigrationProblem.UnknownServer,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
        assertTrue(f.secure.values.isEmpty()); assertTrue(f.journal.values.isEmpty())
    }
    @Test fun duplicateSourceIdsAreRejected() = sync {
        val f = Fixture(); val s = snapshot(); f.snapshot = LegacySnapshot(1, "old", read(),
            servers = s.servers + s.servers.first())
        assertEquals(MigrationProblem.DuplicateIdentity,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun sourceChangedDuringInterruptedMigrationIsRejected() = sync {
        val f = Fixture(); f.sink.failBefore = true
        assertFailsWith<InjectedFailure> { f.run() }
        val s = snapshot(); f.snapshot = LegacySnapshot(1, "installation-1", read(),
            s.servers, s.accounts, selectedAccountKey = "j-account")
        assertEquals(MigrationProblem.SourceChanged,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun changedCredentialsNeverOverwritePriorCapture() = sync {
        val f = Fixture(); f.sink.failBefore = true
        assertFailsWith<InjectedFailure> { f.run() }
        val alias = f.secure.values.keys.first()
        f.secure.values[alias] = SecureSecret.AuthToken("MediaBrowser", "OTHER-SECRET")
        assertEquals(MigrationProblem.CredentialConflict,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
        assertEquals("OTHER-SECRET", (f.secure.values[alias] as SecureSecret.AuthToken).token)
    }
    @Test fun captureToStringNeverContainsSecrets() {
        val s = snapshot(); assertFalse("SECRET" in s.toString())
        assertTrue(s.accounts.none { "SECRET" in it.toString() })
    }
    @Test fun signedBaseUrlIsNotImportedIntoOrdinaryStorage() = sync {
        val f = Fixture(); f.snapshot = LegacySnapshot(1, "old", read(), listOf(
            LegacyServer("s", ProviderKind.Jellyfin, "Server", "https://host/?api_key=SECRET")))
        assertEquals(MigrationProblem.InvalidField,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
        assertTrue(f.journal.values.isEmpty())
    }
    @Test fun stableIdsDoNotDependOnSourceOrder() = sync {
        val a = Fixture(); val b = Fixture(); val s = snapshot()
        b.snapshot = LegacySnapshot(1, s.installationId, s.sources, s.servers.reversed(),
            s.accounts.reversed(), s.playback, s.chasing, s.home)
        a.run(); b.run(); assertEquals(a.sink.records, b.sink.records)
    }
    @Test fun expiredHomeIsExplicitlyInvalidated() = sync {
        val f = Fixture(); f.now = LegacyMigrationPlanner.HOME_MAX_AGE_MS + 1_000
        val result = f.run() as MigrationResult.Completed
        assertEquals(setOf(MigrationWarning.HomeExpired), result.warnings)
        assertNull(f.sink.records!!.home)
    }
    @Test fun retryCrossingHomeExpiryUsesOriginalCaptureClock() = sync {
        val f = Fixture(); f.sink.failBefore = true
        assertFailsWith<InjectedFailure> { f.run() }
        f.now += LegacyMigrationPlanner.HOME_MAX_AGE_MS; f.sink.failBefore = false
        assertIs<MigrationResult.Completed>(f.run()); assertNotNull(f.sink.records!!.home)
    }
    @Test fun malformedJournalIsRejectedBeforeNewWrites() = sync {
        val f = Fixture(); f.run()
        val clockKey = f.journal.values.keys.single { "capture-time" in it }
        f.journal.values[clockKey] = "broken"
        assertEquals(MigrationProblem.CorruptedJournal,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun markerWithoutBusinessReceiptIsCorruptionNotSuccess() = sync {
        val f = Fixture(); f.run(); f.sink.receipts.clear()
        assertEquals(MigrationProblem.CorruptedJournal,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun journalFailureHappensBeforeAnySecretWrite() = sync {
        val f = Fixture(); f.journal.failPlan = true
        assertFailsWith<InjectedFailure> { f.run() }
        assertTrue(f.secure.values.isEmpty()); assertNull(f.sink.records)
        f.journal.failPlan = false; assertIs<MigrationResult.Completed>(f.run())
    }
    @Test fun cancellationPropagatesAndRetryIsSafe() = sync {
        val f = Fixture(); f.secure.cancel = true
        val failure = assertFailsWith<CancellationException> { f.run() }
        assertSame(f.secure.cancelled, failure); assertNull(f.sink.records)
        f.secure.cancel = false; assertIs<MigrationResult.Completed>(f.run())
    }
    @Test fun unknownSelectedAccountCannotFallBackToAnotherUser() = sync {
        val f = Fixture(); val s = snapshot()
        f.snapshot = LegacySnapshot(1, s.installationId, s.sources, s.servers, s.accounts,
            selectedAccountKey = "not-a-user")
        assertEquals(MigrationProblem.UnknownAccount,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun missingCredentialIsNotInventedFromOtherAccount() = sync {
        val f = Fixture(); val s = snapshot()
        f.snapshot = LegacySnapshot(1, s.installationId, s.sources, s.servers,
            listOf(LegacyAccount("j-account", "j", "remote-j", "J")))
        f.run(); assertTrue(f.secure.values.isEmpty())
        assertNull(f.sink.records!!.accounts.single().tokenAlias)
    }
    @Test fun duplicatePreferredSourceMustNotSilentlyOverwrite() = sync {
        val f = Fixture(); val s = snapshot()
        f.snapshot = LegacySnapshot(1, s.installationId, s.sources, s.servers, s.accounts,
            s.playback.copy(preferredSources = s.playback.preferredSources + s.playback.preferredSources))
        assertEquals(MigrationProblem.DuplicateIdentity,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun sourceIdentityEncodingHasNoSeparatorCollisions() {
        val planner = LegacyMigrationPlanner()
        val a = planner.plan(LegacySnapshot(1, "a/b", absent()), 1).id
        val b = planner.plan(LegacySnapshot(1, "a%2fb", absent()), 1).id
        assertNotEquals(a, b)
    }
    @Test fun absentSourceCannotClaimToContainRecords() = sync {
        val f = Fixture(); f.snapshot = snapshot(sources = absent())
        assertEquals(MigrationProblem.IncompleteSources,
            assertFailsWith<MigrationValidationException> { f.run() }.problem)
    }
    @Test fun blankLegacyDisplayNamesRemainValid() = sync {
        val f = Fixture(); f.snapshot = LegacySnapshot(1, "old", read(),
            listOf(LegacyServer("f", ProviderKind.Feiniu, "", "https://f.example")),
            listOf(LegacyAccount("f-account", "f", "uid", "")))
        f.run(); assertEquals("", f.sink.records!!.accounts.single().account.userName)
    }
}

private class InjectedFailure : Exception("injected")
private class Fixture {
    var snapshot = snapshot(); var readFailure: ReadFailure? = null
    var now = 1_000L
    val journal = FakeJournal(); val secure = FakeSecure(); val sink = FakeSink()
    suspend fun run() = LegacyMigrationEngine(
        { readFailure?.let { LegacyReadResult.Unavailable(it) } ?: LegacyReadResult.Captured(snapshot) },
        secure, journal, sink, { now }).migrate()
}
private fun read() = LegacySource.entries.associateWith { LegacySourceState.Read }
private fun absent() = LegacySource.entries.associateWith { LegacySourceState.ConfirmedAbsent }
private fun snapshot(sources: Map<LegacySource, LegacySourceState> = read()) = LegacySnapshot(
    1, "installation-1", sources,
    listOf(LegacyServer("j", ProviderKind.Jellyfin, "Jelly", "https://j.example/media"),
        LegacyServer("f", ProviderKind.Feiniu, "NAS", "http://f.example:5667")),
    listOf(LegacyAccount("j-account", "j", "remote-j", "J", SecureSecret.AuthToken("MediaBrowser", "J-SECRET")),
        LegacyAccount("f-account", "f", "remote-f", "F", SecureSecret.AuthToken("Bearer", "F-SECRET"),
            SecureSecret.CookieText("Cookie-SECRET"))),
    LegacyPlaybackPreferences("auto", true, 1024, listOf(LegacyPreferredSource("j-account", "movie", "source"))),
    listOf(LegacyChasing("j-account", listOf(" series ", "series"))),
    LegacyHomeSnapshot(2, "j-account", 900, resume = listOf(MigratedHomeItem("movie", "A movie"))),
)

private class FakeSink : MigrationSink {
    val receipts = mutableMapOf<String, String>(); var records: MigrationRecords? = null
    var commits = 0; var failBefore = false; var failAfter = false; private var leased = false
    override suspend fun <T> exclusive(block: suspend () -> T): T {
        check(!leased); leased = true; try { return block() } finally { leased = false }
    }
    override suspend fun receipt(migrationId: String): String? { check(leased); return receipts[migrationId] }
    override suspend fun commit(migrationId: String, manifest: String, records: MigrationRecords) {
        check(leased); if (failBefore) throw InjectedFailure()
        receipts[migrationId]?.let { check(it == manifest); return }
        this.records = records; receipts[migrationId] = manifest; commits++
        if (failAfter) throw InjectedFailure()
    }
}
private class FakeJournal : VersionedStorage {
    val values = mutableMapOf<String, String>(); var failMarker = false
    var failPlan = false
    override suspend fun getString(key: String) = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun getBytes(key: String): ByteArray? = error("unused")
    override suspend fun putBytes(key: String, value: ByteArray) = error("unused")
    override suspend fun remove(key: String) = values.remove(key) != null
    override suspend fun contains(key: String) = key in values
    override suspend fun listKeys(prefix: String?) = values.keys.filter { prefix == null || it.startsWith(prefix) }.sorted()
    override suspend fun edit(mutate: StorageEditor.() -> Unit) {
        val next = values.toMutableMap()
        val editor = object : StorageEditor {
            override fun putString(key: String, value: String) {
                if (failMarker && key.startsWith("\$mafei.schema.migration.")) throw InjectedFailure()
                if (failPlan && key.startsWith("\$mafei.migration.plan.")) throw InjectedFailure()
                next[key] = value
            }
            override fun putBytes(key: String, value: ByteArray) = error("unused")
            override fun remove(key: String) { next.remove(key) }
        }
        editor.mutate(); values.clear(); values.putAll(next)
    }
}
private class FakeSecure : SecureCredentialStore {
    val values = mutableMapOf<CredentialAlias, SecureSecret>(); var writes = 0
    var failWrite: Int? = null; var failAfterWrite = false
    var cancel = false; val cancelled = CancellationException("injected cancellation")
    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) {
        writes++; if (cancel) throw cancelled
        if (writes == failWrite) throw InjectedFailure()
        if (!overwrite && alias in values) throw CredentialAccessException(CredentialFailureKind.AlreadyExists)
        values[alias] = secret; if (failAfterWrite) throw InjectedFailure()
    }
    override suspend fun read(alias: CredentialAlias) = values[alias]
    override suspend fun exists(alias: CredentialAlias) = alias in values
    override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
    override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
    override suspend fun deleteAll(prefix: String?): Int {
        val keys = listAliases(prefix); keys.forEach { values.remove(it) }; return keys.size
    }
}

/** Only the in-memory fakes above are synchronous; never use this for real platform operations. */
private fun sync(block: suspend () -> Unit) {
    var completion: Result<Unit>? = null
    block.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { completion = result }
    })
    checkNotNull(completion) { "Test unexpectedly suspended" }.getOrThrow()
}
