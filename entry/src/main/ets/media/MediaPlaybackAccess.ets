import { MediaProviderKind } from './MediaProviderKind'
import { MediaProviderRegistry } from './MediaProviderRegistry'
import { MediaProviderSession } from './MediaProviderSession'
import { JellyfinActiveSessionSource } from './jellyfin/JellyfinActiveSessionSource'
import { JellyfinPlaybackProvider } from './jellyfin/JellyfinPlaybackProvider'
import {
  MediaPlaybackProvider,
  MediaPlaybackProviderFactory,
  MediaPlaybackProviderResolver,
  MediaPlaybackProviderSessionSource
} from './playback/MediaPlaybackProvider'

class ActiveOrLegacyPlaybackSessionSource implements MediaPlaybackProviderSessionSource {
  private readonly registry: MediaProviderRegistry
  private readonly jellyfinFallback: JellyfinActiveSessionSource

  constructor(registry: MediaProviderRegistry = MediaProviderRegistry.instance(),
    jellyfinFallback: JellyfinActiveSessionSource = new JellyfinActiveSessionSource()) {
    this.registry = registry
    this.jellyfinFallback = jellyfinFallback
  }

  currentSession(): MediaProviderSession | undefined {
    return this.registry.getActiveSession() || this.jellyfinFallback.currentSession()
  }
}

class JellyfinPlaybackProviderFactory implements MediaPlaybackProviderFactory {
  readonly provider: MediaProviderKind = MediaProviderKind.Jellyfin

  create(session: MediaProviderSession): MediaPlaybackProvider {
    return new JellyfinPlaybackProvider(session)
  }
}

const sharedResolver = new MediaPlaybackProviderResolver(
  new ActiveOrLegacyPlaybackSessionSource(),
  [new JellyfinPlaybackProviderFactory()]
)

export function currentMediaPlaybackProvider(): MediaPlaybackProvider {
  return sharedResolver.resolve()
}

export function registerMediaPlaybackProviderFactory(factory: MediaPlaybackProviderFactory): void {
  sharedResolver.register(factory)
}
