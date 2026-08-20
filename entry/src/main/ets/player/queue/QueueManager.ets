import { AppPreference } from '../../data/AppPreference'
import { PlayerEngineType } from '../../entity/PlayerEngineType'
import { PlayerPageViewModel } from '../../features/player/PlayerPageViewModel'
import { currentMediaPlaybackProvider } from '../../media/MediaPlaybackAccess'
import { MediaRef } from '../../media/MediaRef'
import { parseMediaRouteRefData, MediaRouteRefData } from '../../media/navigation/MediaRouteRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackItemKind,
  MediaPlaybackPreparedStreams,
  MediaPlaybackQueueItem,
  MediaPlaybackSource,
  MediaPlaybackStream,
  MediaPlaybackStreamType
} from '../../media/playback/MediaPlaybackModels'
import { MediaPlaybackProvider } from '../../media/playback/MediaPlaybackProvider'
import { Constants } from '../../utils/Constants'
import { PlayOptions } from '../interaction/PlayOptions'
import { PlayerEngineResolver } from '../PlayerEngineResolver'
import { PlayerException } from '../PlayerException'
import { MediaSourceResolver } from '../source/MediaSourceResolver'
import { PlaybackRequestResult } from './PlaybackRequestResult'

@ObservedV2
export class QueueManager {
  private readonly viewModel: PlayerPageViewModel
  private readonly playbackProvider: MediaPlaybackProvider
  private readonly mediaSourceResolver: MediaSourceResolver

  @Trace currentQueue: MediaRef[] = []
  @Trace currentQueueIndex: number = 0
  @Trace currentMediaSourceOrNull?: MediaPlaybackSource
  private playbackRequestEpoch: number = 0

  constructor(viewModel: PlayerPageViewModel,
    playbackProvider: MediaPlaybackProvider = currentMediaPlaybackProvider()) {
    this.viewModel = viewModel
    this.playbackProvider = playbackProvider
    this.mediaSourceResolver = new MediaSourceResolver(playbackProvider)
  }

  private getCurrentEngineHint(): MediaPlaybackEngineHint {
    const engineType = PlayerEngineResolver.getRequestProfileEngineType(
      AppPreference.getInstance().getPlayerEngineType()
    )
    if (engineType === PlayerEngineType.AV) {
      return MediaPlaybackEngineHint.PreferAvPlayer
    }
    if (engineType === PlayerEngineType.MPV) {
      return MediaPlaybackEngineHint.PreferMpvHardware
    }
    return MediaPlaybackEngineHint.Auto
  }

  private parseScopedRefs(values: MediaRouteRefData[]): MediaRef[] | undefined {
    const refs: MediaRef[] = []
    for (const value of values) {
      const ref = parseMediaRouteRefData(value)
      if (!ref || !ref.belongsTo(this.playbackProvider.session)) {
        return undefined
      }
      refs.push(ref)
    }
    return refs
  }

  private legacyRefs(ids: string[]): MediaRef[] | undefined {
    const adapter = this.playbackProvider.legacyRefAdapter
    if (!adapter) {
      return undefined
    }
    const refs: MediaRef[] = []
    for (const id of ids) {
      const normalized = id.trim()
      if (normalized.length === 0) {
        return undefined
      }
      refs.push(adapter.createRef(normalized))
    }
    return refs
  }

  private resolveQueue(options: PlayOptions): MediaRef[] | undefined {
    if (options.refs.length > 0) {
      return this.parseScopedRefs(options.refs)
    }
    return this.legacyRefs(options.ids)
  }

  private resolveSingleItem(options: PlayOptions): MediaRef | undefined {
    if (options.mediaSourceRef) {
      const ref = parseMediaRouteRefData(options.mediaSourceRef)
      return ref?.belongsTo(this.playbackProvider.session) ? ref : undefined
    }
    if (!options.mediaSourceId) {
      return undefined
    }
    return this.playbackProvider.legacyRefAdapter?.createRef(options.mediaSourceId)
  }

  async initializePlaybackQueue(options: PlayOptions): Promise<PlayerException | null> {
    const queue = this.resolveQueue(options)
    if (!queue) {
      return PlayerException.InvalidPlayOptions
    }
    const startIndex = options.startIndex
    if (queue.length > 0 && (startIndex < 0 || startIndex >= queue.length)) {
      return PlayerException.InvalidPlayOptions
    }
    const itemRef = queue.length > 0 ? queue[startIndex] : this.resolveSingleItem(options)
    if (!itemRef) {
      return PlayerException.InvalidPlayOptions
    }
    this.currentQueue = queue
    this.currentQueueIndex = queue.length > 0 ? startIndex : 0
    const result = await this.startPlayback(
      itemRef,
      options.mediaSourceId,
      null,
      options.startPositionTicks,
      options.audioStreamIndex,
      options.subtitleStreamIndex,
      true,
      this.currentQueueIndex
    )
    if (result === PlaybackRequestResult.Failure) {
      return PlayerException.NetworkFailure
    }
    return null
  }

