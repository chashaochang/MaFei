package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackEventKind
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.data.migration.MigratedAccount
import cn.mafei.cmp.data.migration.MigratedServerConnection
import cn.mafei.cmp.data.migration.MigrationRecords
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private val jellyfinServer = Server(ServerId("srv-1"), ProviderKind.Jellyfin, "Legacy Jellyfin", "http://192.0.2.10:8096")
private val feiniuServer = Server(ServerId("srv-fn"), ProviderKind.Feiniu, "Legacy NAS", "http://192.0.2.20:5667")

private fun jellyfinAccount() = MigratedAccount(
    account = Account(AccountId("acc-1"), ServerId("srv-1"), ProviderKind.Jellyfin, "demo"),
    remoteUserId = "remote-1",
    tokenAlias = "mafei/session/${"a".repeat(32)}/token",
    credentialKey = "a".repeat(32),
)

private fun feiniuAccount() = MigratedAccount(
    account = Account(AccountId("acc-fn"), ServerId("srv-fn"), ProviderKind.Feiniu, "NAS User"),
    remoteUserId = "remote-fn",
    tokenAlias = "mafei/session/${"b".repeat(32)}/token",
    cookieAlias = "mafei/session/${"b".repeat(32)}/cookie",
    credentialKey = "b".repeat(32),
)

class MigrationCompositionTest {

    private fun records(vararg extras: MigratedAccount, feiniuConnection: MigratedServerConnection? = null) = MigrationRecords(
        servers = listOf(jellyfinServer, feiniuServer),
        accounts = listOf(jellyfinAccount()) + extras,
        playback = cn.mafei.cmp.data.migration.MigratedPlaybackPreferences(null, null, null, emptyList()),
        chasing = emptyList(),
        home = null,
        selectedAccountId = null,
        connections = listOfNotNull(feiniuConnection),
    )

    @Test
    fun mappingUsesTheMigratedCredentialNamespace() {
        val mapping = records().toSavedConnections()
        val connection = mapping.connections.single()
        assertEquals("a".repeat(32), connection.credentialKey)
        // SavedConnection derives its token alias from the SAME namespace, so
        // the migrated secret stays readable after restore.
        assertEquals("mafei/session/${"a".repeat(32)}/token", connection.tokenAlias.value)
        assertEquals(0, mapping.skipped.size)
    }

    @Test
    fun feiniuRoutesAreRebuiltFromTheConnectionRecord() {
        val mapping = records(
            feiniuAccount(),
            feiniuConnection = MigratedServerConnection(
                serverId = ServerId("srv-fn"),
                originalAddress = "fnid://legacy",
                connectionRoute = "Relay",
                fallbackBaseUrls = listOf("http://192.0.2.99:5667"),
            ),
        ).toSavedConnections()
        val feiniu = mapping.connections.single { it.server.provider == ProviderKind.Feiniu }
        assertEquals(
            listOf(
                cn.mafei.cmp.session.SavedFeiniuRoute("http://192.0.2.20:5667", "Relay"),
                cn.mafei.cmp.session.SavedFeiniuRoute("http://192.0.2.99:5667", "Relay"),
            ),
            feiniu.feiniuRoutes,
        )
        assertEquals("mafei/session/${"b".repeat(32)}/token", feiniu.tokenAlias.value)
    }

    @Test
    fun unknownServerAccountsAreSkippedObservably() {
        val orphan = MigratedAccount(
            account = Account(AccountId("acc-orphan"), ServerId("srv-missing"), ProviderKind.Jellyfin, "Orphan"),
            remoteUserId = "remote-2",
            tokenAlias = "mafei/session/${"c".repeat(32)}/token",
            credentialKey = "c".repeat(32),
        )
        val mapping = records(orphan).toSavedConnections()
        assertEquals(listOf("acc-1"), mapping.connections.map { it.account.id.value })
        assertEquals(1, mapping.skipped.size)
        assertEquals("acc-orphan", mapping.skipped.single().account.account.id.value)
    }

    @Test
    fun sessionPlaybackBindsAdapterReportsThroughTheComposition() = runBlocking<Unit> {
        val adapter = RecordingAdapter()
        val runtime = CountingRuntime()
        val playback = SessionPlaybackRuntime.create(
            provider = ProviderKind.Jellyfin,
            adapter = adapter,
            runtime = runtime,
            scope = CoroutineScope(Dispatchers.Unconfined),
            nowMs = { 0L },
        )
        val generation: PlaybackGeneration? = kotlinx.coroutines.withTimeout(5_000) {
            playback.play(MediaId("item-1"))
        }
        assertEquals("item-1", runtime.lastLoad?.id)
        runtime.emitReady(checkNotNull(generation))
        runtime.emitPlaying(checkNotNull(generation))
        // The Start report flows through the SAME adapter instance bound at composition.
        assertEquals(1, adapter.reports.size)
        assertEquals(PlaybackEventKind.Start, adapter.reports.single().second.kind)
        playback.release()
    }

    @Test
    fun releaseRefusesFurtherPlayFromTheComposition() = runBlocking<Unit> {
        val adapter = RecordingAdapter()
        val runtime = CountingRuntime()
        val playback = SessionPlaybackRuntime.create(
            provider = ProviderKind.Jellyfin,
            adapter = adapter,
            runtime = runtime,
            scope = CoroutineScope(Dispatchers.Unconfined),
            nowMs = { 0L },
        )
        playback.release()
        // A released coordinator refuses play instead of silently no-oping.
        assertFailsWith<IllegalStateException> { playback.play(MediaId("item-1")) }
    }
}
