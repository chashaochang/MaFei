import { AxiosResponse } from '@ohos/axios'
import { getMediaInfoApi, getPlaystateApi } from 'sdk'
import { Api } from 'sdk/src/main/ets/api'
import {
  MediaInfoApi,
  MediaInfoApiGetPostedPlaybackInfoRequest
} from 'sdk/src/main/ets/generated-client/api/media-info-api'
import {
  PlaystateApi,
  PlaystateApiReportPlaybackProgressRequest,
  PlaystateApiReportPlaybackStartRequest,
  PlaystateApiReportPlaybackStoppedRequest
} from 'sdk/src/main/ets/generated-client/api/playstate-api'
import {
  MediaSourceInfo,
  PlaybackInfoDto,
  PlaybackInfoResponse,
  PlaybackProgressInfo,
  PlaybackStartInfo,
  PlaybackStopInfo,
  PlayMethod
} from 'sdk/src/main/ets/generated-client/models'
import { ClientInfo } from 'sdk/src/main/ets/models/client-info'
import { DeviceInfo } from 'sdk/src/main/ets/models/device-info'
import { AppPreference } from '../../data/AppPreference'
import { ApiClient } from '../../network/ApiClient'
import {
  millisecondsToMusicTicks,
  MusicPlaybackErrorCode,
  MusicPlayMethod,
  MusicResolvedSource,
  musicTicksToMilliseconds
} from './MusicModels'
import { MusicDeviceProfile } from './MusicDeviceProfile'

export interface MusicMediaInfoClient {
  getPlaybackInfo(itemId: string, playbackInfo: PlaybackInfoDto): Promise<PlaybackInfoResponse>
}

export interface MusicPlaystateClient {
  reportStart(info: PlaybackStartInfo): Promise<void>
  reportProgress(info: PlaybackProgressInfo): Promise<void>
  reportStopped(info: PlaybackStopInfo): Promise<void>
}

export class MusicPlaybackRequestSession {
  scope: string
  basePath: string
  userId: string
  serverId: string
  accessToken: string
  deviceId: string
  mediaInfo: MusicMediaInfoClient
  playstate: MusicPlaystateClient

  constructor(scope: string, basePath: string, userId: string, serverId: string,
    accessToken: string, deviceId: string, mediaInfo: MusicMediaInfoClient,
    playstate: MusicPlaystateClient) {
    this.scope = scope
    this.basePath = basePath
    this.userId = userId
    this.serverId = serverId
    this.accessToken = accessToken
    this.deviceId = deviceId
    this.mediaInfo = mediaInfo
    this.playstate = playstate
  }
}

export class MusicPlaybackInfoResult {
  itemId: string = ''
  mediaSourceId: string = ''
  playSessionId: string = ''
  liveStreamId: string = ''
  protocol: string = ''
  path: string = ''
  container: string = ''
  transcodingUrl: string = ''
  supportsDirectPlay: boolean = false
  supportsDirectStream: boolean = false
  supportsTranscoding: boolean = false
  sourceFound: boolean = false
  durationMs: number = 0
  startTimeTicks: number = 0
  scope: string = ''
  serverPlaybackError: string = ''
}

export class MusicPlaybackServiceError extends Error {
  code: MusicPlaybackErrorCode

  constructor(code: MusicPlaybackErrorCode, message: string) {
    super(message)
    this.name = 'MusicPlaybackServiceError'
    this.code = code
  }
}

export interface MusicPlaybackSourceService {
  getPlaybackInfo(itemId: string, startTimeTicks: number): Promise<MusicPlaybackInfoResult>
  buildStaticStreamUrl(source: MusicPlaybackInfoResult): string
  buildDirectStreamUrl(source: MusicPlaybackInfoResult): string
  absoluteAuthenticatedUrl(pathOrUrl: string, playSessionId?: string): string
}

export interface MusicPlaybackReporter {
  reportStart(source: MusicResolvedSource, positionMs: number): Promise<void>
  reportProgress(source: MusicResolvedSource, positionMs: number, paused: boolean): Promise<void>
  reportStop(source: MusicResolvedSource, positionMs: number, completed: boolean): Promise<void>
}

class JellyfinMusicMediaInfoClient implements MusicMediaInfoClient {
  private api: MediaInfoApi

  constructor(api: MediaInfoApi) {
    this.api = api
  }

