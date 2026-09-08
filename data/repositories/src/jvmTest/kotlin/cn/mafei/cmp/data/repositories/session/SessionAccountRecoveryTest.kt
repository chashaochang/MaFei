package cn.mafei.cmp.data.repositories.session

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.core.storage_api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

/** Focused restart tests; store copies at a write boundary model process interruption. */
class SessionAccountRecoveryTest {
    private class Settings : SettingsStorage {
        val values = mutableMapOf<String, String>()
        var beforeEdit: () -> Unit = {}
        override suspend fun getString(key: String) = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun getBytes(key: String): ByteArray? = error("unused")
        override suspend fun putBytes(key: String, value: ByteArray) = error("unused")
        override suspend fun remove(key: String) = values.remove(key) != null
        override suspend fun contains(key: String) = key in values
        override suspend fun listKeys(prefix: String?) = values.keys.filter { prefix == null || it.startsWith(prefix) }.sorted()
        override suspend fun edit(mutate: StorageEditor.() -> Unit) {
            val next = values.toMutableMap()
            object : StorageEditor {
                override fun putString(key: String, value: String) { next[key] = value }
                override fun putBytes(key: String, value: ByteArray) = error("unused")
                override fun remove(key: String) { next.remove(key) }
            }.mutate()
            beforeEdit()
            values.clear(); values.putAll(next)
        }
        fun copy() = Settings().also { it.values.putAll(values) }
    }

    private class Secrets : SecureCredentialStore {
        val values = mutableMapOf<CredentialAlias, SecureSecret>()
        var beforeSave: suspend (CredentialAlias) -> Unit = {}
        var beforeDelete: suspend (CredentialAlias) -> Unit = {}
        override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { beforeSave(alias); values[alias] = secret }
        override suspend fun read(alias: CredentialAlias) = values[alias]
        override suspend fun delete(alias: CredentialAlias): Boolean { beforeDelete(alias); return values.remove(alias) != null }
        override suspend fun exists(alias: CredentialAlias) = alias in values
        override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
        override suspend fun deleteAll(prefix: String?) = error("must use scoped aliases")
        fun copy() = Secrets().also { it.values.putAll(values) }
    }

    private fun selection(account: String = "a", server: String = "s") = SessionSelection(ProviderKind.Feiniu, ServerId(server), AccountId(account))
    private fun aliases(account: String = "a") = setOf(CredentialAlias("$account/token"), CredentialAlias("$account/cookie"))
    private fun snapshot(account: String = "a", revision: Long = 1, value: String = "original") = SessionCredentialSnapshot(1, revision,
        mapOf(CredentialAlias("$account/token") to SecureSecret.AuthToken("Bearer", "$value-token"), CredentialAlias("$account/cookie") to SecureSecret.CookieText("$value-cookie")))
    private fun source(flow: MutableStateFlow<SessionCredentialSnapshot>) = StateFlowCredentialSource(flow) { it }

