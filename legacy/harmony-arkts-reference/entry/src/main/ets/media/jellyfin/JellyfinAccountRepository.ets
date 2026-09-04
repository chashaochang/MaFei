import { DBManager } from '../../data/DBManager'
import ServerUser from '../../data/ServerUser'
import { MediaAccountRepository } from '../account/MediaAccountRepository'
import { MediaProviderAccount } from '../account/MediaProviderAccount'
import { MediaProviderKind } from '../MediaProviderKind'
import { MediaConnectionRoute, MediaProviderSession } from '../MediaProviderSession'

export interface JellyfinAccountSource {
  listServerUsers(): ServerUser[]
}

export class JellyfinDatabaseAccountSource implements JellyfinAccountSource {
  listServerUsers(): ServerUser[] {
    const manager = DBManager.getInstance()
    const result: ServerUser[] = []
    const users = manager.getUserDao().queryAllDataSync()
    for (const user of users) {
      const server = manager.getServerDao().getServer(user.serverId)
      if (server) {
        result.push(new ServerUser(user, server))
      }
    }
    return result
  }
}

export function createJellyfinProviderAccountId(serverId: number, userId: string): string {
  const normalizedUserId = userId.trim()
  return `v1|${serverId}|${normalizedUserId.length}:${normalizedUserId}`
}

export class JellyfinAccountRepository implements MediaAccountRepository {
  readonly provider: MediaProviderKind = MediaProviderKind.Jellyfin
  private source: JellyfinAccountSource

  constructor(source: JellyfinAccountSource = new JellyfinDatabaseAccountSource()) {
    this.source = source
  }

  listAccounts(): MediaProviderAccount[] {
    const result: MediaProviderAccount[] = []
    for (const stored of this.source.listServerUsers()) {
      const account = this.mapAccount(stored)
      if (account) {
        result.push(account)
      }
    }
    return result
  }

  findAccount(providerAccountId: string): MediaProviderAccount | undefined {
    const normalizedId = providerAccountId.trim()
    return this.listAccounts().find((account: MediaProviderAccount): boolean =>
      account.providerAccountId === normalizedId)
  }

  createSession(providerAccountId: string): MediaProviderSession | undefined {
    return this.findAccount(providerAccountId)?.toSession()
  }

  private mapAccount(stored: ServerUser): MediaProviderAccount | undefined {
    const serverId = stored.server.id
    const userId = stored.user.userId.trim()
    const hostname = stored.server.hostname.trim()
    if (serverId <= 0 || userId.length === 0 || hostname.length === 0) {
      return undefined
    }
    return new MediaProviderAccount({
      provider: MediaProviderKind.Jellyfin,
      providerAccountId: createJellyfinProviderAccountId(serverId, userId),
      userId: userId,
      displayName: stored.user.username,
      serverName: stored.server.servername,
      originalAddress: hostname,
      baseUrl: hostname,
      connectionRoute: MediaConnectionRoute.Direct,
      lastUsedTimestamp: stored.user.lastLoginTimestamp
    })
  }
}
