import { describe, expect, it } from '@ohos/hypium'
import { Api } from 'sdk/src/main/ets/api'
import { PlaybackInfoResponse } from 'sdk'
import { ClientInfo } from 'sdk/src/main/ets/models/client-info'
import { DeviceInfo } from 'sdk/src/main/ets/models/device-info'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  MediaProtocol,
  MediaSourceInfo,
  MediaStreamType,
  PlaybackProgressInfo,
  PlaybackStartInfo,
  PlaybackStopInfo,
  SubtitleDeliveryMethod
} from 'sdk/src/main/ets/generated-client/models'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackSource,
  MediaPlaybackStream
} from '../main/ets/media/playback/MediaPlaybackModels'
import { MediaPlaybackResolveRequest } from '../main/ets/media/playback/MediaPlaybackProvider'
import {
  JellyfinPlaybackInfoRequest,
  JellyfinPlaybackProvider,
  JellyfinPlaybackRequestSession,
  JellyfinPlaybackService
} from '../main/ets/media/jellyfin/JellyfinPlaybackProvider'

class FakeJellyfinPlaybackService implements JellyfinPlaybackService {
  itemResult: BaseItemDto = {
    Id: 'episode-1',
    Name: 'Episode 1',
    SeriesId: 'series-1',
    SeriesName: 'Series One',
    Type: BaseItemKind.Episode,
    RunTimeTicks: 18000000000,
    UserData: { PlaybackPositionTicks: 900000000 }
  }
  sourceResult: MediaSourceInfo = {
    Id: 'episode-1',
    Name: 'Main',
    Container: 'mkv',
    Protocol: MediaProtocol.File,
    SupportsDirectPlay: true,
    SupportsDirectStream: false,
    SupportsTranscoding: false,
    RunTimeTicks: 18000000000,
    DefaultAudioStreamIndex: 1,
    DefaultSubtitleStreamIndex: 2,
    MediaStreams: [{
      Index: 0,
      Type: MediaStreamType.Video,
      Codec: 'hevc'
    }, {
      Index: 1,
      Type: MediaStreamType.Audio,
      Codec: 'aac',
      IsDefault: true
    }, {
      Index: 2,
      Type: MediaStreamType.Subtitle,
      Codec: 'srt',
      IsExternal: true,
      IsTextSubtitleStream: true,
      DeliveryMethod: SubtitleDeliveryMethod.External,
      DeliveryUrl: '/Videos/episode-1/Subtitles/2/0/Stream.srt'
    }]
  }
  itemsResult: BaseItemDtoQueryResult = { Items: [] }
  episodesResult: BaseItemDtoQueryResult = { Items: [] }
  lastPlaybackInfoRequest?: JellyfinPlaybackInfoRequest
  lastStart?: PlaybackStartInfo
  lastProgress?: PlaybackProgressInfo
  lastStop?: PlaybackStopInfo
  markedItemId: string = ''
  closedPlaySessionId: string = ''

  createSession(owner: MediaProviderSession): JellyfinPlaybackRequestSession {
    const clientInfo: ClientInfo = { name: 'MaFei', version: '2.0.0.0' }
    const deviceInfo: DeviceInfo = { id: 'device-1', name: 'HarmonyOS' }
    const api = new Api('https://media.example', clientInfo, deviceInfo, 'test-token')
    return new JellyfinPlaybackRequestSession(owner, api, 'MediaBrowser Token="test"')
  }

  getItem(_session: JellyfinPlaybackRequestSession, _itemId: string): Promise<BaseItemDto> {
    return Promise.resolve(this.itemResult)
  }

  getPlaybackInfo(_session: JellyfinPlaybackRequestSession,
    request: JellyfinPlaybackInfoRequest): Promise<PlaybackInfoResponse> {
    this.lastPlaybackInfoRequest = request
    const response: PlaybackInfoResponse = {
      PlaySessionId: 'play-1',
      MediaSources: [this.sourceResult]
    }
    return Promise.resolve(response)
  }

  getStaticVideoUrl(_session: JellyfinPlaybackRequestSession,
    _source: MediaPlaybackSource): Promise<string> {
    return Promise.resolve('https://media.example/Videos/episode-1/stream')
  }

  getContainerVideoUrl(_session: JellyfinPlaybackRequestSession, _source: MediaPlaybackSource,
    container: string): Promise<string> {
    return Promise.resolve(`https://media.example/Videos/episode-1/stream.${container}`)
  }

  getItems(_session: JellyfinPlaybackRequestSession, _ids: string[]): Promise<BaseItemDtoQueryResult> {
    return Promise.resolve(this.itemsResult)
  }

  getEpisodes(_session: JellyfinPlaybackRequestSession, _seriesId: string): Promise<BaseItemDtoQueryResult> {
    return Promise.resolve(this.episodesResult)
  }

