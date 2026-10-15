import { describe, expect, it } from '@ohos/hypium'
import {
  MediaProtocol,
  MediaSourceInfo,
  PlaybackErrorCode,
  PlaybackInfoDto,
  PlaybackInfoResponse,
  PlaybackProgressInfo,
  PlaybackStartInfo,
  PlaybackStopInfo
} from 'sdk/src/main/ets/generated-client/models'
import { MusicDeviceProfile } from '../main/ets/features/music/MusicDeviceProfile'
import {
  MusicMediaInfoClient,
  MusicPlaybackApiService,
  MusicPlaybackRequestSession,
  MusicPlaybackServiceError,
  MusicPlaystateClient,
  redactMusicPlaybackUrl
} from '../main/ets/features/music/MusicPlaybackApiService'
import {
  MUSIC_TICKS_PER_MILLISECOND,
  MusicPlaybackErrorCode,
  MusicPlayMethod,
  MusicResolvedSource
} from '../main/ets/features/music/MusicModels'
import { MusicSourceResolver } from '../main/ets/features/music/MusicSourceResolver'

class TestMediaSource implements MediaSourceInfo {
  Protocol?: MediaProtocol
  Id?: string | null
  Path?: string | null
  Container?: string | null
  RunTimeTicks?: number | null
  SupportsTranscoding?: boolean
  SupportsDirectStream?: boolean
  SupportsDirectPlay?: boolean
  LiveStreamId?: string | null
  TranscodingUrl?: string | null
}

class TestPlaybackInfoResponse implements PlaybackInfoResponse {
  MediaSources?: MediaSourceInfo[]
  PlaySessionId?: string | null
  ErrorCode?: PlaybackErrorCode
}

class FakeMusicMediaInfoClient implements MusicMediaInfoClient {
  response: PlaybackInfoResponse = new TestPlaybackInfoResponse()
  error?: TestHttpError
  lastPlaybackInfo?: PlaybackInfoDto

  async getPlaybackInfo(_itemId: string, playbackInfo: PlaybackInfoDto): Promise<PlaybackInfoResponse> {
    this.lastPlaybackInfo = playbackInfo
    if (this.error) {
      throw this.error
    }
    return this.response
  }
}

class FakeMusicPlaystateClient implements MusicPlaystateClient {
  start?: PlaybackStartInfo
  progress?: PlaybackProgressInfo
  stopped?: PlaybackStopInfo

  async reportStart(info: PlaybackStartInfo): Promise<void> {
    this.start = info
  }

  async reportProgress(info: PlaybackProgressInfo): Promise<void> {
    this.progress = info
  }

  async reportStopped(info: PlaybackStopInfo): Promise<void> {
    this.stopped = info
  }
}

class TestHttpResponse {
  status: number = 0
}

class TestHttpError extends Error {
  isAxiosError: boolean = true
  response?: TestHttpResponse

  constructor(status?: number) {
    super('test HTTP error')
    if (status !== undefined) {
      this.response = new TestHttpResponse()
      this.response.status = status
    }
  }
}

class TestResolvedSource implements MusicResolvedSource {
  itemId: string = 'track-1'
  mediaSourceId: string = 'source-1'
  playSessionId: string = 'session-1'
  liveStreamId: string = ''
  url: string = ''
  playMethod: MusicPlayMethod = MusicPlayMethod.DirectPlay
  durationMs: number = 180000
  startPositionMs: number = 0
  scope: string = 'http://server|user-1'
}

function mediaSource(protocol: MediaProtocol, directPlay: boolean,
  directStream: boolean, transcode: boolean): TestMediaSource {
  const source = new TestMediaSource()
  source.Id = 'source-1'
  source.Protocol = protocol
  source.Container = 'mp3'
  source.RunTimeTicks = 180000 * MUSIC_TICKS_PER_MILLISECOND
  source.SupportsDirectPlay = directPlay
  source.SupportsDirectStream = directStream
  source.SupportsTranscoding = transcode
  return source
}

function playbackInfo(source?: MediaSourceInfo, playSessionId: string = 'session-1'): TestPlaybackInfoResponse {
  const response = new TestPlaybackInfoResponse()
  response.PlaySessionId = playSessionId
  response.MediaSources = source ? [source] : []
  return response
}

