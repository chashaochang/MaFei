package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.data.repositories.session.SessionRepository
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.providers.feiniu.connection.FeiniuAddressParser
import cn.mafei.cmp.providers.feiniu.connection.FeiniuConnectionApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoutePreference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Process-scoped services, not an Activity/ViewController-scoped persistence writer. */
class ApplicationRuntime(
    private val settings: SettingsStorage,
    private val secureStore: SecureCredentialStore,
    private val transport: HttpTransport,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    /**
     * Legacy (HarmonyOS ArkUI) data reader for the one-shot overwrite-upgrade
     * migration. Null (default) on platforms whose reader is not wired yet —
     * [migrateLegacyData] then reports Pending instead of migrating.
     */
    private val legacySnapshotReader: cn.mafei.cmp.data.migration.LegacySnapshotReader? = null,
    /**
     * Platform player engine factory (Android/iOS/macOS/OHOS runtime). When
     * absent, [playback] returns null and the player UI cannot start — an
     * explicit configuration gap, never a faked player.
     */
    private val playerRuntimeFactory: ((ProviderKind) -> PlayerRuntime)? = null,
    /**
     * 平台亮度/音量（播放页竖向手势）。缺省是不支持：两条手势都不介入，也不会画出改了
     * 没生效的反馈条 —— 与 [playerRuntimeFactory] 同类，缺配置就明确缺，绝不假装能调。
     */
    val deviceControls: cn.mafei.cmp.player.api.PlayerDeviceControls =
        cn.mafei.cmp.player.api.PlayerDeviceControls.Unsupported,
    private val failureReporter: (stage: String, failure: Throwable) -> Unit = { _, _ -> },
    private val persistenceStageReporter: (stage: String) -> Unit = {},
    val photoSaver: cn.mafei.cmp.feature.photo.PhotoSaver = cn.mafei.cmp.feature.photo.UnsupportedPhotoSaver,
    val playerWindowControls: cn.mafei.cmp.player.api.PlayerWindowControls = cn.mafei.cmp.player.api.PlayerWindowControls.Unsupported,
    private val externalSubtitleOverlay: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val appearance = AppearanceStore(settings)
    val glassStyle = GlassStyleStore(settings)
    private val closeMutex = Mutex()
    var isClosed: Boolean = false
        private set

    private var deferredFeiniuRouteRefresh: Job? = null
    val sessions: ApplicationSessions = ApplicationSessions(
        SessionRepository(secureStore, settings, persistenceStageReporter),
        MediaRepository(
            SystemEpochClock,
            homeSnapshots = cn.mafei.cmp.core.storage_api.JsonSnapshotStorage(
                settings,
                Json { ignoreUnknownKeys = true },
                { SystemEpochClock.nowEpochMs() },
            ),
        ),
        ConnectionCatalog(settings), DefaultProviderSessionFactory(transport), scope, failureReporter,
        beforeExplicitLogout = { legacyMigration.suppressLegacyAccountRecovery() },
    )
    private val connectionCatalog = ConnectionCatalog(settings)
    val playerPreferences = PlayerPreferencesStore(settings)
    private val persistedChasingSeries = SettingsChasingSeriesStore(settings)
    val chasingSeriesStore: cn.mafei.cmp.feature.chasing.ChasingSeriesStore
        get() = persistedChasingSeries
    // Logging in to an already saved account must land on that record again: the
    // catalog keeps one credential namespace per selection, and a fresh namespace
    // per login used to append another identical row to the account history.
    private val login = ProviderLogin(
        transport,
        savedConnectionFor = { provider, address, originalAddress, remoteServerId, remoteUserId, username ->
            savedAccount(provider, address, originalAddress, remoteServerId, remoteUserId, username)
        },
    )
    private val artwork = SessionArtwork(transport)

    private val preferences = RuntimePreferences(settings, appearance, { isClosed }, failureReporter)
    private val playbackRegistry = ApplicationPlaybackRegistry(
        sessions, scope, transport, playerRuntimeFactory, playerPreferences, externalSubtitleOverlay, { isClosed },
    )
    private val legacyMigration = ApplicationLegacyMigration(
        settings, secureStore, transport, legacySnapshotReader, sessions, connectionCatalog,
        persistedChasingSeries, failureReporter, persistenceStageReporter, { isClosed },
    )

    val playbackPreferences: cn.mafei.cmp.feature.home.MediaPlaybackPreferences
        get() = preferences.playbackPreferences

    suspend fun loadArtwork(
        session: ApplicationSession,
        image: cn.mafei.cmp.core.domain.MediaImage,
        targetWidthPx: Int = 480,
    ): ByteArray? {
        if (isClosed || sessions.state.value.session !== session) return null
        return artwork.load(session.connection, session.binding, image, targetWidthPx)
            .takeIf { sessions.state.value.session === session }
    }

    suspend fun loadPhotoArtwork(session: ApplicationSession, image: cn.mafei.cmp.core.domain.MediaImage): ByteArray? {
        if (isClosed || sessions.state.value.session !== session) return null
        return artwork.load(session.connection, session.binding, image, photoDimensions = true)
            .takeIf { sessions.state.value.session === session }
    }

    /** Fresh login; ownership transfers to sessions only after admission. */
    suspend fun connect(
        provider: ProviderKind,
        baseUrl: String,
        username: String,
        password: String,
        rememberPassword: Boolean = true,
    ): ApplicationSession {
        check(!isClosed) { "Application is closed" }
        val authenticated = login.authenticate(
            provider = provider,
            baseUrl = baseUrl,
            username = username,
            password = password,
            feiniuRoutePreference = if (provider == ProviderKind.Feiniu) {
                loadFeiniuRoutePreference()
            } else {
                FeiniuRoutePreference.Speed
            },
        )
        val connection = authenticated.connection.copy(rememberLogin = rememberPassword)
        var previousPasswordSecret: SecureSecret? = null
        var passwordStateCaptured = false
        var passwordStateChanged = false
        try {
            previousPasswordSecret = secureStore.read(connection.passwordAlias)
            passwordStateCaptured = true
            if (rememberPassword) {
                secureStore.save(connection.passwordAlias, SecureSecret.Password(password))
            } else {
                secureStore.delete(connection.passwordAlias)
            }
            passwordStateChanged = true
            val session = sessions.activate(connection, authenticated.binding)
            // The account that just logged in is now its single history row. Rows
            // left by earlier logins (one per login, before identities were
            // stable) are unreachable duplicates, not restorable accounts.
            mergeDuplicateAccounts(connection)
            return session
        } catch (failure: Throwable) {
            // Admitted cancellation may already have committed. Never clear that SDK.
            if (sessions.state.value.session?.binding !== authenticated.binding) {
                if (passwordStateCaptured && passwordStateChanged) {
                    withContext(NonCancellable) {
                        try {
                            if (previousPasswordSecret == null) {
                                secureStore.delete(connection.passwordAlias)
                            } else {
                                secureStore.save(connection.passwordAlias, previousPasswordSecret!!)
                            }
                        } catch (rollbackFailure: Throwable) {
                            failureReporter("remembered-password-rollback", rollbackFailure)
                        }
                    }
                }
                authenticated.binding.clear()
            }
            // Authentication errors occurred before this block. A backend's cause/message
            // can contain sensitive data; retain only the stable, non-secret category.
            if (failure is Exception && failure !is CancellationException) {
                throw ConnectionSaveException((failure as? CredentialAccessException)?.kind)
            }
            throw failure
        }
    }

    /**
     * Saved account for one login attempt.
     *
     * [remoteUserId] null is the pre-authentication lookup: it can only match the
     * typed user name, because Jellyfin fixes its credential namespace (which is
     * also its DeviceId) before the request. The remaining picks are contiguous
     * so that both lookups of one login can never disagree about which row of a
     * duplicated account to reuse.
     */
    private suspend fun savedAccount(
        provider: ProviderKind,
        address: String,
        originalAddress: String?,
        remoteServerId: String?,
        remoteUserId: String?,
        username: String,
    ): SavedConnection? {
        val stableServerId = remoteServerId?.takeIf(String::isNotBlank)?.let {
            SessionIdentity.serverId(provider, validateSavedBaseUrl(address), it)
        }
        return connectionCatalog.all()
            .filter { saved ->
                saved.server.provider == provider &&
                (saved.server.id == stableServerId || savedConnectionMatchesAddress(saved, address, originalAddress)) &&
                if (remoteUserId != null) {
                    saved.remoteUserId == remoteUserId
                } else {
                    saved.account.userName.equals(username, ignoreCase = true)
                }
            }
            .maxWithOrNull(
                compareBy<SavedConnection> { it.account.lastActiveEpochMs ?: Long.MIN_VALUE }
                    .thenBy { it.credentialKey },
            )
    }

    /**
     * Collapses the duplicate account-history rows an earlier login left behind
     * for [connection]'s account: same provider, same address, same remote user,
     * different credential namespace. Those rows were never separate accounts —
     * they are one account's previous logins — and the account is reachable
     * through the row that was just saved. Best effort: hygiene must never fail a
     * successful login, and the next login simply retries what a failure left.
     */
    private suspend fun mergeDuplicateAccounts(connection: SavedConnection) {
        val addresses = connection.normalizedServerAddresses()
        try {
            val duplicates = connectionCatalog.all().filter { saved ->
                saved.credentialKey != connection.credentialKey &&
                    saved.server.provider == connection.server.provider &&
                    saved.remoteUserId == connection.remoteUserId &&
                    (
                        saved.normalizedServerAddresses().any(addresses::contains) ||
                            sameFeiniuOriginalAddress(saved, connection)
                    )
            }
            for (duplicate in duplicates) {
                // The application's own deletion path: it refuses the active
                // account, drains the credential index and drops the remembered
                // password. Only a confirmed removal may orphan the rest.
                val removed = sessions.deleteSavedConnection(duplicate) {
                    secureStore.delete(duplicate.passwordAlias)
                }
                if (removed) duplicate.aliases.forEach { secureStore.delete(it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("account-history-merge", failure)
        }
    }

    suspend fun savedConnections(): List<SavedConnection> {
        check(!isClosed) { "Application is closed" }
        val saved = try {
            sessions.savedConnections()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("history-catalog-read", failure)
            throw failure
        }
        legacyMigration.importRememberedPasswordsSafely(saved, "history-password-import")
        return saved
    }

    suspend fun searchHistory(): List<String> = preferences.searchHistory()
    suspend fun saveSearchHistory(items: List<String>) = preferences.saveSearchHistory(items)
    suspend fun loadAppearanceMode(): String? = preferences.loadAppearanceMode()
    suspend fun saveAppearanceMode(value: String) = preferences.saveAppearanceMode(value)
    suspend fun loadGlassIntensity(): Float = preferences.loadGlassIntensity()
    suspend fun saveGlassIntensity(value: Float) = preferences.saveGlassIntensity(value)
    suspend fun loadHomeCarouselVisible(): Boolean = preferences.loadHomeCarouselVisible()
    suspend fun saveHomeCarouselVisible(value: Boolean) = preferences.saveHomeCarouselVisible(value)
    suspend fun loadPlayerRenderMode(): String = preferences.loadPlayerRenderMode()
    suspend fun savePlayerRenderMode(value: String) = preferences.savePlayerRenderMode(value)

    suspend fun loadFeiniuRoutePreference(): FeiniuRoutePreference {
        check(!isClosed) { "Application is closed" }
        return try {
            when (settings.getString(FEINIU_ROUTE_PREFERENCE_KEY)) {
                FEINIU_ROUTE_PUBLIC -> FeiniuRoutePreference.Public
                FEINIU_ROUTE_RELAY -> FeiniuRoutePreference.Relay
                else -> FeiniuRoutePreference.Speed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("feiniu-route-preference-read", failure)
            FeiniuRoutePreference.Speed
        }
    }

    /**
     * Persists the choice first, exactly like the ArkUI store. Re-resolving the
     * selected Feiniu account is best effort and never rolls back a saved choice.
     */
    suspend fun saveFeiniuRoutePreference(value: FeiniuRoutePreference) {
        check(!isClosed) { "Application is closed" }
        val normalized = when (value) {
            FeiniuRoutePreference.Public -> FEINIU_ROUTE_PUBLIC
            FeiniuRoutePreference.Relay -> FEINIU_ROUTE_RELAY
            else -> FEINIU_ROUTE_SPEED
        }
        settings.putString(FEINIU_ROUTE_PREFERENCE_KEY, normalized)
        try {
            refreshSelectedFeiniuConnection(
                when (normalized) {
                    FEINIU_ROUTE_PUBLIC -> FeiniuRoutePreference.Public
                    FEINIU_ROUTE_RELAY -> FeiniuRoutePreference.Relay
                    else -> FeiniuRoutePreference.Speed
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("feiniu-route-refresh", failure)
        }
    }

    private suspend fun refreshSelectedFeiniuConnection(preference: FeiniuRoutePreference) {
        val active = sessions.state.value.session
            ?.takeIf { it.selection.provider == ProviderKind.Feiniu }
            ?: return
        val playback = playbackRegistry.existing(active)
        if (playback != null && playback.controller.state.value.phase.isActivePlaybackPhase()) {
            deferredFeiniuRouteRefresh?.cancel()
            deferredFeiniuRouteRefresh = scope.launch {
                playback.controller.state.first { !it.phase.isActivePlaybackPhase() }
                try {
                    refreshSelectedFeiniuConnectionNow(preference)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failureReporter("feiniu-route-refresh-deferred", failure)
                }
            }
            return
        }
        refreshSelectedFeiniuConnectionNow(preference)
    }

    private suspend fun refreshSelectedFeiniuConnectionNow(preference: FeiniuRoutePreference) {
        val active = sessions.state.value.session
            ?.takeIf { it.selection.provider == ProviderKind.Feiniu }
            ?: return
        val originalAddress = active.connection.feiniuOriginalAddress ?: active.connection.server.baseUrl
        val parsed = FeiniuAddressParser.parse(originalAddress)
        val resolution = when (parsed.kind) {
            FeiniuAddressParser.Kind.Direct -> {
                val direct = validateSavedBaseUrl(parsed.directBaseUrl)
                FeiniuHostResolution(FeiniuHostCandidate(direct.toEncodedString(), FeiniuRoute.Direct))
            }
            FeiniuAddressParser.Kind.FnId -> FeiniuConnectionApi(transport).resolve(originalAddress, preference)
        }
        val preferred = validateSavedBaseUrl(resolution.preferred.baseUrl)
        val refreshed = active.connection.copy(
            server = active.connection.server.copy(
                name = preferred.host,
                baseUrl = preferred.toEncodedString(),
            ),
            feiniuRoutes = resolution.orderedCandidates
                .distinctBy { SessionIdentity.serverAddress(it.baseUrl) }
                .take(16)
                .map { route ->
                    SavedFeiniuRoute(
                        validateSavedBaseUrl(route.baseUrl).toEncodedString(),
                        route.route.name,
                    )
                },
            feiniuOriginalAddress = originalAddress,
        )
        val binding = DefaultProviderSessionFactory(transport).restore(
            refreshed,
            active.binding.source.current().secrets,
        )
        sessions.activate(refreshed, binding)
    }

    /** Returns null only when this account has no saved password or the alias contains another secret type. */
    suspend fun savedPassword(connection: SavedConnection): String? {
        check(!isClosed) { "Application is closed" }
        return (secureStore.read(connection.passwordAlias) as? SecureSecret.Password)?.value
    }

    suspend fun hasSavedPassword(connection: SavedConnection): Boolean = try {
        savedPassword(connection) != null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // The history row remains usable even if its optional password badge cannot be read.
        failureReporter("history-password-status", failure)
        false
    }

    /** Non-secret password status shared by login history and account switching. */
    suspend fun savedPasswordKeys(connections: List<SavedConnection>): Set<String> = buildSet {
        for (connection in connections) {
            if (hasSavedPassword(connection)) add(connection.credentialKey)
        }
    }
    suspend fun switchAccount(connection: SavedConnection): ApplicationSession {
        check(!isClosed) { "Application is closed" }
        return sessions.switchAccount(connection.selection)
    }

    suspend fun deleteSavedConnection(connection: SavedConnection): Boolean {
        check(!isClosed) { "Application is closed" }
        return sessions.deleteSavedConnection(connection) {
            secureStore.delete(connection.passwordAlias)
        }
    }

    /**
     * Restores the CMP session first and only falls back to the one-shot ArkUI
     * import when this installation has not consumed it yet. A completed
     * import stays consumed after logout, so old data cannot silently sign the
     * user back in on a later launch.
     */
    suspend fun start(): ApplicationSession? {
        check(!isClosed) { "Application is closed" }
        try {
            playerPreferences.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // A player preference read error must not invalidate a saved account.
            failureReporter("player-preferences-read", failure)
        }
        val restored = sessions.start()
        if (restored != null) {
            legacyMigration.importRememberedPasswordsSafely(
                sessions.savedConnections(),
                "startup-password-import",
            )
            // Upgrades that already completed before this marker existed have
            // a durable migration journal and a restorable CMP session. Adopt
            // that state without reopening the legacy databases.
            if (legacySnapshotReader != null &&
                cn.mafei.cmp.core.storage_api.VersionedSettingsStorage(settings)
                    .completedMigrations().isNotEmpty()
            ) {
                legacyMigration.markLegacyMigrationConsumed()
            }
            return restored
        }
        if (sessions.state.value.status != ApplicationSessionStatus.SignedOut || legacySnapshotReader == null) return null

        if (legacyMigration.isLegacyMigrationConsumed()) {
            // Some development builds replaced the CMP session index while
            // leaving the legacy ArkUI databases and credentials intact. A
            // consumed marker alone cannot prove that the user logged out.
            // Repair that specific state once unless an explicit logout (or a
            // confirmed empty source) durably suppressed legacy recovery.
            if (legacyMigration.isLegacyAccountRecoverySuppressed() || legacyMigration.isLegacyMigrationConfirmedEmpty()) return null
            return legacyMigration.migrateLegacyData(allowConsumedRecovery = true).let { sessions.state.value.session }
        }

        migrateLegacyData()
        return sessions.state.value.session
    }

    /**
     * Player entry for an ACTIVE session. The same session reuses the same
     * runtime; logout/account-switch releases the retired instance through
     * the session-state observer, and [close] releases everything. Returns
     * null when no platform engine factory was configured.
     */
    fun playback(session: ApplicationSession): SessionPlaybackRuntime? = playbackRegistry.getOrCreate(session)

    /** Host visibility only; account state and music queues remain owned by their services. */
    fun setHostForeground(foreground: Boolean) = playbackRegistry.setHostForeground(foreground)

    suspend fun migrateLegacyData(): cn.mafei.cmp.data.migration.MigrationResult? =
        legacyMigration.migrateLegacyData()

    suspend fun close() = withContext(NonCancellable) {
        closeMutex.withLock {
            if (isClosed) return@withLock
            try {
                playbackRegistry.releaseAll()
                deferredFeiniuRouteRefresh?.cancel()
                deferredFeiniuRouteRefresh = null
                playbackRegistry.cancelObserver()
                sessions.close()
            } finally {
                isClosed = true; transport.close(); scope.cancel()
            }
        }
    }

    /** Desktop host awaits draining before exiting. Mobile roots live for the process. */
    fun requestClose(onClosed: () -> Unit = {}) {
        if (isClosed) { onClosed(); return }
        scope.launch { try { close() } finally { onClosed() } }
    }

    private companion object {
        const val FEINIU_ROUTE_PREFERENCE_KEY = "pref_feiniu_connection_preference"
        const val FEINIU_ROUTE_SPEED = "speed"
        const val FEINIU_ROUTE_PUBLIC = "public"
        const val FEINIU_ROUTE_RELAY = "relay"
    }
}

private fun savedConnectionMatchesAddress(
    saved: SavedConnection,
    address: String,
    originalAddress: String?,
): Boolean {
    if (saved.normalizedServerAddresses().contains(address)) return true
    if (saved.server.provider != ProviderKind.Feiniu || originalAddress == null) return false
    return saved.feiniuOriginalAddress?.normalizedOriginalAddress() ==
        originalAddress.normalizedOriginalAddress()
}

private fun SavedConnection.normalizedServerAddresses(): Set<String> = buildSet {
    SessionIdentity.serverAddress(server.baseUrl)?.let(::add)
    feiniuRoutes.forEach { route -> SessionIdentity.serverAddress(route.baseUrl)?.let(::add) }
}

private fun sameFeiniuOriginalAddress(first: SavedConnection, second: SavedConnection): Boolean {
    if (first.server.provider != ProviderKind.Feiniu || second.server.provider != ProviderKind.Feiniu) return false
    val firstAddress = first.feiniuOriginalAddress?.normalizedOriginalAddress() ?: return false
    val secondAddress = second.feiniuOriginalAddress?.normalizedOriginalAddress() ?: return false
    return firstAddress == secondAddress
}

private fun String.normalizedOriginalAddress(): String = trim().trimEnd('/')

private fun PlayerPhase.isActivePlaybackPhase(): Boolean = when (this) {
    PlayerPhase.Loading,
    PlayerPhase.Ready,
    PlayerPhase.Playing,
    PlayerPhase.Paused,
    PlayerPhase.Buffering -> true
    PlayerPhase.Idle,
    PlayerPhase.Ended,
    PlayerPhase.Error,
    PlayerPhase.Released -> false
}

/** Called only by platform main-thread entry points. Rotation does not create a second writer. */
internal object ProcessApplication {
    private var current: ApplicationRuntime? = null
    fun getOrCreate(create: () -> ApplicationRuntime): ApplicationRuntime {
        val existing = current
        if (existing != null && !existing.isClosed) return existing
        return create().also { current = it }
    }
    fun setForeground(foreground: Boolean) { current?.setHostForeground(foreground) }
}
