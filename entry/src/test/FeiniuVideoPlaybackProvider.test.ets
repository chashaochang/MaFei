import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import { MediaPlaybackEngineHint } from '../main/ets/media/playback/MediaPlaybackModels'
import { MediaPlaybackResolveRequest } from '../main/ets/media/playback/MediaPlaybackProvider'
import {
  FeiniuVideoApiRequest,
  FeiniuVideoHttpMethod,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'
import {
  FeiniuVideoPlaybackAccess
} from '../main/ets/features/feiniuvideo/playback/FeiniuVideoPlaybackMapper'
import {
  FeiniuVideoPlaybackProvider,
  FeiniuVideoPlaybackService
} from '../main/ets/features/feiniuvideo/playback/FeiniuVideoPlaybackProvider'
import {
  FnMediaMutationResp,
  FnMediaPlayPlayResp,
  FnMediaPlayInfoResp,
  FnMediaResetQualityResp,
  FnMediaStreamListResp
} from '../main/ets/features/feiniuvideo/playback/FeiniuVideoPlaybackDtos'
import {
  FnMediaItemDetailResp,
  FnMediaListResp
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoDtos'

class FakeFeiniuPlaybackService implements FeiniuVideoPlaybackService {
  readonly requests: FeiniuVideoApiRequest[] = []
  itemResponse: FnMediaListResp<FnMediaItemDetailResp> = {
    code: 0,
    msg: '',
    data: {
      guid: 'episode-1',
      title: 'Episode 1',
      type: 'Episode',
      parent_guid: 'season-1',
      ancestor_guid: 'series-1',
      ancestor_name: 'Series One',
      duration: 1800,
      ts: 75
    }
  }
  playInfoResponse: FnMediaPlayInfoResp = {
    code: 0,
    msg: '',
    data: {
      guid: 'episode-1',
      media_guid: 'media-1',
      video_guid: 'video-1',
      audio_guid: 'audio-1',
      subtitle_guid: 'subtitle-1',
      ts: 90
    }
  }
  playResponse: FnMediaPlayPlayResp = {
    code: 0,
    msg: '',
    data: {
      media_guid: 'media-1',
      video_guid: 'video-1',
      audio_guid: 'audio-2',
      audio_index: 3,
      subtitle_guid: 'subtitle-2',
      subtitle_index: 4,
      play_link: '/v/api/v1/media/hls/play-1/master.m3u8'
    }
  }
  streamResponse: FnMediaStreamListResp = {
    code: 0,
    msg: '',
    data: {
      files: [{ guid: 'media-1', file_name: 'episode.mkv' }],
      video_streams: [{
        media_guid: 'media-1',
        guid: 'video-1',
        index: 0,
        codec_name: 'hevc',
        resolution_type: '1080P',
        width: 1920,
        height: 1080,
        duration: 1800,
        bps: 8000000
      }],
      audio_streams: [{
        media_guid: 'media-1',
        guid: 'audio-1',
        index: 1,
        codec_name: 'aac',
        channels: 2,
        is_default: 1
      }],
      subtitle_streams: [{
        media_guid: 'media-1',
        guid: 'subtitle-1',
        index: 2,
        codec_name: 'srt',
        is_external: 1,
        is_default: 1
      }]
    }
  }
  episodeResponse: FnMediaListResp<FnMediaItemDetailResp[]> = {
    code: 0,
    msg: '',
    data: []
  }
  mutationResponse: FnMediaMutationResp = {
    code: 0,
    msg: ''
  }
  resetQualityResponse: FnMediaResetQualityResp = {
    code: 0,
    msg: '',
    data: {}
  }

  access(): FeiniuVideoPlaybackAccess {
    return new FeiniuVideoPlaybackAccess('http://lan:5666', 'token-1', 'session=1')
  }

  loadText(): Promise<string> {
    return Promise.resolve('1\n00:00:01,000 --> 00:00:02,000\nsubtitle')
  }

  async request<T>(request: FeiniuVideoApiRequest): Promise<T> {
    this.requests.push(request)
    if (request.path.startsWith('/v/api/v1/item/')) return this.itemResponse as T
    if (request.path === '/v/api/v1/play/info') return this.playInfoResponse as T
    if (request.path === '/v/api/v1/play/play') return this.playResponse as T
    if (request.path.startsWith('/v/api/v1/stream/list/')) return this.streamResponse as T
    if (request.path.startsWith('/v/api/v1/episode/list/')) return this.episodeResponse as T
    if (request.path === '/v/api/v1/play/record' || request.path === '/v/api/v1/item/watched') {
      return this.mutationResponse as T
    }
    if (request.path === '/v/api/v1/media/p') {
      return this.resetQualityResponse as T
    }
    throw new FeiniuVideoRequestError(
      FeiniuVideoRequestFailureKind.InvalidRequest,
      `Unexpected request ${request.path}`
    )
  }
}

function session(): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.FeiniuVideo,
    providerAccountId: 'feiniu-1',
    userId: 'user-1',
    originalAddress: 'FN123456',
    baseUrl: 'http://lan:5666'
  })
}

function request(owner: MediaProviderSession, startTimeTicks?: number, maxStreamingBitrate?: number,
  audioStreamIndex?: number, subtitleStreamIndex?: number): MediaPlaybackResolveRequest {
  return {
    ref: new MediaRef(owner.provider, owner.accountScope, 'episode-1', MediaRefKind.Episode),
    startTimeTicks: startTimeTicks,
    maxStreamingBitrate: maxStreamingBitrate,
    audioStreamIndex: audioStreamIndex,
    subtitleStreamIndex: subtitleStreamIndex,
    autoOpenLiveStream: true,
    engineHint: MediaPlaybackEngineHint.Auto
  }
}

