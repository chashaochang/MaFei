import { buffer, uri } from '@kit.ArkTS'
import { AxiosResponse } from '@ohos/axios'
import {
  getHlsSegmentApi,
  getItemsApi,
  getMediaInfoApi,
  getPlaystateApi,
  getSubtitleApi,
  getTvShowsApi,
  getUserLibraryApi,
  getVideosApi,
  PlaybackInfoResponse
} from 'sdk'
import { Api } from 'sdk/src/main/ets/api'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  DeviceProfile,
  ItemFields,
  MediaSourceInfo,
  MediaStream,
  MediaStreamType,
  PlaybackProgressInfo,
  PlaybackStartInfo,
  PlaybackStopInfo,
  RepeatMode
} from 'sdk/src/main/ets/generated-client/models'
import { ClientInfo } from 'sdk/src/main/ets/models/client-info'
import { DeviceInfo } from 'sdk/src/main/ets/models/device-info'
import { ApiClient } from '../../network/ApiClient'
import { PlayerEngineType } from '../../entity/PlayerEngineType'
import { DeviceProfileBuilder } from '../../player/deviceprofile/DeviceProfileBuilder'
import { Constants } from '../../utils/Constants'
import { MediaProviderKind } from '../MediaProviderKind'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef, MediaRefKind } from '../MediaRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackItemKind,
  MediaPlaybackPlayMethod,
  MediaPlaybackPreparedStreams,
  MediaPlaybackProtocol,
  MediaPlaybackQueue,
  MediaPlaybackQueueItem,
  MediaPlaybackSource,
  MediaPlaybackStream
} from '../playback/MediaPlaybackModels'
import {
  MediaPlaybackProvider,
  MediaPlaybackLegacyRefAdapter,
  MediaPlaybackResolveRequest
} from '../playback/MediaPlaybackProvider'
import {
  MediaPlaybackLifecycle,
  MediaPlaybackProgress,
  MediaPlaybackStop
} from '../playback/MediaPlaybackLifecycle'
import { JellyfinPlaybackMapper } from './JellyfinPlaybackMapper'