  cancelPendingPlayback(): void {
    this.playbackRequestEpoch++
  }

  private isPlaybackRequestCurrent(requestEpoch: number): boolean {
    return requestEpoch === this.playbackRequestEpoch
  }

  private async closeSource(source?: MediaPlaybackSource): Promise<void> {
    if (!source) {
      return
    }
    try {
      await this.playbackProvider.lifecycle.closeTranscode(source)
    } catch (error) {
      console.error(`[QueueManager] Failed to close transcode: ${JSON.stringify(error)}`)
    }
  }

  async startPlayback(
    ref: MediaRef,
    mediaSourceId: string | null,
    maxStreamingBitrate: number | null | undefined,
    startTimeTicks: number | null,
    audioStreamIndex: number | null | undefined,
    subtitleStreamIndex: number | null,
    playWhenReady: boolean = true,
    targetQueueIndex?: number
  ): Promise<PlaybackRequestResult> {
    const requestEpoch = ++this.playbackRequestEpoch
    let mediaSource: MediaPlaybackSource | undefined
    const previousMediaSource = this.currentMediaSourceOrNull
    const previousQueueIndex = this.currentQueueIndex
    try {
      mediaSource = await this.mediaSourceResolver.resolveMediaSource(
        ref,
        mediaSourceId,
        this.getCurrentEngineHint(),
        maxStreamingBitrate,
        startTimeTicks,
        audioStreamIndex,
        subtitleStreamIndex,
        playWhenReady
      )
      if (!this.isPlaybackRequestCurrent(requestEpoch)) {
        await this.closeSource(mediaSource)
        return PlaybackRequestResult.Cancelled
      }

      const streams = await this.prepareStreams(mediaSource)
      if (!this.isPlaybackRequestCurrent(requestEpoch)) {
        await this.closeSource(mediaSource)
        return PlaybackRequestResult.Cancelled
      }

      if (targetQueueIndex !== undefined) {
        this.currentQueueIndex = targetQueueIndex
      }
      this.currentMediaSourceOrNull = mediaSource
      try {
        this.viewModel.load(mediaSource, streams, playWhenReady)
        void this.viewModel.getSubtitle(mediaSource)
      } catch (error) {
        this.currentMediaSourceOrNull = previousMediaSource
        this.currentQueueIndex = previousQueueIndex
        throw new Error(`Failed to apply playback source: ${JSON.stringify(error)}`)
      }
      await this.closeSource(previousMediaSource)
      return PlaybackRequestResult.Success
    } catch (error) {
      await this.closeSource(mediaSource)
      if (!this.isPlaybackRequestCurrent(requestEpoch)) {
        return PlaybackRequestResult.Cancelled
      }
      console.error(`[QueueManager] Failed to start playback: ${JSON.stringify(error)}`)
      return PlaybackRequestResult.Failure
    }
  }

  private isValidPlaybackPosition(position: number): boolean {
    return Number.isFinite(position) && position >= 0
  }

  private restorePlaybackAfterFailure(result: PlaybackRequestResult): void {
    if (result === PlaybackRequestResult.Failure && this.currentMediaSourceOrNull) {
      this.viewModel.resumePlaybackAfterFailedRequest()
    }
  }

  private async restartCurrentPlayback(
    currentMediaSource: MediaPlaybackSource,
    position: number,
    maxStreamingBitrate: number | null | undefined,
    audioStreamIndex: number | null | undefined,
    subtitleStreamIndex: number | null
  ): Promise<PlaybackRequestResult> {
    const result = await this.startPlayback(
      currentMediaSource.ref,
      currentMediaSource.id,
      maxStreamingBitrate,
      position * Constants.TICKS_PER_MILLISECOND,
      audioStreamIndex,
      subtitleStreamIndex,
      true,
      this.currentQueueIndex
    )
    this.restorePlaybackAfterFailure(result)
    return result
  }

