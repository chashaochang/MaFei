import { buffer } from '@kit.ArkTS'
import { http } from '@kit.NetworkKit'
import CryptoJS from '@ohos/crypto-js'
import { Constants } from '../../../utils/Constants'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import {
  MediaConnectionRoute,
  MediaProviderSession
} from '../../../media/MediaProviderSession'
import { MediaRef } from '../../../media/MediaRef'
import {
  MediaPlaybackHttpResource,
  MediaPlaybackItemKind,
  MediaPlaybackPreparedStreams,
  MediaPlaybackQueue,
  MediaPlaybackQueueItem,
  MediaPlaybackRequestHeader,
  MediaPlaybackSource,
  MediaPlaybackStream
} from '../../../media/playback/MediaPlaybackModels'
import {
  MediaPlaybackLegacyRefAdapter,
  MediaPlaybackProvider,
  MediaPlaybackResolveRequest
} from '../../../media/playback/MediaPlaybackProvider'
import {
  MediaPlaybackLifecycle,
  MediaPlaybackProgress,
  MediaPlaybackStop
} from '../../../media/playback/MediaPlaybackLifecycle'
import {
  FeiniuVideoApiClient
} from '../network/FeiniuVideoApiClient'
import {
  FeiniuVideoApiRequest,
  FeiniuVideoAuthenticationError,
  FeiniuVideoHttpMethod,
  FeiniuVideoQueryParameter,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FnMediaUserInfoResp
} from '../network/FeiniuVideoApiModels'
import {
  FeiniuVideoSessionSnapshotSync
} from '../account/FeiniuVideoSessionService'
import {
  FnMediaEpisodeItem,
  FnMediaItemDetailResp,
  FnMediaListResp
} from '../catalog/FeiniuVideoDtos'
import {
  FnMediaGuidReq,
  FnMediaMutationResp,
  FnMediaOfficialStreamData,
  FnMediaOfficialStreamDirectLinkQuality,
  FnMediaOfficialStreamReq,
  FnMediaOfficialStreamResp,
  FnMediaPlayInfoReq,
  FnMediaPlayInfoResp,
  FnMediaPlayPlayReq,
  FnMediaPlayPlayResp,
  FnMediaPlayRecordReq,
  FnMediaPReq,
  FnMediaResetQualityResp,
  FnMediaStreamAudioInfo,
  FnMediaStreamFileInfo,
  FnMediaStreamListData,
  FnMediaStreamListResp,
  FnMediaStreamSubtitleInfo,
  FnMediaStreamVideoInfo
} from './FeiniuVideoPlaybackDtos'
import {
  FeiniuVideoPlaybackAccess,
  FeiniuVideoPlaybackMapper
} from './FeiniuVideoPlaybackMapper'

// Kept identical to fnOS's official stream request so Feiniu can issue a usable STRM link.
const FEINIU_OFFICIAL_STREAM_USER_AGENT: string =
  '1.32.2 (com.trim.app; build:1322007; Android 36) okHttp/4.9.0'

interface FeiniuVideoPlaybackNativeHeaders {
  'Authorization'?: string
  'Cookie'?: string
  'cookie'?: string
}

export interface FeiniuVideoPlaybackService {
  request<T>(request: FeiniuVideoApiRequest): Promise<T>
  access(): FeiniuVideoPlaybackAccess
  loadText(resource: MediaPlaybackHttpResource): Promise<string>
}

interface FeiniuVideoPlaybackHierarchy {
  readonly seriesId?: string
  readonly seriesName?: string
}

export class FeiniuVideoPlaybackApiService implements FeiniuVideoPlaybackService {
  private readonly client: FeiniuVideoApiClient
  private readonly snapshotSync?: FeiniuVideoSessionSnapshotSync

  constructor(client: FeiniuVideoApiClient, snapshotSync?: FeiniuVideoSessionSnapshotSync) {
    this.client = client
    this.snapshotSync = snapshotSync
  }

  async request<T>(request: FeiniuVideoApiRequest): Promise<T> {
    const response = await this.client.request<T>(request)
    this.snapshotSync?.syncSnapshot()
    return response
  }

  access(): FeiniuVideoPlaybackAccess {
    const candidate = this.client.getActiveCandidate()
    const credentials = this.client.getCredentials()
    return new FeiniuVideoPlaybackAccess(
      candidate.baseUrl,
      credentials.token,
      credentials.cookie,
      candidate.route === MediaConnectionRoute.Relay
    )
  }

