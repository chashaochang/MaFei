import {
  MediaCatalogProviderFactory,
  MediaCatalogProviderResolver,
  MediaProviderSessionSource
} from './catalog/MediaCatalogProviderResolver'
import { MediaCatalogProvider } from './catalog/MediaCatalogProvider'
import { JellyfinActiveSessionSource } from './jellyfin/JellyfinActiveSessionSource'
import { JellyfinCatalogProvider } from './jellyfin/JellyfinCatalogProvider'
import { MediaProviderKind } from './MediaProviderKind'
import { MediaProviderRegistry } from './MediaProviderRegistry'
import { MediaProviderSession } from './MediaProviderSession'

class ActiveOrLegacySessionSource implements MediaProviderSessionSource {
  private registry: MediaProviderRegistry
  private jellyfinFallback: JellyfinActiveSessionSource

  constructor(registry: MediaProviderRegistry = MediaProviderRegistry.instance(),
    jellyfinFallback: JellyfinActiveSessionSource = new JellyfinActiveSessionSource()) {
    this.registry = registry
    this.jellyfinFallback = jellyfinFallback
  }

  currentSession(): MediaProviderSession | undefined {
    return this.registry.getActiveSession() || this.jellyfinFallback.currentSession()
  }
}

class JellyfinCatalogProviderFactory implements MediaCatalogProviderFactory {
  readonly provider: MediaProviderKind = MediaProviderKind.Jellyfin

  create(session: MediaProviderSession): MediaCatalogProvider {
    return new JellyfinCatalogProvider(session)
  }
}

const sharedResolver = new MediaCatalogProviderResolver(
  new ActiveOrLegacySessionSource(),
  [new JellyfinCatalogProviderFactory()]
)

export function currentMediaCatalogProvider(): MediaCatalogProvider {
  return sharedResolver.resolve()
}

export function registerMediaCatalogProviderFactory(factory: MediaCatalogProviderFactory): void {
  sharedResolver.register(factory)
}