function requestsFor(service: FakeFeiniuPlaybackService, path: string): FeiniuVideoApiRequest[] {
  return service.requests.filter((item: FeiniuVideoApiRequest): boolean => item.path === path)
}

export default function feiniuVideoPlaybackProviderTest() {
  describe('FeiniuVideoPlaybackProvider', () => {
    it('resolvesDirectSourceAndUsesServerResumeWhenUnspecified', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuPlaybackService()
      const provider = new FeiniuVideoPlaybackProvider(owner, service)

      const source = await provider.resolveSource(request(owner))
      const streams = await provider.prepareStreams(source)

      expect(source.startTimeMs).assertEqual(90000)
      expect(streams.videoUrl).assertEqual('http://lan:5666/v/api/v1/media/range/media-1')
      expect(streams.videoHeaders[0].name).assertEqual('Authorization')
      expect(requestsFor(service, '/v/api/v1/play/info').length).assertEqual(1)
      expect(requestsFor(service, '/v/api/v1/play/record').length).assertEqual(0)
    })

    it('preservesExplicitZeroAndReportsLifecycleRecords', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuPlaybackService()
      const provider = new FeiniuVideoPlaybackProvider(owner, service)
      const source = await provider.resolveSource(request(owner, 0))

      expect(source.startTimeMs).assertEqual(0)
      await provider.reportProgress(source, {
        positionTicks: 300000000,
        paused: false,
        muted: false
      })
      await provider.reportStop(source, {
        positionTicks: 600000000,
        failed: false
      })
      await provider.reportCompleted(source)

      const records = requestsFor(service, '/v/api/v1/play/record')
      expect(records.length).assertEqual(3)
      expect(records[0].bodyText.indexOf('"ts":30') >= 0).assertTrue()
      expect(records[1].bodyText.indexOf('"ts":60') >= 0).assertTrue()
      expect(requestsFor(service, '/v/api/v1/item/watched').length).assertEqual(1)
    })

    it('loadsExternalSubtitleAndRejectsCrossAccountRefs', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuPlaybackService()
      const provider = new FeiniuVideoPlaybackProvider(owner, service)
      const source = await provider.resolveSource(request(owner))

      const subtitle = await provider.loadSubtitle(source, source.subtitleStreams[0])
      expect(subtitle.indexOf('subtitle') >= 0).assertTrue()

      const other = new MediaProviderSession({
        provider: MediaProviderKind.FeiniuVideo,
        providerAccountId: 'feiniu-2',
        userId: 'user-2',
        originalAddress: 'FNOTHER',
        baseUrl: 'http://other:5666'
      })
      let rejected = false
      try {
        await provider.resolveSource(request(other))
      } catch (_) {
        rejected = true
      }
      expect(rejected).assertTrue()
    })

    it('closesOnlyTranscodedPlayLinks', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuPlaybackService()
      const provider = new FeiniuVideoPlaybackProvider(owner, service)
      const source = await provider.resolveSource(request(owner))

      await provider.closeTranscode(source)

      expect(requestsFor(service, '/v/api/v1/media/p').length).assertEqual(0)
      expect(service.requests[0].method).assertEqual(FeiniuVideoHttpMethod.Get)
    })

    it('switchesQualityAndTracksThroughTranscodeThenQuitsOnce', 0, async () => {
      const owner = session()
      const service = new FakeFeiniuPlaybackService()
      service.streamResponse.data.audio_streams?.push({
        media_guid: 'media-1',
        guid: 'audio-2',
        index: 3,
        codec_name: 'aac',
        channels: 6
      })
      service.streamResponse.data.subtitle_streams?.push({
        media_guid: 'media-1',
        guid: 'subtitle-2',
        index: 4,
        codec_name: 'srt',
        is_external: 1
      })
      const provider = new FeiniuVideoPlaybackProvider(owner, service)

      const source = await provider.resolveSource(request(owner, undefined, 2000000, 3, 4))
      const streams = await provider.prepareStreams(source)
      const playRequests = requestsFor(service, '/v/api/v1/play/play')

      expect(playRequests.length).assertEqual(1)
      expect(playRequests[0].bodyText.indexOf('"bitrate":2000000') >= 0).assertTrue()
      expect(playRequests[0].bodyText.indexOf('"startTimestamp":90') >= 0).assertTrue()
      expect(playRequests[0].bodyText.indexOf('"audio_guid":"audio-2"') >= 0).assertTrue()
      expect(playRequests[0].bodyText.indexOf('"subtitle_guid":"subtitle-2"') >= 0).assertTrue()
      expect(source.selectedAudioStreamIndex).assertEqual(3)
      expect(source.selectedSubtitleStreamIndex).assertEqual(4)
      expect(streams.videoUrl).assertEqual('http://lan:5666/v/api/v1/media/hls/play-1/master.m3u8')

      await provider.closeTranscode(source)
      await provider.closeTranscode(source)

      const quitRequests = requestsFor(service, '/v/api/v1/media/p')
      expect(quitRequests.length).assertEqual(1)
      expect(quitRequests[0].bodyText.indexOf('"req":"media.quit"') >= 0).assertTrue()
      expect(quitRequests[0].bodyText.indexOf('/v/api/v1/media/hls/play-1/master.m3u8') >= 0).assertTrue()
    })
  })
}
