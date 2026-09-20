package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.data.migration.MigratedChasing
import cn.mafei.cmp.data.migration.MigrationRecords
import cn.mafei.cmp.feature.chasing.ChasingSeriesStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Account-scoped replacement for ArkUI's `pref_chasing_series_ids_<server>_<user>`. */
internal class SettingsChasingSeriesStore(
    private val settings: SettingsStorage,
) : ChasingSeriesStore {
    private val mutex = Mutex()
    private var migrationBackfillChecked = false

    override suspend fun seriesIds(accountId: AccountId): List<MediaId> = mutex.withLock {
        ensureMigrationBackfillLocked()
        loadLocked().entries.firstOrNull { it.accountId == accountId.value }
            ?.seriesIds.orEmpty()
            .map(::MediaId)
    }

    override suspend fun replace(accountId: AccountId, seriesIds: List<MediaId>) = mutex.withLock {
        ensureMigrationBackfillLocked()
        val current = loadLocked()
        val replacement = ChasingSeriesEntry(
            accountId = accountId.value,
            seriesIds = normalize(seriesIds.map(MediaId::value)),
        )
        saveLocked(
            ChasingSeriesBook(
                entries = (current.entries.filterNot { it.accountId == accountId.value } + replacement)
                    .sortedBy(ChasingSeriesEntry::accountId),
            ),
        )
    }

    /** Called in the same startup that consumes a newly completed ArkUI migration. */
    suspend fun importMigrated(records: List<MigratedChasing>) = mutex.withLock {
        val entries = loadLocked().entries.associateByTo(linkedMapOf(), ChasingSeriesEntry::accountId)
        records.forEach { migrated ->
            // A CMP value, including an explicitly emptied list, is newer and wins.
            if (migrated.accountId.value !in entries) {
                entries[migrated.accountId.value] =
                    ChasingSeriesEntry(migrated.accountId.value, normalize(migrated.seriesIds))
            }
        }
        val encoded = ChasingJson.encodeToString(
            ChasingSeriesBook.serializer(),
            ChasingSeriesBook(entries.values.sortedBy(ChasingSeriesEntry::accountId)),
        )
        settings.edit {
            putString(ChasingSeriesKey, encoded)
            putString(ChasingMigrationBackfillKey, BackfillDone)
        }
        migrationBackfillChecked = true
    }

    /**
     * Earlier CMP builds committed `MigrationRecords.chasing` but had no production consumer.
     * Import those receipts once on first use so upgrades do not lose an already migrated list.
     */
    private suspend fun ensureMigrationBackfillLocked() {
        if (migrationBackfillChecked) return
        if (settings.getString(ChasingMigrationBackfillKey) == BackfillDone) {
            migrationBackfillChecked = true
            return
        }

        val entries = loadLocked().entries.associateByTo(linkedMapOf(), ChasingSeriesEntry::accountId)
        settings.listKeys(MigrationRecordsPrefix).sorted().forEach { key ->
            val raw = settings.getString(key) ?: return@forEach
            val records = ChasingJson.decodeFromString(MigrationRecords.serializer(), raw)
            records.chasing.forEach { migrated ->
                if (migrated.accountId.value !in entries) {
                    entries[migrated.accountId.value] =
                        ChasingSeriesEntry(migrated.accountId.value, normalize(migrated.seriesIds))
                }
            }
        }

        val encoded = ChasingJson.encodeToString(
            ChasingSeriesBook.serializer(),
            ChasingSeriesBook(entries.values.sortedBy(ChasingSeriesEntry::accountId)),
        )
        settings.edit {
            putString(ChasingSeriesKey, encoded)
            putString(ChasingMigrationBackfillKey, BackfillDone)
        }
        migrationBackfillChecked = true
    }

    private suspend fun loadLocked(): ChasingSeriesBook =
        settings.getString(ChasingSeriesKey)?.let {
            ChasingJson.decodeFromString(ChasingSeriesBook.serializer(), it)
        } ?: ChasingSeriesBook()

    private suspend fun saveLocked(book: ChasingSeriesBook) {
        settings.putString(
            ChasingSeriesKey,
            ChasingJson.encodeToString(ChasingSeriesBook.serializer(), book),
        )
    }

    private fun normalize(ids: List<String>): List<String> = ids.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
}

@Serializable
private data class ChasingSeriesBook(
    val entries: List<ChasingSeriesEntry> = emptyList(),
)

@Serializable
private data class ChasingSeriesEntry(
    val accountId: String,
    val seriesIds: List<String>,
)

private val ChasingJson = Json { ignoreUnknownKeys = true }
private const val ChasingSeriesKey = "mafei/ui/chasing-series-v1"
private const val ChasingMigrationBackfillKey = "$ChasingSeriesKey.migration-backfill"
private const val MigrationRecordsPrefix = "mafei.migration.records."
private const val BackfillDone = "done"
