import { PlayerPageViewModel } from "../../features/player/PlayerPageViewModel";
import { PlayOptions } from "../interaction/PlayOptions";
import { PlayerException } from "../PlayerException";
import { JellyfinMediaSource } from "../source/JellyfinMediaSource";
import { MediaSourceResolver } from "../source/MediaSourceResolver";
import { getItemsApi, getTvShowsApi, getVideosApi } from "sdk";
import {
  BaseItemKind,
  BaseItemDtoQueryResult,
  DeviceProfile,
  ItemFields,
  MediaProtocol,
  MediaStream,
  MediaStreamType,
  PlayMethod} from "sdk/src/main/ets/generated-client/models";
import { ApiClient } from "../../network/ApiClient";
import { AxiosResponse } from "@ohos/axios";
import { Constants } from "../../utils/Constants";
import { DeviceProfileBuilder } from "../deviceprofile/DeviceProfileBuilder";
import { AppPreference } from "../../data/AppPreference";
import { PlayerEngineResolver } from "../PlayerEngineResolver";
import { PlayerEngineType } from "../../entity/PlayerEngineType";
import { PlaybackRequestResult } from "./PlaybackRequestResult";

@ObservedV2
export class QueueManager {
  private viewModel: PlayerPageViewModel

  constructor(viewModel: PlayerPageViewModel) {
    this.viewModel = viewModel
  }

  @Trace currentQueue: string[] = []
  @Trace currentQueueIndex: number = 0
  private mediaSourceResolver: MediaSourceResolver = new MediaSourceResolver()
  @Trace currentMediaSourceOrNull?: JellyfinMediaSource
  private playbackRequestEpoch: number = 0

  private deviceProfileBuilder: DeviceProfileBuilder = new DeviceProfileBuilder()

  private getCurrentDeviceProfile(): DeviceProfile {
    return this.deviceProfileBuilder.getDeviceProfile(
      PlayerEngineResolver.getRequestProfileEngineType(AppPreference.getInstance().getPlayerEngineType())
    )
  }

  private shouldUseIsoStaticStreamFallback(source: JellyfinMediaSource): boolean {
    const configuredEngine = AppPreference.getInstance().getPlayerEngineType()
    const container = source.sourceInfo.Container?.trim().toLowerCase()
    return configuredEngine !== PlayerEngineType.AV &&
      source.sourceInfo.Protocol == MediaProtocol.File &&
      container === 'iso'
  }

  private async createIsoStaticStreamUrl(source: JellyfinMediaSource): Promise<string> {
    const sourceInfo = source.sourceInfo
    return await getVideosApi(ApiClient.Instance()).getVideoStreamByContainer({
      itemId: source.itemId,
      container: 'iso',
      _static: true,
      playSessionId: source.playSessionId,
      mediaSourceId: sourceInfo.Id,
      deviceId: ApiClient.Instance().deviceInfo.id,
    })
  }