function normalizeBaseUrl(value: string): string {
  let normalized = value.trim()
  while (normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized
}

export class JellyfinPlaybackRequestSession {
  readonly owner: MediaProviderSession
  readonly accountScope: string
  readonly baseUrl: string
  readonly userId: string
  readonly deviceId: string
  readonly authorizationHeader: string
  readonly api: Api

  constructor(owner: MediaProviderSession, api: Api, authorizationHeader: string = '') {
    this.owner = owner
    this.accountScope = owner.accountScope
    this.baseUrl = normalizeBaseUrl(owner.baseUrl)
    this.userId = owner.userId
    this.deviceId = api.deviceInfo.id
    this.authorizationHeader = authorizationHeader
    this.api = api
  }
}

export interface JellyfinPlaybackInfoRequest {
  readonly itemId: string
  readonly mediaSourceId?: string
  readonly deviceProfile: DeviceProfile
  readonly maxStreamingBitrate?: number
  readonly startTimeTicks?: number
  readonly audioStreamIndex?: number
  readonly subtitleStreamIndex?: number
  readonly autoOpenLiveStream: boolean
  readonly liveChannel: boolean
}

export interface JellyfinPlaybackService {
  createSession(owner: MediaProviderSession): JellyfinPlaybackRequestSession
  getItem(session: JellyfinPlaybackRequestSession, itemId: string): Promise<BaseItemDto>
  getPlaybackInfo(session: JellyfinPlaybackRequestSession,
    request: JellyfinPlaybackInfoRequest): Promise<PlaybackInfoResponse>
  getStaticVideoUrl(session: JellyfinPlaybackRequestSession, source: MediaPlaybackSource): Promise<string>
  getContainerVideoUrl(session: JellyfinPlaybackRequestSession, source: MediaPlaybackSource,
    container: string): Promise<string>
  getItems(session: JellyfinPlaybackRequestSession, ids: string[]): Promise<BaseItemDtoQueryResult>
  getEpisodes(session: JellyfinPlaybackRequestSession, seriesId: string): Promise<BaseItemDtoQueryResult>
  getSubtitle(session: JellyfinPlaybackRequestSession, source: MediaPlaybackSource,
    stream: MediaPlaybackStream): Promise<string>
  reportStart(session: JellyfinPlaybackRequestSession, info: PlaybackStartInfo): Promise<void>
  reportProgress(session: JellyfinPlaybackRequestSession, info: PlaybackProgressInfo): Promise<void>
  reportStop(session: JellyfinPlaybackRequestSession, info: PlaybackStopInfo): Promise<void>
  markPlayed(session: JellyfinPlaybackRequestSession, itemId: string): Promise<void>
  closeTranscode(session: JellyfinPlaybackRequestSession, playSessionId: string): Promise<void>
}

export class JellyfinPlaybackApiService implements JellyfinPlaybackService {
  createSession(owner: MediaProviderSession): JellyfinPlaybackRequestSession {
    if (owner.provider !== MediaProviderKind.Jellyfin) {
      throw new Error('Jellyfin playback cannot serve a different provider')
    }
    if (!ApiClient.isInitialized()) {
      throw new Error('Jellyfin requires an initialized account')
    }
    const activeApi = ApiClient.Instance()
    const activeBaseUrl = normalizeBaseUrl(String(activeApi.basePath || ''))
    const activeUserId = String(activeApi.userId || '')
    if (activeBaseUrl !== normalizeBaseUrl(owner.baseUrl) || activeUserId !== owner.userId) {
      throw new Error('Jellyfin account changed before the playback session was created')
    }
    const clientInfo: ClientInfo = {
      name: String(activeApi.clientInfo.name || ''),
      version: String(activeApi.clientInfo.version || '')
    }
    const deviceInfo: DeviceInfo = {
      id: String(activeApi.deviceInfo.id || ''),
      name: String(activeApi.deviceInfo.name || '')
    }
    const snapshotApi = new Api(activeBaseUrl, clientInfo, deviceInfo,
      String(activeApi.accessToken || ''), activeApi.axiosInstance)
    snapshotApi.userId = owner.userId
    return new JellyfinPlaybackRequestSession(owner, snapshotApi, snapshotApi.authorizationHeader)
  }

  async getItem(session: JellyfinPlaybackRequestSession, itemId: string): Promise<BaseItemDto> {
    const response: AxiosResponse<BaseItemDto> = await getUserLibraryApi(session.api).getItem({ itemId: itemId })
    return response.data
  }

  async getPlaybackInfo(session: JellyfinPlaybackRequestSession,
    request: JellyfinPlaybackInfoRequest): Promise<PlaybackInfoResponse> {
    const response: AxiosResponse<PlaybackInfoResponse> = await getMediaInfoApi(session.api).getPostedPlaybackInfo({
      itemId: request.itemId,
      playbackInfoDto: {
        UserId: session.userId,
        MediaSourceId: request.liveChannel ? undefined : (request.mediaSourceId || request.itemId),
        DeviceProfile: request.deviceProfile,
        MaxStreamingBitrate: request.maxStreamingBitrate,
        StartTimeTicks: request.startTimeTicks,
        AudioStreamIndex: request.audioStreamIndex,
        SubtitleStreamIndex: request.subtitleStreamIndex,
        AutoOpenLiveStream: request.autoOpenLiveStream
      }
    })
    return response.data
  }

  async getStaticVideoUrl(session: JellyfinPlaybackRequestSession,
    source: MediaPlaybackSource): Promise<string> {
    return await getVideosApi(session.api).getVideoStream({
      itemId: source.itemId,
      _static: true,
      playSessionId: source.playSessionId,
      mediaSourceId: source.sourceInfo.Id,
      deviceId: session.deviceId
    })
  }

  async getContainerVideoUrl(session: JellyfinPlaybackRequestSession, source: MediaPlaybackSource,
    container: string): Promise<string> {
    return await getVideosApi(session.api).getVideoStreamByContainer({
      itemId: source.itemId,
      container: container,
      _static: source.isIsoStaticPlaybackFallback() ? true : undefined,
      playSessionId: source.playSessionId,
      mediaSourceId: source.sourceInfo.Id,
      deviceId: session.deviceId
    })
  }

  async getItems(session: JellyfinPlaybackRequestSession, ids: string[]): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(session.api).getItems({ ids: ids })
    return response.data
  }

  async getEpisodes(session: JellyfinPlaybackRequestSession, seriesId: string): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getTvShowsApi(session.api).getEpisodes({
      userId: session.userId,
      fields: [ItemFields.Chapters, ItemFields.Trickplay],
      isMissing: false,
      seriesId: seriesId
    })
    return response.data
  }

  async getSubtitle(session: JellyfinPlaybackRequestSession, source: MediaPlaybackSource,
    stream: MediaPlaybackStream): Promise<string> {
    const response: AxiosResponse<string, ArrayBuffer> = await getSubtitleApi(session.api).getSubtitle({
      routeItemId: source.itemId,
      routeMediaSourceId: source.id,
      routeIndex: stream.Index,
      routeFormat: stream.Codec
    })
    return buffer.from(response.data).toString()
  }

  async reportStart(session: JellyfinPlaybackRequestSession, info: PlaybackStartInfo): Promise<void> {
    await getPlaystateApi(session.api).reportPlaybackStart({ playbackStartInfo: info })
  }

  async reportProgress(session: JellyfinPlaybackRequestSession, info: PlaybackProgressInfo): Promise<void> {
    await getPlaystateApi(session.api).reportPlaybackProgress({ playbackProgressInfo: info })
  }

  async reportStop(session: JellyfinPlaybackRequestSession, info: PlaybackStopInfo): Promise<void> {
    await getPlaystateApi(session.api).reportPlaybackStopped({ playbackStopInfo: info })
  }

  async markPlayed(session: JellyfinPlaybackRequestSession, itemId: string): Promise<void> {
    await getPlaystateApi(session.api).markPlayedItem({ itemId: itemId })
  }

  async closeTranscode(session: JellyfinPlaybackRequestSession, playSessionId: string): Promise<void> {
    await getHlsSegmentApi(session.api).stopEncodingProcess({
      deviceId: session.deviceId,
      playSessionId: playSessionId
    })
  }
}