  async loadText(resource: MediaPlaybackHttpResource): Promise<string> {
    const client = http.createHttp()
    try {
      const response = await client.request(resource.url, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        header: this.nativeHeaders(resource.headers),
        connectTimeout: 10000,
        readTimeout: 30000
      })
      if (response.responseCode === 401 || response.responseCode === 403) {
        throw new FeiniuVideoAuthenticationError('Feiniu Video authorization expired', true)
      }
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          `Feiniu Video subtitle request returned HTTP ${response.responseCode}`
        )
      }
      if (typeof response.result === 'string') {
        return response.result
      }
      if (response.result instanceof ArrayBuffer) {
        return buffer.from(response.result).toString('utf-8')
      }
      return `${response.result || ''}`
    } finally {
      client.destroy()
    }
  }

  private nativeHeaders(headers: MediaPlaybackRequestHeader[]): FeiniuVideoPlaybackNativeHeaders {
    const result: FeiniuVideoPlaybackNativeHeaders = {}
    headers.forEach((header: MediaPlaybackRequestHeader): void => {
      const name = header.name.trim().toLowerCase()
      if (name === 'authorization') {
        result.Authorization = header.value
      } else if (name === 'cookie') {
        result.Cookie = header.value
        result.cookie = header.value
      }
    })
    return result
  }
}

class FeiniuVideoResolvedPlaybackContext {
  readonly item: FnMediaItemDetailResp
  readonly mediaGuid: string
  readonly videoGuid: string
  readonly audioGuid: string
  readonly subtitleGuid: string
  readonly resolution: string
  readonly bitrate: number
  readonly playLink: string
  readonly videoUrl: string
  readonly directOriginal: boolean
  readonly audios: FnMediaStreamAudioInfo[]
  readonly subtitles: FnMediaStreamSubtitleInfo[]

  constructor(item: FnMediaItemDetailResp, mediaGuid: string, videoGuid: string,
    audioGuid: string, subtitleGuid: string, resolution: string, bitrate: number,
    playLink: string, videoUrl: string, directOriginal: boolean,
    audios: FnMediaStreamAudioInfo[], subtitles: FnMediaStreamSubtitleInfo[]) {
    this.item = item
    this.mediaGuid = mediaGuid
    this.videoGuid = videoGuid
    this.audioGuid = audioGuid
    this.subtitleGuid = subtitleGuid
    this.resolution = resolution
    this.bitrate = bitrate
    this.playLink = playLink
    this.videoUrl = videoUrl
    this.directOriginal = directOriginal
    this.audios = audios.slice()
    this.subtitles = subtitles.slice()
  }
}

export class FeiniuVideoPlaybackProvider implements MediaPlaybackProvider, MediaPlaybackLifecycle {
  readonly session: MediaProviderSession
  readonly lifecycle: MediaPlaybackLifecycle
  readonly legacyRefAdapter: MediaPlaybackLegacyRefAdapter
  private readonly service: FeiniuVideoPlaybackService
  private readonly contexts: Map<string, FeiniuVideoResolvedPlaybackContext> =
    new Map<string, FeiniuVideoResolvedPlaybackContext>()
  private playSessionSequence: number = 0