  async initializePlaybackQueue(options: PlayOptions): Promise<PlayerException | null> {
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
    const result = await this.startPlayback(
      itemId!,
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

  async startPlayback(
    itemId: string,
    mediaSourceId: string | null,
    maxStreamingBitrate: number | null | undefined,
    startTimeTicks: number | null,
    audioStreamIndex: number | null | undefined,
    subtitleStreamIndex: number | null,
    playWhenReady: boolean = true,
    targetQueueIndex?: number): Promise<PlaybackRequestResult> {
    const requestEpoch = ++this.playbackRequestEpoch
    let jellyfinMediaSource: JellyfinMediaSource | undefined
    const previousMediaSource = this.currentMediaSourceOrNull
    const previousQueueIndex = this.currentQueueIndex
    try {
      const resolveTask = this.mediaSourceResolver.resolveMediaSource(
        itemId,
        mediaSourceId,
        this.getCurrentDeviceProfile(),
        maxStreamingBitrate,
        startTimeTicks,
        audioStreamIndex,
        subtitleStreamIndex,
        playWhenReady
      )
      if (!resolveTask) {
        return PlaybackRequestResult.Failure
      }
      jellyfinMediaSource = await resolveTask
      if (!this.isPlaybackRequestCurrent(requestEpoch)) {
        this.viewModel.stopTranscoding(jellyfinMediaSource)
        return PlaybackRequestResult.Cancelled
      }

      const streams = await this.prepareStreams(jellyfinMediaSource)
      if (!this.isPlaybackRequestCurrent(requestEpoch)) {
        this.viewModel.stopTranscoding(jellyfinMediaSource)
        return PlaybackRequestResult.Cancelled
      }

      if (targetQueueIndex !== undefined) {
        this.currentQueueIndex = targetQueueIndex
      }
      this.currentMediaSourceOrNull = jellyfinMediaSource
      try {
        this.viewModel.load(jellyfinMediaSource, streams, playWhenReady)
        this.viewModel.getSubtitle(jellyfinMediaSource)
      } catch (error) {
        this.currentMediaSourceOrNull = previousMediaSource
        this.currentQueueIndex = previousQueueIndex
        throw new Error(`Failed to apply playback source: ${JSON.stringify(error)}`)
      }
      if (previousMediaSource) {
        this.viewModel.stopTranscoding(previousMediaSource)
      }
      return PlaybackRequestResult.Success
    } catch (error) {
      if (jellyfinMediaSource) {
        this.viewModel.stopTranscoding(jellyfinMediaSource)
      }
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
    currentMediaSource: JellyfinMediaSource,
    position: number,
    maxStreamingBitrate: number | null | undefined,
    audioStreamIndex: number | null | undefined,
    subtitleStreamIndex: number | null): Promise<PlaybackRequestResult> {
    const result = await this.startPlayback(
      currentMediaSource.itemId,
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

  /**
   * Change the maximum bitrate to the specified value.
   */
  async changeBitrate(bitrate?: number): Promise<PlaybackRequestResult> {
    let currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return PlaybackRequestResult.Failure
    }

    // Bitrate didn't change, ignore
    if (currentMediaSource.maxStreamingBitrate == bitrate) {
      return PlaybackRequestResult.Success
    }

    let position = this.viewModel.getPositionAndPause()
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
    if (!this.hasPrevious()) {
      return PlaybackRequestResult.Failure
    }

    if (!this.currentMediaSourceOrNull) {
      return PlaybackRequestResult.Failure
    }
    const targetQueueIndex = this.currentQueueIndex - 1
    let currentMediaSource = this.currentMediaSourceOrNull

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
    if (!this.hasNext()) {
      return PlaybackRequestResult.Failure
    }
    if (!this.currentMediaSourceOrNull) {
      return PlaybackRequestResult.Failure
    }
    let currentMediaSource = this.currentMediaSourceOrNull
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
    if (!this.currentMediaSourceOrNull) {
      return PlaybackRequestResult.Failure
    }
    if (index < 0 || index >= this.currentQueue.length) {
      return PlaybackRequestResult.Failure
    }
    let currentMediaSource = this.currentMediaSourceOrNull
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


  async prepareStreams(source: JellyfinMediaSource): Promise<string[]> {
    let videoSource = await this.createVideoMediaSource(source)
    let subtitleSources = this.createExternalSubtitleMediaSources(source)
    return [videoSource, ...subtitleSources]
  }

  createExternalSubtitleMediaSources(source: JellyfinMediaSource): string[] {
    return source.externalSubtitleStreams.map(stream => {
      return stream.deliveryUrl
    })
  }

  async createVideoMediaSource(source: JellyfinMediaSource): Promise<string> {
    let sourceInfo = source.sourceInfo
    if (this.shouldUseIsoStaticStreamFallback(source)) {
      const isoStaticUrl = await this.createIsoStaticStreamUrl(source)
      console.info(
        `[PlaybackUrl] playMethod=IsoStaticFallback, itemId=${source.itemId}, mediaSourceId=${sourceInfo.Id ?? 'none'}, ` +
        `container=${sourceInfo.Container ?? 'unknown'}, isoType=${sourceInfo.IsoType ?? 'none'}, ` +
        `sourcePath=${sourceInfo.Path ?? 'none'}, finalUrl=${isoStaticUrl}`
      )
      return isoStaticUrl
    }
    switch (source.playMethod) {
      case PlayMethod.DirectPlay:
        if (sourceInfo.Protocol == MediaProtocol.File) {
          const staticUrl = await getVideosApi(ApiClient.Instance()).getVideoStream({
            itemId: source.itemId,
            _static: true,
            playSessionId: source.playSessionId,
            mediaSourceId: sourceInfo.Id,
            deviceId: ApiClient.Instance().deviceInfo.id,
          })
          console.info(
            `[PlaybackUrl] playMethod=DirectPlay/File, itemId=${source.itemId}, mediaSourceId=${sourceInfo.Id ?? 'none'}, ` +
            `container=${sourceInfo.Container ?? 'unknown'}, isoType=${sourceInfo.IsoType ?? 'none'}, ` +
            `sourcePath=${sourceInfo.Path ?? 'none'}, finalUrl=${staticUrl}`
          )
          return staticUrl
        } else if (sourceInfo.Protocol == MediaProtocol.Http) {
          console.info(
            `[PlaybackUrl] playMethod=DirectPlay/Http, itemId=${source.itemId}, mediaSourceId=${sourceInfo.Id ?? 'none'}, ` +
            `container=${sourceInfo.Container ?? 'unknown'}, isoType=${sourceInfo.IsoType ?? 'none'}, ` +
            `finalUrl=${sourceInfo.Path ?? 'none'}`
          )
          return sourceInfo.Path!
        } else {
          throw new Error(`Unsupported protocol ${sourceInfo.Protocol}`)
        }
      case PlayMethod.DirectStream:
        let container = sourceInfo.Container
        if (container) {
          const directStreamUrl = await getVideosApi(ApiClient.Instance()).getVideoStreamByContainer({
            itemId: source.itemId,
            container: container,
            playSessionId: source.playSessionId,
            mediaSourceId: source.id,
            deviceId: ApiClient.Instance().deviceInfo.id,
          })
          console.info(
            `[PlaybackUrl] playMethod=DirectStream, itemId=${source.itemId}, mediaSourceId=${source.id}, ` +
            `container=${container}, isoType=${sourceInfo.IsoType ?? 'none'}, ` +
            `sourcePath=${sourceInfo.Path ?? 'none'}, finalUrl=${directStreamUrl}`
          )
          return directStreamUrl
        } else {
          throw new Error('Missing direct stream container')
        }
      case PlayMethod.Transcode:
        let transcodingPath = sourceInfo.TranscodingUrl
        if (transcodingPath) {
          let protocol = sourceInfo.TranscodingSubProtocol
          if (protocol == 'hls') {
            let transcodingUrl = ApiClient.Instance().createUrl(transcodingPath)
            console.info(
              `[PlaybackUrl] playMethod=Transcode, itemId=${source.itemId}, mediaSourceId=${sourceInfo.Id ?? 'none'}, ` +
              `container=${sourceInfo.Container ?? 'unknown'}, isoType=${sourceInfo.IsoType ?? 'none'}, ` +
              `sourcePath=${sourceInfo.Path ?? 'none'}, transcodingPath=${transcodingPath}, finalUrl=${transcodingUrl}`
            )
            return transcodingUrl
          } else {
            throw new Error(`Unsupported transcode protocol "${protocol}"`)
          }
        } else {
          throw new Error('Missing transcode URL')
        }
    }
  }

  async selectAudioStreamAndRestartPlayback(selectedMediaStream: MediaStream): Promise<PlaybackRequestResult> {
    // 验证选中的是音频流
    if (selectedMediaStream.Type !== MediaStreamType.Audio) {
      throw new Error('Selected stream must be an audio stream');
    }

    let currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return PlaybackRequestResult.Failure
    }
    let position = this.viewModel.getPositionAndPause()
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

  async selectSubtitleStreamAndRestartPlayback(selectedMediaStream?: MediaStream): Promise<PlaybackRequestResult> {
    // 验证选中的是字幕
    if (selectedMediaStream?.Type !== MediaStreamType.Subtitle) {
      throw new Error('Selected stream must be an subtitle stream');
    }

    let currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return PlaybackRequestResult.Failure
    }
    let position = this.viewModel.getPositionAndPause()
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

  async getItems(): Promise<AxiosResponse<BaseItemDtoQueryResult>> {
    return getItemsApi(ApiClient.Instance()).getItems({
      ids: this.currentQueue
    })
  }

  async expandEpisodeQueueIfNeeded(): Promise<boolean> {
    const currentMediaSource = this.currentMediaSourceOrNull
    if (!currentMediaSource) {
      return false
    }
    const currentItem = currentMediaSource.item
    if (this.currentQueue.length > 1 || currentItem.Type !== BaseItemKind.Episode || !currentItem.SeriesId) {
      return false
    }

    return getTvShowsApi(ApiClient.Instance()).getEpisodes({
      userId: ApiClient.Instance().userId,
      fields: [ItemFields.Chapters, ItemFields.Trickplay],
      isMissing: false,
      seriesId: currentItem.SeriesId,
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      const episodeItems = res.data?.Items
      if (!episodeItems || episodeItems.length <= 1) {
        return false
      }
      if (this.currentMediaSourceOrNull?.itemId !== currentMediaSource.itemId) {
        return false
      }
      const ids = episodeItems.map<string>((item) => item.Id!).filter((id) => id.length > 0)
      const currentIndex = ids.findIndex((id) => id === currentMediaSource.itemId)
      if (ids.length <= 1 || currentIndex < 0) {
        return false
      }
      this.currentQueue = ids
      this.currentQueueIndex = currentIndex
      return true
    }).catch(() => false)
  }

  async getItem(id: string): Promise<AxiosResponse<BaseItemDtoQueryResult>> {
    return getItemsApi(ApiClient.Instance()).getItems({
      ids: Array.of(id),
      includeItemTypes: ['Series','Movie'],
      fields: ['Overview', 'Genres'],
    })
  }
}