export class JellyfinPlaybackProvider implements MediaPlaybackProvider, MediaPlaybackLifecycle {
  readonly session: MediaProviderSession
  readonly lifecycle: MediaPlaybackLifecycle
  readonly legacyRefAdapter: MediaPlaybackLegacyRefAdapter
  private readonly service: JellyfinPlaybackService
  private readonly requestSession: JellyfinPlaybackRequestSession
  private readonly mapper: JellyfinPlaybackMapper
  private readonly deviceProfileBuilder: DeviceProfileBuilder = new DeviceProfileBuilder()

  constructor(session: MediaProviderSession,
    service: JellyfinPlaybackService = new JellyfinPlaybackApiService()) {
    if (session.provider !== MediaProviderKind.Jellyfin) {
      throw new Error('Jellyfin playback cannot serve a different provider')
    }
    this.session = session
    this.service = service
    this.requestSession = service.createSession(session)
    if (this.requestSession.accountScope !== session.accountScope) {
      throw new Error('Jellyfin playback session does not match the provider account')
    }
    this.mapper = new JellyfinPlaybackMapper(session, this.requestSession.authorizationHeader)
    this.lifecycle = this
    this.legacyRefAdapter = new JellyfinLegacyPlaybackRefAdapter(session)
  }

  async resolveSource(request: MediaPlaybackResolveRequest): Promise<MediaPlaybackSource> {
    this.requireOwnedRef(request.ref, 'resolveSource')
    const itemId = request.ref.id
    const item = await this.service.getItem(this.requestSession, itemId)
    const effectiveStartTicks = request.startTimeTicks ?? item.UserData?.PlaybackPositionTicks ?? 0
    const liveChannel = item.Type === BaseItemKind.TvChannel || item.Type === BaseItemKind.LiveTvChannel
    const deviceProfile = this.deviceProfileBuilder.getDeviceProfile(this.profileEngine(request.engineHint))
    let selectedAudioStreamIndex = request.audioStreamIndex
    let playbackInfo = await this.service.getPlaybackInfo(this.requestSession, {
      itemId: itemId,
      mediaSourceId: request.mediaSourceId,
      deviceProfile: deviceProfile,
      maxStreamingBitrate: request.maxStreamingBitrate,
      startTimeTicks: effectiveStartTicks,
      audioStreamIndex: selectedAudioStreamIndex,
      subtitleStreamIndex: request.subtitleStreamIndex,
      autoOpenLiveStream: request.autoOpenLiveStream,
      liveChannel: liveChannel
    })
    let sourceInfo = this.findResolvedMediaSource(playbackInfo, request.mediaSourceId, itemId, liveChannel)
    const fallbackAudioStreamIndex = this.findDirectPlayAudioFallback(sourceInfo, selectedAudioStreamIndex)
    if (fallbackAudioStreamIndex !== undefined) {
      const fallbackPlaybackInfo = await this.service.getPlaybackInfo(this.requestSession, {
        itemId: itemId,
        mediaSourceId: request.mediaSourceId,
        deviceProfile: deviceProfile,
        maxStreamingBitrate: request.maxStreamingBitrate,
        startTimeTicks: effectiveStartTicks,
        audioStreamIndex: fallbackAudioStreamIndex,
        subtitleStreamIndex: request.subtitleStreamIndex,
        autoOpenLiveStream: request.autoOpenLiveStream,
        liveChannel: liveChannel
      })
      const fallbackSourceInfo = this.findResolvedMediaSource(fallbackPlaybackInfo, request.mediaSourceId,
        itemId, liveChannel)
      if (fallbackSourceInfo && this.isDirectPlayback(fallbackSourceInfo)) {
        console.info(`[JellyfinPlayback] Falling back from unsupported default audio to direct-play ` +
          `audio stream ${fallbackAudioStreamIndex}`)
        playbackInfo = fallbackPlaybackInfo
        sourceInfo = fallbackSourceInfo
        selectedAudioStreamIndex = fallbackAudioStreamIndex
      }
    }
    const playSessionId = playbackInfo.PlaySessionId || ''
    if (playSessionId.length === 0) {
      throw new Error('UnsupportedContent')
    }
    if (!sourceInfo) {
      const requestedMediaSourceId = request.mediaSourceId || itemId
      console.error(`[JellyfinPlayback] Requested media source not found: itemId=${itemId}, ` +
        `mediaSourceId=${requestedMediaSourceId}`)
      throw new Error('NetworkFailure')
    }
    this.logResolvedMediaSource(itemId, sourceInfo, playSessionId)
    return this.mapper.source(request.ref, item, sourceInfo, playSessionId,
      sourceInfo.LiveStreamId, request.maxStreamingBitrate, effectiveStartTicks,
      selectedAudioStreamIndex, request.subtitleStreamIndex, request.engineHint)
  }

