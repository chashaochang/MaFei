import { describe, expect, it } from '@ohos/hypium'
import {
  createFeiniuVideoProviderAccountId,
  FeiniuVideoAccountRecord
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoAccountModels'
import {
  CREATE_FEINIU_VIDEO_ACCOUNT_TABLE_SQL,
  FEINIU_VIDEO_ACCOUNT_COLUMNS,
  FEINIU_VIDEO_ACCOUNT_DB_NAME,
  FEINIU_VIDEO_ACCOUNT_DB_VERSION,
  FeiniuVideoAccountBackend,
  FeiniuVideoAccountSchemaBackend,
  FeiniuVideoAccountStore,
  initializeFeiniuVideoAccountSchema
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoAccountStore'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'

class FakeFeiniuVideoAccountBackend implements FeiniuVideoAccountBackend {
  records: Map<string, FeiniuVideoAccountRecord> = new Map()

  listRecords(): FeiniuVideoAccountRecord[] {
    return Array.from(this.records.values())
  }

  findRecord(providerAccountId: string): FeiniuVideoAccountRecord | undefined {
    return this.records.get(providerAccountId)
  }

  upsertRecord(record: FeiniuVideoAccountRecord): void {
    this.records.set(record.providerAccountId, record)
  }

  deleteRecord(providerAccountId: string): void {
    this.records.delete(providerAccountId)
  }
}

class FakeFeiniuVideoSchemaBackend implements FeiniuVideoAccountSchemaBackend {
  version: number
  columns: string[] = FEINIU_VIDEO_ACCOUNT_COLUMNS.slice()
  events: string[] = []
  failExecute: boolean = false

  constructor(version: number) {
    this.version = version
  }

  getVersion(): number {
    return this.version
  }

  setVersion(version: number): void {
    this.events.push(`version:${version}`)
    this.version = version
  }

  execute(_sql: string): void {
    if (this.failExecute) {
      throw new Error('create failed')
    }
    this.events.push('create')
  }

  listColumns(_tableName: string): string[] {
    this.events.push('columns')
    return this.columns.slice()
  }

}

function record(address: string, userId: string, displayName: string): FeiniuVideoAccountRecord {
  return new FeiniuVideoAccountRecord({
    userId: userId,
    displayName: displayName,
    serverName: 'FNOS',
    originalAddress: address,
    baseUrl: 'https://media.example',
    connectionRoute: 'lan',
    fallbackBaseUrls: ['https://relay.example'],
    lastUsedTimestamp: 10
  })
}

export default function feiniuVideoAccountStoreTest() {
  describe('FeiniuVideoAccountStore', () => {
    it('usesAnIndependentDatabaseWithoutCredentialColumns', 0, () => {
      expect(FEINIU_VIDEO_ACCOUNT_DB_NAME).assertEqual('mafei_feiniu_accounts.db')
      expect(FEINIU_VIDEO_ACCOUNT_DB_NAME === 'jellyFin.db').assertFalse()
      expect(CREATE_FEINIU_VIDEO_ACCOUNT_TABLE_SQL.includes('token')).assertFalse()
      expect(CREATE_FEINIU_VIDEO_ACCOUNT_TABLE_SQL.includes('cookie')).assertFalse()
      expect(CREATE_FEINIU_VIDEO_ACCOUNT_TABLE_SQL.includes('password')).assertFalse()
    })

    it('serializesMetadataThroughTheExistingEntityValuesBucketPattern', 0, () => {
      const account = record('fn-one', 'user-1', 'First')
      const values = account.toValuesBucket()

      expect(values.provider_account_id as string).assertEqual(account.providerAccountId)
      expect(values.user_id as string).assertEqual('user-1')
      expect(values.original_address as string).assertEqual('fn-one')
      expect(values.fallback_base_urls as string).assertEqual('["https://relay.example"]')
      expect(JSON.stringify(values).includes('token')).assertFalse()
      expect(JSON.stringify(values).includes('cookie')).assertFalse()
      expect(JSON.stringify(values).includes('password')).assertFalse()
    })

    it('createsStableAccountIdsFromTheOriginalAddressAndUser', 0, () => {
      const first = createFeiniuVideoProviderAccountId(' fn123/ ', ' user-1 ')
      const second = createFeiniuVideoProviderAccountId('fn123', 'user-1')

      expect(first).assertEqual(second)
      expect(first).assertEqual('v1|5:fn123|6:user-1')
      expect(() => createFeiniuVideoProviderAccountId('', 'user-1'))
        .assertThrowError('Feiniu Video account identity must include an address and userId')
    })

    it('persistsAndDeletesOnlyTheMatchingFeiniuAccount', 0, () => {
      const backend = new FakeFeiniuVideoAccountBackend()
      const store = new FeiniuVideoAccountStore(backend)
      const first = record('fn-one', 'user-1', 'First')
      const second = record('fn-two', 'user-2', 'Second')

      store.save(first)
      store.save(second)

      expect(store.listAccounts().length).assertEqual(2)
      expect(store.findAccount(first.providerAccountId)?.displayName).assertEqual('First')
      expect(store.createSession(first.providerAccountId)?.provider).assertEqual(MediaProviderKind.FeiniuVideo)
      expect(store.createSession(first.providerAccountId)?.fallbackBaseUrls.length).assertEqual(1)

      store.delete(first.providerAccountId)

      expect(store.findAccount(first.providerAccountId) === undefined).assertTrue()
      expect(store.findAccount(second.providerAccountId)?.displayName).assertEqual('Second')
    })

    it('initializesTheFinalDevelopmentSchema', 0, () => {
      const backend = new FakeFeiniuVideoSchemaBackend(0)

      initializeFeiniuVideoAccountSchema(backend)

      expect(backend.version).assertEqual(FEINIU_VIDEO_ACCOUNT_DB_VERSION)
      expect(backend.events.join(','))
        .assertEqual('create,columns,version:1')
    })

    it('rejectsOutdatedDevelopmentSchemasInsteadOfMigratingThem', 0, () => {
      const backend = new FakeFeiniuVideoSchemaBackend(1)
      backend.columns = FEINIU_VIDEO_ACCOUNT_COLUMNS.filter((column: string): boolean =>
        column !== 'display_name')

      expect(() => initializeFeiniuVideoAccountSchema(backend))
        .assertThrowError('Feiniu Video development database is outdated; clear app data and reinstall. ' +
          'Missing columns: display_name')
      expect(backend.events.join(',')).assertEqual('create,columns')
    })

    it('rejectsNewerDevelopmentSchemas', 0, () => {
      const failed = new FakeFeiniuVideoSchemaBackend(0)
      const newer = new FakeFeiniuVideoSchemaBackend(FEINIU_VIDEO_ACCOUNT_DB_VERSION + 1)
      expect(() => initializeFeiniuVideoAccountSchema(newer))
        .assertThrowError('Feiniu Video account database version 2 is newer than supported version 1')
      expect(newer.events.length).assertEqual(0)
    })
  })
}
