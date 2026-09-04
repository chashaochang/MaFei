import { MediaPlaybackEngineHint, MediaPlaybackSource } from '../../media/playback/MediaPlaybackModels'
import { MediaPlaybackProvider } from '../../media/playback/MediaPlaybackProvider'
import { MediaRef } from '../../media/MediaRef'

/** Thin shared-player adapter. Playback negotiation remains inside the active provider. */
export class MediaSourceResolver {
  private readonly provider: MediaPlaybackProvider

  constructor(provider: MediaPlaybackProvider) {
    this.provider = provider
  }

  resolveMediaSource(
    ref: MediaRef,
    mediaSourceId: string | null,
    engineHint: MediaPlaybackEngineHint,
    maxStreamingBitrate: number | null | undefined,
    startTimeTicks: number | null,
    audioStreamIndex: number | null | undefined,
    subtitleStreamIndex: number | null,
    autoOpenLiveStream: boolean = true
  ): Promise<MediaPlaybackSource> {
    if (!ref.belongsTo(this.provider.session)) {
      return Promise.reject(new Error('Playback reference belongs to another account'))
    }
    return this.provider.resolveSource({
      ref: ref,
      mediaSourceId: mediaSourceId ?? undefined,
      maxStreamingBitrate: maxStreamingBitrate ?? undefined,
      startTimeTicks: startTimeTicks ?? undefined,
      audioStreamIndex: audioStreamIndex ?? undefined,
      subtitleStreamIndex: subtitleStreamIndex ?? undefined,
      autoOpenLiveStream: autoOpenLiveStream,
      engineHint: engineHint
    })
  }
}