function requestSession(basePath: string, token: string, mediaInfo: MusicMediaInfoClient,
  playstate: MusicPlaystateClient): MusicPlaybackRequestSession {
  return new MusicPlaybackRequestSession(
    `${basePath}|user-1`,
    basePath,
    'user-1',
    'server-id',
    token,
    'device-1',
    mediaInfo,
    playstate
  )
}

async function resolvedErrorCode(resolver: MusicSourceResolver): Promise<MusicPlaybackErrorCode> {
  try {
    await resolver.resolve('track-1', 0)
  } catch (error) {
    return (error as MusicPlaybackServiceError).code
  }
  return MusicPlaybackErrorCode.None
}

export default function musicSourceResolverTest() {
  describe('MusicSourceResolver', () => {
    it('usesAnAudioOnlyDeviceProfile', 0, () => {
      const profile = MusicDeviceProfile.create()

      expect(profile.DirectPlayProfiles?.length).assertEqual(2)
      expect(profile.TranscodingProfiles?.length).assertEqual(1)
      expect(profile.TranscodingProfiles?.[0].Container).assertEqual('mp3')
    })

    it('buildsTheOfficialStaticAudioStreamForFileDirectPlay', 0, async () => {
      const mediaInfo = new FakeMusicMediaInfoClient()
      mediaInfo.response = playbackInfo(mediaSource(MediaProtocol.File, true, false, true))
      const playstate = new FakeMusicPlaystateClient()
      const service = new MusicPlaybackApiService(
        () => requestSession('http://server', 'secret-token', mediaInfo, playstate)
      )

      const result = await new MusicSourceResolver(service).resolve('track-1', 1200)

      expect(result.url.includes('/Audio/track-1/stream.mp3')).assertTrue()
      expect(result.url.includes('static=true')).assertTrue()
      expect(result.url.includes('playSessionId=session-1')).assertTrue()
      expect(result.url.includes('deviceId=device-1')).assertTrue()
      expect(result.url.includes('startTimeTicks=12000000')).assertTrue()
      expect(result.url.includes('api_key=secret-token')).assertTrue()
      expect(result.playSessionId).assertEqual('session-1')
      expect(result.startPositionMs).assertEqual(1200)
    })

    it('keepsAnExternalDirectHttpUrlWithoutLeakingTheJellyfinToken', 0, async () => {
      const source = mediaSource(MediaProtocol.Http, true, false, true)
      source.Path = 'https://cdn.example/audio/track-1.mp3'
      const mediaInfo = new FakeMusicMediaInfoClient()
      mediaInfo.response = playbackInfo(source)
      const service = new MusicPlaybackApiService(
        () => requestSession('http://server', 'secret-token', mediaInfo, new FakeMusicPlaystateClient())
      )

      const result = await new MusicSourceResolver(service).resolve('track-1', 0)

      expect(result.url).assertEqual('https://cdn.example/audio/track-1.mp3')
      expect(result.playMethod).assertEqual(MusicPlayMethod.DirectPlay)
    })

    it('usesTheOfficialDirectStreamEndpointWhenRemuxingIsRequired', 0, async () => {
      const mediaInfo = new FakeMusicMediaInfoClient()
      mediaInfo.response = playbackInfo(mediaSource(MediaProtocol.File, false, true, true))
      const service = new MusicPlaybackApiService(
        () => requestSession('http://server', 'secret-token', mediaInfo, new FakeMusicPlaystateClient())
      )

      const result = await new MusicSourceResolver(service).resolve('track-1', 0)

      expect(result.url.includes('/Audio/track-1/stream.mp3')).assertTrue()
      expect(result.url.includes('static=true')).assertFalse()
      expect(result.playMethod).assertEqual(MusicPlayMethod.DirectStream)
    })

    it('usesTheServerTranscodingUrlWhenDirectPlaybackIsUnavailable', 0, async () => {
      const source = mediaSource(MediaProtocol.File, false, false, true)
      source.TranscodingUrl = '/Audio/track-1/universal?audioCodec=mp3'
      const mediaInfo = new FakeMusicMediaInfoClient()
      mediaInfo.response = playbackInfo(source)
      const service = new MusicPlaybackApiService(
        () => requestSession('http://server', 'secret-token', mediaInfo, new FakeMusicPlaystateClient())
      )

      const result = await new MusicSourceResolver(service).resolve('track-1', 0)

      expect(result.playMethod).assertEqual(MusicPlayMethod.Transcode)
      expect(result.url.startsWith('http://server/Audio/track-1/universal')).assertTrue()
      expect(result.url.includes('api_key=secret-token')).assertTrue()
    })

    it('mapsMissingSessionAndSourceToNoPlayableSource', 0, async () => {
      const missingSessionClient = new FakeMusicMediaInfoClient()
      missingSessionClient.response = playbackInfo(mediaSource(MediaProtocol.File, true, false, false), '')
      const missingSessionService = new MusicPlaybackApiService(
        () => requestSession('http://server', 'token', missingSessionClient, new FakeMusicPlaystateClient())
      )
      const missingSourceClient = new FakeMusicMediaInfoClient()
      missingSourceClient.response = playbackInfo()
      const missingSourceService = new MusicPlaybackApiService(
        () => requestSession('http://server', 'token', missingSourceClient, new FakeMusicPlaystateClient())
      )

      expect(await resolvedErrorCode(new MusicSourceResolver(missingSessionService)))
        .assertEqual(MusicPlaybackErrorCode.NoPlayableSource)
      expect(await resolvedErrorCode(new MusicSourceResolver(missingSourceService)))
        .assertEqual(MusicPlaybackErrorCode.NoPlayableSource)
    })

    it('mapsHttpFailuresWithoutReturningRawTransportErrors', 0, async () => {
      const mediaInfo = new FakeMusicMediaInfoClient()
      const service = new MusicPlaybackApiService(
        () => requestSession('http://server', 'token', mediaInfo, new FakeMusicPlaystateClient())
      )
      const resolver = new MusicSourceResolver(service)

      mediaInfo.error = new TestHttpError(401)
      expect(await resolvedErrorCode(resolver)).assertEqual(MusicPlaybackErrorCode.Authentication)
      mediaInfo.error = new TestHttpError(403)
      expect(await resolvedErrorCode(resolver)).assertEqual(MusicPlaybackErrorCode.PermissionDenied)
      mediaInfo.error = new TestHttpError(404)
      expect(await resolvedErrorCode(resolver)).assertEqual(MusicPlaybackErrorCode.NotFound)
      mediaInfo.error = new TestHttpError()
      expect(await resolvedErrorCode(resolver)).assertEqual(MusicPlaybackErrorCode.Network)
    })

    it('redactsAuthenticatedUrlsInDiagnostics', 0, () => {
      const diagnostic = redactMusicPlaybackUrl(
        'http://server/Audio/track-1/stream.mp3?api_key=secret-token&static=true'
      )

      expect(diagnostic.includes('secret-token')).assertFalse()
      expect(diagnostic.includes('api_key=REDACTED')).assertTrue()
    })

    it('keepsUrlAndPlaybackReportsOnTheOriginalRequestSnapshot', 0, async () => {
      const firstMediaInfo = new FakeMusicMediaInfoClient()
      firstMediaInfo.response = playbackInfo(mediaSource(MediaProtocol.File, true, false, true))
      const firstPlaystate = new FakeMusicPlaystateClient()
      const secondMediaInfo = new FakeMusicMediaInfoClient()
      const secondPlaystate = new FakeMusicPlaystateClient()
      let activeSession = requestSession('http://server-a', 'token-a', firstMediaInfo, firstPlaystate)
      const service = new MusicPlaybackApiService(() => activeSession)
      const playback = await service.getPlaybackInfo('track-1', 0)
      activeSession = requestSession('http://server-b', 'token-b', secondMediaInfo, secondPlaystate)

      const url = service.buildStaticStreamUrl(playback)
      const resolved = new TestResolvedSource()
      resolved.url = url
      resolved.scope = playback.scope
      await service.reportStart(resolved, 1000)
      await service.reportProgress(resolved, 2000, true)
      await service.reportStop(resolved, 3000, true)

      expect(url.startsWith('http://server-a/')).assertTrue()
      expect(url.includes('api_key=token-a')).assertTrue()
      expect(url.includes('token-b')).assertFalse()
      expect(firstPlaystate.start?.PositionTicks).assertEqual(1000 * MUSIC_TICKS_PER_MILLISECOND)
      expect(firstPlaystate.progress?.IsPaused).assertTrue()
      expect(firstPlaystate.stopped?.PositionTicks).assertEqual(180000 * MUSIC_TICKS_PER_MILLISECOND)
      expect(secondPlaystate.start === undefined).assertTrue()
    })
  })
}
