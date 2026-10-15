import { common } from '@kit.AbilityKit'
import { relationalStore } from '@kit.ArkData'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { MediaAccountRepository } from '../../../media/account/MediaAccountRepository'
import { MediaProviderAccount } from '../../../media/account/MediaProviderAccount'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import {
  FEINIU_VIDEO_ACCOUNT_COLUMNS,
  FEINIU_VIDEO_ACCOUNT_COLUMN_LAST_USED_TIMESTAMP,
  FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID,
  FEINIU_VIDEO_ACCOUNT_TABLE_NAME,
  FeiniuVideoAccountRecord
} from './FeiniuVideoAccountModels'

export {
  FEINIU_VIDEO_ACCOUNT_COLUMNS,
  FEINIU_VIDEO_ACCOUNT_TABLE_NAME
} from './FeiniuVideoAccountModels'

export const FEINIU_VIDEO_ACCOUNT_DB_NAME: string = 'mafei_feiniu_accounts.db'
export const FEINIU_VIDEO_ACCOUNT_DB_VERSION: number = 1
export const CREATE_FEINIU_VIDEO_ACCOUNT_TABLE_SQL: string = FeiniuVideoAccountRecord.getCreateTableSql()

export interface FeiniuVideoAccountSchemaBackend {
  getVersion(): number
  setVersion(version: number): void
  execute(sql: string): void
  listColumns(tableName: string): string[]
}

export interface FeiniuVideoAccountBackend {
  listRecords(): FeiniuVideoAccountRecord[]
  findRecord(providerAccountId: string): FeiniuVideoAccountRecord | undefined
  upsertRecord(record: FeiniuVideoAccountRecord): void
  deleteRecord(providerAccountId: string): void
}

export function initializeFeiniuVideoAccountSchema(backend: FeiniuVideoAccountSchemaBackend): void {
  const currentVersion = backend.getVersion()
  if (currentVersion > FEINIU_VIDEO_ACCOUNT_DB_VERSION) {
    throw new Error(`Feiniu Video account database version ${currentVersion} is newer than supported version ` +
      `${FEINIU_VIDEO_ACCOUNT_DB_VERSION}`)
  }
  backend.execute(CREATE_FEINIU_VIDEO_ACCOUNT_TABLE_SQL)
  const columns = backend.listColumns(FEINIU_VIDEO_ACCOUNT_TABLE_NAME)
  const missingColumns = FEINIU_VIDEO_ACCOUNT_COLUMNS.filter((column: string): boolean =>
    columns.indexOf(column) === -1)
  if (missingColumns.length > 0) {
    throw new Error(`Feiniu Video development database is outdated; clear app data and reinstall. Missing columns: ` +
      `${missingColumns.join(', ')}`)
  }
  if (currentVersion < FEINIU_VIDEO_ACCOUNT_DB_VERSION) {
    backend.setVersion(FEINIU_VIDEO_ACCOUNT_DB_VERSION)
  }
}

class RdbFeiniuVideoAccountSchemaBackend implements FeiniuVideoAccountSchemaBackend {
  private store: relationalStore.RdbStore

  constructor(store: relationalStore.RdbStore) {
    this.store = store
  }

  getVersion(): number {
    return this.store.version
  }

  setVersion(version: number): void {
    this.store.version = version
  }

  execute(sql: string): void {
    this.store.executeSync(sql)
  }

  listColumns(tableName: string): string[] {
    const resultSet = this.store.querySqlSync(`PRAGMA table_info(\`${tableName}\`)`)
    const columns: string[] = []
    try {
      const nameIndex = resultSet.getColumnIndex('name')
      while (resultSet.goToNextRow()) {
        columns.push(resultSet.getString(nameIndex))
      }
    } finally {
      resultSet.close()
    }
    return columns
  }

}

// Sync-only subset copied from the existing data/Rdb table wrapper.
class FeiniuVideoAccountRdb {
  private store: relationalStore.RdbStore
  private tableName: string
  private columns: string[]

  constructor(store: relationalStore.RdbStore, tableName: string, columns: string[]) {
    this.store = store
    this.tableName = tableName
    this.columns = columns
  }

  insertDataSync(values: relationalStore.ValuesBucket,
    conflict: relationalStore.ConflictResolution): number {
    return this.store.insertSync(this.tableName, values, conflict)
  }

  deleteDataSync(predicates: relationalStore.RdbPredicates): number {
    return this.store.deleteSync(predicates)
  }

  queryDataSync(predicates: relationalStore.RdbPredicates): relationalStore.ResultSet {
    return this.store.querySync(predicates, this.columns)
  }
}