  getSubtitle(_session: JellyfinPlaybackRequestSession, _source: MediaPlaybackSource,
    _stream: MediaPlaybackStream): Promise<string> {
    return Promise.resolve('subtitle')
  }

  reportStart(_session: JellyfinPlaybackRequestSession, info: PlaybackStartInfo): Promise<void> {
    this.lastStart = info
    return Promise.resolve()
  }

  reportProgress(_session: JellyfinPlaybackRequestSession, info: PlaybackProgressInfo): Promise<void> {
    this.lastProgress = info
    return Promise.resolve()
  }

  reportStop(_session: JellyfinPlaybackRequestSession, info: PlaybackStopInfo): Promise<void> {
    this.lastStop = info
    return Promise.resolve()
  }

  markPlayed(_session: JellyfinPlaybackRequestSession, itemId: string): Promise<void> {
    this.markedItemId = itemId
    return Promise.resolve()
  }

  closeTranscode(_session: JellyfinPlaybackRequestSession, playSessionId: string): Promise<void> {
    this.closedPlaySessionId = playSessionId
    return Promise.resolve()
  }
}

function session(providerAccountId: string = 'jellyfin-account-1'): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.Jellyfin,
    providerAccountId: providerAccountId,
    userId: 'user-1',
    originalAddress: 'https://media.example',
    baseUrl: 'https://media.example'
  })
}

function request(owner: MediaProviderSession, startTimeTicks?: number): MediaPlaybackResolveRequest {
  return {
    ref: new MediaRef(owner.provider, owner.accountScope, 'episode-1', MediaRefKind.Episode),
    startTimeTicks: startTimeTicks,
    autoOpenLiveStream: true,
    engineHint: MediaPlaybackEngineHint.Auto
  }
}

export default function jellyfinPlaybackProviderTest() {
  describe('JellyfinPlaybackProvider', () => {
    it('usesServerResumeUnlessCallerExplicitlyRequestsZero', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinPlaybackService()
      const provider = new JellyfinPlaybackProvider(owner, service)

      const resumed = await provider.resolveSource(request(owner))
      expect(resumed.startTimeMs).assertEqual(90000)
      expect(service.lastPlaybackInfoRequest?.startTimeTicks).assertEqual(900000000)

      const fromStart = await provider.resolveSource(request(owner, 0))
      expect(fromStart.startTimeMs).assertEqual(0)
      expect(service.lastPlaybackInfoRequest?.startTimeTicks).assertEqual(0)
    })

    it('preparesAuthenticatedStreamsAndDelegatesLifecycle', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinPlaybackService()
      const provider = new JellyfinPlaybackProvider(owner, service)
      const source = await provider.resolveSource(request(owner, 0))
      const streams = await provider.prepareStreams(source)

      expect(streams.videoUrl).assertEqual('https://media.example/Videos/episode-1/stream')
      expect(streams.videoHeaders[0].name).assertEqual('Authorization')
      expect(streams.externalSubtitles[0].headers[0].name).assertEqual('Authorization')

      await provider.reportStart(source)
      await provider.reportProgress(source, { positionTicks: 300000000, paused: true, muted: false })
      await provider.reportStop(source, { positionTicks: 600000000, failed: false })
      await provider.reportCompleted(source)

      expect(service.lastStart?.PositionTicks).assertEqual(0)
      expect(service.lastProgress?.PositionTicks).assertEqual(300000000)
      expect(service.lastProgress?.IsPaused).assertTrue()
      expect(service.lastStop?.PositionTicks).assertEqual(600000000)
      expect(service.markedItemId).assertEqual('episode-1')
    })

    it('preparesAndClosesServerTranscode', 0, async () => {
      const owner = session()
      const service = new FakeJellyfinPlaybackService()
      service.sourceResult = {
        Id: 'episode-1',
        Name: 'Transcode',
        Container: 'mkv',
        Protocol: MediaProtocol.File,
        SupportsDirectPlay: false,
        SupportsDirectStream: false,
        SupportsTranscoding: true,
        TranscodingUrl: '/Videos/episode-1/master.m3u8',
        TranscodingSubProtocol: 'hls',
        RunTimeTicks: 18000000000,
        MediaStreams: []
      }
      const provider = new JellyfinPlaybackProvider(owner, service)
      const source = await provider.resolveSource(request(owner))
      const streams = await provider.prepareStreams(source)

      expect(streams.videoUrl).assertEqual('https://media.example/Videos/episode-1/master.m3u8')
      expect(source.usesServerTranscoding()).assertTrue()
      await provider.closeTranscode(source)
      expect(service.closedPlaySessionId).assertEqual('play-1')
    })

    it('rejectsReferencesFromAnotherAccount', 0, async () => {
      const provider = new JellyfinPlaybackProvider(session(), new FakeJellyfinPlaybackService())
      let rejected = false
      try {
        await provider.resolveSource(request(session('jellyfin-account-2')))
      } catch (_) {
        rejected = true
      }
      expect(rejected).assertTrue()
    })
  })
}
