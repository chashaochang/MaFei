import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderRegistry } from '../main/ets/media/MediaProviderRegistry'
import { MediaConnectionRoute, MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import {
  FeiniuVideoSessionContext
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoSessionService'
import {
  FeiniuVideoSessionContextRegistry
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoSessionContextRegistry'
import { FeiniuVideoApiClient } from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiClient'
import {
  FeiniuVideoHostCandidate,
  FeiniuVideoHostResolution,
  MediaConnectionRoute as FeiniuConnectionRoute
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'

function session(providerAccountId: string, baseUrl: string = 'http://lan:5666',
  provider: MediaProviderKind = MediaProviderKind.FeiniuVideo): MediaProviderSession {
  return new MediaProviderSession({
    provider: provider,
    providerAccountId: providerAccountId,
    userId: 'user-1',
    originalAddress: 'FN123456',
    baseUrl: baseUrl,
    connectionRoute: MediaConnectionRoute.Local
  })
}

function context(owner: MediaProviderSession): FeiniuVideoSessionContext {
  const resolution = new FeiniuVideoHostResolution(
    new FeiniuVideoHostCandidate(owner.baseUrl, FeiniuConnectionRoute.Local),
    []
  )
  return new FeiniuVideoSessionContext(owner, new FeiniuVideoApiClient(resolution))
}

export default function feiniuVideoSessionContextRegistryTest() {
  describe('FeiniuVideoSessionContextRegistry', () => {
    it('returnsOnlyTheContextOwnedByTheRequestedAccountScope', 0, () => {
      const providerRegistry = new MediaProviderRegistry()
      const registry = new FeiniuVideoSessionContextRegistry(providerRegistry)
      const first = session('account-1')
      const second = session('account-2')
      const firstContext = context(first)

      registry.register(firstContext)

      expect(registry.get(first) === firstContext).assertTrue()
      expect(registry.get(second) === undefined).assertTrue()
      expect(registry.get(session('account-1', first.baseUrl, MediaProviderKind.Jellyfin)) === undefined)
        .assertTrue()
    })

    it('replacesARefreshedContextWithoutChangingTheAccountScope', 0, async () => {
      const providerRegistry = new MediaProviderRegistry()
      const registry = new FeiniuVideoSessionContextRegistry(providerRegistry)
      const original = session('account-1', 'http://lan:5666')
      const refreshed = session('account-1', 'https://public.example')
      const refreshedContext = context(refreshed)

      await providerRegistry.activateSession(original)
      registry.register(context(original))
      await providerRegistry.activateSession(refreshed)
      registry.register(refreshedContext)

      expect(registry.get(refreshed) === refreshedContext).assertTrue()
    })

    it('clearsThePreviousContextThroughProviderScopeRelease', 0, async () => {
      const providerRegistry = new MediaProviderRegistry()
      const registry = new FeiniuVideoSessionContextRegistry(providerRegistry)
      const previous = session('account-1')
      const next = session('account-2')

      await providerRegistry.activateSession(previous)
      registry.register(context(previous))
      await providerRegistry.activateSession(next)

      expect(registry.get(previous) === undefined).assertTrue()
    })

    it('removesOnlyContextsForTheRequestedProviderAccount', 0, () => {
      const registry = new FeiniuVideoSessionContextRegistry(new MediaProviderRegistry())
      const first = session('account-1')
      const second = session('account-2')
      registry.register(context(first))
      registry.register(context(second))

      registry.removeByProviderAccountId('account-1')

      expect(registry.get(first) === undefined).assertTrue()
      expect(registry.get(second) !== undefined).assertTrue()
      registry.clear()
      expect(registry.get(second) === undefined).assertTrue()
    })

    it('rejectsNonFeiniuContexts', 0, () => {
      const registry = new FeiniuVideoSessionContextRegistry(new MediaProviderRegistry())
      const jellyfin = session('account-1', 'https://jellyfin.example', MediaProviderKind.Jellyfin)

      expect(() => registry.register(context(jellyfin))).assertThrowError(
        'Feiniu session context registry only accepts Feiniu Video sessions')
    })
  })
}