// DAO shape copied from ServerDao and narrowed to the independent account table.
class RdbFeiniuVideoAccountBackend implements FeiniuVideoAccountBackend {
  private accountTable: FeiniuVideoAccountRdb

  constructor(store: relationalStore.RdbStore) {
    this.accountTable = new FeiniuVideoAccountRdb(
      store,
      FEINIU_VIDEO_ACCOUNT_TABLE_NAME,
      FEINIU_VIDEO_ACCOUNT_COLUMNS
    )
  }

  listRecords(): FeiniuVideoAccountRecord[] {
    const predicates = new relationalStore.RdbPredicates(FEINIU_VIDEO_ACCOUNT_TABLE_NAME)
    predicates.orderByDesc(FEINIU_VIDEO_ACCOUNT_COLUMN_LAST_USED_TIMESTAMP)
    const resultSet = this.accountTable.queryDataSync(predicates)
    const records: FeiniuVideoAccountRecord[] = []
    try {
      while (resultSet.goToNextRow()) {
        records.push(FeiniuVideoAccountRecord.fromResultSet(resultSet))
      }
    } finally {
      resultSet.close()
    }
    return records
  }

  findRecord(providerAccountId: string): FeiniuVideoAccountRecord | undefined {
    const predicates = new relationalStore.RdbPredicates(FEINIU_VIDEO_ACCOUNT_TABLE_NAME)
    predicates.equalTo(FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID, providerAccountId.trim())
    const resultSet = this.accountTable.queryDataSync(predicates)
    try {
      return resultSet.goToNextRow() ? FeiniuVideoAccountRecord.fromResultSet(resultSet) : undefined
    } finally {
      resultSet.close()
    }
  }

  upsertRecord(record: FeiniuVideoAccountRecord): void {
    this.accountTable.insertDataSync(record.toValuesBucket(),
      relationalStore.ConflictResolution.ON_CONFLICT_REPLACE)
  }

  deleteRecord(providerAccountId: string): void {
    const predicates = new relationalStore.RdbPredicates(FEINIU_VIDEO_ACCOUNT_TABLE_NAME)
    predicates.equalTo(FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID, providerAccountId.trim())
    this.accountTable.deleteDataSync(predicates)
  }
}

export class FeiniuVideoAccountStore implements MediaAccountRepository {
  readonly provider: MediaProviderKind = MediaProviderKind.FeiniuVideo
  private backend: FeiniuVideoAccountBackend

  constructor(backend: FeiniuVideoAccountBackend) {
    this.backend = backend
  }

  static async create(context: common.Context): Promise<FeiniuVideoAccountStore> {
    let stage = 'open_account_store'
    try {
      const config: relationalStore.StoreConfig = {
        name: FEINIU_VIDEO_ACCOUNT_DB_NAME,
        securityLevel: relationalStore.SecurityLevel.S1
      }
      const store = await relationalStore.getRdbStore(context, config)
      stage = 'initialize_account_schema'
      initializeFeiniuVideoAccountSchema(new RdbFeiniuVideoAccountSchemaBackend(store))
      return new FeiniuVideoAccountStore(new RdbFeiniuVideoAccountBackend(store))
    } catch (error) {
      const normalizedError = error as Error
      hilog.error(0x0000, 'mafei-feiniu-login',
        'account store initialization failed stage=%{public}s type=%{public}s',
        stage, normalizedError.name || 'Error')
      throw normalizedError
    }
  }

  listRecords(): FeiniuVideoAccountRecord[] {
    return this.backend.listRecords()
  }

  findRecord(providerAccountId: string): FeiniuVideoAccountRecord | undefined {
    const normalizedId = providerAccountId.trim()
    return normalizedId.length > 0 ? this.backend.findRecord(normalizedId) : undefined
  }

  save(record: FeiniuVideoAccountRecord): MediaProviderAccount {
    this.backend.upsertRecord(record)
    return record.toProviderAccount()
  }

  delete(providerAccountId: string): void {
    const normalizedId = providerAccountId.trim()
    if (normalizedId.length > 0) {
      this.backend.deleteRecord(normalizedId)
    }
  }

  listAccounts(): MediaProviderAccount[] {
    return this.listRecords().map((record: FeiniuVideoAccountRecord): MediaProviderAccount =>
      record.toProviderAccount())
  }

  findAccount(providerAccountId: string): MediaProviderAccount | undefined {
    return this.findRecord(providerAccountId)?.toProviderAccount()
  }

  createSession(providerAccountId: string): MediaProviderSession | undefined {
    return this.findAccount(providerAccountId)?.toSession()
  }
}
