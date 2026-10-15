import { MediaProviderKind } from '../MediaProviderKind'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef } from '../MediaRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackPreparedStreams,
  MediaPlaybackQueue,
  MediaPlaybackQueueItem,
  MediaPlaybackSource,
  MediaPlaybackStream
} from './MediaPlaybackModels'
import { MediaPlaybackLifecycle } from './MediaPlaybackLifecycle'

export interface MediaPlaybackResolveRequest {
  readonly ref: MediaRef
  readonly mediaSourceId?: string
  readonly maxStreamingBitrate?: number
  readonly startTimeTicks?: number
  readonly audioStreamIndex?: number
  readonly subtitleStreamIndex?: number
  readonly autoOpenLiveStream: boolean
  readonly engineHint: MediaPlaybackEngineHint
}

export interface MediaPlaybackProvider {
  /** Immutable account snapshot used for every request from this provider instance. */
  readonly session: MediaProviderSession
  /** Provider-owned progress, completion, stop, and transcode cleanup callbacks. */
  readonly lifecycle: MediaPlaybackLifecycle
  /** Temporary adapter for legacy Jellyfin callers that still supply an unscoped item id. */
  readonly legacyRefAdapter?: MediaPlaybackLegacyRefAdapter

  resolveSource(request: MediaPlaybackResolveRequest): Promise<MediaPlaybackSource>

  prepareStreams(source: MediaPlaybackSource): Promise<MediaPlaybackPreparedStreams>

  loadSubtitle(source: MediaPlaybackSource, stream: MediaPlaybackStream): Promise<string>

  getQueueItems(refs: MediaRef[]): Promise<MediaPlaybackQueueItem[]>

  expandEpisodeQueue(source: MediaPlaybackSource): Promise<MediaPlaybackQueue | undefined>
}

export interface MediaPlaybackLegacyRefAdapter {
  createRef(itemId: string): MediaRef
}

export interface MediaPlaybackProviderSessionSource {
  currentSession(): MediaProviderSession | undefined
}

export interface MediaPlaybackProviderFactory {
  readonly provider: MediaProviderKind
  create(session: MediaProviderSession): MediaPlaybackProvider
}

export class MediaPlaybackProviderResolver {
  private sessionSource: MediaPlaybackProviderSessionSource
  private factories: MediaPlaybackProviderFactory[] = []

  constructor(sessionSource: MediaPlaybackProviderSessionSource,
    factories: MediaPlaybackProviderFactory[] = []) {
    this.sessionSource = sessionSource
    for (const factory of factories) {
      this.register(factory)
    }
  }

  register(factory: MediaPlaybackProviderFactory): void {
    const index = this.factories.findIndex((item: MediaPlaybackProviderFactory): boolean =>
      item.provider === factory.provider)
    if (index >= 0) {
      this.factories[index] = factory
      return
    }
    this.factories.push(factory)
  }

  resolve(): MediaPlaybackProvider {
    const session = this.sessionSource.currentSession()
    if (!session) {
      throw new Error('No active media provider session')
    }
    const factory = this.factories.find((item: MediaPlaybackProviderFactory): boolean =>
      item.provider === session.provider)
    if (!factory) {
      throw new Error(`No playback provider registered for ${session.provider}`)
    }
    return factory.create(session)
  }
}
