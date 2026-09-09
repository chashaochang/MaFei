package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.ClientConfigError
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
import cn.mafei.cmp.data.migration.MigrationRecords
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MigrationCompositionTest {

    private val records = MigrationRecords(
        servers = listOf(Server(ServerId("srv-1"), ProviderKind.Jellyfin, "Legacy Jellyfin", "http://192.0.2.10:8096")),
        accounts = listOf(
            MigratedAccount(
                account = Account(
                    AccountId("acc-1"),
                    ServerId("srv-1"),
                    ProviderKind.Jellyfin,
                    "demo",
                ),
                remoteUserId = "remote-1",
                tokenAlias = "acc-1/token",
            ),
        ),
        playback = cn.mafei.cmp.data.migration.MigratedPlaybackPreferences(null, null, null, emptyList()),
        chasing = emptyList(),
        home = null,
        selectedAccountId = null,
        connections = emptyList(),
    )

    @Test
    fun mappingProducesJellyfinConnectionsWithStableCredentialKeys() {
        val mapping = records.toSavedConnections(credentialKeyFor = { "a".repeat(32) })
        val connection = mapping.connections.single()
        assertEquals(ProviderKind.Jellyfin, connection.server.provider)
        assertEquals("remote-1", connection.remoteUserId)
        assertEquals("a".repeat(32), connection.credentialKey)
        assertEquals(0, mapping.skipped.size)
    }

    @Test
    fun unknownServerAccountsAreSkippedObservably() {
        val broken = records.copy(
            accounts = records.accounts + MigratedAccount(
                account = Account(
                    AccountId("acc-orphan"),
                    ServerId("srv-missing"),
                    ProviderKind.Jellyfin,
                    "Orphan",
                ),
                remoteUserId = "remote-2",
                tokenAlias = "acc-orphan/token",
            ),
        )
        val mapping = broken.toSavedConnections(credentialKeyFor = { "a".repeat(32) })
        // The known account maps; the orphan is skipped with a reason, never dropped silently.
        assertEquals(listOf("acc-1"), mapping.connections.map { it.account.id.value })
        assertEquals(1, mapping.skipped.size)
        assertEquals("acc-orphan", mapping.skipped.single().account.account.id.value)
    }

    @Test
    fun sessionPlaybackBindsAdapterReportsThroughTheComposition() = runBlocking {
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
