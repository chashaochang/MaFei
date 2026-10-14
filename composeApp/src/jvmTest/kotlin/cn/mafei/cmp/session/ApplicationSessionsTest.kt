package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.*
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.session.*
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class ApplicationSessionsTest {
    private class Document : SettingsDocumentBackend {
        var bytes: ByteArray? = null
        var failure: Exception? = null
        override fun read() = bytes?.copyOf()
        override fun replace(document: ByteArray) { failure?.let { throw it }; bytes = document.copyOf() }
    }
    private class Secrets : SecureCredentialStore {
        val values = mutableMapOf<CredentialAlias, SecureSecret>()
        var beforeSave: suspend (CredentialAlias) -> Unit = {}
        override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { beforeSave(alias); values[alias] = secret }
        override suspend fun read(alias: CredentialAlias) = values[alias]
        override suspend fun exists(alias: CredentialAlias) = alias in values
        override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
        override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
        override suspend fun deleteAll(prefix: String?) = error("unscoped deletion")
    }
    private class Transport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var closes = 0
        var statusCode = 200
        var beforeResponse: suspend () -> Unit = {}
        var response: (HttpRequest) -> String = { """{"Items":[],"TotalRecordCount":0}""" }
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request; beforeResponse()
            return HttpResponse(statusCode, HttpHeaders.EMPTY, response(request).encodeToByteArray(), request.url, request.url)
        }
        override fun close() { closes++ }
    }
    private class Harness(val doc: Document = Document(), val secrets: Secrets = Secrets(), val transport: Transport = Transport()) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val settings = DocumentSettingsStorage(doc)
        val repository = SessionRepository(secrets, settings)
        val factory = DefaultProviderSessionFactory(transport)
        val sessions = ApplicationSessions(repository, MediaRepository(FixedEpochClock()), ConnectionCatalog(settings), factory, scope)
        fun binding(connection: SavedConnection) = factory.restore(connection, mapOf(connection.tokenAlias to SecureSecret.AuthToken("MediaBrowser", "fixture-${connection.account.id.value}")))
        suspend fun close() { sessions.close(); scope.cancel() }
    }
    private fun connection(id: String = "a"): SavedConnection {
        val server = Server(ServerId("server"), ProviderKind.Jellyfin, "Fixture", "https://media.test/base")
        return SavedConnection(server, Account(AccountId(id), server.id, server.provider, id), "remote-$id", id.repeat(32))
    }

    @Test fun coldStartIsSignedOutAndDoesNotSendAnUnauthenticatedRequest() = runBlocking<Unit> {
        val h = Harness()
        try {
            assertNull(h.sessions.start()); assertNull(h.sessions.start())
            assertEquals(ApplicationSessionStatus.SignedOut, h.sessions.state.value.status)
            assertTrue(h.transport.requests.isEmpty()); assertTrue(h.secrets.values.isEmpty())
        } finally { h.close() }
    }

    @Test fun newRootRestoresRealSdkAndSignOutRequiresExplicitHistorySelection() = runBlocking<Unit> {
        val first = Harness(); val c = connection()
        val originalBinding = first.binding(c)
        first.sessions.activate(c, originalBinding); first.close()
        assertFailsWith<CancellationException> { originalBinding.adapter.collections(PageRequest()) }
        val restarted = Harness(first.doc, first.secrets)
        try {
            val restored = assertNotNull(restarted.sessions.start())
            restarted.sessions.media.collections(restored.media)
            assertTrue(restarted.transport.requests.single().headers.first("Authorization")!!.contains("fixture-a"))
            assertEquals(c.selection, restored.selection)
            assertTrue(restarted.sessions.logout(restored))
            assertTrue(first.secrets.values.isNotEmpty())
            assertFalse(first.doc.bytes!!.decodeToString().contains("fixture-a"))
        } finally { restarted.close() }
        val signedOut = Harness(first.doc, first.secrets)
        try {
            assertNull(signedOut.sessions.start())
            assertEquals(
                listOf(c.selection),
                signedOut.sessions.savedConnections().map { it.selection },
            )
            assertEquals(c.selection, signedOut.sessions.switchAccount(c.selection).selection)
        } finally { signedOut.close() }
    }

    @Test fun accountWithoutRememberPasswordIsKeptInHistoryButRequiresLogin() = runBlocking<Unit> {
        val h = Harness(); val c = connection().copy(rememberLogin = false)
        try {
            val session = h.sessions.activate(c, h.binding(c))
            assertTrue(h.sessions.logout(session))
            assertTrue(h.secrets.values.isEmpty())
            assertNull(h.sessions.start())
            assertEquals(listOf(c.selection), h.sessions.savedConnections().map { it.selection })
            assertFailsWith<IllegalStateException> { h.sessions.switchAccount(c.selection) }
        } finally { h.close() }
    }

    @Test fun expiredSessionCanBeReauthenticatedWithoutLogoutOrLosingHistoryAndPreferences() = runBlocking<Unit> {
        val h = Harness(); val c = connection()
        try {
            val original = h.sessions.activate(c, h.binding(c))
            h.secrets.save(c.passwordAlias, SecureSecret.Password("remembered"))
            h.settings.putString("appearance-test", "dark")
            h.transport.statusCode = 401
            assertFailsWith<AuthenticationError> { h.sessions.media.collections(original.media, refresh = true) }
            assertEquals(ApplicationSessionStatus.Ready, h.sessions.state.value.status)
            assertSame(original, h.sessions.state.value.session)
            assertEquals(listOf(c.selection), h.sessions.savedConnections().map { it.selection })

            h.transport.statusCode = 200
            val renewed = h.factory.restore(c, mapOf(c.tokenAlias to SecureSecret.AuthToken("MediaBrowser", "renewed-token")))
            val restored = h.sessions.activate(c, renewed)
            assertNotSame(original, restored)
            assertEquals(c.selection, restored.selection)
            h.sessions.media.collections(restored.media, refresh = true)
            assertTrue(h.transport.requests.last().headers.first("Authorization")!!.contains("renewed-token"))
            assertEquals(listOf(c.selection), h.sessions.savedConnections().map { it.selection })
            assertEquals(SecureSecret.Password("remembered"), h.secrets.read(c.passwordAlias))
            assertEquals("dark", h.settings.getString("appearance-test"))
        } finally { h.close() }
    }

    @Test fun switchingPreservesSavedAccountsButRevokesOldSdkCacheAndLogoutHandle() = runBlocking<Unit> {
        val h = Harness(); val a = connection(); val b = connection("b")
        try {
            val bindingA = h.binding(a)
            val old = h.sessions.activate(a, bindingA)
            h.sessions.media.collections(old.media)
            val fresh = h.sessions.activate(b, h.binding(b))
            assertFalse(h.sessions.logout(old))
            assertFailsWith<CancellationException> { bindingA.adapter.collections(PageRequest()) }
            assertFailsWith<CancellationException> { h.sessions.media.collections(old.media) }
            h.sessions.media.collections(fresh.media)
            assertTrue(h.transport.requests.last().headers.first("Authorization")!!.contains("fixture-b"))
            assertEquals(setOf(a.selection, b.selection), h.repository.savedAccounts().toSet())
            assertEquals(setOf(a.selection, b.selection), h.sessions.savedConnections().map { it.selection }.toSet())
            assertEquals(a.selection, h.sessions.switchAccount(a.selection).selection)
        } finally { h.close() }
    }

    @Test fun deletingInactiveAccountRemovesHistoryCredentialsAndRememberedPasswordOnlyForThatAccount() = runBlocking<Unit> {
        val h = Harness(); val a = connection(); val b = connection("b")
        try {
            h.sessions.activate(a, h.binding(a))
            val current = h.sessions.activate(b, h.binding(b))
            h.secrets.save(a.passwordAlias, SecureSecret.Password("remember-a"))
            h.secrets.save(b.passwordAlias, SecureSecret.Password("remember-b"))

            assertFalse(h.sessions.deleteSavedConnection(b) { h.secrets.delete(b.passwordAlias) })
            assertTrue(h.sessions.deleteSavedConnection(a) { h.secrets.delete(a.passwordAlias) })

            assertEquals(listOf(b.selection), h.sessions.savedConnections().map { it.selection })
            assertNull(h.repository.restoreAccount(a.selection))
            assertNotNull(h.repository.restoreAccount(b.selection))
            assertFalse(a.tokenAlias in h.secrets.values)
            assertFalse(a.passwordAlias in h.secrets.values)
            assertTrue(b.tokenAlias in h.secrets.values)
            assertEquals(SecureSecret.Password("remember-b"), h.secrets.values[b.passwordAlias])
            assertSame(current, h.sessions.state.value.session)
        } finally { h.close() }
    }

    @Test fun appearanceModeUsesOrdinarySettingsAndRejectsUnknownValues() = runBlocking<Unit> {
        val h = Harness()
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            assertNull(root.loadAppearanceMode())
            root.saveAppearanceMode("dark")
            assertEquals("dark", root.loadAppearanceMode())
            assertFailsWith<IllegalArgumentException> { root.saveAppearanceMode("sepia") }
        } finally { root.close(); h.close() }
    }

    @Test fun glassIntensityDefaultsToMiddleAndPersistsClampedPercentage() = runBlocking<Unit> {
        val h = Harness()
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            assertEquals(.5f, root.loadGlassIntensity())
            root.saveGlassIntensity(.82f)
            assertEquals(.82f, root.loadGlassIntensity())
            root.saveGlassIntensity(2f)
            assertEquals(1f, root.loadGlassIntensity())
            root.saveGlassIntensity(-1f)
            assertEquals(0f, root.loadGlassIntensity())
        } finally { root.close(); h.close() }
    }

    @Test fun lateOldResponseCannotPublishAfterAccountSwitch() = runBlocking<Unit> {
        withTimeout(5000) {
            val h = Harness(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            try {
                val a = h.sessions.activate(connection(), h.binding(connection()))
                h.transport.beforeResponse = { entered.complete(Unit); release.await() }
                val pending = async { h.sessions.media.collections(a.media) }
                entered.await()
                val b = connection("b"); h.sessions.activate(b, h.binding(b))
                release.complete(Unit)
                assertFailsWith<CancellationException> { pending.await() }
                assertEquals(b.selection, h.sessions.state.value.selection)
            } finally { release.complete(Unit); h.close() }
        }
    }

    @Test fun cancelledAdmittedActivationRetainsItsCommittedSdkAndReadyState() = runBlocking<Unit> {
        withTimeout(5000) {
            val h = Harness(); val c = connection(); val binding = h.binding(c)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            h.secrets.beforeSave = { entered.complete(Unit); release.await() }
            try {
                val activation = launch { h.sessions.activate(c, binding) }
                entered.await(); activation.cancel(); release.complete(Unit); activation.join()
                assertEquals(ApplicationSessionStatus.Ready, h.sessions.state.value.status)
                binding.adapter.collections(PageRequest())
                assertNotNull(h.repository.restoreCurrent())
            } finally { release.complete(Unit); h.close() }
        }
    }

    @Test fun shutdownDrainsObserverWriteAndDoesNotDeleteSavedLogin() = runBlocking<Unit> {
        withTimeout(5000) {
            val h = Harness(); val c = connection(); val real = h.binding(c)
            val flow = MutableStateFlow(real.source.current())
            val binding = object : ProviderSessionBinding by real { override val source = StateFlowCredentialSource(flow) { it } }
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            try {
                h.sessions.activate(c, binding)
                h.secrets.beforeSave = { entered.complete(Unit); release.await() }
                flow.value = SessionCredentialSnapshot(0, 1, mapOf(c.tokenAlias to SecureSecret.AuthToken("MediaBrowser", "rotated")))
                entered.await()
                val close = async { h.sessions.close() }; yield(); assertFalse(close.isCompleted)
                release.complete(Unit); close.await()
                assertEquals(SecureSecret.AuthToken("MediaBrowser", "rotated"), h.secrets.values[c.tokenAlias])
                assertEquals(ApplicationSessionStatus.Closed, h.sessions.state.value.status)
            } finally { release.complete(Unit); h.close() }
        }
    }

    @Test fun corruptMetadataAndMissingSecretsNeverPublishReady() = runBlocking<Unit> {
        val first = Harness(); val c = connection()
        first.sessions.activate(c, first.binding(c)); first.close()
        val missing = Harness(first.doc, Secrets())
        try { assertNull(missing.sessions.start()); assertEquals(ApplicationSessionStatus.SignedOut, missing.sessions.state.value.status) }
        finally { missing.close() }
        val settings = DocumentSettingsStorage(first.doc)
        settings.putString("mafei.connection.v1/${c.credentialKey}", "corrupt")
        val corrupt = Harness(first.doc, first.secrets)
        try { assertNull(corrupt.sessions.start()); assertEquals(ApplicationSessionStatus.NeedsAttention, corrupt.sessions.state.value.status) }
        finally { corrupt.close() }
    }

    @Test fun sdkIdentityRevocationClosesMediaAndRequiresExplicitRecovery() = runBlocking<Unit> {
        withTimeout(5000) {
            val h = Harness(); val c = connection(); val real = h.binding(c)
            val flow = MutableStateFlow(real.source.current())
            val binding = object : ProviderSessionBinding by real { override val source = StateFlowCredentialSource(flow) { it } }
            try {
                val session = h.sessions.activate(c, binding)
                flow.value = SessionCredentialSnapshot(1, 1, emptyMap())
                while (h.sessions.state.value.status != ApplicationSessionStatus.NeedsAttention) yield()
                assertTrue(h.secrets.values.isEmpty())
                assertFailsWith<CancellationException> { h.sessions.media.collections(session.media) }
                assertFailsWith<CancellationException> { real.adapter.collections(PageRequest()) }
                h.sessions.retryCleanup()
                assertEquals(ApplicationSessionStatus.SignedOut, h.sessions.state.value.status)
            } finally { h.close() }
        }
    }

    @Test fun applicationRootClosesTransportOnceWithoutLoggingOutSavedAccount() = runBlocking<Unit> {
        val h = Harness(); val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            val c = connection()
            val binding = h.binding(c)
            root.sessions.activate(c, binding)
            root.close(); root.close()
            assertEquals(1, h.transport.closes)
            assertTrue(root.isClosed)
            assertNotNull(h.repository.restoreCurrent())
            assertFailsWith<CancellationException> { binding.adapter.collections(PageRequest()) }
        } finally { root.close(); h.close() }
    }

    @Test fun credentialBearingAvatarIsRejectedBeforeConnectionPersistence() = runBlocking<Unit> {
        val h = Harness(); val c = connection()
        try {
            val runtimeAccount = c.account.copy(avatarImage = MediaImage(url = "https://media.test/avatar?api_key=private-avatar-token"))
            val failure = assertFailsWith<ClientConfigError> { c.copy(account = runtimeAccount) }
            assertFalse(failure.message.orEmpty().contains("private-avatar-token"))
            assertNull(h.doc.bytes)
            val catalog = ConnectionCatalog(h.settings)
            catalog.save(c.copy(account = runtimeAccount.copy(avatarImage = null)))
            assertFalse(h.doc.bytes!!.decodeToString().contains("private-avatar-token"))
            assertNull(catalog.find(c.selection)!!.account.avatarImage)
        } finally { h.close() }
    }

    @Test fun loginThroughApplicationRootPersistsThenBrowsesAndRestores() = runBlocking<Unit> {
        val h = Harness()
        h.transport.response = { request ->
            when {
                request.url.encodedPath.endsWith("/AuthenticateByName") ->
                    """{"User":{"Id":"remote-a","Name":"Demo"},"AccessToken":"secret-access-token"}"""
                request.url.encodedPath.endsWith("/UserViews") ->
                    """{"Items":[{"Id":"library","Name":"Movies","Type":"CollectionFolder","CollectionType":"movies"}],"TotalRecordCount":1}"""
                request.url.encodedPath.endsWith("/Items/movie") ->
                    """{"Id":"movie","Name":"Fixture movie","Type":"Movie","Overview":"A story"}"""
                else -> """{"Items":[{"Id":"movie","Name":"Fixture movie","Type":"Movie"}],"TotalRecordCount":1}"""
            }
        }
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            root.sessions.start()
            val session = root.connect(ProviderKind.Jellyfin, "https://media.test/base", "Demo", "never-store-password")
            assertSame(session, root.sessions.state.value.session)
            assertEquals("never-store-password", root.savedPassword(session.connection))
            assertTrue(root.hasSavedPassword(session.connection))
            val browser = cn.mafei.cmp.feature.home.MediaBrowser(root.sessions.media, session.media)
            browser.start()
            assertEquals(listOf("Fixture movie"), browser.state.value.items.map { it.title })
            browser.openDetail(MediaId("movie"))
            assertEquals("A story", browser.state.value.detail?.overview)
            assertTrue(h.transport.requests.drop(1).all { it.headers.first("Authorization")!!.contains("secret-access-token") })
            assertTrue(h.secrets.values.values.any { it == SecureSecret.AuthToken("MediaBrowser", "secret-access-token") })
            assertFalse(h.doc.bytes!!.decodeToString().contains("secret-access-token"))
            assertFalse(h.doc.bytes!!.decodeToString().contains("never-store-password"))
            root.close()
            val restored = Harness(h.doc, h.secrets)
            try {
                val next = assertNotNull(restored.sessions.start())
                assertEquals(session.selection, next.selection)
                assertTrue(restored.sessions.logout(next)); assertNull(restored.sessions.state.value.session)
            } finally { restored.close() }
        } finally { root.close(); h.close() }
    }

    @Test fun reopeningSameSelectionPublishesNewRuntimeHandle() = runBlocking<Unit> {
        val h = Harness(); val c = connection()
        try {
            val first = h.sessions.activate(c, h.binding(c))
            val firstState = h.sessions.state.value
            val second = h.sessions.switchAccount(c.selection)
            assertNotEquals(firstState, h.sessions.state.value)
            assertSame(second, h.sessions.state.value.session)
            assertNotSame(first, second)
        } finally { h.close() }
    }

    @Test fun authenticatedButKeychainDeniedIsNotReportedAsWrongPassword() = runBlocking<Unit> {
        val h = Harness()
        h.transport.response = { """{"User":{"Id":"u","Name":"Demo"},"AccessToken":"fixture-token"}""" }
        h.secrets.beforeSave = { throw CredentialAccessException(CredentialFailureKind.AccessDenied, "private-token") }
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            root.sessions.start()
            val error = assertFailsWith<ConnectionSaveException> {
                root.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "private-password")
            }
            assertEquals(CredentialFailureKind.AccessDenied, error.credentialFailure)
            assertTrue(connectFailureMessage(error).contains("服务器认证已通过"))
            assertTrue(connectFailureMessage(error).contains("应用签名"))
            assertFalse(connectFailureMessage(error).contains("private-"))
            assertNull(error.cause)
            assertEquals(ApplicationSessionStatus.SignedOut, root.sessions.state.value.status)
            assertTrue(h.secrets.values.isEmpty())
        } finally { root.close(); h.close() }
    }

    @Test fun authenticatedButSettingsWriteFailedKeepsThePersistenceStage() = runBlocking<Unit> {
        val h = Harness()
        h.transport.response = { """{"User":{"Id":"u"},"AccessToken":"fixture-token"}""" }
        h.doc.failure = IllegalStateException("private-backend-details")
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            root.sessions.start()
            val error = assertFailsWith<ConnectionSaveException> {
                root.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")
            }
            assertNull(error.credentialFailure); assertNull(error.cause)
            assertTrue(connectFailureMessage(error).contains("无法保存登录状态"))
            assertTrue(h.secrets.values.isEmpty())
        } finally { root.close(); h.close() }
    }

    @Test fun rejectedAuthenticationStillRemainsAuthenticationFailure() = runBlocking<Unit> {
        val h = Harness(); h.transport.statusCode = 401
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            root.sessions.start()
            val error = assertFailsWith<AuthenticationError> {
                root.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw")
            }
            assertEquals("登录未通过，请检查账号和密码", connectFailureMessage(error))
            assertEquals(ApplicationSessionStatus.SignedOut, root.sessions.state.value.status)
            assertNull(h.doc.bytes); assertTrue(h.secrets.values.isEmpty())
        } finally { root.close(); h.close() }
    }

    @Test fun cancellingRootConnectAfterAdmissionDoesNotReportSaveFailureOrClearCommittedSdk() = runBlocking<Unit> {
        withTimeout(5000) {
            val h = Harness()
            h.transport.response = { """{"User":{"Id":"u"},"AccessToken":"fixture-token"}""" }
            val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            h.secrets.beforeSave = { alias ->
                if (alias.value.endsWith("/token")) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            try {
                root.sessions.start()
                val pending = async { root.connect(ProviderKind.Jellyfin, "https://media.test", "Demo", "pw") }
                entered.await(); pending.cancel(); release.complete(Unit)
                assertFailsWith<CancellationException> { pending.await() }
                val session = assertNotNull(root.sessions.state.value.session)
                assertEquals(ApplicationSessionStatus.Ready, root.sessions.state.value.status)
                h.transport.response = { """{"Items":[],"TotalRecordCount":0}""" }
                session.binding.adapter.collections(PageRequest())
            } finally { release.complete(Unit); root.close(); h.close() }
        }
    }

    @Test fun rememberPasswordFalseDeletesPasswordAndEmptyPasswordStillCountsAsSaved() = runBlocking<Unit> {
        val h = Harness()
        h.transport.response = { """{"User":{"Id":"u","Name":"Demo"},"AccessToken":"fixture-token"}""" }
        val root = ApplicationRuntime(h.settings, h.secrets, h.transport, Dispatchers.Unconfined)
        try {
            root.sessions.start()
            val emptyPassword = root.connect(
                ProviderKind.Jellyfin,
                "https://media.test",
                "Demo",
                "",
                rememberPassword = true,
            )
            assertEquals("", root.savedPassword(emptyPassword.connection))
            assertTrue(root.hasSavedPassword(emptyPassword.connection))

            h.transport.response = { """{"User":{"Id":"u2","Name":"Demo 2"},"AccessToken":"fixture-token-2"}""" }
            val notRemembered = root.connect(
                ProviderKind.Jellyfin,
                "https://media.test",
                "Demo",
                "do-not-store",
                rememberPassword = false,
            )
            assertNull(root.savedPassword(notRemembered.connection))
            assertFalse(root.hasSavedPassword(notRemembered.connection))
            assertTrue(h.secrets.values.values.none { it == SecureSecret.Password("do-not-store") })
        } finally { root.close(); h.close() }
    }
}
