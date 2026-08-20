import { LiveTvViewModel } from "./LiveTvViewModel";
import { MediaSourceResolver } from "../../player/source/MediaSourceResolver";
import { PlayOptions } from "../../player/interaction/PlayOptions";
import { PlayerException } from "../../player/PlayerException";
import { AppPreference } from "../../data/AppPreference";
import { PlayerEngineResolver } from "../../player/PlayerEngineResolver";
import { PlayerEngineType } from '../../entity/PlayerEngineType'
import { currentMediaPlaybackProvider } from '../../media/MediaPlaybackAccess'
import { MediaPlaybackEngineHint, MediaPlaybackSource } from '../../media/playback/MediaPlaybackModels'
import { MediaPlaybackProvider } from '../../media/playback/MediaPlaybackProvider'

@ObservedV2
export class QueueManager {
  private viewModel: LiveTvViewModel
  private readonly playbackProvider: MediaPlaybackProvider
  private readonly mediaSourceResolver: MediaSourceResolver

  constructor(viewModel: LiveTvViewModel,
    playbackProvider: MediaPlaybackProvider = currentMediaPlaybackProvider()) {
    this.viewModel = viewModel
    this.playbackProvider = playbackProvider
    this.mediaSourceResolver = new MediaSourceResolver(playbackProvider)
  }

  @Trace currentQueue: string[] = []
  @Trace currentQueueIndex: number = 0
  @Trace currentMediaSourceOrNull?: MediaPlaybackSource

  private getCurrentEngineHint(): MediaPlaybackEngineHint {
    const engineType = PlayerEngineResolver.getRequestProfileEngineType(
      AppPreference.getInstance().getPlayerEngineType())
    if (engineType === PlayerEngineType.AV) {
      return MediaPlaybackEngineHint.PreferAvPlayer
    }
    if (engineType === PlayerEngineType.MPV) {
      return MediaPlaybackEngineHint.PreferMpvHardware
    }
    return MediaPlaybackEngineHint.Auto
  }

  initializePlaybackQueue(options: PlayOptions): PlayerException | null {
    this.currentQueue = options.ids
    this.currentQueueIndex = options.startIndex
    let itemId: string | null
    if (this.currentQueue.length > 0) {
      itemId = this.currentQueue[this.currentQueueIndex]
    } else {
      itemId = options.mediaSourceId
    }
    if (itemId == null) {
      return PlayerException.InvalidPlayOptions
    }
    void this.startPlayback(
      itemId!,
      options.mediaSourceId,
      null,
      options.startPositionTicks,
      options.audioStreamIndex,
      options.subtitleStreamIndex,
      true
    )
    return null
  }

  async startPlayback(
    itemId: string,
    mediaSourceId: string | null,
    maxStreamingBitrate: number | null | undefined,
    startTimeTicks: number | null,
    audioStreamIndex: number | null | undefined,
    subtitleStreamIndex: number | null,
    playWhenReady: boolean = true): Promise<PlayerException | null> {
    const ref = this.playbackProvider.legacyRefAdapter?.createRef(itemId)
    if (!ref) {
      return PlayerException.InvalidPlayOptions
    }
    try {
      const source = await this.mediaSourceResolver.resolveMediaSource(
        ref,
        mediaSourceId,
        this.getCurrentEngineHint(),
        maxStreamingBitrate,
        startTimeTicks,
        audioStreamIndex,
        subtitleStreamIndex,
        true
      )
      const streams = await this.playbackProvider.prepareStreams(source)
      this.currentMediaSourceOrNull = source
      this.viewModel.load(source, streams, playWhenReady)
      return null
    } catch (error) {
      console.error(`[LiveTV] resolve playback source failed: ${JSON.stringify(error)}`)
      return PlayerException.NetworkFailure
    }
  }

  hasPrevious(): boolean {
    return this.currentQueue.length > 0 && this.currentQueueIndex > 0
  }

  hasNext(): boolean {
    return this.currentQueue.length > 0 && this.currentQueueIndex < this.currentQueue.length - 1
  }

  async previous(): Promise<boolean> {
    if (!this.hasPrevious()) {
      return false
    }

    if (!this.currentMediaSourceOrNull) {
      return false
    }
    this.currentQueueIndex--;
    let currentMediaSource = this.currentMediaSourceOrNull

    await this.startPlayback(
      this.currentQueue[this.currentQueueIndex],
      null,
      currentMediaSource.maxStreamingBitrate ?? null,
      null,
      null,
      null
    )
    return true
  }

  async next(): Promise<boolean> {
    if (!this.hasNext()) {
      return false
    }
    if (!this.currentMediaSourceOrNull) {
      return false
    }
    let currentMediaSource = this.currentMediaSourceOrNull
    this.currentQueueIndex++;
    await this.startPlayback(
      this.currentQueue[this.currentQueueIndex],
      null,
      currentMediaSource.maxStreamingBitrate ?? null,
      null,
      null,
      null
    )
    return true
  }

  async changeEpisode(index: number) {
    if (!this.currentMediaSourceOrNull) {
      return false
    }
    let currentMediaSource = this.currentMediaSourceOrNull
    this.currentQueueIndex = index
    await this.startPlayback(
      this.currentQueue[this.currentQueueIndex],
      null,
      currentMediaSource.maxStreamingBitrate ?? null,
      null,
      null,
      null
    )
    return true
  }
}
