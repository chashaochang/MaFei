import { describe, expect, it } from '@ohos/hypium'
import ServerEntity from '../main/ets/data/ServerEntity'
import ServerUser from '../main/ets/data/ServerUser'
import UserEntity from '../main/ets/data/UserEntity'
import {
  createJellyfinProviderAccountId,
  JellyfinAccountRepository,
  JellyfinAccountSource
} from '../main/ets/media/jellyfin/JellyfinAccountRepository'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaConnectionRoute } from '../main/ets/media/MediaProviderSession'

class FakeJellyfinAccountSource implements JellyfinAccountSource {
  items: ServerUser[]
  reads: number = 0

  constructor(items: ServerUser[]) {
    this.items = items
  }

  listServerUsers(): ServerUser[] {
    this.reads += 1
    return this.items.slice()
  }
}

function storedAccount(serverId: number, hostname: string, userId: string,
  username: string = 'Jellyfin User'): ServerUser {
  const server = new ServerEntity(hostname)
  server.id = serverId
  server.servername = 'Jellyfin Server'
  server.version = '10.10.7'
  server.lastUsedTimestamp = 100
  const user = new UserEntity(serverId, userId, 'secret-access-token', username)
  user.id = serverId + 100
  user.lastLoginTimestamp = 200
  return new ServerUser(user, server)
}

export default function jellyfinAccountRepositoryTest() {
  describe('JellyfinAccountRepository', () => {
    it('mapsExistingServerUserDataWithoutExposingCredentials', 0, () => {
      const stored = storedAccount(41, 'https://jellyfin.example', 'user-1')
      const source = new FakeJellyfinAccountSource([stored])
      const repository = new JellyfinAccountRepository(source)

      const accounts = repository.listAccounts()

      expect(accounts.length).assertEqual(1)
      expect(accounts[0].provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(accounts[0].providerAccountId).assertEqual(
        createJellyfinProviderAccountId(41, 'user-1'))
      expect(accounts[0].userId).assertEqual('user-1')
      expect(accounts[0].displayName).assertEqual('Jellyfin User')
      expect(accounts[0].serverName).assertEqual('Jellyfin Server')
      expect(accounts[0].baseUrl).assertEqual('https://jellyfin.example')
      expect(accounts[0].lastUsedTimestamp).assertEqual(200)
      expect(JSON.stringify(accounts[0]).includes('secret-access-token')).assertFalse()
      expect(stored.user.accessToken).assertEqual('secret-access-token')
      expect(source.reads).assertEqual(1)
    })

    it('createsANeutralSessionFromTheSelectedStoredAccount', 0, () => {
      const repository = new JellyfinAccountRepository(new FakeJellyfinAccountSource([
        storedAccount(41, 'https://jellyfin.example', 'user-1')
      ]))
      const providerAccountId = createJellyfinProviderAccountId(41, 'user-1')

      const session = repository.createSession(providerAccountId)

      expect(session?.provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(session?.providerAccountId).assertEqual(providerAccountId)
      expect(session?.userId).assertEqual('user-1')
      expect(session?.originalAddress).assertEqual('https://jellyfin.example')
      expect(session?.baseUrl).assertEqual('https://jellyfin.example')
      expect(session?.connectionRoute).assertEqual(MediaConnectionRoute.Direct)
      expect(session?.capabilities.management).assertTrue()
    })

    it('keepsSameUserIdsOnDifferentServersAsDifferentAccounts', 0, () => {
      const repository = new JellyfinAccountRepository(new FakeJellyfinAccountSource([
        storedAccount(41, 'https://first.example', 'shared-user'),
        storedAccount(42, 'https://second.example', 'shared-user')
      ]))

      const accounts = repository.listAccounts()

      expect(accounts.length).assertEqual(2)
      expect(accounts[0].providerAccountId === accounts[1].providerAccountId).assertFalse()
      expect(accounts[0].accountScope === accounts[1].accountScope).assertFalse()
    })

    it('skipsInvalidRowsAndReturnsUndefinedForMissingAccounts', 0, () => {
      const invalidServer = storedAccount(0, '', 'user-1')
      const invalidUser = storedAccount(41, 'https://jellyfin.example', '  ')
      const repository = new JellyfinAccountRepository(new FakeJellyfinAccountSource([
        invalidServer,
        invalidUser
      ]))

      expect(repository.listAccounts().length).assertEqual(0)
      expect(repository.findAccount('missing') === undefined).assertTrue()
      expect(repository.createSession('missing') === undefined).assertTrue()
    })
  })
}