  async getPlaybackInfo(itemId: string, playbackInfo: PlaybackInfoDto): Promise<PlaybackInfoResponse> {
    const request: MediaInfoApiGetPostedPlaybackInfoRequest = {
      itemId: itemId,
      playbackInfoDto: playbackInfo
    }
    const response: AxiosResponse<PlaybackInfoResponse> = await this.api.getPostedPlaybackInfo(request)
    return response.data
  }
}

class JellyfinMusicPlaystateClient implements MusicPlaystateClient {
  private api: PlaystateApi

  constructor(api: PlaystateApi) {
    this.api = api
  }

  async reportStart(info: PlaybackStartInfo): Promise<void> {
    const request: PlaystateApiReportPlaybackStartRequest = { playbackStartInfo: info }
    await this.api.reportPlaybackStart(request)
  }

  async reportProgress(info: PlaybackProgressInfo): Promise<void> {
    const request: PlaystateApiReportPlaybackProgressRequest = { playbackProgressInfo: info }
    await this.api.reportPlaybackProgress(request)
  }

  async reportStopped(info: PlaybackStopInfo): Promise<void> {
    const request: PlaystateApiReportPlaybackStoppedRequest = { playbackStopInfo: info }
    await this.api.reportPlaybackStopped(request)
  }
}

export function redactMusicPlaybackUrl(url: string): string {
  const sensitiveQuery = new RegExp('([?&](?:api_key|x-emby-token|access_token)=)[^&#]*', 'gi')
  return url.replace(sensitiveQuery, '$1REDACTED')
}

export class MusicPlaybackApiService implements MusicPlaybackSourceService, MusicPlaybackReporter {
  private sessionFactory?: () => MusicPlaybackRequestSession
  private sessions: Map<string, MusicPlaybackRequestSession> = new Map<string, MusicPlaybackRequestSession>()
  private latestSession?: MusicPlaybackRequestSession

  constructor(sessionFactory?: () => MusicPlaybackRequestSession) {
    this.sessionFactory = sessionFactory
  }

  createSession(): MusicPlaybackRequestSession {
    if (this.sessionFactory) {
      return this.sessionFactory()
    }
    return this.createApiSession()
  }

  async getPlaybackInfo(itemId: string, startTimeTicks: number): Promise<MusicPlaybackInfoResult> {
    const session = this.createSession()
    this.latestSession = session
    const playbackInfo: PlaybackInfoDto = {
      UserId: session.userId,
      DeviceProfile: MusicDeviceProfile.create(),
      StartTimeTicks: this.normalizeTicks(startTimeTicks),
      EnableDirectPlay: true,
      EnableDirectStream: true,
      EnableTranscoding: true,
      AllowVideoStreamCopy: false,
      AllowAudioStreamCopy: true,
      AutoOpenLiveStream: true
    }
    const response = await session.mediaInfo.getPlaybackInfo(itemId, playbackInfo)
    const result = this.mapPlaybackInfo(itemId, startTimeTicks, session, response)
    if (result.playSessionId.length > 0) {
      this.sessions.set(result.playSessionId, session)
    }
    return result
  }

  buildStaticStreamUrl(source: MusicPlaybackInfoResult): string {
    let url = this.audioStreamUrl(source)
    url = this.appendQuery(url, 'static', 'true')
    url = this.appendSourceQuery(url, source)
    return this.absoluteAuthenticatedUrl(url, source.playSessionId)
  }

  buildDirectStreamUrl(source: MusicPlaybackInfoResult): string {
    let url = this.audioStreamUrl(source)
    url = this.appendSourceQuery(url, source)
    return this.absoluteAuthenticatedUrl(url, source.playSessionId)
  }

  absoluteAuthenticatedUrl(pathOrUrl: string, playSessionId: string = ''): string {
    const session = this.sessionForUrl(playSessionId)
    const relative = !this.isAbsoluteHttpUrl(pathOrUrl)
    const absoluteUrl = relative ? this.joinBasePath(session.basePath, pathOrUrl) : pathOrUrl
    if (!relative && !this.isServerUrl(absoluteUrl, session.basePath)) {
      return absoluteUrl
    }
    if (session.accessToken.length === 0 || this.hasApiKey(absoluteUrl)) {
      return absoluteUrl
    }
    return this.appendQuery(absoluteUrl, 'api_key', session.accessToken)
  }

  async reportStart(source: MusicResolvedSource, positionMs: number): Promise<void> {
    const session = this.sessionForSource(source)
    const info: PlaybackStartInfo = {
      ItemId: source.itemId,
      MediaSourceId: source.mediaSourceId,
      PlaySessionId: source.playSessionId,
      LiveStreamId: source.liveStreamId,
      PlayMethod: this.toJellyfinPlayMethod(source.playMethod),
      CanSeek: true,
      IsPaused: false,
      IsMuted: false,
      PositionTicks: millisecondsToMusicTicks(this.normalizeMilliseconds(positionMs))
    }
    await session.playstate.reportStart(info)
  }