    @Test fun savingThenNewRepositoryRestoresWholeCurrentAccountWithoutSecretsInSettings() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        val restarted = SessionRepository(secrets, settings)
        val restored = assertNotNull(restarted.restoreCurrent())
        assertEquals(selection(), restored.selection)
        assertEquals(snapshot().secrets, restored.snapshot.secrets)
        assertEquals(listOf(selection()), restarted.savedAccounts())
        assertEquals(null, restarted.state.value.selection) // SDK installation is not faked.
        assertFalse(settings.values.values.single().contains("original-token"))
        assertFalse(settings.values.values.single().contains("original-cookie"))
        assertFalse(restored.toString().contains("original-token"))
    }

    @Test fun logoutCommitsRevocationBeforeDeleteSoInterruptedDeletionCannotRestore() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val handle = repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        lateinit var restartSettings: Settings; lateinit var restartSecrets: Secrets
        secrets.beforeDelete = {
            restartSettings = settings.copy(); restartSecrets = secrets.copy()
            throw CredentialAccessException(CredentialFailureKind.StorageFailure)
        }
        assertFailsWith<CredentialAccessException> { repo.logout(handle) }
        assertNull(SessionRepository(restartSecrets, restartSettings).restoreCurrent())
        assertNull(SessionRepository(secrets, settings).restoreAccount(selection()))
        secrets.beforeDelete = {}; repo.retryCleanup()
        assertTrue(secrets.values.isEmpty())
        assertEquals(emptyList(), SessionRepository(secrets, settings).savedAccounts())
    }

    @Test fun successfulLogoutDoesNotAutomaticallyRestoreAnotherSavedAccount() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        val b = repo.select(selection("b"), aliases("b"), source(MutableStateFlow(snapshot("b"))))
        assertTrue(repo.logout(b))
        val restarted = SessionRepository(secrets, settings)
        assertNull(restarted.restoreCurrent())
        assertEquals(listOf(selection()), restarted.savedAccounts())
        assertNotNull(restarted.restoreAccount(selection()))
        assertNull(restarted.restoreAccount(selection("b")))
    }

    @Test fun snapshotTakenBetweenTokenAndCookieWritesCannotRecoverMixedGeneration() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val flow = MutableStateFlow(snapshot()); val handle = repo.select(selection(), aliases(), source(flow))
        lateinit var crashedSettings: Settings; lateinit var crashedSecrets: Secrets
        secrets.beforeSave = { alias -> if (alias.value.endsWith("/cookie")) {
            crashedSettings = settings.copy(); crashedSecrets = secrets.copy()
        } }
        flow.value = snapshot(revision = 2, value = "rotated")
        repo.persistLatest(handle)
        assertEquals(SecureSecret.AuthToken("Bearer", "rotated-token"), crashedSecrets.values[CredentialAlias("a/token")])
        assertEquals(SecureSecret.CookieText("original-cookie"), crashedSecrets.values[CredentialAlias("a/cookie")])
        val crashed = SessionRepository(crashedSecrets, crashedSettings)
        assertNull(crashed.restoreCurrent()); assertNull(crashed.restoreAccount(selection()))
        assertEquals(snapshot(revision = 2, value = "rotated").secrets, SessionRepository(secrets, settings).restoreCurrent()!!.snapshot.secrets)
    }

    @Test fun settingsCommitFailureAfterCredentialWritesDoesNotPublishRecoverableAccount() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        secrets.beforeSave = { alias -> if (alias.value.endsWith("/cookie")) settings.beforeEdit = { throw StorageUnavailableException("fixture failure") } }
        assertFailsWith<StorageUnavailableException> { repo.select(selection(), aliases(), source(MutableStateFlow(snapshot()))) }
        settings.beforeEdit = {}
        val restarted = SessionRepository(secrets, settings)
        assertNull(restarted.restoreCurrent()); assertNull(restarted.restoreAccount(selection()))
    }

    @Test fun aMissingCommittedCredentialRejectsTheEntireGroup() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        secrets.values.remove(CredentialAlias("a/cookie"))
        assertNull(SessionRepository(secrets, settings).restoreCurrent())
        assertNull(SessionRepository(secrets, settings).restoreAccount(selection()))
    }

    @Test fun differentAccountsCannotReuseOneSavedCredentialAlias() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val a = repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        assertFailsWith<IllegalArgumentException> {
            repo.select(selection("b"), aliases(), source(MutableStateFlow(snapshot(value = "b"))))
        }
        assertEquals(selection(), repo.state.value.selection)
        assertEquals(snapshot().secrets, SessionRepository(secrets, settings).restoreCurrent()!!.snapshot.secrets)
        assertTrue(repo.logout(a))
    }

    @Test fun failedBeginMarkerDoesNotTouchOldCredentialsOrMixedRestore() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val flow = MutableStateFlow(snapshot()); val handle = repo.select(selection(), aliases(), source(flow))
        val oldValues = secrets.values.toMap()
        var saves = 0; secrets.beforeSave = { saves++ }
        settings.beforeEdit = { throw StorageUnavailableException("fixture failure") }
        flow.value = snapshot(revision = 2, value = "must-not-write")
        assertFailsWith<StorageUnavailableException> { repo.persistLatest(handle) }
        assertEquals(0, saves)
        // A failed durable admission may keep the old complete login; no new field was written.
        assertEquals(oldValues, secrets.values)
        settings.beforeEdit = {}
        val recovered = SessionRepository(secrets, settings).restoreCurrent()
        assertEquals(snapshot().secrets, recovered!!.snapshot.secrets)
    }

    @Test fun corruptIndexFailsClosedInsteadOfInventingEmptyState() = runBlocking<Unit> {
        val settings = Settings(); settings.values["mafei.session-accounts.v1"] = "not-json"
        assertFailsWith<StorageCorruptedException> { SessionRepository(Secrets(), settings).restoreCurrent() }
    }

    @Test fun failedLogoutMarkerRetainsCleanupIdentityAndDeletesNothingUntilRetry() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val handle = repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        val original = secrets.values.toMap()
        settings.beforeEdit = { throw StorageUnavailableException("fixture failure") }
        assertFailsWith<StorageUnavailableException> { repo.logout(handle) }
        assertEquals(original, secrets.values)
        assertEquals(SessionPersistenceStatus.CleanupRequired, repo.state.value.status)
        assertFalse(repo.persistLatest(handle))
        settings.beforeEdit = {}
        // The operation failed, so only the previous complete login may recover.
        assertNotNull(SessionRepository(secrets, settings).restoreCurrent())
        repo.retryCleanup()
        assertTrue(secrets.values.isEmpty())
        assertNull(SessionRepository(secrets, settings).restoreCurrent())
    }

    @Test fun reselectingSavedAccountAfterRestartRemovesOldAliasesBeforePublishingNewGroup() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        repo.select(selection(), aliases(), source(MutableStateFlow(snapshot())))
        val restarted = SessionRepository(secrets, settings)
        val newAlias = CredentialAlias("a/new-token")
        val next = SessionCredentialSnapshot(2, 1, mapOf(newAlias to SecureSecret.AuthToken("Bearer", "new")))
        restarted.select(selection(), setOf(newAlias), source(MutableStateFlow(next)))
        assertEquals(setOf(newAlias), secrets.values.keys)
        assertEquals(next.secrets, SessionRepository(secrets, settings).restoreCurrent()!!.snapshot.secrets)
    }

    @Test fun deliberatelyAbsentOptionalCookieDoesNotRequireAnInventedSecureValue() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val flow = MutableStateFlow(snapshot()); val handle = repo.select(selection(), aliases(), source(flow))
        val token = CredentialAlias("a/token")
        flow.value = SessionCredentialSnapshot(1, 2, mapOf(token to SecureSecret.AuthToken("Bearer", "token-only")))
        assertTrue(repo.persistLatest(handle))
        val restored = SessionRepository(secrets, settings).restoreCurrent()!!
        assertEquals(setOf(token), restored.snapshot.secrets.keys)
        assertNull(secrets.values[CredentialAlias("a/cookie")])
    }

    @Test fun sdkIdentityChangeDuringIndexCommitCannotPublishReadyOrRemainRestorable() = runBlocking<Unit> {
        val settings = Settings(); val secrets = Secrets(); val repo = SessionRepository(secrets, settings)
        val flow = MutableStateFlow(snapshot()); val handle = repo.select(selection(), aliases(), source(flow))
        flow.value = snapshot(revision = 2, value = "rotated")
        var edits = 0
        settings.beforeEdit = {
            edits++
            if (edits == 2) flow.value = SessionCredentialSnapshot(2, 3, emptyMap())
        }
        assertFalse(repo.persistLatest(handle))
        assertEquals(SessionPersistenceStatus.SignedOut, repo.state.value.status)
        assertNull(SessionRepository(secrets, settings).restoreCurrent())
        assertTrue(secrets.values.isEmpty())
    }
}
