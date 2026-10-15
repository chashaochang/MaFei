import { describe, expect, it } from '@ohos/hypium'
import { MediaCatalogProvider } from '../main/ets/media/catalog/MediaCatalogProvider'
import {
  MediaCatalogProviderFactory,
  MediaCatalogProviderResolver,
  MediaProviderSessionSource
} from '../main/ets/media/catalog/MediaCatalogProviderResolver'
import { createMediaImageKnifeOption } from '../main/ets/media/catalog/MediaImageKnifeAdapter'
import { MediaImageKind, MediaImageRef } from '../main/ets/media/catalog/MediaCatalogModels'
import {
  JellyfinActiveIdentity,
  JellyfinActiveIdentitySource,
  JellyfinActiveSessionSource
} from '../main/ets/media/jellyfin/JellyfinActiveSessionSource'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaConnectionRoute, MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import {
  FeiniuVideoCatalogProviderFactory
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoCatalogRegistration'
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
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import {
  MediaRouteRefData,
  parseMediaRouteRefData,
  toMediaRouteRefData
} from '../main/ets/media/navigation/MediaRouteRef'

class FakeSessionSource implements MediaProviderSessionSource {
  session?: MediaProviderSession

  currentSession(): MediaProviderSession | undefined {
    return this.session
  }
}

class FakeCatalogFactory implements MediaCatalogProviderFactory {
  readonly provider: MediaProviderKind
  createdSession?: MediaProviderSession

  constructor(provider: MediaProviderKind) {
    this.provider = provider
  }

  create(session: MediaProviderSession): MediaCatalogProvider {
    this.createdSession = session
    throw new Error(`factory-selected:${this.provider}`)
  }
}

class FakeJellyfinIdentitySource implements JellyfinActiveIdentitySource {
  identity?: JellyfinActiveIdentity

  currentIdentity(): JellyfinActiveIdentity | undefined {
    return this.identity
  }
}

function session(provider: MediaProviderKind = MediaProviderKind.Jellyfin): MediaProviderSession {
  return new MediaProviderSession({
    provider: provider,
    providerAccountId: 'account-1',
    userId: 'user-1',
    originalAddress: 'https://media.example',
    baseUrl: 'https://media.example',
    connectionRoute: MediaConnectionRoute.Direct
  })
}

function feiniuContext(owner: MediaProviderSession): FeiniuVideoSessionContext {
  const resolution = new FeiniuVideoHostResolution(
    new FeiniuVideoHostCandidate(owner.baseUrl, FeiniuConnectionRoute.Direct),
    []
  )
  return new FeiniuVideoSessionContext(owner, new FeiniuVideoApiClient(resolution))
}

export default function mediaCatalogFoundationTest() {
  describe('MediaCatalogFoundation', () => {
    it('resolvesTheFactoryForTheCurrentProviderSession', 0, () => {
      const source = new FakeSessionSource()
      source.session = session(MediaProviderKind.FeiniuVideo)
      const jellyfinFactory = new FakeCatalogFactory(MediaProviderKind.Jellyfin)
      const feiniuFactory = new FakeCatalogFactory(MediaProviderKind.FeiniuVideo)
      const resolver = new MediaCatalogProviderResolver(source, [jellyfinFactory, feiniuFactory])

      expect(() => resolver.resolve()).assertThrowError('factory-selected:feiniu_video')
      expect(feiniuFactory.createdSession?.accountScope).assertEqual(source.session.accountScope)
      expect(jellyfinFactory.createdSession === undefined).assertTrue()
    })

    it('rejectsMissingSessionsAndUnregisteredProviders', 0, () => {
      const source = new FakeSessionSource()
      const resolver = new MediaCatalogProviderResolver(source)
      expect(() => resolver.resolve()).assertThrowError('No active media provider session')

      source.session = session(MediaProviderKind.FeiniuVideo)
      expect(() => resolver.resolve()).assertThrowError(
        'No catalog provider registered for feiniu_video')
    })

    it('createsFeiniuCatalogOnlyFromTheMatchingAuthenticatedContext', 0, () => {
      const contexts = new FeiniuVideoSessionContextRegistry()
      const owner = session(MediaProviderKind.FeiniuVideo)
      const other = new MediaProviderSession({
        provider: MediaProviderKind.FeiniuVideo,
        providerAccountId: 'account-2',
        userId: 'user-2',
        originalAddress: 'FN654321',
        baseUrl: 'https://other.example'
      })
      const factory = new FeiniuVideoCatalogProviderFactory(contexts)

      expect(() => factory.create(owner)).assertThrowError(
        `No authenticated Feiniu Video context for ${owner.accountScope}`)
      contexts.register(feiniuContext(owner))

      expect(factory.create(owner).session.accountScope).assertEqual(owner.accountScope)
      expect(() => factory.create(other)).assertThrowError(
        `No authenticated Feiniu Video context for ${other.accountScope}`)
      contexts.clear()
    })

    it('createsALegacyJellyfinSessionWithoutCredentials', 0, () => {
      const identitySource = new FakeJellyfinIdentitySource()
      identitySource.identity = {
        serverId: 41,
        userId: ' user-1 ',
        baseUrl: 'https://jellyfin.example/'
      }
      const activeSession = new JellyfinActiveSessionSource(identitySource).currentSession()

      expect(activeSession?.provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(activeSession?.providerAccountId).assertEqual('v1|41|6:user-1')
      expect(activeSession?.baseUrl).assertEqual('https://jellyfin.example')
      expect(JSON.stringify(activeSession).includes('token')).assertFalse()
    })

    it('mapsAuthenticatedImagesToImageKnifeHeadersAndScopedSignatures', 0, () => {
      const owner = new MediaRef(
        MediaProviderKind.FeiniuVideo,
        session(MediaProviderKind.FeiniuVideo).accountScope,
        'movie-1',
        MediaRefKind.Movie
      )
      const image = new MediaImageRef({
        owner: owner,
        kind: MediaImageKind.Primary,
        url: 'https://media.example/poster.jpg',
        variant: 'poster-600',
        revision: 'r1',
        headers: [
          { name: 'Cookie', value: 'session=value' },
          { name: 'Authorization', value: 'Bearer test' }
        ]
      })

      const option = createMediaImageKnifeOption(image)

      expect(option.loadSrc as string).assertEqual(image.url)
      expect(option.signature).assertEqual(image.cacheKey)
      expect(option.headerOption?.length).assertEqual(2)
      expect(option.headerOption?.[0].key).assertEqual('Cookie')
      expect(String(option.headerOption?.[0].value)).assertEqual('session=value')
    })

    it('roundTripsScopedMediaRouteDataAndRejectsMalformedValues', 0, () => {
      const ref = new MediaRef(
        MediaProviderKind.Jellyfin,
        session().accountScope,
        'movie-1',
        MediaRefKind.Movie
      )
      const restored = parseMediaRouteRefData(toMediaRouteRefData(ref))

      expect(restored?.equals(ref)).assertTrue()
      expect(restored?.kind).assertEqual(MediaRefKind.Movie)
      expect(parseMediaRouteRefData({
        provider: 'unknown',
        accountScope: ref.accountScope,
        id: ref.id,
        kind: ref.kind
      } as MediaRouteRefData) === undefined).assertTrue()
    })
  })
}