  async changeBitrate(bitrate?: number): Promise<PlaybackRequestResult> {
    const currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return PlaybackRequestResult.Failure
    }
    if (currentMediaSource.maxStreamingBitrate == bitrate) {
      return PlaybackRequestResult.Success
    }
    const position = this.viewModel.getPositionAndPause()
    if (!this.isValidPlaybackPosition(position)) {
      return PlaybackRequestResult.Failure
    }
    return await this.restartCurrentPlayback(
      currentMediaSource,
      position,
      bitrate,
      currentMediaSource.selectedAudioStreamIndex,
      currentMediaSource.selectedSubtitleStreamIndex
    )
  }

  hasPrevious(): boolean {
    return this.currentQueue.length > 0 && this.currentQueueIndex > 0
  }

  hasNext(): boolean {
    return this.currentQueue.length > 0 && this.currentQueueIndex < this.currentQueue.length - 1
  }

  async previous(): Promise<PlaybackRequestResult> {
    if (!this.hasPrevious() || !this.currentMediaSourceOrNull) {
      return PlaybackRequestResult.Failure
    }
    const targetQueueIndex = this.currentQueueIndex - 1
    const currentMediaSource = this.currentMediaSourceOrNull
    const result = await this.startPlayback(
      this.currentQueue[targetQueueIndex],
      null,
      currentMediaSource.maxStreamingBitrate ?? null,
      null,
      null,
      null,
      true,
      targetQueueIndex
    )
    this.restorePlaybackAfterFailure(result)
    return result
  }

  async next(): Promise<PlaybackRequestResult> {
    if (!this.hasNext() || !this.currentMediaSourceOrNull) {
      return PlaybackRequestResult.Failure
    }
    const currentMediaSource = this.currentMediaSourceOrNull
    const targetQueueIndex = this.currentQueueIndex + 1
    const result = await this.startPlayback(
      this.currentQueue[targetQueueIndex],
      null,
      currentMediaSource.maxStreamingBitrate ?? null,
      null,
      null,
      null,
      true,
      targetQueueIndex
    )
    this.restorePlaybackAfterFailure(result)
    return result
  }

  async changeEpisode(index: number): Promise<PlaybackRequestResult> {
    if (!this.currentMediaSourceOrNull || index < 0 || index >= this.currentQueue.length) {
      return PlaybackRequestResult.Failure
    }
    const currentMediaSource = this.currentMediaSourceOrNull
    const result = await this.startPlayback(
      this.currentQueue[index],
      null,
      currentMediaSource.maxStreamingBitrate ?? null,
      null,
      null,
      null,
      true,
      index
    )
    this.restorePlaybackAfterFailure(result)
    return result
  }

  async prepareStreams(source: MediaPlaybackSource): Promise<MediaPlaybackPreparedStreams> {
    return await this.playbackProvider.prepareStreams(source)
  }

  createExternalSubtitleMediaSources(source: MediaPlaybackSource): string[] {
    return source.externalSubtitleStreams.map((stream): string => stream.deliveryUrl)
  }

  async createVideoMediaSource(source: MediaPlaybackSource): Promise<string> {
    return (await this.playbackProvider.prepareStreams(source)).videoUrl
  }

  async selectAudioStreamAndRestartPlayback(
    selectedMediaStream: MediaPlaybackStream
  ): Promise<PlaybackRequestResult> {
    if (selectedMediaStream.Type !== MediaPlaybackStreamType.Audio) {
      throw new Error('Selected stream must be an audio stream')
    }
    const currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return PlaybackRequestResult.Failure
    }
    const position = this.viewModel.getPositionAndPause()
    if (!this.isValidPlaybackPosition(position)) {
      return PlaybackRequestResult.Failure
    }
    return await this.restartCurrentPlayback(
      currentMediaSource,
      position,
      currentMediaSource.maxStreamingBitrate,
      selectedMediaStream.Index!,
      currentMediaSource.selectedSubtitleStreamIndex
    )
  }

  async selectSubtitleStreamAndRestartPlayback(
    selectedMediaStream?: MediaPlaybackStream
  ): Promise<PlaybackRequestResult> {
    if (selectedMediaStream && selectedMediaStream.Type !== MediaPlaybackStreamType.Subtitle) {
      throw new Error('Selected stream must be an subtitle stream')
    }
    const currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return PlaybackRequestResult.Failure
    }
    const position = this.viewModel.getPositionAndPause()
    if (!this.isValidPlaybackPosition(position)) {
      return PlaybackRequestResult.Failure
    }
    return await this.restartCurrentPlayback(
      currentMediaSource,
      position,
      currentMediaSource.maxStreamingBitrate,
      currentMediaSource.selectedAudioStreamIndex,
      selectedMediaStream?.Index ?? -1
    )
  }

  async getItems(): Promise<MediaPlaybackQueueItem[]> {
    return await this.playbackProvider.getQueueItems(this.currentQueue)
  }

  async expandEpisodeQueueIfNeeded(): Promise<boolean> {
    const currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource || this.currentQueue.length > 1 ||
      currentMediaSource.item.Type !== MediaPlaybackItemKind.Episode) {
      return false
    }
    try {
      const queue = await this.playbackProvider.expandEpisodeQueue(currentMediaSource)
      if (!queue || this.currentMediaSourceOrNull?.ref.identity !== currentMediaSource.ref.identity) {
        return false
      }
      this.currentQueue = queue.refs.slice()
      this.currentQueueIndex = queue.currentIndex
      return true
    } catch (error) {
      console.error(`[QueueManager] Failed to expand episode queue: ${JSON.stringify(error)}`)
      return false
    }
  }
}