  async prepareStreams(source: MediaPlaybackSource): Promise<MediaPlaybackPreparedStreams> {
    this.requireOwned(source, 'prepareStreams')
    let videoUrl: string
    if (source.isIsoStaticPlaybackFallback() && source.engineHint !== MediaPlaybackEngineHint.PreferAvPlayer) {
      videoUrl = await this.service.getContainerVideoUrl(this.requestSession, source, 'iso')
    } else {
      switch (source.playMethod) {
        case MediaPlaybackPlayMethod.DirectPlay:
          if (source.sourceInfo.Protocol === MediaPlaybackProtocol.File) {
            videoUrl = await this.service.getStaticVideoUrl(this.requestSession, source)
          } else if (source.sourceInfo.Protocol === MediaPlaybackProtocol.Http && source.sourceInfo.Path) {
            videoUrl = source.sourceInfo.Path
          } else {
            throw new Error(`Unsupported protocol ${source.sourceInfo.Protocol}`)
          }
          break
        case MediaPlaybackPlayMethod.DirectStream:
          if (!source.sourceInfo.Container) {
            throw new Error('Missing direct stream container')
          }
          videoUrl = await this.service.getContainerVideoUrl(this.requestSession, source,
            source.sourceInfo.Container)
          break
        case MediaPlaybackPlayMethod.Transcode:
          if (!source.sourceInfo.TranscodingUrl) {
            throw new Error('Missing transcode URL')
          }
          if (source.sourceInfo.TranscodingSubProtocol !== 'hls') {
            throw new Error(`Unsupported transcode protocol "${source.sourceInfo.TranscodingSubProtocol}"`)
          }
          videoUrl = this.absoluteUrl(source.sourceInfo.TranscodingUrl)
          break
      }
    }
    return {
      videoUrl: this.rewriteXiaoyaHost(videoUrl),
      videoHeaders: source.requestHeaders.slice(),
      externalSubtitles: source.externalSubtitleStreams.slice()
    }
  }