  async reportProgress(source: MusicResolvedSource, positionMs: number, paused: boolean): Promise<void> {
    const session = this.sessionForSource(source)
    const info: PlaybackProgressInfo = {
      ItemId: source.itemId,
      MediaSourceId: source.mediaSourceId,
      PlaySessionId: source.playSessionId,
      LiveStreamId: source.liveStreamId,
      PlayMethod: this.toJellyfinPlayMethod(source.playMethod),
      CanSeek: true,
      IsPaused: paused,
      IsMuted: false,
      PositionTicks: millisecondsToMusicTicks(this.normalizeMilliseconds(positionMs))
    }
    await session.playstate.reportProgress(info)
  }

  async reportStop(source: MusicResolvedSource, positionMs: number, completed: boolean): Promise<void> {
    const session = this.sessionForSource(source)
    const completedPosition = completed && source.durationMs > 0 ? source.durationMs : positionMs
    const info: PlaybackStopInfo = {
      ItemId: source.itemId,
      MediaSourceId: source.mediaSourceId,
      PlaySessionId: source.playSessionId,
      LiveStreamId: source.liveStreamId,
      PositionTicks: millisecondsToMusicTicks(this.normalizeMilliseconds(completedPosition)),
      Failed: false
    }
    await session.playstate.reportStopped(info)
    this.sessions.delete(source.playSessionId)
  }

