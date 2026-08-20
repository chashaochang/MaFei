import { describe, expect, it } from '@ohos/hypium'
import { mediaProviderCapabilities } from '../main/ets/media/MediaProviderCapabilities'
import {
  MediaProviderKind,
  normalizeMediaProviderKind
} from '../main/ets/media/MediaProviderKind'
import {
  MediaProviderRegistry,
  MediaProviderScopedMemoryCache,
  MediaProviderScopedPlayback
} from '../main/ets/media/MediaProviderRegistry'
import {
  MediaConnectionRoute,
  MediaProviderSession
} from '../main/ets/media/MediaProviderSession'

class RecordingScopedPlayback implements MediaProviderScopedPlayback {
  stoppedScopes: string[] = []

  async stopForScope(accountScope: string): Promise<void> {
    this.stoppedScopes.push(accountScope)
  }
}

class RecordingScopedCache implements MediaProviderScopedMemoryCache {
  clearedScopes: string[] = []

  clearScope(accountScope: string): void {
    this.clearedScopes.push(accountScope)
  }
}

class RetryOnceScopedPlayback implements MediaProviderScopedPlayback {
  attempts: number = 0

  async stopForScope(accountScope: string): Promise<void> {
    this.attempts += 1
    if (this.attempts === 1) {
      throw new Error(`playback cleanup failed for ${accountScope}`)
    }
  }
}

class RetryOnceScopedCache implements MediaProviderScopedMemoryCache {
  attempts: number = 0

  clearScope(accountScope: string): void {
    this.attempts += 1
    if (this.attempts === 1) {
      throw new Error(`cache cleanup failed for ${accountScope}`)
    }
  }
}

function session(provider: MediaProviderKind, providerAccountId: string, userId: string,
  baseUrl: string = 'https://media.example'): MediaProviderSession {
  return new MediaProviderSession({
    provider: provider,
    providerAccountId: providerAccountId,
    userId: userId,
    originalAddress: baseUrl,
    baseUrl: baseUrl,
    connectionRoute: MediaConnectionRoute.Direct,
    displayName: userId
  })
}