  async loadSubtitle(source: MediaPlaybackSource, stream: MediaPlaybackStream): Promise<string> {
    this.requireOwned(source, 'loadSubtitle')
    if (stream.Index === undefined || !stream.Codec) {
      throw new Error('Subtitle stream is incomplete')
    }
    return await this.service.getSubtitle(this.requestSession, source, stream)
  }

  async getQueueItems(refs: MediaRef[]): Promise<MediaPlaybackQueueItem[]> {
    for (const ref of refs) {
      this.requireOwnedRef(ref, 'getQueueItems')
    }
    const ids = refs.map((ref: MediaRef): string => ref.id)
    const result = await this.service.getItems(this.requestSession, ids)
    const items = result.Items || []
    const mapped: MediaPlaybackQueueItem[] = []
    for (const id of ids) {
      const item = items.find((value: BaseItemDto): boolean => value.Id === id)
      const queueItem = item ? this.mapper.queueItem(item) : undefined
      if (queueItem) {
        mapped.push(queueItem)
      }
    }
    return mapped
  }

  async expandEpisodeQueue(source: MediaPlaybackSource): Promise<MediaPlaybackQueue | undefined> {
    this.requireOwned(source, 'expandEpisodeQueue')
    if (source.item.Type !== MediaPlaybackItemKind.Episode || !source.item.SeriesId) {
      return undefined
    }
    const result = await this.service.getEpisodes(this.requestSession, source.item.SeriesId)
    const episodeItems = result.Items || []
    if (episodeItems.length <= 1) {
      return undefined
    }
    const items: MediaPlaybackQueueItem[] = []
    for (const item of episodeItems) {
      const mapped = this.mapper.queueItem(item)
      if (mapped) {
        items.push(mapped)
      }
    }
    const currentIndex = items.findIndex((item: MediaPlaybackQueueItem): boolean =>
      item.ref.id === source.itemId)
    if (items.length <= 1 || currentIndex < 0) {
      return undefined
    }
    return {
      refs: items.map((item: MediaPlaybackQueueItem): MediaRef => item.ref),
      currentIndex: currentIndex,
      items: items
    }
  }

  async reportStart(source: MediaPlaybackSource): Promise<void> {
    this.requireOwned(source, 'reportStart')
    await this.service.reportStart(this.requestSession, {
      ItemId: source.itemId,
      PlayMethod: source.getEffectivePlayMethod(),
      PlaySessionId: source.playSessionId,
      AudioStreamIndex: source.selectedAudioStream?.Index,
      SubtitleStreamIndex: source.selectedSubtitleStream?.Index,
      IsMuted: false,
      CanSeek: true,
      PositionTicks: source.startTimeMs * Constants.TICKS_PER_MILLISECOND,
      RepeatMode: RepeatMode.RepeatNone
    })
  }

