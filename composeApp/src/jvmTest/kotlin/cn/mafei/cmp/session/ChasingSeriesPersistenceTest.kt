package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.data.migration.InMemorySettingsStorage
import cn.mafei.cmp.data.migration.MigratedChasing
import cn.mafei.cmp.data.migration.MigratedPlaybackPreferences
import cn.mafei.cmp.data.migration.MigrationRecords
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChasingSeriesPersistenceTest {
    @Test
    fun keepsSeriesListsIsolatedByAccountAndNormalizesIds() = runBlocking {
        val storage = InMemorySettingsStorage()
        val store = SettingsChasingSeriesStore(storage)

        store.replace(AccountId("account-a"), listOf(MediaId(" series-1 "), MediaId("series-1"), MediaId("series-2")))
        store.replace(AccountId("account-b"), listOf(MediaId("series-3")))

        assertEquals(listOf("series-1", "series-2"), store.seriesIds(AccountId("account-a")).map(MediaId::value))
        assertEquals(listOf("series-3"), store.seriesIds(AccountId("account-b")).map(MediaId::value))
    }

    @Test
    fun backfillsChasingFromAPreviouslyCompletedMigrationReceipt() = runBlocking {
        val storage = InMemorySettingsStorage()
        val accountId = AccountId("legacy-account")
        val records = MigrationRecords(
            servers = emptyList(),
            accounts = emptyList(),
            playback = MigratedPlaybackPreferences(null, null, null, emptyList()),
            chasing = listOf(MigratedChasing(accountId, listOf("series-b", "series-a"))),
            home = null,
            selectedAccountId = null,
            connections = emptyList(),
        )
        storage.putString(
            "mafei.migration.records.legacy-v1/test",
            Json.Default.encodeToString(MigrationRecords.serializer(), records),
        )

        val store = SettingsChasingSeriesStore(storage)

        assertEquals(listOf("series-b", "series-a"), store.seriesIds(accountId).map(MediaId::value))
    }

    @Test
    fun newerCmpValueWinsOverMigrationBackfill() = runBlocking {
        val storage = InMemorySettingsStorage()
        val accountId = AccountId("legacy-account")
        val store = SettingsChasingSeriesStore(storage)
        store.replace(accountId, emptyList())

        store.importMigrated(listOf(MigratedChasing(accountId, listOf("old-series"))))

        assertEquals(emptyList(), store.seriesIds(accountId))
    }
}