export default function mediaProviderSessionTest() {
  describe('MediaProviderSession', () => {
    it('scopesAccountsByProviderAndProviderAccountId', 0, () => {
      const jellyfin = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1')
      const sameAccountWithRefreshedUser = session(
        MediaProviderKind.Jellyfin,
        'jellyfin-account-1',
        'user-2'
      )
      const anotherAccount = session(MediaProviderKind.Jellyfin, 'jellyfin-account-2', 'user-1')
      const feiniu = session(MediaProviderKind.FeiniuVideo, 'feiniu-account-1', 'user-1')

      expect(jellyfin.isSameAccount(
        session(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1'))).assertTrue()
      expect(jellyfin.isSameAccount(sameAccountWithRefreshedUser)).assertTrue()
      expect(jellyfin.isSameAccount(anotherAccount)).assertFalse()
      expect(jellyfin.isSameAccount(feiniu)).assertFalse()
    })

    it('exposesOnlyVideoCapabilitiesForFeiniuVideo', 0, () => {
      const capabilities = mediaProviderCapabilities(MediaProviderKind.FeiniuVideo)

      expect(capabilities.videoCatalog).assertTrue()
      expect(capabilities.search).assertTrue()
      expect(capabilities.playback).assertTrue()
      expect(capabilities.chasing).assertFalse()
      expect(capabilities.music).assertFalse()
      expect(capabilities.photos).assertFalse()
      expect(capabilities.liveTv).assertFalse()
      expect(capabilities.management).assertFalse()
    })

    it('keepsLegacyChasingCapabilityOnJellyfinOnly', 0, () => {
      expect(mediaProviderCapabilities(MediaProviderKind.Jellyfin).chasing).assertTrue()
      expect(mediaProviderCapabilities(MediaProviderKind.FeiniuVideo).chasing).assertFalse()
    })

    it('defaultsLegacyAndUnknownProviderValuesToJellyfin', 0, () => {
      expect(normalizeMediaProviderKind('feiniu_video')).assertEqual(MediaProviderKind.FeiniuVideo)
      expect(normalizeMediaProviderKind('')).assertEqual(MediaProviderKind.Jellyfin)
      expect(normalizeMediaProviderKind('unknown')).assertEqual(MediaProviderKind.Jellyfin)
    })

    it('releasesOnlyThePreviousAccountScopeOnSwitch', 0, async () => {
      const registry = new MediaProviderRegistry()
      const previous = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1')
      const next = session(MediaProviderKind.FeiniuVideo, 'feiniu-account-2', 'user-2')
      const previousPlayback = new RecordingScopedPlayback()
      const previousCache = new RecordingScopedCache()
      const nextCache = new RecordingScopedCache()

      await registry.activateSession(previous)
      registry.registerScopedPlayback(previous.accountScope, previousPlayback)
      registry.registerScopedMemoryCache(previous.accountScope, previousCache)
      registry.registerScopedMemoryCache(next.accountScope, nextCache)
      await registry.activateSession(next)

      expect(previousPlayback.stoppedScopes.length).assertEqual(1)
      expect(previousPlayback.stoppedScopes[0]).assertEqual(previous.accountScope)
      expect(previousCache.clearedScopes.length).assertEqual(1)
      expect(previousCache.clearedScopes[0]).assertEqual(previous.accountScope)
      expect(nextCache.clearedScopes.length).assertEqual(0)
      expect(registry.getActiveSession()?.accountScope).assertEqual(next.accountScope)
    })

    it('updatesConnectionDetailsWithoutClearingTheSameAccount', 0, async () => {
      const registry = new MediaProviderRegistry()
      const previous = session(
        MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1', 'https://old.example')
      const refreshed = new MediaProviderSession({
        provider: MediaProviderKind.Jellyfin,
        providerAccountId: 'jellyfin-account-1',
        userId: 'user-1',
        originalAddress: 'https://old.example',
        baseUrl: 'https://new.example',
        connectionRoute: MediaConnectionRoute.Public
      })
      const cache = new RecordingScopedCache()

      await registry.activateSession(previous)
      registry.registerScopedMemoryCache(previous.accountScope, cache)
      await registry.activateSession(refreshed)

      expect(cache.clearedScopes.length).assertEqual(0)
      expect(registry.getActiveSession()?.baseUrl).assertEqual('https://new.example')
    })

    it('retriesOnlyFailedScopeCleanupBeforeActivatingTheNextSession', 0, async () => {
      const registry = new MediaProviderRegistry()
      const previous = session(MediaProviderKind.Jellyfin, 'jellyfin-account-1', 'user-1')
      const next = session(MediaProviderKind.FeiniuVideo, 'feiniu-account-2', 'user-2')
      const successfulPlayback = new RecordingScopedPlayback()
      const retryingPlayback = new RetryOnceScopedPlayback()
      const successfulCache = new RecordingScopedCache()
      const retryingCache = new RetryOnceScopedCache()

      await registry.activateSession(previous)
      registry.registerScopedPlayback(previous.accountScope, successfulPlayback)
      registry.registerScopedPlayback(previous.accountScope, retryingPlayback)
      registry.registerScopedMemoryCache(previous.accountScope, successfulCache)
      registry.registerScopedMemoryCache(previous.accountScope, retryingCache)

      let firstCleanupError: Error | undefined
      try {
        await registry.activateSession(next)
      } catch (error) {
        firstCleanupError = error as Error
      }

      expect(firstCleanupError?.message).assertEqual(`playback cleanup failed for ${previous.accountScope}`)
      expect(registry.getActiveSession()?.accountScope).assertEqual(previous.accountScope)
      expect(successfulPlayback.stoppedScopes.length).assertEqual(1)
      expect(retryingPlayback.attempts).assertEqual(1)
      expect(successfulCache.clearedScopes.length).assertEqual(1)
      expect(retryingCache.attempts).assertEqual(1)

      await registry.activateSession(next)

      expect(registry.getActiveSession()?.accountScope).assertEqual(next.accountScope)
      expect(successfulPlayback.stoppedScopes.length).assertEqual(1)
      expect(retryingPlayback.attempts).assertEqual(2)
      expect(successfulCache.clearedScopes.length).assertEqual(1)
      expect(retryingCache.attempts).assertEqual(2)
    })
  })
}
