import { MediaProviderKind } from '../MediaProviderKind'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaProviderAccount } from './MediaProviderAccount'

export interface MediaAccountRepository {
  readonly provider: MediaProviderKind

  listAccounts(): MediaProviderAccount[]

  findAccount(providerAccountId: string): MediaProviderAccount | undefined

  createSession(providerAccountId: string): MediaProviderSession | undefined
}