  async reportProgress(source: MediaPlaybackSource, progress: MediaPlaybackProgress): Promise<void> {
    this.requireOwned(source, 'reportProgress')
    await this.service.reportProgress(this.requestSession, {
      ItemId: source.itemId,
      PlayMethod: source.getEffectivePlayMethod(),
      PlaySessionId: source.playSessionId,
      AudioStreamIndex: source.selectedAudioStream?.Index,
      SubtitleStreamIndex: source.selectedSubtitleStream?.Index,
      IsPaused: progress.paused,
      IsMuted: progress.muted,
      CanSeek: true,
      PositionTicks: progress.positionTicks,
      RepeatMode: RepeatMode.RepeatNone
    })
  }

  async reportStop(source: MediaPlaybackSource, stop: MediaPlaybackStop): Promise<void> {
    this.requireOwned(source, 'reportStop')
    await this.service.reportStop(this.requestSession, {
      ItemId: source.itemId,
      PositionTicks: stop.positionTicks,
      PlaySessionId: source.playSessionId,
      LiveStreamId: source.liveStreamId,
      Failed: stop.failed
    })
  }

  async reportCompleted(source: MediaPlaybackSource): Promise<void> {
    this.requireOwned(source, 'reportCompleted')
    await this.service.markPlayed(this.requestSession, source.itemId)
  }

  async closeTranscode(source: MediaPlaybackSource): Promise<void> {
    this.requireOwned(source, 'closeTranscode')
    if (source.usesServerTranscoding()) {
      await this.service.closeTranscode(this.requestSession, source.playSessionId)
    }
  }

  private profileEngine(hint: MediaPlaybackEngineHint): PlayerEngineType {
    return hint === MediaPlaybackEngineHint.PreferAvPlayer ? PlayerEngineType.AV : PlayerEngineType.MPV
  }

  private findResolvedMediaSource(playbackInfo: PlaybackInfoResponse, requestedMediaSourceId: string | undefined,
    itemId: string, liveChannel: boolean): MediaSourceInfo | undefined {
    if (liveChannel) {
      return playbackInfo.MediaSources?.[0]
    }
    const selectedSourceId = requestedMediaSourceId || itemId
    return playbackInfo.MediaSources?.find((source: MediaSourceInfo): boolean => source.Id === selectedSourceId) ??
      // Jellyfin's default response can omit the item-id-shaped source id. Only use
      // the server's first-source fallback when the caller did not explicitly select
      // a source; otherwise selecting source B must never silently play source A.
      (!requestedMediaSourceId ? playbackInfo.MediaSources?.[0] : undefined)
  }

  private findDirectPlayAudioFallback(source: MediaSourceInfo | undefined,
    requestedAudioStreamIndex: number | undefined): number | undefined {
    // An explicit user selection always wins. This fallback only prevents Jellyfin from
    // needlessly transcoding when its implicit default audio codec is not in our profile.
    if (!source || requestedAudioStreamIndex !== undefined || this.isDirectPlayback(source) ||
      source.SupportsTranscoding !== true) {
      return undefined
    }
    const audioStreams = (source.MediaStreams || []).filter((stream: MediaStream): boolean =>
      stream.Type === MediaStreamType.Audio && stream.Index !== undefined)
    const defaultAudio = audioStreams.find((stream: MediaStream): boolean =>
      stream.Index === source.DefaultAudioStreamIndex) ??
      audioStreams.find((stream: MediaStream): boolean => stream.IsDefault === true)
    if (!defaultAudio || this.isMpvDirectPlayAudioCodec(defaultAudio.Codec)) {
      return undefined
    }
    const compatibleAudio = audioStreams.filter((stream: MediaStream): boolean =>
      this.isMpvDirectPlayAudioCodec(stream.Codec))
    if (compatibleAudio.length === 0) {
      return undefined
    }
    const defaultLanguage = defaultAudio.Language?.toLowerCase()
    const sameLanguage = defaultLanguage ? compatibleAudio.find((stream: MediaStream): boolean =>
      stream.Language?.toLowerCase() === defaultLanguage) : undefined
    return (sameLanguage ?? compatibleAudio[0]).Index
  }