  private createApiSession(): MusicPlaybackRequestSession {
    if (!ApiClient.isInitialized()) {
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.Authentication,
        'Music playback requires an initialized account'
      )
    }
    const api = ApiClient.Instance()
    const basePath = this.normalizeBasePath(String(api.basePath || ''))
    const userId = String(api.userId || '')
    const accessToken = String(api.accessToken || '')
    const deviceId = String(api.deviceInfo.id || '')
    const clientInfo: ClientInfo = {
      name: String(api.clientInfo.name || ''),
      version: String(api.clientInfo.version || '')
    }
    const deviceInfo: DeviceInfo = {
      id: deviceId,
      name: String(api.deviceInfo.name || '')
    }
    const snapshotApi = new Api(basePath, clientInfo, deviceInfo, accessToken, api.axiosInstance)
    snapshotApi.userId = userId
    const serverId = this.currentServerId(basePath)
    const scope = `${basePath}|${userId}`
    return new MusicPlaybackRequestSession(
      scope,
      basePath,
      userId,
      serverId,
      accessToken,
      deviceId,
      new JellyfinMusicMediaInfoClient(getMediaInfoApi(snapshotApi)),
      new JellyfinMusicPlaystateClient(getPlaystateApi(snapshotApi))
    )
  }

  private currentServerId(fallback: string): string {
    try {
      const serverId = AppPreference.getInstance().getCurrentServerSystemId()
      return serverId.length > 0 ? serverId : fallback
    } catch (_error) {
      return fallback
    }
  }

  private mapPlaybackInfo(itemId: string, startTimeTicks: number, session: MusicPlaybackRequestSession,
    response: PlaybackInfoResponse): MusicPlaybackInfoResult {
    const result = new MusicPlaybackInfoResult()
    result.itemId = itemId
    result.playSessionId = response.PlaySessionId || ''
    result.scope = session.scope
    result.startTimeTicks = this.normalizeTicks(startTimeTicks)
    result.serverPlaybackError = response.ErrorCode || ''
    const source = this.selectMediaSource(response.MediaSources || [])
    if (!source) {
      return result
    }
    result.sourceFound = true
    result.mediaSourceId = source.Id || itemId
    result.liveStreamId = source.LiveStreamId || ''
    result.protocol = source.Protocol || ''
    result.path = source.Path || ''
    result.container = source.Container || ''
    result.transcodingUrl = source.TranscodingUrl || ''
    result.supportsDirectPlay = source.SupportsDirectPlay === true
    result.supportsDirectStream = source.SupportsDirectStream === true
    result.supportsTranscoding = source.SupportsTranscoding === true
    result.durationMs = musicTicksToMilliseconds(source.RunTimeTicks)
    return result
  }

  private selectMediaSource(sources: MediaSourceInfo[]): MediaSourceInfo | undefined {
    let directStream: MediaSourceInfo | undefined
    let transcode: MediaSourceInfo | undefined
    let fallback: MediaSourceInfo | undefined
    for (const source of sources) {
      if (!fallback) {
        fallback = source
      }
      if (source.SupportsDirectPlay === true) {
        return source
      }
      if (!directStream && source.SupportsDirectStream === true) {
        directStream = source
      }
      if (!transcode && source.SupportsTranscoding === true) {
        transcode = source
      }
    }
    return directStream || transcode || fallback
  }

  private audioStreamUrl(source: MusicPlaybackInfoResult): string {
    const itemId = encodeURIComponent(source.itemId)
    const container = this.normalizeContainer(source.container)
    return container.length > 0 ? `/Audio/${itemId}/stream.${encodeURIComponent(container)}` :
      `/Audio/${itemId}/stream`
  }

  private normalizeContainer(container: string): string {
    let value = container.trim().toLowerCase()
    const commaIndex = value.indexOf(',')
    if (commaIndex >= 0) {
      value = value.slice(0, commaIndex)
    }
    while (value.startsWith('.')) {
      value = value.slice(1)
    }
    return value
  }

  private appendSourceQuery(url: string, source: MusicPlaybackInfoResult): string {
    let next = this.appendQuery(url, 'mediaSourceId', source.mediaSourceId)
    next = this.appendQuery(next, 'playSessionId', source.playSessionId)
    const session = this.sessionForUrl(source.playSessionId)
    next = this.appendQuery(next, 'deviceId', session.deviceId)
    if (source.startTimeTicks > 0) {
      next = this.appendQuery(next, 'startTimeTicks', source.startTimeTicks.toString())
    }
    return next
  }

  private appendQuery(url: string, key: string, value: string): string {
    if (value.length === 0) {
      return url
    }
    const fragmentIndex = url.indexOf('#')
    const fragment = fragmentIndex >= 0 ? url.slice(fragmentIndex) : ''
    const base = fragmentIndex >= 0 ? url.slice(0, fragmentIndex) : url
    const separator = base.includes('?') ? '&' : '?'
    return `${base}${separator}${encodeURIComponent(key)}=${encodeURIComponent(value)}${fragment}`
  }

  private sessionForUrl(playSessionId: string): MusicPlaybackRequestSession {
    if (playSessionId.length > 0) {
      const session = this.sessions.get(playSessionId)
      if (session) {
        return session
      }
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.Authentication,
        'Music playback request session is no longer available'
      )
    }
    if (this.latestSession) {
      return this.latestSession
    }
    throw new MusicPlaybackServiceError(
      MusicPlaybackErrorCode.Authentication,
      'Music playback request session is unavailable'
    )
  }

  private sessionForSource(source: MusicResolvedSource): MusicPlaybackRequestSession {
    const session = this.sessionForUrl(source.playSessionId)
    if (source.scope !== session.scope) {
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.Authentication,
        'Music playback source belongs to another account scope'
      )
    }
    return session
  }

  private toJellyfinPlayMethod(method: MusicPlayMethod): PlayMethod {
    if (method === MusicPlayMethod.DirectPlay) {
      return PlayMethod.DirectPlay
    }
    if (method === MusicPlayMethod.DirectStream) {
      return PlayMethod.DirectStream
    }
    return PlayMethod.Transcode
  }

  private normalizeTicks(ticks: number): number {
    return Number.isFinite(ticks) && ticks > 0 ? Math.floor(ticks) : 0
  }

  private normalizeMilliseconds(milliseconds: number): number {
    return Number.isFinite(milliseconds) && milliseconds > 0 ? Math.floor(milliseconds) : 0
  }

  private normalizeBasePath(basePath: string): string {
    let normalized = basePath.trim()
    while (normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1)
    }
    return normalized
  }

  private joinBasePath(basePath: string, path: string): string {
    const normalizedBase = this.normalizeBasePath(basePath)
    if (path.startsWith('/')) {
      return `${normalizedBase}${path}`
    }
    return `${normalizedBase}/${path}`
  }

  private isAbsoluteHttpUrl(value: string): boolean {
    const normalized = value.toLowerCase()
    return normalized.startsWith('http://') || normalized.startsWith('https://')
  }

  private isServerUrl(url: string, basePath: string): boolean {
    const normalizedBase = this.normalizeBasePath(basePath)
    return url === normalizedBase || url.startsWith(`${normalizedBase}/`) || url.startsWith(`${normalizedBase}?`)
  }

  private hasApiKey(url: string): boolean {
    return url.toLowerCase().includes('api_key=')
  }
}
