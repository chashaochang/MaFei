import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackItemKind,
  MediaPlaybackPlayMethod,
  MediaPlaybackProtocol,
  MediaPlaybackSource,
  MediaPlaybackSourceOptions,
  MediaPlaybackStreamType,
  MediaPlaybackSubtitleDeliveryMethod
} from '../main/ets/media/playback/MediaPlaybackModels'

function session(): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.Jellyfin,
    providerAccountId: 'jellyfin-account-1',
    userId: 'user-1',
    originalAddress: 'https://media.example',
    baseUrl: 'https://media.example'
  })
}

function sourceOptions(transcode: boolean = false, subtitleIndex?: number,
  container: string = 'mkv'): MediaPlaybackSourceOptions {
  const owner = session()
  return {
    ref: new MediaRef(owner.provider, owner.accountScope, 'episode-1', MediaRefKind.Episode),
    item: {
      Id: 'episode-1',
      Name: 'Episode 1',
      SeriesId: 'series-1',
      SeriesName: 'Series One',
      Type: MediaPlaybackItemKind.Episode,
      RunTimeTicks: 18000000000
    },
    sourceInfo: {
      Id: 'media-1',
      Name: 'Main',
      Container: container,
      Protocol: MediaPlaybackProtocol.File,
      SupportsDirectPlay: !transcode,
      SupportsDirectStream: false,
      SupportsTranscoding: transcode,
      TranscodingUrl: transcode ? '/Videos/episode-1/master.m3u8' : undefined,
      TranscodingSubProtocol: transcode ? 'hls' : undefined,
      RunTimeTicks: 18000000000,
      DefaultAudioStreamIndex: 2,
      DefaultSubtitleStreamIndex: 4,
      MediaStreams: [{
        Index: 0,
        Type: MediaPlaybackStreamType.Video,
        Codec: 'hevc'
      }, {
        Index: 1,
        Type: MediaPlaybackStreamType.Audio,
        Codec: 'aac'
      }, {
        Index: 2,
        Type: MediaPlaybackStreamType.Audio,
        Codec: 'aac',
        IsDefault: true
      }, {
        Index: 4,
        Type: MediaPlaybackStreamType.Subtitle,
        Codec: 'srt',
        IsDefault: true,
        IsExternal: true,
        IsTextSubtitleStream: true,
        DeliveryMethod: MediaPlaybackSubtitleDeliveryMethod.External,
        DeliveryUrl: '/subtitle/4.srt'
      }]
    },
    playSessionId: 'play-1',
    startTimeTicks: 900000000,
    subtitleStreamIndex: subtitleIndex,
    requestHeaders: [{ name: 'Authorization', value: 'MediaBrowser Token="test"' }],
    externalSubtitles: [{
      index: 4,
      deliveryUrl: 'https://media.example/subtitle/4.srt',
      mimeType: 'application/x-subrip',
      displayTitle: 'Chinese',
      language: 'chi',
      headers: [{ name: 'Authorization', value: 'MediaBrowser Token="test"' }]
    }],
    metadata: {
      assetId: 'episode-1',
      title: 'Series One',
      subtitle: 'Episode 1'
    },
    serverUrl: owner.baseUrl,
    engineHint: MediaPlaybackEngineHint.Auto
  }
}

export default function mediaPlaybackModelsTest() {
  describe('MediaPlaybackModels', () => {
    it('selectsDefaultTracksAndKeepsAccountScopedResumeState', 0, () => {
      const source = new MediaPlaybackSource(sourceOptions())

      expect(source.playMethod).assertEqual(MediaPlaybackPlayMethod.DirectPlay)
      expect(source.startTimeMs).assertEqual(90000)
      expect(source.resume.startTimeTicks).assertEqual(900000000)
      expect(source.selectedAudioStreamIndex).assertEqual(2)
      expect(source.selectedSubtitleStreamIndex).assertEqual(4)
      expect(source.requestHeaders[0].name).assertEqual('Authorization')
      expect(source.externalSubtitleStreams[0].index).assertEqual(4)
    })

    it('preservesExplicitSubtitleOffAndMarksTranscodeCleanup', 0, () => {
      const source = new MediaPlaybackSource(sourceOptions(true, -1))

      expect(source.playMethod).assertEqual(MediaPlaybackPlayMethod.Transcode)
      expect(source.selectedSubtitleStreamIndex).assertEqual(-1)
      expect(source.usesServerTranscoding()).assertTrue()
      expect(source.transcodeContext.active).assertTrue()
      expect(source.capabilities.transcodeCleanupAvailable).assertTrue()
    })

    it('keepsIsoFallbackClientSideAndDoesNotMarkServerTranscoding', 0, () => {
      const source = new MediaPlaybackSource(sourceOptions(true, undefined, 'iso'))

      expect(source.isIsoStaticPlaybackFallback()).assertTrue()
      expect(source.getEffectivePlayMethod()).assertEqual(MediaPlaybackPlayMethod.DirectPlay)
      expect(source.usesServerTranscoding()).assertFalse()
      expect(source.transcodeContext.active).assertFalse()
    })
  })
}