  private isDirectPlayback(source: MediaSourceInfo): boolean {
    return source.SupportsDirectPlay === true || source.SupportsDirectStream === true
  }

  private isMpvDirectPlayAudioCodec(codec: string | null | undefined): boolean {
    switch (codec?.toLowerCase()) {
      case 'pcm_s16le':
      case 'pcm_s24le':
      case 'pcm_s32le':
      case 'pcm_f32le':
      case 'pcm_f64le':
      case 'mp1':
      case 'mp2':
      case 'mp3':
      case 'aac':
      case 'vorbis':
      case 'opus':
      case 'flac':
      case 'alac':
      case 'ac3':
      case 'eac3':
      case 'dts':
      case 'mlp':
      case 'truehd':
        return true
      default:
        return false
    }
  }

  private absoluteUrl(value: string): string {
    if (value.startsWith('http://') || value.startsWith('https://')) {
      return value
    }
    return value.startsWith('/') ? `${this.requestSession.baseUrl}${value}` :
      `${this.requestSession.baseUrl}/${value}`
  }

  private rewriteXiaoyaHost(value: string): string {
    if (!value.includes('xiaoya.host')) {
      return value
    }
    const baseUri = new uri.URI(this.requestSession.baseUrl)
    return value.replace('http://xiaoya.host', `${baseUri.scheme}:${baseUri.host}`)
  }

  private requireOwned(source: MediaPlaybackSource, operation: string): void {
    if (!source.ref.belongsTo(this.session)) {
      throw new Error(`${operation} received a media source from another account`)
    }
  }

  private requireOwnedRef(ref: MediaRef, operation: string): void {
    if (!ref.belongsTo(this.session)) {
      throw new Error(`${operation} received a media reference from another account`)
    }
  }

  private logResolvedMediaSource(itemId: string, source: MediaSourceInfo, playSessionId: string): void {
    const streams = (source.MediaStreams || []).map((stream): string => [
      `index=${stream.Index ?? -1}`,
      `type=${stream.Type || 'unknown'}`,
      `codec=${stream.Codec || 'unknown'}`,
      `codecTag=${stream.CodecTag || 'unknown'}`,
      `title=${stream.DisplayTitle || stream.Title || 'none'}`,
      `videoRange=${stream.VideoRangeType || 'unknown'}`,
      `pixelFormat=${stream.PixelFormat || 'unknown'}`,
      `bitDepth=${stream.BitDepth || 'unknown'}`,
      `profile=${stream.Profile || 'unknown'}`
    ].join(',')).join(' || ')
    console.info(
      `[PlaybackInfo] itemId=${itemId}, playSessionId=${playSessionId}, mediaSourceId=${source.Id || 'none'}, ` +
      `container=${source.Container || 'unknown'}, protocol=${source.Protocol || 'unknown'}, ` +
      `isoType=${source.IsoType || 'none'}, path=${source.Path || 'none'}, ` +
      `supportsDirectPlay=${source.SupportsDirectPlay === true}, ` +
      `supportsDirectStream=${source.SupportsDirectStream === true}, ` +
      `supportsTranscoding=${source.SupportsTranscoding === true}, ` +
      `transcodingUrl=${source.TranscodingUrl || 'none'}`
    )
    console.info(`[PlaybackStreams] itemId=${itemId}, mediaSourceId=${source.Id || 'none'}, streams=${streams || 'none'}`)
  }
}

class JellyfinLegacyPlaybackRefAdapter implements MediaPlaybackLegacyRefAdapter {
  private readonly session: MediaProviderSession

  constructor(session: MediaProviderSession) {
    this.session = session
  }

  createRef(itemId: string): MediaRef {
    return new MediaRef(this.session.provider, this.session.accountScope, itemId, MediaRefKind.Unknown)
  }
}