  constructor(session: MediaProviderSession, service: FeiniuVideoPlaybackService) {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      throw new Error('Feiniu Video playback cannot serve a different provider')
    }
    if (!session.capabilities.playback) {
      throw new Error('Feiniu Video playback is disabled for this account')
    }
    this.session = session
    this.service = service
    this.lifecycle = this
    this.legacyRefAdapter = new FeiniuVideoLegacyPlaybackRefAdapter(session)
  }

  static fromClient(session: MediaProviderSession, client: FeiniuVideoApiClient,
    snapshotSync?: FeiniuVideoSessionSnapshotSync): FeiniuVideoPlaybackProvider {
    return new FeiniuVideoPlaybackProvider(
      session,
      new FeiniuVideoPlaybackApiService(client, snapshotSync)
    )
  }

  async resolveSource(request: MediaPlaybackResolveRequest): Promise<MediaPlaybackSource> {
    this.requireOwnedRef(request.ref, 'resolveSource')
    const item = await this.getItem(request.ref.id)
    const explicitlySelectedMediaGuid = `${request.mediaSourceId || ''}`.trim()
    const supplemental = await Promise.all([
      this.getPlayInfo(request.ref.id, explicitlySelectedMediaGuid),
      this.getStreamList(request.ref.id),
      this.playbackHierarchy(item)
    ])
    const playInfo = supplemental[0] as FnMediaPlayInfoResp
    const streamData = supplemental[1] as FnMediaStreamListData
    const hierarchy = supplemental[2] as FeiniuVideoPlaybackHierarchy
    const preferredMediaGuid = `${explicitlySelectedMediaGuid || playInfo.data?.media_guid ||
      item.media_guid || ''}`.trim()

    const file = this.pickFile(streamData.files || [], preferredMediaGuid || playInfo.data?.media_guid || '')
    const video = this.pickVideo(
      streamData.video_streams || [],
      `${playInfo.data?.video_guid || item.video_guid || ''}`,
      `${file?.guid || playInfo.data?.media_guid || preferredMediaGuid}`
    )
    let mediaGuid = `${file?.guid || video?.media_guid || playInfo.data?.media_guid || preferredMediaGuid}`.trim()
    let videoGuid = `${video?.guid || playInfo.data?.video_guid || item.video_guid || ''}`.trim()
    if (mediaGuid.length === 0) {
      throw new Error('UnsupportedContent')
    }
    if (videoGuid.length === 0) {
      videoGuid = mediaGuid
    }

    const audios = this.audiosForMedia(streamData.audio_streams || [], mediaGuid)
    const subtitles = this.subtitlesForMedia(streamData.subtitle_streams || [], mediaGuid)
    let selectedAudio = this.pickAudio(
      audios,
      request.audioStreamIndex,
      `${playInfo.data?.audio_guid || item.audio_guid || ''}`,
      playInfo.data?.direct_link_audio_index
    )
    let selectedSubtitle = this.pickSubtitle(
      subtitles,
      request.subtitleStreamIndex,
      `${playInfo.data?.subtitle_guid || item.subtitle_guid || ''}`
    )

    const mapper = this.mapper()
    const startTimeTicks = mapper.resumeTicks(item, playInfo.data?.ts || 0, request.startTimeTicks)
    const requestedBitrate = request.maxStreamingBitrate
    let directOriginal = requestedBitrate === undefined || requestedBitrate <= 0
    let videoUrl = mapper.directMediaUrl(mediaGuid)
    let playLink = mapper.relativePlayLink(videoUrl)
    let resolution = `${video?.resolution_type || ''}`.trim()
    let bitrate = requestedBitrate ?? video?.bps ?? 0
    const strmSource = this.isStrmFile(file, item)
    // Third-party STRM backends can rate-limit or flag parallel Range traffic.
    // Keep the fallback media/range path single-connection as well.
    const multiConnectionAllowed = !strmSource
    let directLinkHeaders: MediaPlaybackRequestHeader[] | undefined
    let allowInsecureTls = false

    if (directOriginal && strmSource) {
      const directLink = await this.resolveStrmDirectLink(mediaGuid)
      if (directLink !== undefined) {
        videoUrl = directLink.url
        // Keep the Feiniu play link for progress reporting; never send a signed
        // third-party URL back to Feiniu or attach Feiniu credentials to it.
        directLinkHeaders = directLink.headers
        // The official STRM endpoint can issue cloud URLs whose certificate
        // chain is unavailable to some devices. Do not extend this exception
        // to Feiniu APIs, ordinary media, or any fallback media/range URL.
        allowInsecureTls = directLink.url.toLowerCase().startsWith('https://')
      }
    }

    if (!directOriginal) {
      const playResponse = await this.playTranscoded(
        mediaGuid,
        videoGuid,
        video,
        selectedAudio,
        selectedSubtitle,
        resolution,
        bitrate,
        startTimeTicks
      )
      const responseLink = `${playResponse.data?.play_link || ''}`.trim()
      if (responseLink.length === 0) {
        throw new Error('UnsupportedContent')
      }
      mediaGuid = `${playResponse.data?.media_guid || mediaGuid}`.trim()
      videoGuid = `${playResponse.data?.video_guid || videoGuid}`.trim()
      selectedAudio = this.pickAudio(
        audios,
        playResponse.data?.audio_index,
        `${playResponse.data?.audio_guid || selectedAudio?.guid || ''}`
      )
      selectedSubtitle = this.pickSubtitle(
        subtitles,
        playResponse.data?.subtitle_index,
        `${playResponse.data?.subtitle_guid || selectedSubtitle?.guid || ''}`
      )
      videoUrl = mapper.absoluteUrl(responseLink)
      playLink = responseLink
      directOriginal = false
    }

    if (videoUrl.length === 0) {
      throw new Error('UnsupportedContent')
    }
    const playSessionId = this.nextPlaySessionId(request.ref.id)
    const audioGuid = `${selectedAudio?.guid || ''}`.trim()
    const subtitleGuid = mapper.normalizeSubtitleGuid(`${selectedSubtitle?.guid || ''}`)
    this.contexts.set(playSessionId, new FeiniuVideoResolvedPlaybackContext(
      item,
      mediaGuid,
      videoGuid,
      audioGuid,
      subtitleGuid,
      resolution,
      Math.max(0, Math.floor(bitrate)),
      playLink,
      videoUrl,
      directOriginal,
      audios,
      subtitles
    ))
    return mapper.source({
      ref: request.ref,
      item: item,
      file: file,
      video: video,
      audios: audios,
      subtitles: subtitles,
      selectedAudio: selectedAudio,
      selectedSubtitle: selectedSubtitle,
      playSessionId: playSessionId,
      mediaGuid: mediaGuid,
      videoUrl: videoUrl,
      directOriginal: directOriginal,
      requestHeaders: directLinkHeaders,
      multiConnectionAllowed: multiConnectionAllowed,
      allowInsecureTls: allowInsecureTls,
      startTimeTicks: startTimeTicks,
      maxStreamingBitrate: request.maxStreamingBitrate,
      engineHint: request.engineHint,
      seriesId: hierarchy.seriesId,
      seriesName: hierarchy.seriesName
    })
  }

  async prepareStreams(source: MediaPlaybackSource): Promise<MediaPlaybackPreparedStreams> {
    this.requireOwned(source, 'prepareStreams')
    const context = this.contextFor(source)
    return {
      videoUrl: context.videoUrl,
      videoHeaders: source.requestHeaders.slice(),
      externalSubtitles: source.externalSubtitleStreams.slice()
    }
  }

  async loadSubtitle(source: MediaPlaybackSource, stream: MediaPlaybackStream): Promise<string> {
    this.requireOwned(source, 'loadSubtitle')
    const external = source.externalSubtitleStreams.find((item): boolean => item.index === stream.Index)
    if (!external) {
      throw new Error('Subtitle stream is not externally readable')
    }
    return await this.service.loadText({
      url: external.deliveryUrl,
      headers: external.headers.slice()
    })
  }

  async getQueueItems(refs: MediaRef[]): Promise<MediaPlaybackQueueItem[]> {
    const result: MediaPlaybackQueueItem[] = []
    for (let index = 0; index < refs.length; index += 1) {
      const ref = refs[index]
      this.requireOwnedRef(ref, 'getQueueItems')
      const item = await this.getItem(ref.id)
      const mapped = this.mapper().queueItem(item, index)
      if (mapped) {
        result.push(mapped)
      }
    }
    return result
  }

  async expandEpisodeQueue(source: MediaPlaybackSource): Promise<MediaPlaybackQueue | undefined> {
    this.requireOwned(source, 'expandEpisodeQueue')
    if (source.item.Type !== MediaPlaybackItemKind.Episode) {
      return undefined
    }
    const parentGuid = `${this.contextFor(source).item.parent_guid || ''}`.trim()
    if (parentGuid.length === 0) {
      return undefined
    }
    const response = await this.request<FnMediaListResp<FnMediaEpisodeItem[]>>(
      `/v/api/v1/episode/list/${encodeURIComponent(parentGuid)}`,
      FeiniuVideoHttpMethod.Get
    )
    const episodes = (response.data || []).slice().sort(
      (left: FnMediaEpisodeItem, right: FnMediaEpisodeItem): number =>
        (left.episode_number || 0) - (right.episode_number || 0)
    )
    const items: MediaPlaybackQueueItem[] = []
    episodes.forEach((episode: FnMediaEpisodeItem, index: number): void => {
      const mapped = this.mapper().queueItem(episode, index)
      if (mapped) {
        items.push(mapped)
      }
    })
    const currentIndex = items.findIndex((item: MediaPlaybackQueueItem): boolean =>
      item.ref.id === source.itemId)
    if (items.length <= 1 || currentIndex < 0) {
      return undefined
    }
    return {
      items: items,
      refs: items.map((item: MediaPlaybackQueueItem): MediaRef => item.ref),
      currentIndex: currentIndex
    }
  }

  async reportStart(source: MediaPlaybackSource): Promise<void> {
    this.requireOwned(source, 'reportStart')
    const positionTicks = Math.max(0, source.resume.startTimeTicks)
    if (positionTicks > 0) {
      await this.reportRecord(source, positionTicks)
    }
  }

  async reportProgress(source: MediaPlaybackSource, progress: MediaPlaybackProgress): Promise<void> {
    this.requireOwned(source, 'reportProgress')
    if (progress.positionTicks > 0) {
      await this.reportRecord(source, progress.positionTicks)
    }
  }

  async reportStop(source: MediaPlaybackSource, stop: MediaPlaybackStop): Promise<void> {
    this.requireOwned(source, 'reportStop')
    if (stop.positionTicks > 0) {
      await this.reportRecord(source, stop.positionTicks)
    }
  }

  async reportCompleted(source: MediaPlaybackSource): Promise<void> {
    this.requireOwned(source, 'reportCompleted')
    if (source.runTimeTicks > 0) {
      await this.reportRecord(source, source.runTimeTicks)
    }
    const payload: FnMediaGuidReq = { item_guid: source.itemId }
    await this.request<FnMediaMutationResp>(
      '/v/api/v1/item/watched',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload)
    )
  }

  async closeTranscode(source: MediaPlaybackSource): Promise<void> {
    this.requireOwned(source, 'closeTranscode')
    const context = this.contexts.get(source.playSessionId)
    if (!context) {
      return
    }
    if (!context.directOriginal && context.playLink.length > 0 &&
      context.playLink.indexOf('/v/api/v1/media/range/') < 0) {
      const payload: FnMediaPReq = {
        req: 'media.quit',
        reqid: '1234567890ABCDEF',
        playLink: context.playLink
      }
      await this.service.request<FnMediaResetQualityResp>(new FeiniuVideoApiRequest(
        '/v/api/v1/media/p',
        FeiniuVideoHttpMethod.Post,
        JSON.stringify(payload),
        [],
        true,
        60000
      ))
    }
    this.contexts.delete(source.playSessionId)
  }

  private async resolveStrmDirectLink(mediaGuid: string): Promise<MediaPlaybackHttpResource | undefined> {
    try {
      const streamInfo = await this.getOfficialStreamInfo(mediaGuid)
      const directUrl = this.pickOfficialDirectLink(streamInfo.direct_link_qualities)
      const url = directUrl.length > 0 ? directUrl : this.pickOfficialDirectLink(streamInfo.qualities)
      if (url.length === 0) {
        return undefined
      }
      return {
        url: url,
        // An empty list is intentional: it prevents mapper.source() from
        // falling back to the Feiniu authorization headers for a cloud URL.
        headers: this.officialDirectLinkHeaders(streamInfo)
      }
    } catch (_) {
      console.warn(`[FeiniuVideoPlayback] STRM official stream unavailable; using Feiniu fallback media=${mediaGuid}`)
      return undefined
    }
  }

  private async getOfficialStreamInfo(mediaGuid: string): Promise<FnMediaOfficialStreamData> {
    const userInfo = await this.request<FnMediaUserInfoResp>(
      '/v/api/v1/user/info',
      FeiniuVideoHttpMethod.Get
    )
    const sources = userInfo.data?.sources || []
    const sourceName = sources.length > 0 ? `${sources[0].source_name || ''}`.trim() : ''
    const payload: FnMediaOfficialStreamReq = {
      media_guid: mediaGuid,
      ip: CryptoJS.MD5(sourceName).toString(),
      level: 1,
      header: {
        'User-Agent': [FEINIU_OFFICIAL_STREAM_USER_AGENT]
      }
    }
    const response = await this.service.request<FnMediaOfficialStreamResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/stream',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload),
      [],
      true,
      60000
    ))
    return response.data || {}
  }

  private pickOfficialDirectLink(
    qualities: FnMediaOfficialStreamDirectLinkQuality[] | undefined): string {
    if (qualities === undefined) {
      return ''
    }
    for (const quality of qualities) {
      const url = `${quality.url || ''}`.trim()
      const normalized = url.toLowerCase()
      if (normalized.startsWith('https://') || normalized.startsWith('http://')) {
        return url
      }
    }
    return ''
  }

  private officialDirectLinkHeaders(streamInfo: FnMediaOfficialStreamData): MediaPlaybackRequestHeader[] {
    if (streamInfo.header === undefined) {
      return []
    }
    const headers = streamInfo.header as Object as Record<string, Object | string | string[]>
    const result: MediaPlaybackRequestHeader[] = []
    const keys = Object.keys(headers)
    for (let index = 0; index < keys.length; index += 1) {
      const key = keys[index].trim()
      if (key.length === 0) continue
      const rawValue = headers[key]
      const values: string[] = Array.isArray(rawValue)
        ? rawValue.map((item: string): string => `${item || ''}`)
        : [`${rawValue || ''}`]
      const normalizedValues = values.map((candidate: string): string => candidate.trim())
        .filter((candidate: string): boolean => candidate.length > 0)
      if (normalizedValues.length === 0) continue
      result.push({
        name: key,
        value: key.toLowerCase() === 'cookie'
          ? normalizedValues.join('; ') : normalizedValues.join(', ')
      })
    }
    return result
  }

  private async playbackHierarchy(item: FnMediaItemDetailResp): Promise<FeiniuVideoPlaybackHierarchy> {
    const type = `${item.type || ''}`.trim().toLowerCase()
    if (type !== 'episode' && (item.episode_number || 0) <= 0) {
      return {}
    }
    let seriesName = `${item.tv_title || ''}`.trim()
    const parentId = `${item.parent_guid || ''}`.trim()
    if (parentId.length === 0) {
      return { seriesName: seriesName || undefined }
    }
    try {
      const parent = await this.getItem(parentId)
      const parentType = `${parent.type || ''}`.trim().toLowerCase()
      if (parentType === 'tv' || parentType === 'series') {
        seriesName = seriesName || `${parent.title || item.parent_title || ''}`.trim()
        return {
          seriesId: parent.guid.trim() || parentId,
          seriesName: seriesName || undefined
        }
      }
      if (parentType !== 'season') {
        return { seriesName: seriesName || undefined }
      }
      const season = parent
      const seriesParentId = `${season.parent_guid || ''}`.trim()
      const libraryId = `${season.ancestor_guid || ''}`.trim()
      const seriesId = seriesParentId.length > 0 && seriesParentId !== libraryId ? seriesParentId : ''
      seriesName = seriesName || `${season.parent_title || ''}`.trim()
      if (seriesName.length === 0 && seriesId.length > 0) {
        const series = await this.getItem(seriesId)
        seriesName = `${series.title || ''}`.trim()
      }
      return {
        seriesId: seriesId || undefined,
        seriesName: seriesName || undefined
      }
    } catch (error) {
      console.warn(`[FeiniuVideoPlayback] hierarchy failed item=${item.guid} ` +
        `error=${(error as Error).message || 'unknown'}`)
      return { seriesName: seriesName || undefined }
    }
  }

  private async getItem(itemId: string): Promise<FnMediaItemDetailResp> {
    const normalizedId = itemId.trim()
    const response = await this.request<FnMediaListResp<FnMediaItemDetailResp>>(
      `/v/api/v1/item/${encodeURIComponent(normalizedId)}`,
      FeiniuVideoHttpMethod.Get
    )
    if (!response.data || `${response.data.guid || ''}`.trim() !== normalizedId) {
      throw new Error('NetworkFailure')
    }
    return response.data
  }

  private async getPlayInfo(itemGuid: string, mediaGuid: string): Promise<FnMediaPlayInfoResp> {
    const payload: FnMediaPlayInfoReq = { item_guid: itemGuid }
    if (mediaGuid.length > 0) {
      payload.media_guid = mediaGuid
    }
    return await this.request<FnMediaPlayInfoResp>(
      '/v/api/v1/play/info',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload)
    )
  }

  private async getStreamList(itemGuid: string): Promise<FnMediaStreamListData> {
    const response = await this.request<FnMediaStreamListResp>(
      `/v/api/v1/stream/list/${encodeURIComponent(itemGuid)}`,
      FeiniuVideoHttpMethod.Get,
      '',
      [new FeiniuVideoQueryParameter('before_play', 1)]
    )
    return response.data || {}
  }

  private async playTranscoded(mediaGuid: string, videoGuid: string,
    video: FnMediaStreamVideoInfo | undefined,
    audio: FnMediaStreamAudioInfo | undefined,
    subtitle: FnMediaStreamSubtitleInfo | undefined,
    resolution: string,
    bitrate: number,
    startTimeTicks: number): Promise<FnMediaPlayPlayResp> {
    const payload: FnMediaPlayPlayReq = {
      media_guid: mediaGuid,
      video_guid: videoGuid,
      video_encoder: `${video?.codec_name || 'h264'}`.trim(),
      resolution: resolution,
      bitrate: Math.max(0, Math.floor(bitrate)),
      startTimestamp: Math.max(0, Math.floor(startTimeTicks / (1000 * Constants.TICKS_PER_MILLISECOND))),
      audio_encoder: `${audio?.codec_name || 'aac'}`.trim(),
      audio_guid: `${audio?.guid || ''}`.trim(),
      subtitle_guid: this.mapper().normalizeSubtitleGuid(`${subtitle?.guid || ''}`),
      channels: Math.max(1, Math.floor(audio?.channels || 2)),
      forced_sdr: 0
    }
    return await this.service.request<FnMediaPlayPlayResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/play/play',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload),
      [],
      true,
      60000
    ))
  }

  private async reportRecord(source: MediaPlaybackSource, positionTicks: number): Promise<void> {
    const context = this.contextFor(source)
    const audio = this.audioForSource(source, context)
    const subtitle = this.subtitleForSource(source, context)
    const durationSeconds = Math.max(0, Math.floor(source.runTimeTicks /
      (1000 * Constants.TICKS_PER_MILLISECOND)))
    const payload: FnMediaPlayRecordReq = {
      item_guid: source.itemId,
      media_guid: context.mediaGuid,
      video_guid: context.videoGuid,
      audio_guid: `${audio?.guid || context.audioGuid}`.trim(),
      resolution: context.resolution,
      bitrate: context.bitrate,
      ts: Math.max(0, Math.floor(positionTicks / (1000 * Constants.TICKS_PER_MILLISECOND))),
      duration: durationSeconds,
      play_link: context.playLink
    }
    const subtitleGuid = this.mapper().normalizeSubtitleGuid(`${subtitle?.guid || context.subtitleGuid}`)
    if (subtitleGuid.length > 0) {
      payload.subtitle_guid = subtitleGuid
    }
    if (payload.item_guid.length === 0 || payload.media_guid.length === 0 ||
      payload.video_guid.length === 0 || payload.ts <= 0) {
      return
    }
    await this.request<FnMediaMutationResp>(
      '/v/api/v1/play/record',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload)
    )
  }

  private contextFor(source: MediaPlaybackSource): FeiniuVideoResolvedPlaybackContext {
    const context = this.contexts.get(source.playSessionId)
    if (!context) {
      throw new Error('Feiniu Video playback context is no longer available')
    }
    return context
  }

  private mapper(): FeiniuVideoPlaybackMapper {
    return new FeiniuVideoPlaybackMapper(this.session, this.service.access())
  }

  private request<T>(path: string, method: FeiniuVideoHttpMethod, bodyText: string = '',
    parameters: FeiniuVideoQueryParameter[] = []): Promise<T> {
    return this.service.request<T>(new FeiniuVideoApiRequest(
      path,
      method,
      bodyText,
      parameters,
      true
    ))
  }

  private isStrmFile(file: FnMediaStreamFileInfo | undefined, item?: FnMediaItemDetailResp): boolean {
    const fileName = `${file?.file_name || file?.path || item?.file_name || ''}`.trim().toLowerCase()
    return fileName.endsWith('.strm')
  }

  private pickFile(files: FnMediaStreamFileInfo[], mediaGuid: string): FnMediaStreamFileInfo | undefined {
    const preferred = mediaGuid.trim()
    return files.find((item: FnMediaStreamFileInfo): boolean => `${item.guid || ''}`.trim() === preferred) ||
      files[0]
  }

  private pickVideo(videos: FnMediaStreamVideoInfo[], videoGuid: string,
    mediaGuid: string): FnMediaStreamVideoInfo | undefined {
    const preferredVideo = videoGuid.trim()
    const preferredMedia = mediaGuid.trim()
    return videos.find((item: FnMediaStreamVideoInfo): boolean =>
      preferredVideo.length > 0 && `${item.guid || ''}`.trim() === preferredVideo) ||
      videos.find((item: FnMediaStreamVideoInfo): boolean =>
        preferredMedia.length > 0 && `${item.media_guid || ''}`.trim() === preferredMedia) ||
      videos[0]
  }

  private audiosForMedia(audios: FnMediaStreamAudioInfo[], mediaGuid: string): FnMediaStreamAudioInfo[] {
    const preferred = mediaGuid.trim()
    if (preferred.length === 0) {
      return audios.slice()
    }
    const scoped = audios.filter((item: FnMediaStreamAudioInfo): boolean =>
      `${item.media_guid || ''}`.trim() === preferred)
    return scoped.length > 0 ? scoped : audios.slice()
  }

  private subtitlesForMedia(subtitles: FnMediaStreamSubtitleInfo[],
    mediaGuid: string): FnMediaStreamSubtitleInfo[] {
    const preferred = mediaGuid.trim()
    if (preferred.length === 0) {
      return subtitles.slice()
    }
    const scoped = subtitles.filter((item: FnMediaStreamSubtitleInfo): boolean =>
      `${item.media_guid || ''}`.trim() === preferred)
    return scoped.length > 0 ? scoped : subtitles.slice()
  }

  private pickAudio(audios: FnMediaStreamAudioInfo[], requestedIndex?: number,
    preferredGuid: string = '', fallbackIndex?: number): FnMediaStreamAudioInfo | undefined {
    if (requestedIndex !== undefined) {
      const requested = audios.find((item: FnMediaStreamAudioInfo): boolean => item.index === requestedIndex)
      if (requested) return requested
    }
    const preferred = preferredGuid.trim()
    if (preferred.length > 0) {
      const matched = audios.find((item: FnMediaStreamAudioInfo): boolean =>
        `${item.guid || ''}`.trim() === preferred)
      if (matched) return matched
    }
    if (fallbackIndex !== undefined) {
      const fallback = audios.find((item: FnMediaStreamAudioInfo): boolean => item.index === fallbackIndex)
      if (fallback) return fallback
    }
    return audios.find((item: FnMediaStreamAudioInfo): boolean => (item.is_default || 0) > 0) || audios[0]
  }

  private pickSubtitle(subtitles: FnMediaStreamSubtitleInfo[], requestedIndex?: number,
    preferredGuid: string = ''): FnMediaStreamSubtitleInfo | undefined {
    if (requestedIndex === -1) {
      return undefined
    }
    if (requestedIndex !== undefined) {
      const requested = subtitles.find((item: FnMediaStreamSubtitleInfo): boolean =>
        item.index === requestedIndex)
      if (requested) return requested
    }
    const preferred = this.mapper().normalizeSubtitleGuid(preferredGuid)
    if (preferred.length > 0) {
      const matched = subtitles.find((item: FnMediaStreamSubtitleInfo): boolean =>
        `${item.guid || ''}`.trim() === preferred)
      if (matched) return matched
    }
    return subtitles.find((item: FnMediaStreamSubtitleInfo): boolean => (item.is_default || 0) > 0)
  }

  private audioForSource(source: MediaPlaybackSource,
    context: FeiniuVideoResolvedPlaybackContext): FnMediaStreamAudioInfo | undefined {
    const index = source.selectedAudioStream?.Index
    return context.audios.find((item: FnMediaStreamAudioInfo): boolean => item.index === index)
  }

  private subtitleForSource(source: MediaPlaybackSource,
    context: FeiniuVideoResolvedPlaybackContext): FnMediaStreamSubtitleInfo | undefined {
    const index = source.selectedSubtitleStream?.Index
    return context.subtitles.find((item: FnMediaStreamSubtitleInfo): boolean => item.index === index)
  }

  private nextPlaySessionId(itemId: string): string {
    this.playSessionSequence += 1
    return `feiniu-${this.session.providerAccountId}-${itemId}-${this.playSessionSequence}`
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
}

class FeiniuVideoLegacyPlaybackRefAdapter implements MediaPlaybackLegacyRefAdapter {
  private readonly session: MediaProviderSession

  constructor(session: MediaProviderSession) {
    this.session = session
  }

  createRef(itemId: string): MediaRef {
    return new MediaRef(this.session.provider, this.session.accountScope, itemId)
  }
}
