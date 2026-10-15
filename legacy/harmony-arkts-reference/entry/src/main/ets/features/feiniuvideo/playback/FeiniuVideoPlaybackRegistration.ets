import {
  registerMediaPlaybackProviderFactory
} from '../../../media/MediaPlaybackAccess'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import {
  MediaPlaybackProvider,
  MediaPlaybackProviderFactory
} from '../../../media/playback/MediaPlaybackProvider'
import {
  FeiniuVideoSessionContextRegistry
} from '../account/FeiniuVideoSessionContextRegistry'
import { FeiniuVideoPlaybackProvider } from './FeiniuVideoPlaybackProvider'

export class FeiniuVideoPlaybackProviderFactory implements MediaPlaybackProviderFactory {
  readonly provider: MediaProviderKind = MediaProviderKind.FeiniuVideo
  private readonly contexts: FeiniuVideoSessionContextRegistry

  constructor(contexts: FeiniuVideoSessionContextRegistry =
    FeiniuVideoSessionContextRegistry.instance()) {
    this.contexts = contexts
  }

  create(session: MediaProviderSession): MediaPlaybackProvider {
    const context = this.contexts.get(session)
    if (!context) {
      throw new Error(`No authenticated Feiniu Video context for ${session.accountScope}`)
    }
    return FeiniuVideoPlaybackProvider.fromClient(session, context.apiClient, context)
  }
}

export function registerFeiniuVideoPlaybackProvider(
  contexts: FeiniuVideoSessionContextRegistry = FeiniuVideoSessionContextRegistry.instance()
): void {
  registerMediaPlaybackProviderFactory(new FeiniuVideoPlaybackProviderFactory(contexts))
}
