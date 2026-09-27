package cn.mafei.cmp.session

import cn.mafei.cmp.common.core.SystemEpochClock
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.CredentialAccessException
import cn.mafei.cmp.core.secure_credential_api.CredentialFailureKind
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable
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
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closeMutex = Mutex()
    var isClosed: Boolean = false
        private set

    /** Copy-on-write session playback map; guarded by [playbackMutex] + CAS. */
    private val playbackMutex = Mutex()
    private val playbacks = MutableStateFlow(emptyMap<ApplicationSession, SessionPlaybackRuntime>())
    private var playbackObserver: Job? = null
    private var deferredFeiniuRouteRefresh: Job? = null
    val sessions = ApplicationSessions(
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
        beforeExplicitLogout = { suppressLegacyAccountRecovery() },
    )
    private val connectionCatalog = ConnectionCatalog(settings)
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

    /** Legacy-compatible account + item scoped media-source selections. */
    val playbackPreferences: cn.mafei.cmp.feature.home.MediaPlaybackPreferences by lazy {
        val storage = cn.mafei.cmp.core.storage_api.JsonSnapshotStorage(
            settings,
            Json { ignoreUnknownKeys = true },
            { SystemEpochClock.nowEpochMs() },
        )
        val preferenceMutex = Mutex()
        object : cn.mafei.cmp.feature.home.MediaPlaybackPreferences {
            override suspend fun preferredMediaSourceId(accountId: AccountId, mediaId: MediaId): String? =
                preferenceMutex.withLock {
                    try {
                        val current = storage.load(
                            PreferredMediaSourcesKey,
                            PreferredMediaSources.serializer(),
                        )
                        current?.entries?.firstOrNull {
                            it.accountId == accountId.value && it.mediaId == mediaId.value
                        }?.sourceId ?: storage.load(PreferredMediaSourceKey, String.serializer())
                    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                }

            override suspend fun setPreferredMediaSourceId(
                accountId: AccountId,
                mediaId: MediaId,
                sourceId: String,
            ) {
                preferenceMutex.withLock {
                    try {
                        val retained = storage.load(
                            PreferredMediaSourcesKey,
                            PreferredMediaSources.serializer(),
                        )?.entries.orEmpty().filterNot {
                            it.accountId == accountId.value && it.mediaId == mediaId.value
                        }
                        storage.save(
                            PreferredMediaSourcesKey,
                            PreferredMediaSources(
                                (listOf(PreferredMediaSource(accountId.value, mediaId.value, sourceId)) + retained)
                                    .take(PreferredMediaSourceLimit),
                            ),
                            PreferredMediaSources.serializer(),
                        )
                        // The previous CMP draft stored one global string. Once a scoped
                        // selection is written it must not leak into unrelated items.
                        storage.delete(PreferredMediaSourceKey)
                    } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A preference write must not block playback or source switching.
                    }
                }
            }
        }
    }

    suspend fun loadArtwork(
        session: ApplicationSession,
        image: cn.mafei.cmp.core.domain.MediaImage,
        targetWidthPx: Int = 480,
    ): ByteArray? {
        if (isClosed || sessions.state.value.session !== session) return null
        return artwork.load(session.connection, session.binding, image, targetWidthPx)
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
        importLegacyRememberedPasswordsSafely(saved, "history-password-import")
        return saved
    }

    /** Same non-secret search history that ArkUI stored in AppPreference. */
    suspend fun searchHistory(): List<String> {
        check(!isClosed) { "Application is closed" }
        val raw = settings.getString(SEARCH_HISTORY_KEY) ?: return emptyList()
        return runCatching {
            RuntimeSettingsJson.decodeFromString(ListSerializer(String.serializer()), raw)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(10)
        }.getOrDefault(emptyList())
    }

    suspend fun saveSearchHistory(items: List<String>) {
        check(!isClosed) { "Application is closed" }
        val normalized = items.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        settings.putString(
            SEARCH_HISTORY_KEY,
            RuntimeSettingsJson.encodeToString(ListSerializer(String.serializer()), normalized),
        )
    }

    /**
     * Reserved appearance storage. The product is dark-only for now and its UI no
     * longer offers a choice (`SettingsScreen` hides the entry), so nothing reads
     * or writes this yet; keeping the key and its vocabulary makes re-enabling an
     * appearance setting a UI-only change.
     */
    suspend fun loadAppearanceMode(): String? {
        check(!isClosed) { "Application is closed" }
        return settings.getString(APPEARANCE_MODE_KEY)?.takeIf { it in APPEARANCE_MODES }
    }

    suspend fun saveAppearanceMode(value: String) {
        check(!isClosed) { "Application is closed" }
        require(value in APPEARANCE_MODES) { "Unsupported appearance mode" }
        settings.putString(APPEARANCE_MODE_KEY, value)
    }

    /** Product-wide Liquid Glass strength, persisted as a 0..100 percentage. */
    suspend fun loadGlassIntensity(): Float {
        check(!isClosed) { "Application is closed" }
        return try {
            settings.getString(GLASS_INTENSITY_KEY)
                ?.toIntOrNull()
                ?.coerceIn(0, 100)
                ?.div(100f)
                ?: DEFAULT_GLASS_INTENSITY
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureReporter("glass-intensity-read", failure)
            DEFAULT_GLASS_INTENSITY
        }
    }

    suspend fun saveGlassIntensity(value: Float) {
        check(!isClosed) { "Application is closed" }
        settings.putString(
            GLASS_INTENSITY_KEY,
            (value.coerceIn(0f, 1f) * 100f).toInt().toString(),
        )
    }

    /** ArkUI-compatible Feiniu route strategy. Missing/corrupt values are Speed. */
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
        val playback = playbacks.value[active]
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

    private suspend fun importLegacyRememberedPasswordsSafely(
        saved: List<SavedConnection>,
        failureStage: String,
    ) {
        try {
            importLegacyRememberedPasswords(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Password recovery is optional. A locked or temporarily unavailable
            // credential backend must not hide accounts or block normal startup.
            // Leave the import marker unset so a later attempt can retry it.
            failureReporter(failureStage, failure)
        }
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

    /**
     * Older ArkUI builds stored remembered Jellyfin passwords under an AssetStore alias that is
     * unrelated to the CMP session namespace. The Harmony host includes those values in its
     * in-memory legacy snapshot; import each missing password once without touching the old asset.
     */
    private suspend fun importLegacyRememberedPasswords(saved: List<SavedConnection>) {
        val reader = legacySnapshotReader ?: return
        if (settings.getString(LEGACY_PASSWORD_IMPORT_KEY) == LEGACY_PASSWORD_IMPORT_DONE) return
        val captured = reader.read() as? cn.mafei.cmp.data.migration.LegacyReadResult.Captured ?: return
        val snapshot = captured.snapshot
        val servers = snapshot.servers.associateBy { it.key }
        for (account in snapshot.accounts) {
            val remembered = account.password ?: continue
            val oldServer = servers[account.serverKey] ?: continue
            val connection = saved.firstOrNull { candidate ->
                candidate.server.provider == oldServer.provider &&
                    candidate.remoteUserId == account.remoteUserId &&
                    candidate.server.baseUrl.trimEnd('/') == oldServer.baseUrl.trimEnd('/')
            } ?: continue
            // A later explicit "do not remember" login is authoritative. Do not let a
            // still-present ArkUI AssetStore record resurrect the password.
            if (!connection.rememberLogin) continue
            if (secureStore.read(connection.passwordAlias) == null) {
                try {
                    secureStore.save(connection.passwordAlias, remembered, overwrite = false)
                } catch (failure: CredentialAccessException) {
                    if (failure.kind != CredentialFailureKind.AlreadyExists) throw failure
                }
            }
        }
        settings.putString(LEGACY_PASSWORD_IMPORT_KEY, LEGACY_PASSWORD_IMPORT_DONE)
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
        val restored = sessions.start()
        if (restored != null) {
            importLegacyRememberedPasswordsSafely(
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
                markLegacyMigrationConsumed()
            }
            return restored
        }
        if (sessions.state.value.status != ApplicationSessionStatus.SignedOut || legacySnapshotReader == null) return null

        if (isLegacyMigrationConsumed()) {
            // Some development builds replaced the CMP session index while
            // leaving the legacy ArkUI databases and credentials intact. A
            // consumed marker alone cannot prove that the user logged out.
            // Repair that specific state once unless an explicit logout (or a
            // confirmed empty source) durably suppressed legacy recovery.
            if (isLegacyAccountRecoverySuppressed() || isLegacyMigrationConfirmedEmpty()) return null
            return migrateLegacyData(allowConsumedRecovery = true).let { sessions.state.value.session }
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
    fun playback(session: ApplicationSession): SessionPlaybackRuntime? {
        check(!isClosed) { "Application is closed" }
        val factory = playerRuntimeFactory ?: return null
        // Refuse sessions that were logged out or switched away: a stale
        // session must never obtain a fresh player bound to retired adapters.
        if (session !== sessions.state.value.session) {
            throw IllegalStateException("Session is no longer active")
        }
        while (true) {
            val current = playbacks.value
            current[session]?.let { return it }
            val created = SessionPlaybackRuntime.create(
                provider = session.selection.provider,
                adapter = session.binding.adapter,
                runtime = factory(session.selection.provider),
                scope = scope,
                nowMs = { cn.mafei.cmp.common.core.SystemEpochClock.nowEpochMs() },
            )
            if (playbacks.compareAndSet(current, current + (session to created))) {
                ensurePlaybackObserver()
                return created
            }
            // CAS lost to a concurrent lifecycle change: release the loser
            // asynchronously so engines, timers and workers never leak.
            scope.launch(NonCancellable) {
                try { created.release() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { } // best-effort cleanup of a discarded instance
            }
        }
    }

    private fun ensurePlaybackObserver() {
        if (playbackObserver != null) return
        playbackObserver = scope.launch {
            sessions.state.collect { state ->
                playbackMutex.withLock {
                    for ((session, runtime) in playbacks.value) {
                        if (session !== state.session) {
                            playbacks.value -= session
                            runtime.release()
                        }
                    }
                }
            }
        }
    }

    private suspend fun releaseAllPlaybacks() {
        playbackMutex.withLock {
            for ((_, runtime) in playbacks.value) runtime.release()
            playbacks.value = emptyMap()
        }
    }

    /**
     * One-shot legacy migration: runs the frozen engine over the injected
     * reader, persists the business records durably, saves the resulting
     * connections into the catalog and activates the migrated current
     * account through the SAME session pipeline as a fresh login. Null when
     * no reader is configured (platform Pending) or legacy data is absent.
     */
    suspend fun migrateLegacyData(): cn.mafei.cmp.data.migration.MigrationResult? =
        migrateLegacyData(allowConsumedRecovery = false)

    private suspend fun migrateLegacyData(
        allowConsumedRecovery: Boolean,
    ): cn.mafei.cmp.data.migration.MigrationResult? {
        val reader = legacySnapshotReader ?: return null
        check(!isClosed) { "Application is closed" }
        if (isLegacyMigrationConsumed() && !allowConsumedRecovery) return null
        val journal = cn.mafei.cmp.core.storage_api.VersionedSettingsStorage(settings)
        val sink = cn.mafei.cmp.data.migration.SettingsBackedMigrationSink(settings)
        val engine = cn.mafei.cmp.data.migration.LegacyMigrationEngine(
            reader = reader,
            secure = secureStore,
            journal = journal,
            sink = sink,
            nowEpochMs = { SystemEpochClock.nowEpochMs() },
            reportStage = { persistenceStageReporter("migration-$it") },
        )
        val result = try {
            engine.migrate()
        } catch (failure: Throwable) {
            failureReporter("migration", failure)
            throw failure
        }
        if (result == cn.mafei.cmp.data.migration.MigrationResult.NotNeeded) {
            settings.putString(LEGACY_MIGRATION_EMPTY_KEY, LEGACY_MIGRATION_EMPTY)
            markLegacyMigrationConsumed()
            return result
        }
        if (result is cn.mafei.cmp.data.migration.MigrationResult.Completed) {
            persistenceStageReporter("migration-records-read")
            val records = sink.storedRecords(result.migrationId)
                ?: return result
            persistenceStageReporter("migration-chasing-save")
            persistedChasingSeries.importMigrated(records.chasing)
            // Persist every mappable connection, then admit the migrated
            // current account through the SAME activate pipeline as a fresh
            // login: credentials are read from the secure store by the
            // SavedConnection aliases (which the planner shaped to match).
            val mapping = records.toSavedConnections()
            for (connection in mapping.connections) {
                persistenceStageReporter("migration-catalog-save")
                connectionCatalog.save(connection)
            }
            // A first activation may have failed after the migration receipt
            // was committed. Re-read the receipt on later starts and retry the
            // same activation until the normal session index is durable.
            if (sessions.state.value.session == null) {
                val connection = records.selectedAccountId?.let { selected ->
                    mapping.connections.firstOrNull { it.account.id == selected }
                } ?: mapping.connections.maxWithOrNull(
                    compareBy<SavedConnection> { it.account.lastActiveEpochMs ?: Long.MIN_VALUE }
                        .thenBy { it.account.id.value },
                )
                connection?.let {
                    persistenceStageReporter("migration-credential-restore")
                    val secrets = it.aliases.mapNotNull { alias ->
                        secureStore.read(alias)?.let { alias to it }
                    }.toMap()
                    if (secrets.isEmpty()) return result
                    persistenceStageReporter("migration-session-activate")
                    sessions.activate(it, cn.mafei.cmp.session.DefaultProviderSessionFactory(transport).restore(it, secrets))
                }
            }
            markLegacyMigrationConsumed()
            settings.putString(LEGACY_PASSWORD_IMPORT_KEY, LEGACY_PASSWORD_IMPORT_DONE)
        }
        return result
    }

    private suspend fun isLegacyMigrationConsumed(): Boolean =
        settings.getString(LEGACY_MIGRATION_CONSUMED_KEY) == LEGACY_MIGRATION_CONSUMED

    private suspend fun markLegacyMigrationConsumed() {
        if (!isLegacyMigrationConsumed()) {
            settings.putString(LEGACY_MIGRATION_CONSUMED_KEY, LEGACY_MIGRATION_CONSUMED)
        }
    }

    private suspend fun isLegacyMigrationConfirmedEmpty(): Boolean =
        settings.getString(LEGACY_MIGRATION_EMPTY_KEY) == LEGACY_MIGRATION_EMPTY

    private suspend fun isLegacyAccountRecoverySuppressed(): Boolean =
        settings.getString(LEGACY_RECOVERY_SUPPRESSED_KEY) == LEGACY_RECOVERY_SUPPRESSED

    private suspend fun suppressLegacyAccountRecovery() {
        settings.putString(LEGACY_RECOVERY_SUPPRESSED_KEY, LEGACY_RECOVERY_SUPPRESSED)
    }

    suspend fun close() = withContext(NonCancellable) {
        closeMutex.withLock {
            if (isClosed) return@withLock
            try {
                releaseAllPlaybacks()
                deferredFeiniuRouteRefresh?.cancel()
                deferredFeiniuRouteRefresh = null
                playbackObserver?.cancel()
                playbackObserver = null
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
        const val APPEARANCE_MODE_KEY = "mafei.ui.appearance-mode.v1"
        val APPEARANCE_MODES = setOf("auto", "light", "dark")
        const val GLASS_INTENSITY_KEY = "mafei.ui.liquid-glass-intensity.v1"
        const val DEFAULT_GLASS_INTENSITY = .5f
        const val FEINIU_ROUTE_PREFERENCE_KEY = "pref_feiniu_connection_preference"
        const val FEINIU_ROUTE_SPEED = "speed"
        const val FEINIU_ROUTE_PUBLIC = "public"
        const val FEINIU_ROUTE_RELAY = "relay"
        const val LEGACY_MIGRATION_CONSUMED_KEY = "mafei.legacy-migration.v1.consumed"
        const val LEGACY_MIGRATION_CONSUMED = "done"
        const val LEGACY_MIGRATION_EMPTY_KEY = "mafei.legacy-migration.v1.empty"
        const val LEGACY_MIGRATION_EMPTY = "confirmed"
        const val LEGACY_RECOVERY_SUPPRESSED_KEY = "mafei.legacy-migration.v1.recovery-suppressed"
        const val LEGACY_RECOVERY_SUPPRESSED = "logout"
        // v1 was written before the ArkUI password bridge was reliable. v2 could then be
        // committed while the HAP used a different signing certificate, making AssetStore
        // appear empty. Use a fresh receipt after restoring the historical signing identity.
        const val LEGACY_PASSWORD_IMPORT_KEY = "mafei.legacy-password.v3.imported"
        const val LEGACY_PASSWORD_IMPORT_DONE = "done"
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

private val RuntimeSettingsJson = Json { ignoreUnknownKeys = true }
private const val SEARCH_HISTORY_KEY = "mafei/ui/search-history-v1"
private const val PreferredMediaSourceKey = "mafei/media/playback/preferred-media-source-v1"
private const val PreferredMediaSourcesKey = "mafei/media/playback/preferred-media-sources-v2"
private const val PreferredMediaSourceLimit = 128

@Serializable
private data class PreferredMediaSource(
    val accountId: String,
    val mediaId: String,
    val sourceId: String,
)

@Serializable
private data class PreferredMediaSources(
    val entries: List<PreferredMediaSource> = emptyList(),
)

/** Called only by platform main-thread entry points. Rotation does not create a second writer. */
internal object ProcessApplication {
    private var current: ApplicationRuntime? = null
    fun getOrCreate(create: () -> ApplicationRuntime): ApplicationRuntime {
        val existing = current
        if (existing != null && !existing.isClosed) return existing
        return create().also { current = it }
    }
}
